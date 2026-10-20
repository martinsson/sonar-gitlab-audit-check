package ch.ge.edg.sonar.gitlab.audit.cli.cross;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.matching.Report;
import ch.ge.edg.sonar.gitlab.audit.matching.report.ReportPrinter;
import java.nio.file.Path;
import java.util.List;

/**
 * Ce que {@link CrossAudit} dit à la console autour de l'appariement : les entrées lues, le taux
 * d'appariement, et l'annonce de la passe sur tout GitLab.
 */
final class CrossReport {

  static final String BOLD = "\033[1m", DIM = "\033[2m";
  static final String GREEN = "\033[32m", YELLOW = "\033[33m";

  private final Console out;

  CrossReport(Console out) {
    this.out = out;
  }

  static void title(Console out, String text) {
    out.println();
    out.println(out.color(text, BOLD));
    out.println(out.color("-".repeat(Math.min(text.length(), 72)), DIM));
  }

  static String pct(long n, long total) {
    return total == 0 ? "—" : "%.0f %%".formatted(100.0 * n / total);
  }

  // ----------------------------------------------------------------------
  // Les entrées
  // ----------------------------------------------------------------------

  void inputs(Csv.Table sonar, Path sonarCsv, Csv.Table gitlab, Path gitlabCsv) {
    out.println(out.color("\nCroisement SonarQube × GitLab", BOLD));
    out.out().printf("  Côté Sonar   : %d projets (%s)%n", sonar.rows().size(), sonarCsv);
    out.out().printf("  Côté GitLab  : %d projets (%s)%n", gitlab.rows().size(), gitlabCsv);

    if (!gitlab.has("cle_sonar")) {
      out.println(out.color("\n  pratiques.csv ne porte pas de colonne cle_sonar.", YELLOW));
      out.println(
          out.color("  Elle vient de GitlabActivityAudit --deep : sans elle, la seule", YELLOW));
      out.println(
          out.color("  jointure possible est le rapprochement de noms, qui ne sort", YELLOW));
      out.println(
          out.color("  qu'en suggestions à confirmer. Relancer l'audit GitLab donne", YELLOW));
      out.println(out.color("  la jointure exacte.", YELLOW));
    }
  }

  void noInventory() {
    out.println(
        out.color(
            "\n  Pas d'inventaire GitLab à côté de pratiques.csv : les projets Sonar ne", YELLOW));
    out.println(
        out.color(
            "  sont cherchés que dans la sélection. --inventaire <inventaire.csv> pour", YELLOW));
    out.println(out.color("  les chercher dans tout GitLab.", YELLOW));
  }

  void inventory(Csv.Table t, Path file) {
    out.out().printf("  Tout GitLab  : %d projets (%s)%n", t.rows().size(), file);
  }

  // ----------------------------------------------------------------------
  // Le taux d'appariement, qui est un résultat et pas une note d'exécution
  // ----------------------------------------------------------------------

  void matchRate(List<CrossPair> pairs, Csv.Table sonar, Report rep, boolean wide) {
    title(out, "1. Appariement");
    out.println(out.color("  Méthodes appliquées dans l'ordre ; les suggestions de noms ne", DIM));
    out.println(out.color("  comptent dans aucun constat, elles sortent dans le CSV pour", DIM));
    out.println(out.color("  qu'un humain tranche.", DIM));
    new ReportPrinter(out).print(rep);

    long joined = pairs.stream().filter(CrossPair::joined).count();
    if (joined < pairs.size() * 0.5) lowRate();

    long sonarUnmatched =
        sonar.rows().size()
            - pairs.stream().filter(CrossPair::joined).map(p -> p.sqStr("key")).distinct().count();
    out.out().printf("%n  Projets Sonar sans dépôt GitLab apparié : %d%n", sonarUnmatched);
    if (wide) {
      out.println(
          out.color(
              "    Dans la sélection seulement : la section suivante les cherche dans tout GitLab.",
              DIM));
      return;
    }
    out.println(
        out.color("    Attention au dénominateur : pratiques.csv ne contient que les", DIM));
    out.println(
        out.color("    projets sélectionnés par l'audit GitLab, pas tout le parc. Un", DIM));
    out.println(
        out.color("    projet Sonar non apparié peut simplement ne pas avoir été tiré.", DIM));
  }

  private void lowRate() {
    out.println();
    for (String l :
        List.of(
            "Moins d'un projet sur deux apparié. Ce n'est pas un échec de l'outil,",
            "c'est le constat : l'intégration GitLab↔SonarQube n'est pas configurée,",
            "et personne ne peut aujourd'hui répondre « ce dépôt est-il analysé ? »",
            "sans le faire à la main. Même classe de trou de gouvernance que les",
            "projets jamais analysés.")) {
      out.println(out.color("  " + l, YELLOW));
    }
  }

  void wideIntro() {
    title(out, "1b. Chaque projet Sonar contre tout GitLab");
    out.println(
        out.color("  La sélection ne contient que les projets GitLab retenus ; ici, chaque", DIM));
    out.println(
        out.color("  projet Sonar est cherché dans tout l'inventaire. Mêmes règles : seules", DIM));
    out.println(
        out.color(
            "  les paires exactes et dérivées comptent, les suggestions sont à vérifier.", DIM));
  }

  void mappingReport(Path report) {
    out.out().printf("  Rapport d'appariement : %s%n", report.toAbsolutePath());
  }
}
