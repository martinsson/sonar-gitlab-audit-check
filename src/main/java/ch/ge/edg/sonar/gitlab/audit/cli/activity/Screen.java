package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import org.jspecify.annotations.Nullable;

/**
 * La mise en forme du rapport à l'écran, sur la console prise une fois à l'entrée de la commande.
 */
record Screen(Console out) {

  static final String BOLD = "\033[1m";
  static final String DIM = "\033[2m";
  static final String RESET = "\033[0m";
  static final String GREEN = "\033[32m";
  static final String RED = "\033[31m";
  static final String YELLOW = "\033[33m";

  String c(String text, String color) {
    return out.color(text, color);
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
