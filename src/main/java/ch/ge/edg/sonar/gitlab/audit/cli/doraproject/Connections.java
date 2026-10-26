package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.gitlab.GitlabWarnings;
import ch.ge.edg.sonar.gitlab.audit.gocd.GocdApi;
import ch.ge.edg.sonar.gitlab.audit.jira.JiraApi;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import ch.ge.edg.sonar.gitlab.audit.runtime.Sleeper;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Option;

/**
 * Les trois instances et leurs jetons, tels que les options ou l'environnement les donnent. Chacune
 * ne s'ouvre que si l'extraction en a besoin : sans pipeline GoCD ni projet Jira, leurs URL peuvent
 * manquer.
 */
final class Connections {

  private static final int TIMEOUT_SECONDS = 60;

  @Option(names = "--gitlab-url", defaultValue = "${env:GITLAB_URL}")
  @Nullable String gitlabUrl;

  @Option(names = "--gitlab-token", defaultValue = "${env:GITLAB_TOKEN}")
  @Nullable String gitlabToken;

  @Option(names = "--gocd-url", defaultValue = "${env:GOCD_URL}")
  @Nullable String gocdUrl;

  @Option(names = "--gocd-token", defaultValue = "${env:GOCD_TOKEN}")
  @Nullable String gocdToken;

  @Option(names = "--jira-url", defaultValue = "${env:JIRA_URL}")
  @Nullable String jiraUrl;

  @Option(names = "--jira-token", defaultValue = "${env:JIRA_TOKEN}")
  @Nullable String jiraToken;

  @Option(names = "--insecure", description = "Accepte un certificat auto-signé")
  boolean insecure;

  /** GitLab par PRIVATE-TOKEN ; ses avertissements vont sur la sortie d'erreur. */
  Gitlab gitlab(Console console, Sleeper sleeper)
      throws Stop, IOException, GeneralSecurityException {
    Gitlab.Connection c =
        new Gitlab.Connection(
            Stop.require(gitlabUrl, "GITLAB_URL"),
            Stop.require(gitlabToken, "GITLAB_TOKEN"),
            TIMEOUT_SECONDS,
            insecure,
            null);
    return new Gitlab(c, GitlabWarnings.onError(console), sleeper);
  }

  GocdApi gocd(Sleeper sleeper) throws Stop, IOException {
    return GocdApi.connect(gocdUrl, gocdToken, insecure, sleeper);
  }

  JiraApi jira(Sleeper sleeper) throws Stop, IOException {
    return JiraApi.connect(jiraUrl, jiraToken, insecure, sleeper);
  }
}
