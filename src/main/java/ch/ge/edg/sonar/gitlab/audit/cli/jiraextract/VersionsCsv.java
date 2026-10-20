package ch.ge.edg.sonar.gitlab.audit.cli.jiraextract;

import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/**
 * jira-versions.csv : les versions des projets et leurs dates de livraison, de quoi recouper les
 * mises en production. Un projet dont les versions sont illisibles est signalé et sauté.
 */
public final class VersionsCsv {

  static final List<String> HEADER =
      List.of("projet", "version", "livree", "archivee", "debut_le", "livree_le", "description");

  private VersionsCsv() {}

  public static void write(JiraSource jira, List<String> projects, Output output, PrintStream err)
      throws ExtractionStop, IOException {
    try (CountedCsv csv =
        new CountedCsv(output.dir().resolve("jira-versions.csv"), output.comma(), HEADER)) {
      for (String p : projects) {
        RestResponse r = jira.versions(p);
        if (r.ok()) {
          for (JsonNode v : r.json()) csv.row(row(p, v));
        } else {
          err.println("versions de " + p + " : " + r.why());
        }
      }
      err.println(csv.path() + " : " + csv.rows() + " versions");
    }
  }

  static List<String> row(String project, JsonNode v) {
    return List.of(
        project,
        v.path("name").asText(),
        v.path("released").asBoolean(false) ? "O" : "N",
        v.path("archived").asBoolean(false) ? "O" : "N",
        v.path("startDate").asText(""),
        v.path("releaseDate").asText(""),
        v.path("description").asText(""));
  }
}
