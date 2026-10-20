package ch.ge.edg.sonar.gitlab.audit.matching;

import java.util.Locale;

/** Nombres tels que l'appariement les affiche. */
public final class Fmt {

  private Fmt() {}

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
