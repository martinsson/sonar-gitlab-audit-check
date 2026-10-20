package ch.ge.edg.sonar.gitlab.audit.text;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** The small text and number helpers every command was keeping its own copy of. */
public final class Text {

  private Text() {}

  public static boolean isBlank(@Nullable String s) {
    return s == null || s.isBlank();
  }

  public static String orEmpty(@Nullable String s) {
    return s == null ? "" : s;
  }

  public static String orNa(@Nullable String s) {
    return isBlank(s) ? "n/a" : orEmpty(s);
  }

  /** At most {@code max} characters: the rest is cut. */
  public static String truncate(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max);
  }

  /** At most {@code max} characters, the cut shown: {@code max - 3} characters and three dots. */
  public static String ellipsize(String s, int max) {
    return s.length() <= max ? s : s.substring(0, Math.max(0, max - 3)) + "...";
  }

  /** Two decimals with a point whatever the locale: CSV files are read back by other tools. */
  public static String decimal(@Nullable Double d) {
    return d == null ? "" : String.format(Locale.ROOT, "%.2f", d);
  }

  public static double round(double v) {
    return Math.round(v * 1000) / 1000.0;
  }

  public static String pct(long n, long total) {
    return total == 0 ? "—" : "%.0f %%".formatted(100.0 * n / total);
  }

  public static String pct(double ratio) {
    return "%3.0f %%".formatted(100 * ratio);
  }
}
