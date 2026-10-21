package ch.ge.edg.sonar.gitlab.audit.gitlab;

import ch.ge.edg.sonar.gitlab.audit.rest.Capture;
import ch.ge.edg.sonar.gitlab.audit.rest.RestClient;
import ch.ge.edg.sonar.gitlab.audit.rest.RestClient.Retries;
import ch.ge.edg.sonar.gitlab.audit.rest.RestClient.Settings;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import ch.ge.edg.sonar.gitlab.audit.runtime.Sleeper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/** GitLab sur le client commun : son en-tête de jeton, sa cadence, son compte de 403. */
final class GitlabHttp {

  /**
   * Le limiteur de débit de GitLab est configuré par instance et souvent abaissé. On respecte
   * Retry-After dès la première exécution plutôt qu'après le premier 429 rencontré en production.
   */
  private static final Retries RETRIES = new Retries(3, 0, 0);

  private final RestClient client;

  // Compté depuis plusieurs fils dès que parallel() est utilisé.
  final AtomicInteger forbiddenCount = new AtomicInteger();

  GitlabHttp(Gitlab.Connection c, GitlabListener listener, Sleeper sleeper) throws IOException {
    this.client =
        new RestClient(
            new Settings(
                c.url(), headers(c.token()), c.timeoutSeconds(), c.insecure(), true, RETRIES),
            new Capture(c.dumpDir(), null, listener::captureNotWritten),
            sleeper);
  }

  /**
   * PRIVATE-TOKEN accepte les PAT comme les jetons de groupe ; Bearer ne vaut que pour un jeton
   * OAuth. Sans jeton du tout, on ne pose pas l'en-tête : un projet public se lit anonymement, et
   * c'est le seul moyen de vérifier l'outil contre une instance réelle qu'on ne possède pas.
   */
  private static Map<String, String> headers(@Nullable String token) {
    Map<String, String> h = new LinkedHashMap<>();
    h.put("Accept", "application/json");
    h.put("Content-Type", "application/json");
    if (token != null && !token.isBlank()) h.put("PRIVATE-TOKEN", token);
    return h;
  }

  int calls() {
    return client.calls();
  }

  int throttled() {
    return client.throttled();
  }

  RestResponse send(String method, String uri, @Nullable String body) {
    RestResponse r = client.send(method, uri, body, Map.of());
    if (r.is(403)) forbiddenCount.incrementAndGet();
    return r;
  }
}
