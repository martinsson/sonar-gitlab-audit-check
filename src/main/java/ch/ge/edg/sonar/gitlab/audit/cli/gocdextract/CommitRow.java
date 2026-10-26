package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import ch.ge.edg.sonar.gitlab.audit.dora.DeployedCommit;
import ch.ge.edg.sonar.gitlab.audit.dora.Times;
import java.time.ZoneId;
import java.util.List;

/** Une ligne de gocd-commits.csv : un commit qu'un run a embarqué. */
final class CommitRow {

  static final List<String> HEADER =
      List.of("pipeline", "compteur", "via", "depot", "revision", "commit_le", "cles_jira");

  private CommitRow() {}

  static String[] cells(DeployedCommit c, String pipeline, int counter, ZoneId zone) {
    return new String[] {
      pipeline,
      String.valueOf(counter),
      c.via(),
      c.repo(),
      c.revision(),
      Times.format(c.at(), zone),
      String.join("|", c.keys())
    };
  }
}
