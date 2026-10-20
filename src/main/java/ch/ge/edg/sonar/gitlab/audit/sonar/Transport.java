package ch.ge.edg.sonar.gitlab.audit.sonar;

import ch.ge.edg.sonar.gitlab.audit.runtime.Sleeper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.jspecify.annotations.Nullable;

/** Un GET avec jeton, et la cadence : 429 et 5xx retentés. */
final class Transport {

  private final @Nullable String token;
  private final Duration timeout;
  private final HttpClient client;

  // Comptés depuis plusieurs fils dès que Sonar.map() est utilisé : un int nu
  // perdrait des incréments, et le total d'appels est ce sur quoi on juge le
  // coût de l'outil. Il doit être juste, pas approximativement juste.
  private final AtomicInteger calls;
  private final Sleeper sleeper;
  private final AtomicInteger throttled = new AtomicInteger();
  private final AtomicInteger retried = new AtomicInteger();

  Transport(SonarConnection connection, AtomicInteger calls, Sleeper sleeper)
      throws GeneralSecurityException {
    this.token = connection.token();
    this.timeout = Duration.ofSeconds(connection.timeoutSeconds());
    this.calls = calls;
    this.sleeper = sleeper;
    HttpClient.Builder b =
        HttpClient.newBuilder()
            .connectTimeout(this.timeout)
            .followRedirects(HttpClient.Redirect.NORMAL);
    if (connection.insecure()) {
      System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
      b.sslContext(trustEverything());
    }
    this.client = b.build();
  }

  int throttled() {
    return throttled.get();
  }

  int retried() {
    return retried.get();
  }

  /**
   * SonarQube ne limite pas le débit lui-même, mais ce qu'on a devant lui le fait souvent — et un
   * 502 isolé d'un reverse proxy, au milieu d'une passe concurrente, ne ressemble à rien d'autre
   * qu'à un projet sans mesures. Une panne de transport doit coûter une seconde, pas une ligne
   * fausse.
   */
  Sonar.Response send(String uri) {
    for (int attempt = 0; ; attempt++) {
      calls.incrementAndGet();
      try {
        HttpResponse<String> res =
            client.send(request(uri), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        OptionalLong pause = retryPause(res, attempt);
        if (pause.isEmpty()) return new Sonar.Response(res.statusCode(), res.body());
        sleeper.sleep(Duration.ofMillis(pause.getAsLong()));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return new Sonar.Response(0, "interrompu");
      } catch (IOException | IllegalArgumentException e) {
        // ConnectException.getMessage() est souvent null : sans le nom de la
        // classe, « injoignable » ne dit pas s'il s'agit d'un refus, d'un
        // délai dépassé ou d'un échec TLS.
        return new Sonar.Response(0, describe(e));
      }
    }
  }

  private HttpRequest request(String uri) {
    return HttpRequest.newBuilder(URI.create(uri))
        .GET()
        .timeout(timeout)
        .header("Authorization", "Bearer " + token)
        .header("Accept", "application/json")
        .build();
  }

  /** L'attente avant de retenter, en millisecondes ; vide quand la réponse est définitive. */
  private OptionalLong retryPause(HttpResponse<String> res, int attempt) {
    int code = res.statusCode();
    if (code == 429 && attempt < 3) {
      throttled.incrementAndGet();
      long wait = res.headers().firstValue("retry-after").map(Transport::parseSeconds).orElse(5L);
      return OptionalLong.of(Math.min(wait, 60) * 1000);
    }
    if ((code == 502 || code == 503 || code == 504) && attempt < 2) {
      retried.incrementAndGet();
      return OptionalLong.of(1000L * (attempt + 1));
    }
    return OptionalLong.empty();
  }

  private static String describe(Exception e) {
    String message = e.getMessage();
    String name = e.getClass().getSimpleName();
    return message == null || message.isBlank() ? name : name + ": " + message;
  }

  private static long parseSeconds(String v) {
    try {
      return Long.parseLong(v.trim());
    } catch (NumberFormatException e) {
      return 5L;
    }
  }

  private static SSLContext trustEverything() throws GeneralSecurityException {
    TrustManager[] trustAll = {
      new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] c, String a) {}

        @Override
        public void checkServerTrusted(X509Certificate[] c, String a) {}

        @Override
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
      }
    };
    SSLContext ctx = SSLContext.getInstance("TLS");
    ctx.init(null, trustAll, new SecureRandom());
    return ctx;
  }
}
