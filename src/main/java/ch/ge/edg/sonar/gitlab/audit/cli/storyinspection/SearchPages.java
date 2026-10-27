package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

import ch.ge.edg.sonar.gitlab.audit.jira.JiraApi;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Les stories d'un projet Jira, page après page. Chaque réponse peut être consignée dans un
 * répertoire ({@code search_<début>.json}) pour être rejouée par {@link ReplayDump}.
 */
final class SearchPages {

  private final JiraApi jira;
  private final String projectKey;
  private final int pageSize;
  private final @Nullable Path dumpDir;
  private final PrintStream err;

  SearchPages(
      JiraApi jira, String projectKey, int pageSize, @Nullable Path dumpDir, PrintStream err) {
    this.jira = jira;
    this.projectKey = projectKey;
    this.pageSize = pageSize;
    this.dumpDir = dumpDir;
    this.err = err;
  }

  /** Les tickets lus ; une page refusée arrête la lecture et garde ce qui a déjà été lu. */
  List<JsonNode> issues() {
    String jql = "project=" + projectKey + " AND issuetype=Story";
    List<JsonNode> issues = new ArrayList<>();
    int startAt = 0;
    int total = Integer.MAX_VALUE;
    while (startAt < total) {
      RestResponse page = jira.search(jql, startAt, pageSize, "fields=max");
      dump(page, startAt);
      if (!page.ok()) {
        err.println("Erreur Jira: " + page.statusLine() + " " + page.message());
        break;
      }
      if (issues.isEmpty()) total = page.tree().path("total").asInt(-1);
      JsonNode batch = page.tree().path("issues");
      if (batch.isEmpty()) break;
      batch.forEach(issues::add);
      startAt += batch.size();
    }
    return issues;
  }

  private void dump(RestResponse page, int startAt) {
    if (dumpDir == null) return;
    try {
      Files.writeString(dumpDir.resolve("search_" + startAt + ".json"), page.body());
    } catch (IOException e) {
      err.println("Consignation impossible : " + e.getMessage());
    }
  }
}
