package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.dec;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.median;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.pct;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.plural;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Screen.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Screen.YELLOW;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Ce que la passe profonde affiche : les routes de lecture de la CI, et la couverture. */
final class DeepReport {

  private final Screen con;
  private final Counters counters;
  private final List<Proj> selected;

  DeepReport(Screen con, Counters counters, List<Proj> selected) {
    this.con = con;
    this.counters = counters;
    this.selected = selected;
  }

  /**
   * Par quelle route la CI a été lue, et combien n'ont rien coûté. Publié parce que les trois
   * routes ne voient pas la même chose : merged_yaml voit tout l'arbre d'inclusion, le repli ne
   * voit que ce qu'il sait lire, et un parc majoritairement lu par le repli a un ci_sonar qui
   * sous-compte. Le taux de repli est donc une réserve sur la mesure, pas une statistique
   * d'exécution.
   */
  void ciRoutes() {
    Counters n = counters;
    int cached = n.ciFromCacheN.get();
    int lint = n.ciByLintN.get();
    int back = n.ciByFallbackN.get();
    if (cached + lint + back > 0) {
      con.out()
          .out()
          .printf(
              "%n  CI lue : %d par ci/lint, %d par repli, %d depuis le cache.%n",
              lint, back, cached);
    }
    if (back > 0 && n.lintRefusedStatus > 0) {
      con.say(
          "    ci/lint refusé (HTTP %d) : le jeton n'a pas le droit de "
              .formatted(n.lintRefusedStatus),
          YELLOW);
      con.say("    déplier la configuration. Le repli ne suit que les includes", YELLOW);
      con.say("    « project: » qu'il sait lire — ci_sonar y sous-compte. D'un", YELLOW);
      con.say("    « component: », il ne lit que le project_key passé en inputs.", YELLOW);
    }
    mavenKeys();
  }

  /**
   * La clé Maven se lit à côté de la CI, pas dedans : elle ne dépend d'aucune route, et c'est elle
   * qui apparie le composant Sonar partagé quand project_key est laissé vide.
   */
  private void mavenKeys() {
    long pom = count(r -> r.pomKey != null);
    long pomVar = count(r -> r.pomUnresolved != null);
    long both = count(r -> r.pomKey != null && r.sonarKey != null);
    con.out()
        .out()
        .printf(
            "  Clé Maven (pom.xml) : %d projets, dont %d ont aussi une clé dans la CI.%n",
            pom, both);
    if (pomVar > 0) {
      con.say(
          ("    %d pom avec ${…} dans groupId/artifactId : clé inconnue, " + "pas devinée.")
              .formatted(pomVar),
          YELLOW);
    }
  }

  private long count(Predicate<Prat> test) {
    return selected.stream().filter(p -> test.test(p.prat)).count();
  }

  /**
   * Le témoin aléatoire sert ici : si la couverture des pratiques diverge fortement entre les
   * sélectionnés et le tirage témoin, c'est la règle de sélection qui est le sujet, pas les
   * pratiques.
   */
  void coverage() {
    List<Proj> core = selected.stream().filter(p -> !isWitness(p)).toList();
    List<Proj> witness = selected.stream().filter(DeepReport::isWitness).toList();

    con.out().out().printf("%n  %-38s %12s %12s%n", "", "sélection", "témoin");
    row("Branche par défaut protégée", core, witness, r -> r.defaultProtected);
    row("Au moins une MR fusionnée", core, witness, r -> r.mergedMrs > 0);
    row("Pipelines sur la branche par défaut", core, witness, r -> r.pipelines > 0);
    row("CI configurée pour Sonar", core, witness, r -> r.ciSonar);
    row("Scan de sécurité dans la CI", core, witness, r -> r.ciSecurity);
    row("CODEOWNERS présent", core, witness, r -> r.files.contains("CODEOWNERS"));
    row(
        "Aucun environnement déclaré",
        core,
        witness,
        r -> r.environments != null && r.environments == 0);

    recovery(core);
    reverts(core);
    unreviewed(core);
  }

  private static boolean isWitness(Proj p) {
    return p.fate instanceof Fate.Selected s && s.isWitness();
  }

  private void recovery(List<Proj> core) {
    List<Double> recoveries =
        core.stream().map(p -> p.prat.recoveryMedianHours).filter(Objects::nonNull).toList();
    if (recoveries.isEmpty()) return;
    con.out()
        .out()
        .printf(
            "%n  Retour au vert (médiane des médianes) : %s h sur %d projet%s.%n",
            dec(median(recoveries)), recoveries.size(), plural(recoveries.size()));
    con.say("    Temps pendant lequel la branche par défaut reste cassée. " + "Ce n'est pas", DIM);
    con.say(
        "    une métrique DORA : sans environnement déclaré, aucune mise " + "en production", DIM);
    con.say("    n'est datable, et rien ici ne s'y substitue.", DIM);
  }

  private void reverts(List<Proj> core) {
    List<Integer> counts =
        core.stream().map(p -> p.commits.reverts()).filter(Objects::nonNull).toList();
    long reverting = counts.stream().filter(n -> n > 0).count();
    if (reverting == 0) return;
    int total = counts.stream().mapToInt(Integer::intValue).sum();
    con.out()
        .out()
        .printf(
            "%n  %d revert%s sur %d projet%s.%n",
            total, plural(total), reverting, plural(reverting));
    con.say("    Signal de qualité direct, indépendant de Sonar et des " + "environnements.", DIM);
  }

  private void unreviewed(List<Proj> core) {
    long noReview =
        core.stream().filter(p -> p.prat.mergedMrs == 0 && p.commitsOrZero() >= 20).count();
    if (noReview == 0) return;
    con.out()
        .out()
        .printf(
            "%n  %d projet%s à ≥20 commits sans une seule MR fusionnée.%n",
            noReview, plural(noReview));
    con.say("    Le changement atteint la branche par défaut sans revue.", YELLOW);
  }

  private void row(String label, List<Proj> core, List<Proj> witness, Predicate<Prat> test) {
    con.out()
        .out()
        .printf(
            "  %-38s %12s %12s%n",
            label, share(core, test), witness.isEmpty() ? "—" : share(witness, test));
  }

  private static String share(List<Proj> ps, Predicate<Prat> test) {
    long n = ps.stream().filter(p -> test.test(p.prat)).count();
    return "%d (%s)".formatted(n, pct(n, ps.size()));
  }
}
