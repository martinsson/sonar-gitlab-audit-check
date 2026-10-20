package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.dec;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.median;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.pct;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.plural;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Screen.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Screen.YELLOW;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Ce que la passe profonde affiche : les routes de lecture de la CI, et la couverture. */
final class DeepReport {

  private final Screen con;
  private final List<Assessed> selected;

  DeepReport(Screen con, List<Assessed> selected) {
    this.con = con;
    this.selected = selected;
  }

  /** Avant la passe : combien de projets elle va sonder. */
  static void begin(Screen con, int selected) {
    con.title("6. Signaux de pratique sur les %d sélectionnés".formatted(selected));
  }

  /** pratiques.csv vient d'être écrit. */
  void written() {
    con.out().out().printf("%n  Pratiques : %d lignes.%n", selected.size());
  }

  /**
   * Par quelle route la CI a été lue, et combien n'ont rien coûté. Publié parce que les trois
   * routes ne voient pas la même chose : merged_yaml voit tout l'arbre d'inclusion, le repli ne
   * voit que ce qu'il sait lire, et un parc majoritairement lu par le repli a un ci_sonar qui
   * sous-compte. Le taux de repli est donc une réserve sur la mesure, pas une statistique
   * d'exécution.
   */
  void ciRoutes(int lintRefusedStatus) {
    Map<String, Long> byRoute =
        selected.stream()
            .map(a -> a.practices().sonar().ciRoute())
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(r -> r, Collectors.counting()));
    long cached = byRoute.getOrDefault(Practices.Sonar.CACHE, 0L);
    long lint = byRoute.getOrDefault(CiConfig.LINT, 0L);
    long back = byRoute.getOrDefault(CiConfig.FALLBACK, 0L);
    if (cached + lint + back > 0) {
      con.out()
          .out()
          .printf(
              "%n  CI lue : %d par ci/lint, %d par repli, %d depuis le cache.%n",
              lint, back, cached);
    }
    if (back > 0 && lintRefusedStatus > 0) {
      con.say(
          "    ci/lint refusé (HTTP %d) : le jeton n'a pas le droit de"
              .formatted(lintRefusedStatus),
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
    long pom = count(r -> r.sonar().facts().pomKey != null);
    long pomVar = count(r -> r.sonar().facts().pomUnresolved != null);
    long both = count(r -> r.sonar().facts().pomKey != null && r.sonar().facts().sonarKey != null);
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

  private long count(Predicate<Practices> test) {
    return selected.stream().filter(p -> test.test(p.practices())).count();
  }

  /**
   * Le témoin aléatoire sert ici : si la couverture des pratiques diverge fortement entre les
   * sélectionnés et le tirage témoin, c'est la règle de sélection qui est le sujet, pas les
   * pratiques.
   */
  void coverage() {
    List<Assessed> core = selected.stream().filter(p -> !isWitness(p)).toList();
    List<Assessed> witness = selected.stream().filter(DeepReport::isWitness).toList();

    con.out().out().printf("%n  %-38s %12s %12s%n", "", "sélection", "témoin");
    row("Branche par défaut protégée", core, witness, r -> r.protection().defaultProtected());
    row("Au moins une MR fusionnée", core, witness, r -> r.review().mergedMrs() > 0);
    row("Pipelines sur la branche par défaut", core, witness, r -> r.builds().pipelines() > 0);
    row("CI configurée pour Sonar", core, witness, r -> r.sonar().facts().ciSonar);
    row("Scan de sécurité dans la CI", core, witness, r -> r.sonar().facts().ciSecurity);
    row("CODEOWNERS présent", core, witness, r -> r.sonar().files().contains("CODEOWNERS"));
    row(
        "Aucun environnement déclaré",
        core,
        witness,
        r -> {
          Integer environments = r.deployment().environments();
          return environments != null && environments == 0;
        });

    recovery(core);
    reverts(core);
    unreviewed(core);
  }

  private static boolean isWitness(Assessed p) {
    return p.audited().fate() instanceof Fate.Selected s && s.isWitness();
  }

  private void recovery(List<Assessed> core) {
    List<Double> recoveries =
        core.stream()
            .map(p -> p.practices().builds().recovery())
            .filter(Objects::nonNull)
            .map(Practices.Recovery::medianHours)
            .filter(Objects::nonNull)
            .toList();
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

  private void reverts(List<Assessed> core) {
    List<Integer> counts =
        core.stream().map(p -> p.audited().commits().reverts()).filter(Objects::nonNull).toList();
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

  private void unreviewed(List<Assessed> core) {
    long noReview =
        core.stream()
            .filter(
                p -> p.practices().review().mergedMrs() == 0 && p.audited().commitsOrZero() >= 20)
            .count();
    if (noReview == 0) return;
    con.out()
        .out()
        .printf(
            "%n  %d projet%s à ≥20 commits sans une seule MR fusionnée.%n",
            noReview, plural(noReview));
    con.say("    Le changement atteint la branche par défaut sans revue.", YELLOW);
  }

  private void row(
      String label, List<Assessed> core, List<Assessed> witness, Predicate<Practices> test) {
    con.out()
        .out()
        .printf(
            "  %-38s %12s %12s%n",
            label, share(core, test), witness.isEmpty() ? "—" : share(witness, test));
  }

  private static String share(List<Assessed> ps, Predicate<Practices> test) {
    long n = ps.stream().filter(p -> test.test(p.practices())).count();
    return "%d (%s)".formatted(n, pct(n, ps.size()));
  }
}
