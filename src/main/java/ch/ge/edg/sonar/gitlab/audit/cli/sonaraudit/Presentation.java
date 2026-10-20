package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import ch.ge.edg.sonar.gitlab.audit.console.Console;

/** Couleurs, titres de section et lignes de verdict du rapport console. */
final class Presentation {

  static final String BOLD = "\033[1m";
  static final String DIM = "\033[2m";
  static final String GREEN = "\033[32m";
  static final String RED = "\033[31m";
  static final String YELLOW = "\033[33m";

  private Presentation() {}

  static void title(Console out, String text) {
    out.println();
    out.println(
        out.color("=== " + text + " ", BOLD)
            + out.color("=".repeat(Math.max(0, 66 - text.length())), BOLD));
  }

  static void line(Console out, String label, CallVerdict v, String detail) {
    out.printf(
        "  %s %-42s %s%n",
        out.color(v.tag, v.color), label, detail.isBlank() ? "" : out.color(detail, DIM));
  }
}
