package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Console.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Console.YELLOW;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.orEmpty;

import java.io.PrintStream;
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

  private final AuditContext ctx;

  ActivityFloor(AuditContext ctx) {
    this.ctx = ctx;
  }

  int run(List<Proj> fresh) {
    ctx.con().title("4. Plancher d'activité (étape 3)");

    // La distribution ne porte que sur les projets effectivement comptés :
    // y verser un zéro pour chaque projet volontairement non compté
    // déplacerait les déciles, donc le plancher déduit, donc la sélection.
    List<Proj> counted = fresh.stream().filter(p -> p.excluded == null).toList();
    List<Integer> volumes =
        counted.stream().map(Proj::commitsOrZero).sorted(Comparator.reverseOrder()).toList();
    printDeciles(volumes);
    int chosen = choose(volumes);

    int eligible = 0;
    int unmeasurable = 0;
    for (Proj p : counted) {
      Integer commits = p.commits;
      if (commits == null) {
        p.excluded = "activité non mesurable (" + orEmpty(p.unmeasurable) + ")";
        unmeasurable++;
      } else if (commits < chosen) {
        p.excluded =
            CommitActivity.botOnly(p)
                ? "activité robotique seule (%d commits de bots)".formatted(p.commitsBots)
                : "sous le plancher (%d commits)".formatted(commits);
      } else {
        eligible++;
      }
    }
    ctx.counters().floor = chosen;
    ctx.counters().eligible = eligible;
    report(counted.size() - eligible - unmeasurable, eligible, unmeasurable);
    return chosen;
  }

  private void printDeciles(List<Integer> volumes) {
    PrintStream out = ctx.out();
    out.print("  Distribution des commits (déciles) :");
    for (int d = 1; d <= 9; d += 2) {
      int idx = Math.min(volumes.size() - 1, volumes.size() * d / 10);
      out.printf(" D%d=%d", d, volumes.isEmpty() ? 0 : volumes.get(idx));
    }
    out.println();
  }

  private int choose(List<Integer> volumes) {
    int chosen = ctx.funnel().floor;
    if (chosen >= 0) {
      ctx.out().printf("  Plancher imposé (--floor)       : %d commits%n", chosen);
      return chosen;
    }
    long budget = ctx.funnel().top * 3L / 2;
    chosen = MIN_FLOOR;
    for (int t = MIN_FLOOR; t <= 60; t++) {
      final int threshold = t;
      long n = volumes.stream().filter(v -> v >= threshold).count();
      chosen = t;
      if (n <= budget) break;
    }
    ctx.out()
        .printf(
            "  Plancher déduit des données     : %d commits sur %d jours%n",
            chosen, ctx.sinceDays());
    return chosen;
  }

  private void report(int lowActivity, int eligible, int unmeasurable) {
    PrintStream out = ctx.out();
    Console con = ctx.con();
    out.printf("  Éligibles au tirage             : %d%n", eligible);
    out.printf("  Cohorte faible activité         : %d%n", lowActivity);
    if (unmeasurable > 0) {
      out.printf("  Activité non mesurable          : %d%n", unmeasurable);
      con.say("    403 ou erreur sur les commits. Écartés, pas classés à zéro :", YELLOW);
      con.say("    « je n'ai pas pu voir » n'est pas « il ne se passe rien ».", YELLOW);
    }
    con.say("    Cette cohorte n'est pas perdue : elle se rapporte en effectifs,", DIM);
    con.say("    comme les « jamais analysés » côté Sonar. C'est un constat.", DIM);
  }
}
