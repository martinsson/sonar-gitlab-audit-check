package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Les nombres, dates et durées tels que la page les montre : en français, virgule décimale. */
final class Show {

  private static final DateTimeFormatter DAY_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

  static final String NONE = "—";

  private Show() {}

  static String dateTime(@Nullable Instant at, ZoneId zone) {
    return at == null ? NONE : DAY_TIME.format(at.atZone(zone));
  }

  static String day(Instant at, ZoneId zone) {
    return DAY.format(at.atZone(zone));
  }

  /** « 45 min », « 3,5 h » jusqu'à deux jours, « 4,2 j » au-delà. */
  static String duration(@Nullable Duration d) {
    if (d == null) return NONE;
    long minutes = d.toMinutes();
    if (minutes < 60) return minutes + " min";
    if (minutes < 48 * 60) return tenths(minutes / 60.0) + " h";
    return tenths(minutes / 1440.0) + " j";
  }

  /** Une décimale, virgule décimale. */
  static String tenths(double value) {
    return Text.format("%.1f", value).replace('.', ',');
  }

  /** « 12 % » ; un tiret sans valeur. */
  static String percent(@Nullable Double ratio) {
    return ratio == null ? NONE : Text.format("%.0f", 100 * ratio) + " %";
  }

  /** Le texte, prêt pour du HTML. */
  static String html(@Nullable String s) {
    if (s == null) return "";
    StringBuilder out = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '&' -> out.append("&amp;");
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        case '"' -> out.append("&quot;");
        case '\'' -> out.append("&#39;");
        default -> out.append(c);
      }
    }
    return out.toString();
  }
}
