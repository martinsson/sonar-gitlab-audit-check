package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.dora.DeployedCommit;
import ch.ge.edg.sonar.gitlab.audit.dora.DeployedCommits;
import ch.ge.edg.sonar.gitlab.audit.dora.RepoPath;
import ch.ge.edg.sonar.gitlab.audit.dora.RunOutcome;
import ch.ge.edg.sonar.gitlab.audit.gocd.GocdApi;
import ch.ge.edg.sonar.gitlab.audit.gocd.Run;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import java.io.PrintStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Les runs des pipelines de production planifiés pendant la période, et pour chacun les commits du
 * projet qu'il a mis en production, y compris ceux arrivés par les pipelines amont (build, recette)
 * : la même règle que GocdExtract.
 */
final class GocdReader {

  private final GocdApi gocd;
  private final DeployedCommits commits;
  private final PrintStream err;

  GocdReader(GocdApi gocd, int maxUpstream, PrintStream err) {
    this.gocd = gocd;
    this.commits = new DeployedCommits(gocd, maxUpstream);
    this.err = err;
  }

  /** Les déploiements, du plus ancien au plus récent ; un run jamais planifié n'en est pas un. */
  List<Snapshot.Deployment> deployments(List<String> pipelines, String projectPath, Instant from)
      throws Stop {
    List<Snapshot.Deployment> out = new ArrayList<>();
    for (String pipeline : pipelines) {
      List<Run> runs = history(pipeline, from);
      err.println(pipeline + " : " + runs.size() + " runs");
      for (Run run : runs) {
        Instant at = run.scheduledDate();
        if (at == null) continue;
        out.add(
            new Snapshot.Deployment(
                pipeline,
                run.counter(),
                at,
                RunOutcome.of(run),
                run.buildCause().triggerForced(),
                changes(pipeline, run, projectPath),
                gocd.base()
                    + "/go/pipelines/value_stream_map/"
                    + GocdApi.enc(pipeline)
                    + "/"
                    + run.counter()));
      }
    }
    out.sort(Comparator.comparing(Snapshot.Deployment::at));
    return out;
  }

  private List<Run> history(String pipeline, Instant from) throws Stop {
    AtomicBoolean older = new AtomicBoolean();
    GocdApi.History h =
        gocd.history(
            pipeline,
            run -> {
              Instant at = run.scheduledDate();
              older.set(at != null && at.isBefore(from));
              return older.get();
            },
            Integer.MAX_VALUE);
    gocd.unlessRefused(h.first());
    if (!h.first().ok()) {
      throw new Stop(pipeline + " : historique illisible (" + h.first().why() + ")");
    }
    if (!h.complete() && !older.get()) {
      err.println(pipeline + " : historique interrompu après " + h.pages() + " page(s)");
    }
    return h.runs();
  }

  /** Les commits du projet que le run a embarqués ; ceux d'autres dépôts ne le concernent pas. */
  private List<Snapshot.Change> changes(String pipeline, Run run, String projectPath) {
    List<Snapshot.Change> out = new ArrayList<>();
    for (DeployedCommit c : commits.of(pipeline, run)) {
      if (RepoPath.matches(c.repo(), projectPath))
        out.add(new Snapshot.Change(c.revision(), c.at()));
    }
    return out;
  }
}
