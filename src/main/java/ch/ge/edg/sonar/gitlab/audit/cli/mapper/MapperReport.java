package ch.ge.edg.sonar.gitlab.audit.cli.mapper;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.matching.Report;
import ch.ge.edg.sonar.gitlab.audit.matching.report.ReportPrinter;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.nio.file.Path;

/** Ce que l'appariement dit à la console, autour du rapport d'évaluation. */
record MapperReport(Console console) {

  private static final String BOLD = "\033[1m";
  private static final String DIM = "\033[2m";

  void inputs(Csv.Table left, Path leftFile, Csv.Table right, Path rightFile) {
    console.println(console.color("\nAppariement", BOLD));
    console.print(Text.format("  Gauche : %d lignes (%s)%n", left.rows().size(), leftFile));
    console.print(Text.format("  Droite : %d lignes (%s)%n", right.rows().size(), rightFile));
  }

  void evaluation(Report rep) {
    new ReportPrinter(console).print(rep);
  }

  /** Comment ouvrir l'appariement écrit, selon son séparateur. */
  void openingHint(Path mapping, boolean comma) {
    console.println(console.color(Csv.openingHint(mapping, comma), DIM));
  }

  void reportWritten(Path json) {
    console.print(Text.format("  Rapport     : %s%n", json.toAbsolutePath()));
  }
}
