package ch.ge.edg.sonar.gitlab.audit.cli.jiraextract;

import ch.ge.edg.sonar.gitlab.audit.dora.Times;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Une ligne de jira-tickets.csv : ce que les mesures DORA lisent d'un ticket (type, statut, dates
 * et délais, versions, rattachement), puis les colonnes de {@link FieldSelection}.
 */
public final class IssueRow {

  static final List<String> HEADER =
      List.of(
          "cle",
          "projet",
          "type",
          "sous_tache",
          "statut",
          "categorie_statut",
          "priorite",
          "resolution",
          "cree_le",
          "en_cours_le",
          "termine_le",
          "resolu_le",
          "mis_a_jour_le",
          "delai_resolution_j",
          "delai_cycle_j",
          "nb_changements_statut",
          "composants",
          "versions_corrigees",
          "versions_affectees",
          "labels",
          "parent",
          "epic");

  private IssueRow() {}

  public static List<String> header(FieldSelection fields) {
    List<String> out = new ArrayList<>(HEADER);
    out.addAll(fields.headers());
    return out;
  }

  public static List<String> cells(
      JsonNode issue, StatusTimeline timeline, FieldSelection fields, ZoneId zone) {
    JsonNode f = issue.path("fields");
    List<String> out = new ArrayList<>(describe(issue.path("key").asText(), f));
    out.addAll(dates(f, timeline, zone));
    out.add(String.valueOf(timeline.transitions().size()));
    out.addAll(
        List.of(
            FieldValue.render(f.path("components")),
            FieldValue.render(f.path("fixVersions")),
            FieldValue.render(f.path("versions")),
            FieldValue.render(f.path("labels")),
            f.path("parent").path("key").asText(""),
            fields.epic(f)));
    out.addAll(fields.cells(f));
    return out;
  }

  /** Clé, projet, type, statut, priorité, résolution. */
  private static List<String> describe(String key, JsonNode f) {
    return List.of(
        key,
        f.path("project").path("key").asText(),
        f.path("issuetype").path("name").asText(),
        f.path("issuetype").path("subtask").asBoolean(false) ? "O" : "N",
        f.path("status").path("name").asText(),
        f.path("status").path("statusCategory").path("key").asText(),
        f.path("priority").path("name").asText(""),
        f.path("resolution").path("name").asText(""));
  }

  /** Créé, en cours, terminé, résolu, mis à jour ; délais de résolution et de cycle en jours. */
  private static List<String> dates(JsonNode f, StatusTimeline t, ZoneId zone) {
    Instant created = Times.parse(f.path("created"));
    Instant resolved = Times.parse(f.path("resolutiondate"));
    return List.of(
        Times.format(created, zone),
        Times.format(t.inProgress(), zone),
        Times.format(t.done(), zone),
        Times.format(resolved, zone),
        Times.format(Times.parse(f.path("updated")), zone),
        Times.days(created, resolved),
        Times.days(t.inProgress(), t.done()));
  }
}
