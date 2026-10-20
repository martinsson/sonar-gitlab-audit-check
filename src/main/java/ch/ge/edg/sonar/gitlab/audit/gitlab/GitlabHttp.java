package ch.ge.edg.sonar.gitlab.audit.gitlab;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab.Response;
import ch.ge.edg.sonar.gitlab.audit.rest.InsecureTls;
import ch.ge.edg.sonar.gitlab.audit.runtime.Sleeper;
import java.io.IOException;
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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/** Le transport : une requête, le 429 attendu, la capture sur disque, les compteurs. */
final class GitlabHttp {

  private final @Nullable String token;
  private final Duration timeout;
  private final @Nullable Path dumpDir;
  private final GitlabListener listener;
  private final Sleeper sleeper;
  private final HttpClient client;

  // Comptés depuis plusieurs fils dès que parallel() est utilisé : un int nu
  // perdrait des incréments, et le total d'appels est ce sur quoi on juge le
  // coût de l'outil. Il doit être juste, pas approximativement juste.
  final AtomicInteger callCount = new AtomicInteger();
  final AtomicInteger forbiddenCount = new AtomicInteger();
  final AtomicInteger throttledCount = new AtomicInteger();

  GitlabHttp(Gitlab.Connection c, GitlabListener listener, Sleeper sleeper)
      throws IOException, GeneralSecurityException {
    this.token = c.token();
    this.timeout = Duration.ofSeconds(c.timeoutSeconds());
    this.dumpDir = c.dumpDir();
    this.listener = listener;
    this.sleeper = sleeper;
    if (dumpDir != null) Files.createDirectories(dumpDir);

    HttpClient.Builder b =
        HttpClient.newBuilder()
            .connectTimeout(this.timeout)
            .followRedirects(HttpClient.Redirect.NORMAL);
    if (c.insecure()) {
      System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
      b.sslContext(InsecureTls.trustEverything());
    }
    this.client = b.build();
  }

  /**
   * Le limiteur de débit de GitLab est configuré par instance et souvent abaissé. On respecte
   * Retry-After dès la première exécution plutôt qu'après le premier 429 rencontré en production.
   */
  Response send(String method, String uri, @Nullable String body) {
    for (int attempt = 0; ; attempt++) {
      int seq = callCount.incrementAndGet();
      try {
        HttpResponse<String> res =
            client.send(
                request(method, uri, body),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() == 429 && attempt < 3) {
          throttledCount.incrementAndGet();
          sleeper.sleep(Duration.ofSeconds(Math.min(retryAfter(res), 60)));
          continue;
        }
        if (res.statusCode() == 403) forbiddenCount.incrementAndGet();
        dump(seq, uri, res.body());
        return new Response(res.statusCode(), res.body(), headers(res));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return new Response(0, "interrompu", Map.of());
      } catch (IOException | IllegalArgumentException e) {
        // IllegalArgumentException : une URL que URI.create ou le client refuse.
        return unreachable(e);
      }
    }
  }

  private HttpRequest request(String method, String uri, @Nullable String body) {
    HttpRequest.BodyPublisher pub =
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
    HttpRequest.Builder rb =
        HttpRequest.newBuilder(URI.create(uri))
            .method(method, pub)
            .timeout(timeout)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json");
    // PRIVATE-TOKEN accepte les PAT comme les jetons de groupe ; Bearer
    // ne vaut que pour un jeton OAuth. Sans jeton du tout, on ne pose pas
    // l'en-tête : un projet public se lit anonymement, et c'est le seul
    // moyen de vérifier l'outil contre une instance réelle qu'on ne
    // possède pas.
    if (token != null && !token.isBlank()) rb.header("PRIVATE-TOKEN", token);
    return rb.build();
  }

  private static long retryAfter(HttpResponse<String> res) {
    return res.headers()
        .firstValue("retry-after")
        .map(
            v -> {
              try {
                return Long.parseLong(v.trim());
              } catch (NumberFormatException e) {
                return 5L;
              }
            })
        .orElse(5L);
  }

  private static Map<String, String> headers(HttpResponse<String> res) {
    Map<String, String> h = new HashMap<>();
    res.headers()
        .map()
        .forEach((k, v) -> h.put(k.toLowerCase(Locale.ROOT), v.isEmpty() ? "" : v.get(0)));
    return h;
  }

  /**
   * ConnectException.getMessage() est souvent null : sans le nom de la classe, « injoignable » ne
   * dit pas s'il s'agit d'un refus, d'un délai dépassé ou d'un échec TLS.
   */
  private static Response unreachable(Exception e) {
    String message = e.getMessage();
    String msg =
        message == null || message.isBlank()
            ? e.getClass().getSimpleName()
            : e.getClass().getSimpleName() + ": " + message;
    return new Response(0, msg, Map.of());
  }

  /**
   * Le nom d'une capture : numéro d'appel et requête coupés à 120 caractères, puis « .json ».
   * L'extension n'est jamais coupée : une date en nanosecondes dans l'URL suffit à dépasser.
   */
  static String dumpName(int seq, String uri) {
    String request = uri.replaceFirst("^https?://[^/]+/", "").replaceAll("[^A-Za-z0-9]+", "_");
    String stem = "%04d-%s".formatted(seq, request);
    return Gitlab.truncate(stem, 120) + ".json";
  }

  private void dump(int seq, String uri, String body) {
    if (dumpDir == null) return;
    try {
      Files.writeString(dumpDir.resolve(dumpName(seq, uri)), body);
    } catch (IOException e) {
      listener.captureNotWritten(String.valueOf(e.getMessage()));
    }
  }
}
