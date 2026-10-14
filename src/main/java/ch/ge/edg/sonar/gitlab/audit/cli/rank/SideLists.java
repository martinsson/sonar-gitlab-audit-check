package ch.ge.edg.sonar.gitlab.audit.cli.rank;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Les listes annexes : classées sur sqale_debt_ratio seul, un niveau et pas une vitesse. */
final class SideLists {

  private SideLists() {}

  /** Actifs sans code neuf : ils portent la dette sans en créer. */
  static List<Scored> carrying(List<InventoryRow> active, RankSettings settings) {
    return byDebtRatio(active, r -> r.noNewCode() && r.ncloc() >= settings.minNcloc());
  }

  /** Obsolètes récents et endettés : quelqu'un a lâché un projet en mauvais état. */
  static List<Scored> abandoned(List<InventoryRow> stale, RankSettings settings) {
    return byDebtRatio(
        stale,
        r -> {
          Integer days = r.daysSinceAnalysis();
          return days != null
              && days <= settings.abandonedDays()
              && r.ncloc() >= settings.minNcloc();
        });
  }

  /** Ceux qui passent le filtre et ont un ratio de dette, du plus endetté au moins. */
  private static List<Scored> byDebtRatio(List<InventoryRow> rows, Predicate<InventoryRow> keep) {
    List<Scored> out = new ArrayList<>();
    for (InventoryRow r : rows) {
      Double debt = r.num("sqale_debt_ratio");
      if (debt != null && keep.test(r)) {
        Scored s = new Scored(r);
        s.score = debt;
        out.add(s);
      }
    }
    out.sort(Comparator.comparingDouble((Scored s) -> s.score).reversed());
    return out;
  }
}
