package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.util.List;

/**
 * Les étapes 1 à 4 de l'entonnoir : chacune rend le périmètre avec ce qu'elle a établi, et le
 * rapport l'affiche aussitôt.
 */
final class FunnelStages {

  private FunnelStages() {}

  /** Chaque projet exclu, ou sélectionné avec son motif. */
  static FunnelResult run(
      Gitlab gl, FunnelReport report, Funnel funnel, List<AuditedProject> inventory) {
    RecencyGate gate = new RecencyGate(gl, funnel);
    List<AuditedProject> all = gate.run(inventory);
    report.recency(all);
    all = gate.validate(all);
    report.validation(all);

    report.beginActivity();
    CommitActivity activity = new CommitActivity(gl, funnel);
    int graphqlCalls = 0;
    if (funnel.options().graphql) {
      CommitActivity.Dated dated = activity.date(all);
      report.dating(dated, activity);
      all = dated.all();
      graphqlCalls = dated.dating().calls();
    }
    all = activity.cut(all);
    report.cut(all);
    int counted = CommitActivity.toCount(all).size();
    all = activity.count(all);
    report.counts(all, counted);

    ActivityFloor.Floor floor = new ActivityFloor(funnel).run(all);
    report.floor(floor);
    FunnelResult result = new FunnelResult(Selection.run(funnel, floor.all()), graphqlCalls);
    report.selection(result);
    return result;
  }
}
