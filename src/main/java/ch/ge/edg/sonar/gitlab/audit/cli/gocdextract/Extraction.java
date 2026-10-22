package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import ch.ge.edg.sonar.gitlab.audit.gocd.Run;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/** Une extraction : pour chaque pipeline choisi, ses runs depuis une date, écrits run par run. */
final class Extraction {

  /**
   * Ce que l'extraction écrit et jusqu'où elle remonte.
   *
   * @param comma CSV pour un outil (virgule, sans BOM) plutôt que pour Excel
   * @param raw écrire aussi gocd-runs.jsonl
   * @param maxUpstream appels de runs amont permis pour un seul run
   * @param durations lire chaque stage pour sa fin (un appel de plus par stage)
   */
  record Settings(
      Path outDir, boolean comma, boolean raw, int maxUpstream, boolean durations, ZoneId zone) {}

  private final Gocd gocd;
  private final Selection selection;
  private final Settings settings;
  private final DeployedCommits commits;
  private final StageRows stages;
  private final PrintStream err;

  public Extraction(Gocd gocd, Selection selection, Settings settings, PrintStream err) {
    this.gocd = gocd;
    this.selection = selection;
    this.settings = settings;
    this.commits = new DeployedCommits(gocd.api(), settings.maxUpstream());
    this.stages = new StageRows(gocd, settings.durations(), settings.zone());
    this.err = err;
  }

  public void run(Instant since) throws IOException, Stop {
    try (Output out = new Output(settings.outDir(), settings.comma(), settings.raw())) {
      for (String pipeline : selection.pipelines()) {
        List<Run> history = RunHistory.since(gocd, pipeline, since, err);
        err.print(Text.format("%s : %d runs%n", pipeline, history.size()));
        for (Run run : history) write(pipeline, run, out);
      }
      err.print(
          Text.format(
              "%s : %d runs, %d stages, %d commits%n",
              settings.outDir(), out.runs.rows(), out.stages.rows(), out.commits.rows()));
    }
  }

  private void write(String pipeline, Run run, Output out) throws IOException, Stop {
    Optional<JsonNode> raw = gocd.api().raw(pipeline, run.counter());
    if (raw.isPresent()) out.raw(raw.get());
    List<Commit> found = commits.of(pipeline, run);
    ZoneId zone = settings.zone();
    out.runs.row(RunRow.cells(pipeline, selection.group(pipeline), run, found, zone));
    for (String[] row : stages.of(pipeline, run)) out.stages.row(row);
    for (Commit c : found) out.commits.row(c.cells(pipeline, run.counter(), zone));
  }
}
