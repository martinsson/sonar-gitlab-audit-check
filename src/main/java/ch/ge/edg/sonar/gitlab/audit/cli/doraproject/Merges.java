package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.dora.RunOutcome;
import java.util.ArrayList;
import java.util.List;

/**
 * Sans GoCD, ce que Middleware fait aussi : chaque merge request fusionnée sur la branche par
 * défaut vaut une mise en production, à l'heure de sa fusion.
 */
final class Merges {

  /** Le « pipeline » de ces déploiements, dans la page. */
  static final String PIPELINE = "fusion";

  private Merges() {}

  static List<Snapshot.Deployment> asDeployments(List<Snapshot.MergeRequest> mrs) {
    List<Snapshot.Deployment> out = new ArrayList<>();
    for (Snapshot.MergeRequest mr : mrs) {
      List<Snapshot.Change> changes =
          mr.shas().stream().map(sha -> new Snapshot.Change(sha, null)).toList();
      out.add(
          new Snapshot.Deployment(
              PIPELINE, mr.iid(), mr.mergedAt(), RunOutcome.SUCCESS, false, changes, mr.url()));
    }
    return out;
  }
}
