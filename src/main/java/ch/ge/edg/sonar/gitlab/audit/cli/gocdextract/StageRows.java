package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import ch.ge.edg.sonar.gitlab.audit.dora.Platforms;
import ch.ge.edg.sonar.gitlab.audit.dora.Times;
import ch.ge.edg.sonar.gitlab.audit.gocd.GocdApi;
import ch.ge.edg.sonar.gitlab.audit.gocd.Run;
import ch.ge.edg.sonar.gitlab.audit.gocd.Stage;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Les lignes de gocd-stages.csv. Le début d'un stage est dans l'historique (planification du
 * premier job) ; sa fin n'y est pas : avec {@code durations}, chaque stage lancé coûte un appel de
 * plus, pour la dernière transition « Completed » de ses jobs.
 */
final class StageRows {

  static final List<String> HEADER =
      List.of(
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
          "duree_min");

  private final Gocd gocd;
  private final boolean durations;
  private final ZoneId zone;

  StageRows(Gocd gocd, boolean durations, ZoneId zone) {
    this.gocd = gocd;
    this.durations = durations;
    this.zone = zone;
  }

  List<String[]> of(String pipeline, Run run) throws Stop {
    int counter = run.counter();
    List<String[]> rows = new ArrayList<>();
    for (Stage s : run.stages()) {
      boolean scheduled = s.launched();
      Instant start = s.start();
      Instant end = durations && scheduled ? end(pipeline, counter, s) : null;
      rows.add(
          new String[] {
            pipeline,
            String.valueOf(counter),
            s.name(),
            String.valueOf(s.counter()),
            Platforms.guess(pipeline, s.name()),
            scheduled ? "O" : "N",
            scheduled ? s.result() : "",
            s.approvalType(),
            s.counter() > 1 ? "O" : "N",
            Times.format(start, zone),
            Times.format(end, zone),
            minutes(start, end)
          });
    }
    return rows;
  }

  /** La dernière fois qu'un job du stage est passé à « Completed », d'après le détail du stage. */
  private @Nullable Instant end(String pipeline, int counter, Stage stage) throws Stop {
    String path =
        Text.format(
            "/go/api/stages/%s/%d/%s/%d",
            GocdApi.enc(pipeline), counter, GocdApi.enc(stage.name()), stage.counter());
    RestResponse r = gocd.checked(gocd.api().api(path));
    return r.ok() ? Stage.of(r.tree()).completed() : null;
  }

  /** Une décimale, virgule décimale ; vide si l'un des deux bouts manque. */
  static String minutes(@Nullable Instant start, @Nullable Instant end) {
    if (start == null || end == null) return "";
    double m = Duration.between(start, end).toSeconds() / 60.0;
    return Text.format("%.1f", m).replace('.', ',');
  }
}
