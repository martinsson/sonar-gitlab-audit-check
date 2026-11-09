package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import ch.ge.edg.sonar.gitlab.audit.dora.DeployedCommit;
import ch.ge.edg.sonar.gitlab.audit.dora.DeployedCommits;
import ch.ge.edg.sonar.gitlab.audit.gocd.Run;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/** Une extraction : pour chaque pipeline choisi, ses runs depuis une date, écrits run par run. */
final class Extraction {

  private final Gocd gocd;
  private final Selection selection;
  private final Reading reading;
  private final DeployedCommits commits;
  private final StageRows stages;
  private final PrintStream err;

  public Extraction(Gocd gocd, Selection selection, Reading reading, PrintStream err) {
    this.gocd = gocd;
    this.selection = selection;
    this.reading = reading;
    this.commits = new DeployedCommits(gocd.api(), reading.maxUpstream());
    this.stages = new StageRows(gocd, reading.durations(), reading.zone());
    this.err = err;
  }

  public void run(Instant since, OutputTarget target) throws IOException, Stop {
    try (Output out = new Output(target.dir(), target.comma(), target.raw())) {
      for (String pipeline : selection.pipelines()) {
        List<Run> history = RunHistory.since(gocd, pipeline, since, err);
        err.print(Text.format("%s : %d runs%n", pipeline, history.size()));
        for (Run run : history) write(pipeline, run, out);
      }
      err.print(
          Text.format(
              "%s : %d runs, %d stages, %d commits%n",
              target.dir(), out.runs.rows(), out.stages.rows(), out.commits.rows()));
    }
  }

  private void write(String pipeline, Run run, Output out) throws IOException, Stop {
    Optional<JsonNode> raw = gocd.api().raw(pipeline, run.counter());
    if (raw.isPresent()) out.raw(raw.get());
    List<DeployedCommit> found = commits.of(pipeline, run);
    ZoneId zone = reading.zone();
    out.runs.row(RunRow.cells(pipeline, selection.group(pipeline), run, found, zone));
    for (String[] row : stages.of(pipeline, run)) out.stages.row(row);
    for (DeployedCommit c : found)
      out.commits.row(CommitRow.cells(c, pipeline, run.counter(), zone));
  }
}
