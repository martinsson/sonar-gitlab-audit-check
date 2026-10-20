package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import ch.ge.edg.sonar.gitlab.audit.console.Console;

/** La sortie du rapport : titres de section, lignes de statut, couleurs. */
final class Report {

  static final String BOLD = "\033[1m";
  static final String DIM = "\033[2m";
  static final String RESET = "\033[0m";
  static final String GREEN = "\033[32m";
  static final String RED = "\033[31m";
  static final String YELLOW = "\033[33m";

  private final Console out;

  Report(Console out) {
    this.out = out;
  }

  String c(String text, String color) {
    return out.color(text, color);
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
