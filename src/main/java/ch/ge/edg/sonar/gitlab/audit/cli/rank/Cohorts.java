package ch.ge.edg.sonar.gitlab.audit.cli.rank;

import java.util.ArrayList;
import java.util.List;

/**
 * Les trois cohortes. Jamais analysé et obsolète ne se classent pas : ce sont des constats de
 * gouvernance, pas des mesures de qualité.
 */
record Cohorts(List<InventoryRow> never, List<InventoryRow> stale, List<InventoryRow> active) {

  static Cohorts split(List<InventoryRow> rows, int staleDays) {
    Cohorts c = new Cohorts(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    for (InventoryRow r : rows) {
      Integer days = r.daysSinceAnalysis();
      if (days == null) c.never.add(r);
      else if (days > staleDays) c.stale.add(r);
      else c.active.add(r);
    }
    return c;
  }
}
