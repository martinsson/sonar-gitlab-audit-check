package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import ch.ge.edg.sonar.gitlab.audit.dora.Platforms;
import ch.ge.edg.sonar.gitlab.audit.dora.Times;
import ch.ge.edg.sonar.gitlab.audit.gocd.GocdApi;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Les lignes de gocd-stages.csv. Le début d'un stage est dans l'historique (planification du
 * premier job) ; sa fin n'y est pas : avec {@code durations}, chaque stage lancé coûte un appel de
 * plus, pour la dernière transition « Completed » de ses jobs.
 */
final class StageRows {

  static final String[] HEADER = {
    "pipeline",
    "compteur",
    "stage",
    "compteur_stage",
    "plateforme_supposee",
    "lance",
    "resultat",
    "approbation",
    "relance",
    "debut_le",
    "fin_le",
    "duree_min"
  };

  private final Gocd gocd;
  private final boolean durations;
  private final ZoneId zone;

  StageRows(Gocd gocd, boolean durations, ZoneId zone) {
    this.gocd = gocd;
    this.durations = durations;
    this.zone = zone;
  }

  List<String[]> of(String pipeline, JsonNode run) throws Stop {
    int counter = run.path("counter").asInt();
    List<String[]> rows = new ArrayList<>();
    for (JsonNode s : run.path("stages")) {
      boolean scheduled = s.path("scheduled").asBoolean(true);
      Instant start = start(s);
      Instant end = durations && scheduled ? end(pipeline, counter, s) : null;
      rows.add(
          new String[] {
            pipeline,
            String.valueOf(counter),
            s.path("name").asText(),
            s.path("counter").asText(),
            Platforms.guess(pipeline, s.path("name").asText()),
            scheduled ? "O" : "N",
            scheduled ? s.path("result").asText() : "",
            s.path("approval_type").asText(),
            s.path("counter").asInt(1) > 1 ? "O" : "N",
            Times.format(start, zone),
            Times.format(end, zone),
            minutes(start, end)
          });
    }
    return rows;
  }

  /** La planification du premier job. */
  static @Nullable Instant start(JsonNode stage) {
    Instant start = null;
    for (JsonNode j : stage.path("jobs")) {
      Instant at = Times.parse(j.path("scheduled_date"));
      if (at != null && (start == null || at.isBefore(start))) start = at;
    }
    return start;
  }

  private @Nullable Instant end(String pipeline, int counter, JsonNode stage) throws Stop {
    String path =
        "/go/api/stages/%s/%d/%s/%s"
            .formatted(
                GocdApi.enc(pipeline),
                counter,
                GocdApi.enc(stage.path("name").asText()),
                stage.path("counter").asText("1"));
    RestResponse r = gocd.checked(gocd.api().api(path));
    return r.ok() ? completed(r.json()) : null;
  }

  /** La dernière fois qu'un job du stage est passé à « Completed ». */
  static @Nullable Instant completed(JsonNode stage) {
    Instant end = null;
    for (JsonNode j : stage.path("jobs")) {
      for (JsonNode t : j.path("job_state_transitions")) {
        Instant at = Times.parse(t.path("state_change_time"));
        if (at == null || !"Completed".equals(t.path("state").asText())) continue;
        if (end == null || at.isAfter(end)) end = at;
      }
    }
    return end;
  }

  /** Une décimale, virgule décimale ; vide si l'un des deux bouts manque. */
  static String minutes(@Nullable Instant start, @Nullable Instant end) {
    if (start == null || end == null) return "";
    double m = Duration.between(start, end).toSeconds() / 60.0;
    return String.format(Locale.ROOT, "%.1f", m).replace('.', ',');
  }
}
