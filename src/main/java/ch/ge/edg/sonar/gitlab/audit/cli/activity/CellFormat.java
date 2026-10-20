package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Mise en forme des valeurs : une valeur absente s'écrit vide, jamais zéro. */
final class CellFormat {

  private CellFormat() {}

  static String pct(long n, long total) {
    return total == 0 ? "—" : Text.format("%.0f %%", 100.0 * n / total);
  }

  static @Nullable Double median(@Nullable List<Double> xs) {
    if (xs == null || xs.isEmpty()) return null;
    List<Double> s = new ArrayList<>(xs);
    Collections.sort(s);
    int m = s.size() / 2;
    return s.size() % 2 == 1 ? s.get(m) : (s.get(m - 1) + s.get(m)) / 2;
  }

  static String iso(@Nullable OffsetDateTime t) {
    return t == null ? "" : t.toString();
  }

  static String num(@Nullable Number n) {
    return n == null ? "" : String.valueOf(n);
  }

  static String dec(@Nullable Double d) {
    return d == null ? "" : Text.format("%.2f", d);
  }

  static boolean isBlank(@Nullable String s) {
    return s == null || s.isBlank();
  }

  static String orEmpty(@Nullable String s) {
    return s == null ? "" : s;
  }

  static String truncate(@Nullable String s, int max) {
    if (s == null) return "";
    return s.length() <= max ? s : s.substring(0, max - 1) + "…";
  }

  /** « s » au pluriel. */
  static String plural(long n) {
    return n > 1 ? "s" : "";
  }
}
