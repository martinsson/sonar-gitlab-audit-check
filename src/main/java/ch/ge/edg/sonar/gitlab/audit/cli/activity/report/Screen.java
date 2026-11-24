package ch.ge.edg.sonar.gitlab.audit.cli.activity.report;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import org.jspecify.annotations.Nullable;

/**
 * La mise en forme du rapport à l'écran, sur la console prise une fois à l'entrée de la commande.
 */
public record Screen(Console out) {

  public static final String BOLD = "\033[1m";
  public static final String DIM = "\033[2m";
  static final String RESET = "\033[0m";
  static final String GREEN = "\033[32m";
  public static final String RED = "\033[31m";
  public static final String YELLOW = "\033[33m";

  String paint(String text, String color) {
    return out.color(text, color);
  }

  /** Une ligne colorée. */
  public void say(String text, String color) {
    out.println(paint(text, color));
  }

  public void title(String text) {
    out.println();
    out.println(paint(text, BOLD));
    out.println(paint("-".repeat(Math.min(text.length(), 72)), DIM));
  }

  public void line(String label, ProbeVerdict v, @Nullable String detail) {
    out.printf(
        "  %-32s %s  %s%n", label, paint("[" + v.tag + "]", v.color), detail == null ? "" : detail);
  }
}
