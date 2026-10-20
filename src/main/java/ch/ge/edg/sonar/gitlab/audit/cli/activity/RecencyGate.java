package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.pct;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.plural;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Screen.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Screen.YELLOW;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/** Étape 1 : filtre de fraîcheur, et sa validation. */
final class RecencyGate {

  private final Gitlab gl;
  private final Screen con;
  private final Counters counters;
  private final Funnel funnel;

  RecencyGate(Gitlab gl, Screen con, Counters counters, Funnel funnel) {
    this.gl = gl;
    this.con = con;
    this.counters = counters;
    this.funnel = funnel;
  }

  /**
   * last_activity_at est inutilisable pour *classer* — un commentaire de ticket le déplace — mais
   * il est exactement bon pour *exclure*, parce qu'il se trompe dans le sens sûr : il est gonflé
   * par les événements non-commit, donc tout projet ayant commité dans la fenêtre a forcément un
   * last_activity_at récent. Le filtre sur-inclut ; il ne sous-inclut pas.
   */
  List<Proj> run(List<Proj> all) {
    con.title("2. Filtre de fraîcheur (étape 1) — 0 appel");

    List<Proj> kept = all.stream().filter(p -> p.excluded == null).toList();
    for (Proj p : kept) {
      OffsetDateTime last = p.lastActivity;
      if (last == null || last.isBefore(funnel.windowStart())) {
        p.excluded = "inactif > %dj".formatted(funnel.sinceDays());
      }
    }
    List<Proj> fresh = kept.stream().filter(p -> p.excluded == null).toList();
    counters.fresh = fresh.size();
    con.out()
        .out()
        .printf(
            "  Actifs sur %d jours              : %d / %d (%s)%n",
            funnel.sinceDays(), fresh.size(), kept.size(), pct(fresh.size(), kept.size()));
    con.say("    Coupe la plus rentable du pipeline : gratuite.", DIM);
    return fresh;
  }

  /**
   * La propriété « le filtre ne sous-inclut pas » a une classe de fuite connue : réécritures
   * d'historique, imports et transferts peuvent désynchroniser last_activity_at des dates de
   * commit. On l'échantillonne au lieu de la croire — zéro faux négatif rend la porte gratuite, un
   * chiffre non nul remplace une hypothèse par une mesure.
   */
  void validate(List<Proj> all) {
    int wanted = funnel.options().validationSample;
    if (wanted <= 0) return;
    List<Proj> below =
        all.stream()
            .filter(p -> p.excluded != null && p.excluded.startsWith("inactif"))
            .collect(Collectors.toCollection(ArrayList::new));
    if (below.isEmpty()) return;

    Collections.shuffle(below, new Random(funnel.options().seed));
    List<Proj> sample = below.subList(0, Math.min(wanted, below.size()));
    int leaks = 0;
    CommitPager pager = new CommitPager(gl, funnel);
    List<Integer> counts = gl.map(sample, funnel.concurrency(), pager::count);
    for (int i = 0; i < sample.size(); i++) {
      int n = counts.get(i);
      if (n > 0) {
        leaks++;
        sample.get(i).leaked = true;
        sample.get(i).commits = n;
      }
    }
    counters.sampleSize = sample.size();
    counters.sampleLeaks = leaks;
    con.out()
        .out()
        .printf(
            "  Échantillon de contrôle sous la coupe : %d projets, %d faux négatif%s (%s)%n",
            sample.size(), leaks, plural(leaks), pct(leaks, sample.size()));
    if (leaks > 0) {
      con.say("    Le filtre laisse passer des projets actifs. Chiffre à publier", YELLOW);
      con.say("    tel quel dans le rapport : la sélection n'est pas exhaustive.", YELLOW);
    } else {
      con.say("    Aucune fuite mesurée : la porte tient, et elle reste gratuite.", DIM);
    }
  }
}
