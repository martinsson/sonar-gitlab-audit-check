package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import ch.ge.edg.sonar.gitlab.audit.console.ConsoleOut;
import java.io.PrintStream;
import org.jspecify.annotations.Nullable;

/**
 * Le rapport à l'écran : la sortie prise une fois à l'entrée de la commande, et sa mise en forme.
 */
record Console(PrintStream out) {

  static final String BOLD = "\033[1m";
  static final String DIM = "\033[2m";
  static final String RESET = "\033[0m";
  static final String GREEN = "\033[32m";
  static final String RED = "\033[31m";
  static final String YELLOW = "\033[33m";

  static String c(String text, String color) {
    return ConsoleOut.color(text, color);
  }

  /** Une ligne colorée. */
  void say(String text, String color) {
    out.println(c(text, color));
  }

  void title(String text) {
    out.println();
    out.println(c(text, BOLD));
    out.println(c("-".repeat(Math.min(text.length(), 72)), DIM));
  }

  void line(String label, Verdict v, @Nullable String detail) {
    out.printf(
        "  %-32s %s  %s%n", label, c("[" + v.tag + "]", v.color), detail == null ? "" : detail);
  }
}
