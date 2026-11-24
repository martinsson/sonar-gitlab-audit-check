package ch.ge.edg.sonar.gitlab.audit.cli.rank;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Le classement de bout en bout : cohortes, vélocité, listes annexes, rapport et CSV.
 *
 * <ol>
 *   <li>Séparer les cohortes.
 *   <li>Classer les seuls projets actifs, par percentile *à taille comparable*, sur quatre signaux
 *       orientés vélocité plutôt que niveau de dette.
 *   <li>Une métrique absente est retirée de la moyenne, jamais remplacée par 0.
 * </ol>
 */
final class Ranking {

  private final Thresholds thresholds;
  private final RankReport report;

  public Ranking(Thresholds thresholds, Console out) {
    this.thresholds = thresholds;
    this.report = new RankReport(out, thresholds);
  }

  /** Classe {@code rows}, le dit sur la console et l'écrit dans {@code file}. */
  public void run(List<InventoryRow> rows, Path file, boolean comma) throws IOException {
    Cohorts cohorts = Cohorts.split(rows, thresholds.staleDays());
    report.governance(rows, cohorts);

    List<Scored> velocity = VelocityRanking.rank(cohorts.active(), thresholds);
    List<Scored> carrying = SideLists.carrying(cohorts.active(), thresholds);
    List<Scored> abandoned = SideLists.abandoned(cohorts.stale(), thresholds);

    report.velocity(velocity);
    report.sideLists(carrying, abandoned);

    RankCsv.write(file, comma, velocity, carrying, abandoned);
    report.written(file, comma);
  }
}
