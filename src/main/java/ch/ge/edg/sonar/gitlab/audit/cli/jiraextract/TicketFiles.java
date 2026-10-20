package ch.ge.edg.sonar.gitlab.audit.cli.jiraextract;

import ch.ge.edg.sonar.gitlab.audit.dora.Jsonl;
import ch.ge.edg.sonar.gitlab.audit.dora.Times;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Les trois fichiers qu'alimente chaque ticket : jira-tickets.csv, jira-transitions.csv et, sauf
 * --no-raw, jira-tickets.jsonl, le ticket tel que Jira l'a rendu.
 */
final class TicketFiles implements AutoCloseable {

  static final List<String> TRANSITIONS_HEADER =
      List.of("cle", "le", "de", "vers", "categorie_vers");

  private final Output output;
  private final FieldSelection fields;
  private final Map<String, String> categories;
  private final CountedCsv tickets;
  private final CountedCsv transitions;
  private final @Nullable Jsonl raw;

  /**
   * Ouvre les fichiers dans {@code output.dir()}, en-têtes écrits.
   *
   * @param categories id de statut → catégorie
   */
  public TicketFiles(Output output, FieldSelection fields, Map<String, String> categories)
      throws IOException {
    this.output = output;
    this.fields = fields;
    this.categories = categories;
    this.tickets =
        new CountedCsv(
            output.dir().resolve("jira-tickets.csv"), output.comma(), IssueRow.header(fields));
    this.transitions =
        new CountedCsv(
            output.dir().resolve("jira-transitions.csv"), output.comma(), TRANSITIONS_HEADER);
    this.raw = output.raw() ? new Jsonl(output.dir().resolve("jira-tickets.jsonl")) : null;
  }

  /** Un ticket, historique complet. */
  public void write(JsonNode issue) throws IOException {
    if (raw != null) raw.write(issue);
    String key = issue.path("key").asText();
    StatusTimeline timeline = StatusTimeline.of(issue.path("changelog"), categories);
    for (StatusTimeline.Transition t : timeline.transitions()) {
      transitions.row(
          List.of(key, Times.format(t.at(), output.zone()), t.from(), t.to(), t.category()));
    }
    tickets.row(IssueRow.cells(issue, timeline, fields, output.zone()));
  }

  /** « …/jira-tickets.csv : 12 tickets, 40 changements de statut ». */
  public String summary() {
    return tickets.path()
        + " : "
        + tickets.rows()
        + " tickets, "
        + transitions.rows()
        + " changements de statut";
  }

  @Override
  public void close() throws IOException {
    // Une erreur d'écriture arrête l'outil : inutile de fermer les autres à tout prix.
    tickets.close();
    transitions.close();
    if (raw != null) raw.close();
  }
}
