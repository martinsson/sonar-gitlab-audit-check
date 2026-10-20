package ch.ge.edg.sonar.gitlab.audit.matching.report;

import ch.ge.edg.sonar.gitlab.audit.console.ConsoleOut;

/** Couleurs telles que l'appariement les affiche. */
public final class Colors {

  public static final String BOLD = "\033[1m";
  public static final String DIM = "\033[2m";
  public static final String GREEN = "\033[32m";
  public static final String YELLOW = "\033[33m";

  private Colors() {}

  public static String c(String text, String color) {
    return ConsoleOut.color(text, color);
  }
}
