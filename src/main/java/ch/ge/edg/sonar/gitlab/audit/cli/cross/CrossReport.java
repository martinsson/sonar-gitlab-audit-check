package ch.ge.edg.sonar.gitlab.audit.cli.cross;

import ch.ge.edg.sonar.gitlab.audit.console.ConsoleOut;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.matching.Report;
import ch.ge.edg.sonar.gitlab.audit.matching.report.ReportPrinter;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Ce que {@link CrossAudit} dit à la console autour de l'appariement : les entrées lues, le taux
 * d'appariement, et l'annonce de la passe sur tout GitLab.
 */
final class CrossReport {

  static final String BOLD = "\033[1m", DIM = "\033[2m";
  static final String GREEN = "\033[32m", YELLOW = "\033[33m";

  private final PrintStream out;

  CrossReport(PrintStream out) {
    this.out = out;
  }

  static String c(String text, String color) {
    return ConsoleOut.color(text, color);
  }

  static void title(PrintStream out, String text) {
    out.println();
    out.println(c(text, BOLD));
    out.println(c("-".repeat(Math.min(text.length(), 72)), DIM));
  }

  static String pct(long n, long total) {
    return total == 0 ? "—" : "%.0f %%".formatted(100.0 * n / total);
  }

  // ----------------------------------------------------------------------
  // Les entrées
  // ----------------------------------------------------------------------

  void inputs(Csv.Table sonar, Path sonarCsv, Csv.Table gitlab, Path gitlabCsv) {
    out.println(c("\nCroisement SonarQube × GitLab", BOLD));
    out.printf("  Côté Sonar   : %d projets (%s)%n", sonar.rows().size(), sonarCsv);
    out.printf("  Côté GitLab  : %d projets (%s)%n", gitlab.rows().size(), gitlabCsv);

    if (!gitlab.has("cle_sonar")) {
      out.println(c("\n  pratiques.csv ne porte pas de colonne cle_sonar.", YELLOW));
      out.println(c("  Elle vient de GitlabActivityAudit --deep : sans elle, la seule", YELLOW));
      out.println(c("  jointure possible est le rapprochement de noms, qui ne sort", YELLOW));
      out.println(c("  qu'en suggestions à confirmer. Relancer l'audit GitLab donne", YELLOW));
      out.println(c("  la jointure exacte.", YELLOW));
    }
  }

  void noInventory() {
    out.println(
        c("\n  Pas d'inventaire GitLab à côté de pratiques.csv : les projets Sonar ne", YELLOW));
    out.println(
        c("  sont cherchés que dans la sélection. --inventaire <inventaire.csv> pour", YELLOW));
    out.println(c("  les chercher dans tout GitLab.", YELLOW));
  }

  void inventory(Csv.Table t, Path file) {
    out.printf("  Tout GitLab  : %d projets (%s)%n", t.rows().size(), file);
  }

  // ----------------------------------------------------------------------
  // Le taux d'appariement, qui est un résultat et pas une note d'exécution
  // ----------------------------------------------------------------------

  void matchRate(List<CrossPair> pairs, Csv.Table sonar, Report rep, boolean wide) {
    title(out, "1. Appariement");
    out.println(c("  Méthodes appliquées dans l'ordre ; les suggestions de noms ne", DIM));
    out.println(c("  comptent dans aucun constat, elles sortent dans le CSV pour", DIM));
    out.println(c("  qu'un humain tranche.", DIM));
    new ReportPrinter(out).print(rep);

    long joined = pairs.stream().filter(CrossPair::joined).count();
    if (joined < pairs.size() * 0.5) lowRate();

    long sonarUnmatched =
        sonar.rows().size()
            - pairs.stream().filter(CrossPair::joined).map(p -> p.sqStr("key")).distinct().count();
    out.printf("%n  Projets Sonar sans dépôt GitLab apparié : %d%n", sonarUnmatched);
    if (wide) {
      out.println(
          c(
              "    Dans la sélection seulement : la section suivante les cherche dans tout GitLab.",
              DIM));
      return;
    }
    out.println(c("    Attention au dénominateur : pratiques.csv ne contient que les", DIM));
    out.println(c("    projets sélectionnés par l'audit GitLab, pas tout le parc. Un", DIM));
    out.println(c("    projet Sonar non apparié peut simplement ne pas avoir été tiré.", DIM));
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
      out.println(c("  " + l, YELLOW));
    }
  }

  void wideIntro() {
    title(out, "1b. Chaque projet Sonar contre tout GitLab");
    out.println(c("  La sélection ne contient que les projets GitLab retenus ; ici, chaque", DIM));
    out.println(c("  projet Sonar est cherché dans tout l'inventaire. Mêmes règles : seules", DIM));
    out.println(
        c("  les paires exactes et dérivées comptent, les suggestions sont à vérifier.", DIM));
  }

  void mappingReport(Path report) {
    out.printf("  Rapport d'appariement : %s%n", report.toAbsolutePath());
  }
}
