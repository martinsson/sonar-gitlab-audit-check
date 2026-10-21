package ch.ge.edg.sonar.gitlab.audit.cli.doralocate;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.gitlab.GitlabWarnings;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import ch.ge.edg.sonar.gitlab.audit.runtime.Sleeper;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.jspecify.annotations.Nullable;

/**
 * GitLab, ouvert d'après les options, par PRIVATE-TOKEN ; Jira et GoCD s'ouvrent par leur client
 * ({@code JiraApi.connect}, {@code GocdApi.connect}). Un jeton refusé (401) arrête l'outil : la
 * suite ne lirait plus rien.
 */
final class Access {

  private static final int TIMEOUT_SECONDS = 60;

  private Access() {}

  public static Gitlab gitlab(
      @Nullable String url,
      @Nullable String token,
      boolean insecure,
      Console console,
      Sleeper sleeper)
      throws Stop, IOException, GeneralSecurityException {
    Gitlab.Connection c =
        new Gitlab.Connection(
            Stop.require(url, "GITLAB_URL"),
            Stop.require(token, "GITLAB_TOKEN"),
            TIMEOUT_SECONDS,
            insecure,
            null);
    return new Gitlab(c, GitlabWarnings.onError(console), sleeper);
  }

  /** Une réponse GitLab, telle quelle sauf un 401. */
  static RestResponse checked(RestResponse r, Gitlab gitlab) throws Stop {
    return Stop.unlessRefused(r, gitlab.base);
  }

  /** Pourquoi une réponse GitLab n'est pas exploitable, sans recopier le corps. */
  static String why(RestResponse r) {
    return r instanceof RestResponse.Unreachable u ? u.reason() : r.statusLine();
  }
}
