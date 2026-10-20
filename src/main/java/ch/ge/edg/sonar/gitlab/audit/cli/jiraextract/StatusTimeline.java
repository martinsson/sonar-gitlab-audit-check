package ch.ge.edg.sonar.gitlab.audit.cli.jiraextract;

import ch.ge.edg.sonar.gitlab.audit.dora.Times;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Les changements de statut d'un ticket, dans l'ordre, et les deux dates qu'on en tire : la
 * première entrée en cours, et le dernier passage à terminé. Un ticket réouvert n'est plus terminé
 * : seule une nouvelle clôture lui redonne une date.
 *
 * @param inProgress premier passage dans un statut de catégorie « indeterminate »
 * @param done dernier passage à « done », s'il n'a pas été réouvert depuis
 */
record StatusTimeline(
    List<Transition> transitions, @Nullable Instant inProgress, @Nullable Instant done) {

  static final String IN_PROGRESS = "indeterminate";
  static final String DONE = "done";

  /**
   * Un changement de statut.
   *
   * @param category la catégorie du statut d'arrivée ; vide si le statut est inconnu
   */
  record Transition(@Nullable Instant at, String from, String to, String category) {}

  /**
   * La chronologie d'un ticket.
   *
   * @param changelog l'historique du ticket (« changelog »), complet
   * @param categories id de statut → catégorie
   */
  public static StatusTimeline of(JsonNode changelog, Map<String, String> categories) {
    List<Transition> transitions = transitions(changelog, categories);
    Instant inProgress = null;
    Instant done = null;
    for (Transition t : transitions) {
      if (t.category().equals(IN_PROGRESS) && inProgress == null) inProgress = t.at();
      if (t.category().equals(DONE)) done = t.at();
      else if (!t.category().isEmpty()) done = null;
    }
    return new StatusTimeline(List.copyOf(transitions), inProgress, done);
  }

  private static List<Transition> transitions(JsonNode changelog, Map<String, String> categories) {
    List<JsonNode> histories = new ArrayList<>();
    changelog.path("histories").forEach(histories::add);
    histories.sort(Comparator.comparing(h -> h.path("created").asText()));
    List<Transition> out = new ArrayList<>();
    for (JsonNode h : histories) {
      Instant at = Times.parse(h.path("created"));
      for (JsonNode item : h.path("items")) {
        if (!"status".equals(item.path("field").asText())) continue;
        out.add(
            new Transition(
                at,
                item.path("fromString").asText(""),
                item.path("toString").asText(""),
                categories.getOrDefault(item.path("to").asText(), "")));
      }
    }
    return out;
  }
}
