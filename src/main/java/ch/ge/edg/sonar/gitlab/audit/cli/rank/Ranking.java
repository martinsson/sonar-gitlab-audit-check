package ch.ge.edg.sonar.gitlab.audit.cli.rank;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Le classement de bout en bout : cohortes, vélocité, listes annexes, rapport et CSV.
 *
 * <p>1. Séparer les cohortes. 2. Classer les seuls projets actifs, par percentile *à taille
 * comparable*, sur quatre signaux orientés vélocité plutôt que niveau de dette. 3. Une métrique
 * absente est retirée de la moyenne, jamais remplacée par 0.
 */
final class Ranking {

  private final RankSettings settings;
  private final RankReport report;

  public Ranking(RankSettings settings, Console out) {
    this.settings = settings;
    this.report = new RankReport(out, settings);
  }

  /** Classe {@code rows}, le dit sur la console et l'écrit dans {@code file}. */
  public void run(List<InventoryRow> rows, Path file, boolean comma) throws IOException {
    Cohorts cohorts = Cohorts.split(rows, settings.staleDays());
    report.governance(rows, cohorts);

    List<Scored> velocity = VelocityRanking.rank(cohorts.active(), settings);
    List<Scored> carrying = SideLists.carrying(cohorts.active(), settings);
    List<Scored> abandoned = SideLists.abandoned(cohorts.stale(), settings);

    report.velocity(velocity);
    report.sideLists(carrying, abandoned);

    RankCsv.write(file, comma, velocity, carrying, abandoned);
    report.written(file, comma);
  }
}
