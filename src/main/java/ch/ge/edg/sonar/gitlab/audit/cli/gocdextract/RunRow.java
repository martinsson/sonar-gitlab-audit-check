package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import ch.ge.edg.sonar.gitlab.audit.dora.DeployedCommit;
import ch.ge.edg.sonar.gitlab.audit.dora.Platforms;
import ch.ge.edg.sonar.gitlab.audit.dora.RunOutcome;
import ch.ge.edg.sonar.gitlab.audit.dora.Times;
import ch.ge.edg.sonar.gitlab.audit.gocd.Material;
import ch.ge.edg.sonar.gitlab.audit.gocd.Run;
import ch.ge.edg.sonar.gitlab.audit.gocd.Run.BuildCause;
import ch.ge.edg.sonar.gitlab.audit.gocd.Run.MaterialRevision;
import ch.ge.edg.sonar.gitlab.audit.gocd.Run.Modification;
import ch.ge.edg.sonar.gitlab.audit.gocd.Stage;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Une ligne de gocd-runs.csv : un run, ses matériaux, son issue, et les commits embarqués. */
final class RunRow {

  static final List<String> HEADER =
      List.of(
          "pipeline",
          "groupe",
          "plateforme_supposee",
          "compteur",
          "label",
          "planifie_le",
          "declenchement",
          "resultat",
          "stages",
          "depots_git",
          "amont",
          "nb_commits",
          "premier_commit_le",
          "dernier_commit_le",
          "delai_premier_commit_h",
          "cles_jira");

  private RunRow() {}

  static String[] cells(
      String pipeline, String group, Run run, List<DeployedCommit> found, ZoneId zone) {
    BuildCause cause = run.buildCause();
    Instant scheduled = run.scheduledDate();
    Span span = Span.of(found);
    return new String[] {
      pipeline,
      group,
      Platforms.guess(pipeline),
      String.valueOf(run.counter()),
      run.label(),
      Times.format(scheduled, zone),
      cause.triggerForced() ? "forcé" : "automatique",
      RunOutcome.of(run).label(),
      String.join("|", stages(run)),
      String.join("|", repos(cause)),
      String.join("|", upstream(cause)),
      String.valueOf(found.size()),
      Times.format(span.first(), zone),
      Times.format(span.last(), zone),
      Times.hours(span.first(), scheduled),
      String.join("|", span.keys())
    };
  }

  /** « build:Passed », ou « NonLancé » pour un stage qui attend son approbation. */
  static List<String> stages(Run run) {
    List<String> out = new ArrayList<>();
    for (Stage s : run.stages()) {
      out.add(s.name() + ":" + (s.launched() ? s.result() : "NonLancé"));
    }
    return out;
  }

  /** Les dépôts git du run, changés ou non. */
  static Set<String> repos(BuildCause cause) {
    Set<String> out = new LinkedHashSet<>();
    for (MaterialRevision mr : cause.materialRevisions()) {
      Material m = mr.material();
      if (m.isGit()) out.add(m.gitUrl());
    }
    return out;
  }

  /** Les révisions amont prises par le run, « recette/3/deploy/1 ». */
  static Set<String> upstream(BuildCause cause) {
    Set<String> out = new LinkedHashSet<>();
    for (MaterialRevision mr : cause.materialRevisions()) {
      if (!mr.material().isPipeline()) continue;
      for (Modification mod : mr.modifications()) out.add(mod.revision());
    }
    return out;
  }

  /** Le premier et le dernier commit embarqués, et toutes les clés Jira qu'ils citent. */
  record Span(@Nullable Instant first, @Nullable Instant last, Set<String> keys) {

    static Span of(List<DeployedCommit> found) {
      Instant first = null;
      Instant last = null;
      Set<String> keys = new LinkedHashSet<>();
      for (DeployedCommit c : found) {
        Instant at = c.at();
        if (at != null && (first == null || at.isBefore(first))) first = at;
        if (at != null && (last == null || at.isAfter(last))) last = at;
        keys.addAll(c.keys());
      }
      return new Span(first, last, keys);
    }
  }
}
