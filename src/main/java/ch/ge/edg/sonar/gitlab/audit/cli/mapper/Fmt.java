package ch.ge.edg.sonar.gitlab.audit.cli.mapper;

import ch.ge.edg.sonar.gitlab.audit.console.ConsoleOut;
import java.util.Locale;

/** Nombres et couleurs tels que l'appariement les affiche. */
public final class Fmt {

  static final String BOLD = "\033[1m";
  static final String DIM = "\033[2m";
  static final String GREEN = "\033[32m";
  static final String YELLOW = "\033[33m";

  private Fmt() {}

  static String c(String text, String color) {
    return ConsoleOut.color(text, color);
  }

  public static String score(double s) {
    return String.format(Locale.ROOT, "%.2f", s);
  }

  public static String pct(long n, long total) {
    return total == 0 ? "—" : "%.0f %%".formatted(100.0 * n / total);
  }

  public static String pct(double ratio) {
    return "%3.0f %%".formatted(100 * ratio);
  }

  static double round(double v) {
    return Math.round(v * 1000) / 1000.0;
  }
}
