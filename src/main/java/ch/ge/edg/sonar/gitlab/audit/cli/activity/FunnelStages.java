package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.util.List;

/** Les étapes 1 à 4 de l'entonnoir : chacune resserre la liste de la précédente. */
final class FunnelStages {

  private FunnelStages() {}

  /** Marque chaque projet exclu, ou sélectionné avec son motif. */
  static void run(Gitlab gl, Screen con, Counters counters, Funnel funnel, List<Proj> all) {
    RecencyGate gate = new RecencyGate(gl, con, counters, funnel);
    List<Proj> fresh = gate.run(all);
    gate.validate(all);
    new CommitActivity(gl, con, counters, funnel).run(fresh);
    new ActivityFloor(con, counters, funnel).run(fresh);
    new Selection(con, counters, funnel).run(fresh);
  }
}
