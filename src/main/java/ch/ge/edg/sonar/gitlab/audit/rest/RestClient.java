package ch.ge.edg.sonar.gitlab.audit.rest;

import ch.ge.edg.sonar.gitlab.audit.runtime.Sleeper;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/**
 * Le seul client HTTP de l'outil : Jira, GoCD, GitLab et Sonar passent tous par lui. Les en-têtes
 * fixes (authentification, Accept) viennent de {@link Settings}, la cadence de {@link Retries} : un
 * 429 attend Retry-After et recommence, un 5xx de proxy et une coupure réseau recommencent si la
 * politique le dit. Chaque réponse finale passe par la {@link Capture}, qui l'enregistre ou la
 * rejoue.
 */
public final class RestClient {

  private static final long MAX_WAIT_SECONDS = 60;
  static final String INTERRUPTED = "interrompu";

  /**
   * Combien de fois recommencer, par cause.
   *
   * @param throttled après un 429, en attendant Retry-After
   * @param serverErrors après un 502, 503 ou 504, en attendant une puis deux secondes…
   * @param unreachable après une coupure réseau, sans attendre
   */
  public record Retries(int throttled, int serverErrors, int unreachable) {

    /** Jira et GoCD : deux nouveaux essais sur un 429 ou une coupure. */
    public static final Retries DEFAULT = new Retries(2, 0, 2);
  }

  /**
   * Ce qui ne change pas d'une requête à l'autre.
   *
   * @param base racine de l'instance, sans barre finale
   * @param headers en-têtes posés sur chaque requête
   * @param followRedirects faux quand un 302 vers une page de login est précisément ce qu'on veut
   *     voir
   */
  public record Settings(
      String base,
      Map<String, String> headers,
      int timeoutSeconds,
      boolean insecure,
      boolean followRedirects,
      Retries retries) {

    public Settings {
      base = base.trim().replaceAll("/+$", "");
      headers = Map.copyOf(headers);
    }

    public static Settings of(String base, Map<String, String> headers, boolean insecure) {
      return new Settings(base, headers, 30, insecure, false, Retries.DEFAULT);
    }
  }

  private final Settings settings;
  private final @Nullable Wire wire;
  private final Capture capture;
  private final Sleeper sleeper;

  // Comptés depuis plusieurs fils dès qu'un client est partagé par une passe
  // parallèle : un int nu perdrait des incréments, et le total d'appels est ce
  // sur quoi on juge le coût de l'outil.
  private final AtomicInteger calls = new AtomicInteger();
  private final AtomicInteger throttled = new AtomicInteger();
  private final AtomicInteger retried = new AtomicInteger();

  /**
   * Un client.
   *
   * @param capture où écrire chaque réponse, ou d'où la rejouer
   */
  public RestClient(Settings settings, Capture capture, Sleeper sleeper) throws IOException {
    this.settings = settings;
    this.capture = capture;
    this.sleeper = sleeper;
    capture.prepare();
    this.wire = capture.replaying() ? null : new Wire(settings);
  }

  public String base() {
    return settings.base();
  }

  /** Les essais faits, nouveaux essais et rejeux compris. */
  public int calls() {
    return calls.get();
  }

  /** Les 429 suivis d'un nouvel essai. */
  public int throttled() {
    return throttled.get();
  }

  /** Les 5xx et coupures suivis d'un nouvel essai. */
  public int retried() {
    return retried.get();
  }

  public boolean replaying() {
    return capture.replaying();
  }

  /** Captures réclamées et absentes. */
  public int missingCaptures() {
    return capture.missing();
  }

  /** {@code path} commence par « / », ou est une URL complète (lien de pagination). */
  public RestResponse get(String path) {
    return get(path, Map.of());
  }

  /** Comme {@link #get(String)}, avec des en-têtes qui s'ajoutent à ceux des réglages. */
  public RestResponse get(String path, Map<String, String> extra) {
    return send("GET", path, null, extra);
  }

  /**
   * Une requête quelconque.
   *
   * @param body le corps envoyé, ou null
   * @param extra en-têtes qui s'ajoutent à ceux des réglages
   */
  public RestResponse send(
      String method, String path, @Nullable String body, Map<String, String> extra) {
    String url = path.startsWith("http") ? path : settings.base() + path;
    String request = request(url);
    String name = Capture.name(method, request, body);
    Optional<RestResponse> replayed = capture.replay(name, request);
    if (replayed.isPresent()) {
      calls.incrementAndGet();
      return replayed.get();
    }
    Map<String, String> headers = new LinkedHashMap<>(settings.headers());
    headers.putAll(extra);
    RestResponse r = withRetries(method, url, body, headers);
    capture.dump(name, request, r);
    return r;
  }

  /** La requête relative à la racine de l'instance : ce qui nomme une capture. */
  private String request(String url) {
    String base = settings.base();
    String rest =
        !base.isEmpty() && url.startsWith(base)
            ? url.substring(base.length())
            : url.replaceFirst("^https?://[^/]+", "");
    return rest.replaceFirst("^/+", "");
  }

  private RestResponse withRetries(
      String method, String url, @Nullable String body, Map<String, String> headers) {
    int[] spent = new int[3];
    while (true) {
      calls.incrementAndGet();
      Wire w = wire;
      RestResponse r =
          w == null
              ? RestResponse.unreachable("rejeu : aucun appel réseau")
              : w.once(method, url, body, headers);
      Optional<Duration> pause = pauseBefore(r, spent);
      if (pause.isEmpty()) return r;
      if (!pause.get().isZero() && !pauseFor(pause.get())) {
        return RestResponse.unreachable(INTERRUPTED);
      }
    }
  }

  /**
   * L'attente avant un nouvel essai ; vide quand la réponse est définitive.
   *
   * @param spent les nouveaux essais déjà faits : 429, 5xx, coupures
   */
  private Optional<Duration> pauseBefore(RestResponse r, int[] spent) {
    Retries policy = settings.retries();
    if (r.is(429) && spent[0] < policy.throttled()) {
      spent[0]++;
      throttled.incrementAndGet();
      return Optional.of(Duration.ofSeconds(Math.min(retryAfter(r), MAX_WAIT_SECONDS)));
    }
    if ((r.is(502) || r.is(503) || r.is(504)) && spent[1] < policy.serverErrors()) {
      spent[1]++;
      retried.incrementAndGet();
      return Optional.of(Duration.ofSeconds(spent[1]));
    }
    if (r instanceof RestResponse.Unreachable u
        && !INTERRUPTED.equals(u.reason())
        && spent[2] < policy.unreachable()) {
      spent[2]++;
      retried.incrementAndGet();
      return Optional.of(Duration.ZERO);
    }
    return Optional.empty();
  }

  private boolean pauseFor(Duration pause) {
    try {
      sleeper.sleep(pause);
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  static long retryAfter(RestResponse r) {
    try {
      return Math.max(0, Long.parseLong(r.header("retry-after").trim()));
    } catch (NumberFormatException e) {
      return 5;
    }
  }
}
