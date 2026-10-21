package ch.ge.edg.sonar.gitlab.audit.rest;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Un échange sur le réseau, un seul : le reste — cadence, capture, comptes — est à RestClient. */
final class Wire {

  private final HttpClient client;
  private final Duration timeout;

  Wire(RestClient.Settings settings) throws IOException {
    this.timeout = Duration.ofSeconds(settings.timeoutSeconds());
    HttpClient.Builder b =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(settings.timeoutSeconds()))
            .proxy(ProxySelector.getDefault())
            .followRedirects(
                settings.followRedirects()
                    ? HttpClient.Redirect.NORMAL
                    : HttpClient.Redirect.NEVER);
    if (settings.insecure()) {
      // Le client du JDK n'a pas de vérificateur de nom d'hôte : seule cette propriété l'éteint.
      System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
      try {
        b.sslContext(InsecureTls.trustEverything());
      } catch (GeneralSecurityException e) {
        throw new IOException("contexte TLS --insecure : " + e.getMessage(), e);
      }
    }
    this.client = b.build();
  }

  RestResponse once(String method, String url, @Nullable String body, Map<String, String> headers) {
    try {
      HttpRequest.BodyPublisher pub =
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
      HttpRequest.Builder rb =
          HttpRequest.newBuilder(URI.create(url)).method(method, pub).timeout(timeout);
      headers.forEach(rb::header);
      HttpResponse<String> res =
          client.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return new RestResponse.Answered(res.statusCode(), res.body(), lowerCase(res));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return RestResponse.unreachable(RestClient.INTERRUPTED);
    } catch (IOException | IllegalArgumentException e) {
      // IllegalArgumentException : une URL que URI.create ou le client refuse.
      // ConnectException.getMessage() est souvent null : sans le nom de la classe,
      // « injoignable » ne dit pas s'il s'agit d'un refus, d'un délai ou d'un échec TLS.
      String m = e.getMessage();
      String name = e.getClass().getSimpleName();
      return RestResponse.unreachable(m == null || m.isBlank() ? name : name + ": " + m);
    }
  }

  private static Map<String, String> lowerCase(HttpResponse<String> res) {
    Map<String, String> h = new HashMap<>();
    res.headers()
        .map()
        .forEach((k, v) -> h.put(k.toLowerCase(Locale.ROOT), v.isEmpty() ? "" : v.get(0)));
    return h;
  }
}
