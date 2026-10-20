package ch.ge.edg.sonar.gitlab.audit.cli.jiraextract;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tous les tickets d'une requête JQL, page après page, chacun avec son historique complet : la
 * recherche peut tronquer un historique, le ticket est alors relu seul.
 */
final class IssuePages {

  /** Ce qu'on fait de chaque ticket. */
  @FunctionalInterface
  interface Sink {
    void accept(JsonNode issue) throws IOException;
  }

  private final JiraSource jira;
  private final PrintStream err;

  public IssuePages(JiraSource jira, PrintStream err) {
    this.jira = jira;
    this.err = err;
  }

  /** La requête par défaut : les tickets des projets mis à jour dans les N derniers jours. */
  public static String defaultJql(List<String> projects, int days) {
    String keys = projects.stream().map(p -> "\"" + p + "\"").collect(Collectors.joining(","));
    return "project in (" + keys + ") AND updated >= -" + days + "d ORDER BY key ASC";
  }

  public void each(String jql, Sink sink) throws ExtractionStop, IOException {
    int startAt = 0;
    int total = -1;
    while (total < 0 || startAt < total) {
      JsonNode page = jira.search(jql, startAt);
      total = page.path("total").asInt();
      JsonNode issues = page.path("issues");
      if (issues.isEmpty()) break;
      for (JsonNode issue : issues) sink.accept(complete(issue));
      startAt += issues.size();
      err.printf("  %d / %d tickets%n", startAt, total);
    }
  }

  /** Le ticket tel quel si son historique est entier, relu seul sinon. */
  JsonNode complete(JsonNode issue) throws ExtractionStop {
    JsonNode changelog = issue.path("changelog");
    if (changelog.path("total").asInt(0) <= changelog.path("histories").size()) return issue;
    return jira.issue(issue.path("key").asText());
  }
}
