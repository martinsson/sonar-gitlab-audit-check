package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import ch.ge.edg.sonar.gitlab.audit.console.ConsoleOut;
import java.io.PrintStream;

/** Couleurs, titres de section et lignes de verdict du rapport console. */
final class Presentation {

  static final String BOLD = "\033[1m";
  static final String DIM = "\033[2m";
  static final String GREEN = "\033[32m";
  static final String RED = "\033[31m";
  static final String YELLOW = "\033[33m";

  private Presentation() {}

  static String c(String text, String color) {
    return ConsoleOut.color(text, color);
  }

  static void title(PrintStream out, String text) {
    out.println();
    out.println(
        c("=== " + text + " ", BOLD) + c("=".repeat(Math.max(0, 66 - text.length())), BOLD));
  }

  static void line(PrintStream out, String label, Verdict v, String detail) {
    out.printf("  %s %-42s %s%n", c(v.tag, v.color), label, detail.isBlank() ? "" : c(detail, DIM));
  }
}
