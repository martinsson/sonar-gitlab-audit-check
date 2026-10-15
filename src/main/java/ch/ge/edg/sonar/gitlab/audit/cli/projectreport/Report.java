package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import ch.ge.edg.sonar.gitlab.audit.console.ConsoleOut;
import java.io.PrintStream;

/** La sortie du rapport : titres de section, lignes de statut, couleurs. */
final class Report {

  static final String BOLD = "\033[1m";
  static final String DIM = "\033[2m";
  static final String RESET = "\033[0m";
  static final String GREEN = "\033[32m";
  static final String RED = "\033[31m";
  static final String YELLOW = "\033[33m";

  private final PrintStream out;

  Report(PrintStream out) {
    this.out = out;
  }

  static String c(String text, String color) {
    return ConsoleOut.color(text, color);
  }

  void title(String text) {
    out.println();
    out.println(
        c("=== " + text + " ", BOLD) + c("=".repeat(Math.max(0, 66 - text.length())), BOLD));
  }

  void line(String label, Verdict v, String detail) {
    out.printf("  %s %-42s %s%n", c(v.tag, v.color), label, detail.isBlank() ? "" : c(detail, DIM));
  }

  void println() {
    out.println();
  }

  void println(String text) {
    out.println(text);
  }

  /** Une ligne entière dans une couleur. */
  void println(String text, String color) {
    out.println(c(text, color));
  }
}
