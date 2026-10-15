package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import java.util.List;

/** Les étapes 1 à 4 de l'entonnoir : chacune resserre la liste de la précédente. */
final class FunnelStages {

  private FunnelStages() {}

  /** Marque chaque projet exclu, ou sélectionné avec son motif. */
  static void run(AuditContext ctx, List<Proj> all) {
    RecencyGate gate = new RecencyGate(ctx);
    List<Proj> fresh = gate.run(all);
    gate.validate(all);
    new CommitActivity(ctx).run(fresh);
    new ActivityFloor(ctx).run(fresh);
    new Selection(ctx).run(fresh);
  }
}
