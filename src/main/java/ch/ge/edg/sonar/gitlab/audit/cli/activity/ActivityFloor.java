package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.util.Comparator;
import java.util.List;

/**
 * Étape 3 : le plancher d'activité.
 *
 * <p>Sous un certain volume, les signaux profonds sont de l'arithmétique sur du bruit : 3 commits
 * et 1 MR ne peuvent pas porter un taux de revue, un DORA ni un taux de succès de pipeline — ils
 * renvoient 0 % ou 100 %, et les deux sont vides de sens. Même logique que le garde-fou ncloc < 500
 * côté Sonar.
 *
 * <p>Le seuil se déduit de la distribution plutôt que de se fixer d'avance : elle est franchement à
 * longue traîne, et la rupture naturelle tombe souvent près du budget visé — le plancher devient
 * alors descriptif au lieu d'être arbitraire.
 */
final class ActivityFloor {

  static final int MIN_FLOOR = 5;

  private final Funnel funnel;

  ActivityFloor(Funnel funnel) {
    this.funnel = funnel;
  }

  /**
   * Le plancher retenu, et chaque projet en lice départagé : éligible dans sa strate, ou écarté.
   *
   * @param volumes les commits humains des projets comptés, du plus grand au plus petit
   * @param imposed vrai quand --floor l'impose, faux quand il est déduit de la distribution
   */
  record Floor(List<AuditedProject> all, List<Integer> volumes, int threshold, boolean imposed) {}

  Floor run(List<AuditedProject> all) {
    // La distribution ne porte que sur les projets effectivement comptés :
    // y verser un zéro pour chaque projet volontairement non compté
    // déplacerait les déciles, donc le plancher déduit, donc la sélection.
    List<Integer> volumes =
        CommitActivity.inPlay(all).stream()
            .map(AuditedProject::commitsOrZero)
            .sorted(Comparator.reverseOrder())
            .toList();
    int imposed = funnel.options().floor;
    int chosen = imposed >= 0 ? imposed : deduce(volumes);
    List<AuditedProject> decided =
        all.stream().map(p -> p.fate().inPlay() ? p.with(fate(p, chosen)) : p).toList();
    return new Floor(decided, volumes, chosen, imposed >= 0);
  }

  private static Fate fate(AuditedProject p, int floor) {
    CommitMeasure measure = p.commits();
    Integer commits = measure.human();
    if (commits == null) {
      String status = measure instanceof CommitMeasure.Unmeasurable u ? u.status() : "";
      return new Fate.Excluded(Fate.Stage.FLOOR, "activité non mesurable (" + status + ")");
    }
    if (commits < floor) {
      return new Fate.Excluded(
          Fate.Stage.FLOOR,
          measure.botOnly()
              ? Text.format("activité robotique seule (%d commits de bots)", measure.bots())
              : Text.format("sous le plancher (%d commits)", commits));
    }
    return new Fate.Eligible(Selection.bucketOf(p.project()));
  }

  private int deduce(List<Integer> volumes) {
    long budget = funnel.options().top * 3L / 2;
    int chosen = MIN_FLOOR;
    for (int t = MIN_FLOOR; t <= 60; t++) {
      final int threshold = t;
      long n = volumes.stream().filter(v -> v >= threshold).count();
      chosen = t;
      if (n <= budget) break;
    }
    return chosen;
  }
}
