package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.dora.Times;
import ch.ge.edg.sonar.gitlab.audit.jira.JiraApi;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.PrintStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Les incidents : les tickets Jira créés pendant la période dont le type est l'un de ceux retenus,
 * ou ceux d'une requête JQL donnée telle quelle. Filtrer le type ici plutôt qu'en JQL évite le
 * refus de toute la requête quand un type n'existe pas dans le projet, et permet de dire quels
 * types on a vus.
 */
final class JiraReader {

  static final int PAGE = 100;

  static final String FIELDS = "fields=summary,issuetype,status,created,resolutiondate";

  private final JiraApi jira;
  private final PrintStream err;

  JiraReader(JiraApi jira, PrintStream err) {
    this.jira = jira;
    this.err = err;
  }

  /** La requête par défaut : tout ce que les projets ont créé depuis N jours. */
  static String defaultJql(List<String> projects, int days) {
    String keys = projects.stream().map(p -> "\"" + p + "\"").collect(Collectors.joining(","));
    return "project in (" + keys + ") AND created >= -" + days + "d ORDER BY created ASC";
  }

  /**
   * Les incidents d'une requête.
   *
   * @param types les types retenus, sans égard à la casse ; vide : tous
   */
  List<Snapshot.Incident> incidents(String jql, List<String> types) throws Stop {
    List<String> wanted = types.stream().map(t -> t.toLowerCase(Locale.ROOT)).toList();
    Map<String, Integer> seen = new TreeMap<>();
    List<Snapshot.Incident> out = new ArrayList<>();
    int startAt = 0;
    int total = -1;
    while (total < 0 || startAt < total) {
      JsonNode page = search(jql, startAt);
      total = page.path("total").asInt();
      JsonNode issues = page.path("issues");
      if (issues.isEmpty()) break;
      for (JsonNode issue : issues) {
        String type = issue.path("fields").path("issuetype").path("name").asText("");
        seen.merge(type, 1, Integer::sum);
        if (wanted.isEmpty() || wanted.contains(type.toLowerCase(Locale.ROOT))) {
          incident(issue).ifPresent(out::add);
        }
      }
      startAt += issues.size();
    }
    err.println(
        Text.format(
            "Jira : %d tickets lus, %d incidents retenus. Types vus : %s",
            startAt, out.size(), seen));
    return out;
  }

  private JsonNode search(String jql, int startAt) throws Stop {
    RestResponse r = jira.unlessRefused(jira.search(jql, startAt, PAGE, FIELDS));
    if (!r.ok()) {
      String body = r.body().replaceAll("\\s+", " ");
      throw new Stop(("Jira search : " + r.why() + " " + Text.truncate(body, 300)).strip());
    }
    return r.tree();
  }

  private Optional<Snapshot.Incident> incident(JsonNode issue) {
    JsonNode f = issue.path("fields");
    Instant created = Times.parse(f.path("created"));
    if (created == null) return Optional.empty();
    String key = issue.path("key").asText();
    return Optional.of(
        new Snapshot.Incident(
            key,
            f.path("summary").asText(""),
            f.path("issuetype").path("name").asText(""),
            created,
            Times.parse(f.path("resolutiondate")),
            jira.base().replaceAll("/+$", "") + "/browse/" + key));
  }
}
