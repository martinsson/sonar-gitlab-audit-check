package ch.ge.edg.sonar.gitlab.audit.cli.doralocate;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.gocd.GocdApi;
import ch.ge.edg.sonar.gitlab.audit.jira.JiraApi;
import ch.ge.edg.sonar.gitlab.audit.rest.AuthHeaders;
import ch.ge.edg.sonar.gitlab.audit.rest.RestClient;
import ch.ge.edg.sonar.gitlab.audit.rest.RestClient.Settings;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import java.io.IOException;
import java.io.PrintStream;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Les trois serveurs, ouverts d'après les options : GitLab par PRIVATE-TOKEN, Jira et GoCD par un
 * PAT en Bearer. Un jeton refusé (401) arrête l'outil : la suite ne lirait plus rien.
 */
final class Access {

  private static final int TIMEOUT_SECONDS = 60;

  private Access() {}

  public static Gitlab gitlab(
      @Nullable String url, @Nullable String token, boolean insecure, PrintStream err)
      throws Stop, IOException, GeneralSecurityException {
    Gitlab.Connection c =
        new Gitlab.Connection(
            Stop.require(url, "GITLAB_URL"),
            Stop.require(token, "GITLAB_TOKEN"),
            TIMEOUT_SECONDS,
            insecure,
            null);
    return new Gitlab(c, err, err);
  }

  public static JiraApi jira(
      @Nullable String url, @Nullable String token, boolean insecure, PrintStream err)
      throws Stop, IOException {
    Map<String, String> headers =
        new HashMap<>(AuthHeaders.bearer(Stop.require(token, "JIRA_TOKEN")));
    headers.put("Accept", "application/json");
    return new JiraApi(
        new RestClient(Settings.of(Stop.require(url, "JIRA_URL"), headers, insecure), err));
  }

  /** Une URL GoCD donnée avec ou sans « /go » final : les chemins d'API le remettent. */
  public static GocdApi gocd(
      @Nullable String url, @Nullable String token, boolean insecure, PrintStream err)
      throws Stop, IOException {
    String base = Stop.require(url, "GOCD_URL").replaceAll("/+$", "").replaceFirst("/go$", "");
    Map<String, String> headers = AuthHeaders.bearer(Stop.require(token, "GOCD_TOKEN"));
    return new GocdApi(new RestClient(Settings.of(base, headers, insecure), err));
  }

  /** Une réponse Jira ou GoCD, telle quelle sauf un 401. */
  static RestResponse checked(RestResponse r, RestClient http) throws Stop {
    if (r.status() == 401) throw new Stop(http.base() + " : jeton refusé (401)");
    return r;
  }

  /** Une réponse GitLab, telle quelle sauf un 401. */
  static Gitlab.Response checked(Gitlab.Response r, Gitlab gitlab) throws Stop {
    if (r.status() == 401) throw new Stop(gitlab.base + " : jeton refusé (401)");
    return r;
  }

  /** Pourquoi une réponse GitLab n'est pas exploitable, sans recopier le corps. */
  static String why(Gitlab.Response r) {
    return r.unreachable() ? Objects.toString(r.body(), "") : "HTTP " + r.status();
  }
}
