package ch.ge.edg.sonar.gitlab.audit.rest;

import ch.ge.edg.sonar.gitlab.audit.runtime.Sleeper;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Un client GET, et rien d'autre : ce que lisent les outils Jira et GoCD. Les en-têtes fixes
 * (authentification, Accept) viennent de {@link Settings}. Un 429 attend Retry-After et recommence
 * ; une coupure réseau recommence deux fois. Les redirections ne sont pas suivies : un 302 vers une
 * page de login est précisément ce qu'on veut voir.
 */
public final class RestClient {

  private static final int MAX_RETRIES = 3;
  private static final long MAX_WAIT_SECONDS = 60;

  /**
   * Ce qui ne change pas d'une requête à l'autre.
   *
   * @param base racine de l'instance, sans barre finale
   * @param headers en-têtes posés sur chaque requête
   * @param dumpDir où écrire chaque réponse brute, ou null
   */
  public record Settings(
      String base,
      Map<String, String> headers,
      int timeoutSeconds,
      boolean insecure,
      @Nullable Path dumpDir) {

    public Settings {
      base = base.trim().replaceAll("/+$", "");
      headers = Map.copyOf(headers);
    }

    public static Settings of(String base, Map<String, String> headers, boolean insecure) {
      return new Settings(base, headers, 30, insecure, null);
    }
  }

  private final Settings settings;
  private final HttpClient client;
  private final Sleeper sleeper;
  private final PrintStream err;
  private int calls;

  /**
   * Un client.
   *
   * @param err où dire qu'une capture n'a pas pu s'écrire
   */
  public RestClient(Settings settings, PrintStream err, Sleeper sleeper) throws IOException {
    this.settings = settings;
    this.err = err;
    this.sleeper = sleeper;
    if (settings.dumpDir() != null) Files.createDirectories(settings.dumpDir());
    this.client = build(settings);
  }

  private static HttpClient build(Settings settings) throws IOException {
    HttpClient.Builder b =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(settings.timeoutSeconds()))
            .proxy(ProxySelector.getDefault())
            .followRedirects(HttpClient.Redirect.NEVER);
    if (settings.insecure()) {
      System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
      try {
        b.sslContext(InsecureTls.trustEverything());
      } catch (GeneralSecurityException e) {
        throw new IOException("contexte TLS --insecure : " + e.getMessage(), e);
      }
    }
    return b.build();
  }

  public String base() {
    return settings.base();
  }

  public int calls() {
    return calls;
  }

  /** {@code path} commence par « / », ou est une URL complète (lien de pagination). */
  public RestResponse get(String path) {
    return get(path, Map.of());
  }

  /** Comme {@link #get(String)}, avec des en-têtes qui s'ajoutent à ceux des réglages. */
  public RestResponse get(String path, Map<String, String> extra) {
    String url = path.startsWith("http") ? path : settings.base() + path;
    Map<String, String> headers = new LinkedHashMap<>(settings.headers());
    headers.putAll(extra);
    RestResponse last = RestResponse.unreachable("aucun essai");
    for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
      last = once(url, headers);
      if (!retry(last, attempt)) break;
    }
    return last;
  }

  private boolean retry(RestResponse r, int attempt) {
    if (attempt + 1 >= MAX_RETRIES) return false;
    if (r.status() == 0) return true;
    if (r.status() != 429) return false;
    try {
      sleeper.sleep(Duration.ofSeconds(Math.min(retryAfter(r), MAX_WAIT_SECONDS)));
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

  private RestResponse once(String url, Map<String, String> headers) {
    calls++;
    try {
      HttpRequest.Builder rb =
          HttpRequest.newBuilder(URI.create(url))
              .GET()
              .timeout(Duration.ofSeconds(settings.timeoutSeconds()));
      headers.forEach(rb::header);
      HttpResponse<String> res =
          client.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      dump(url, res.statusCode(), res.body());
      return new RestResponse(res.statusCode(), res.body(), lowerCase(res), "");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return RestResponse.unreachable("interrompu");
    } catch (IOException | IllegalArgumentException e) {
      String m = e.getMessage();
      return RestResponse.unreachable(e.getClass().getSimpleName() + (m == null ? "" : ": " + m));
    }
  }

  private static Map<String, String> lowerCase(HttpResponse<String> res) {
    Map<String, String> h = new HashMap<>();
    res.headers()
        .map()
        .forEach((k, v) -> h.put(k.toLowerCase(Locale.ROOT), v.isEmpty() ? "" : v.get(0)));
    return h;
  }

  private void dump(String url, int status, String body) {
    Path dir = settings.dumpDir();
    if (dir == null) return;
    String name =
        "%04d-%d-%s"
            .formatted(
                calls,
                status,
                url.replaceFirst("^https?://[^/]+/", "").replaceAll("[^A-Za-z0-9]+", "_"));
    try {
      Files.writeString(
          dir.resolve(name.substring(0, Math.min(name.length(), 120)) + ".txt"), body);
    } catch (IOException e) {
      err.println("  (capture non écrite : " + e.getMessage() + ")");
    }
  }
}
