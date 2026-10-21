package ch.ge.edg.sonar.gitlab.audit.text;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Les petits utilitaires de texte et de nombres que chaque commande recopiait. */
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

  /** Deux décimales avec un point, quelle que soit la locale : d'autres outils relisent les CSV. */
  public static String decimal(@Nullable Double d) {
    return d == null ? "" : String.format(Locale.ROOT, "%.2f", d);
  }

  public static double round(double v) {
    return Math.round(v * 1000) / 1000.0;
  }

  public static String pct(long n, long total) {
    return total == 0 ? "—" : format("%.0f %%", 100.0 * n / total);
  }

  public static String pct(double ratio) {
    return format("%3.0f %%", 100 * ratio);
  }

  /**
   * {@link String#format} en {@link Locale#ROOT} : la seule façon dont une commande met nombres et
   * dates en texte, pour qu'une machine française écrive aussi 1.5 et que le même rapport sorte
   * partout.
   */
  @FormatMethod
  public static String format(@FormatString String pattern, @Nullable Object... args) {
    return String.format(Locale.ROOT, pattern, args);
  }
}
