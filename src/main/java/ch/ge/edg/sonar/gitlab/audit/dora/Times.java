package ch.ge.edg.sonar.gitlab.audit.dora;

import ch.ge.edg.sonar.gitlab.audit.text.Text;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Les dates telles que Jira et GoCD les écrivent, et telles qu'Excel les lit. */
public final class Times {

  private static final DateTimeFormatter OUT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
  private static final DateTimeFormatter JIRA =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT);

  private Times() {}

  /** Epoch en millisecondes (GoCD), ISO-8601, ou le format Jira 2025-03-04T10:15:30.000+0100. */
  public static @Nullable Instant parse(@Nullable JsonNode n) {
    if (n == null || n.isMissingNode() || n.isNull()) return null;
    if (n.isNumber()) return Instant.ofEpochMilli(n.asLong());
    return parse(n.asText(""));
  }

  public static @Nullable Instant parse(@Nullable String s) {
    if (s == null || s.isBlank()) return null;
    Instant iso = parseWith(s, null);
    return iso != null ? iso : parseWith(s, JIRA);
  }

  private static @Nullable Instant parseWith(String s, @Nullable DateTimeFormatter format) {
    try {
      return (format == null ? OffsetDateTime.parse(s) : OffsetDateTime.parse(s, format))
          .toInstant();
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /** Heure locale du poste ; vide si absent. */
  public static String format(@Nullable Instant t, ZoneId zone) {
    return t == null ? "" : OUT.format(t.atZone(zone));
  }

  /** Durée en heures, une décimale, virgule décimale ; vide si inconnue ou négative. */
  public static String hours(@Nullable Instant from, @Nullable Instant to) {
    Duration d = elapsed(from, to);
    return d == null ? "" : tenths(d.toMinutes() / 60.0);
  }

  /** Durée en jours, une décimale, virgule décimale ; vide si inconnue ou négative. */
  public static String days(@Nullable Instant from, @Nullable Instant to) {
    Duration d = elapsed(from, to);
    return d == null ? "" : tenths(d.toHours() / 24.0);
  }

  private static @Nullable Duration elapsed(@Nullable Instant from, @Nullable Instant to) {
    if (from == null || to == null) return null;
    return to.isBefore(from) ? null : Duration.between(from, to);
  }

  private static String tenths(double value) {
    return Text.format("%.1f", value).replace('.', ',');
  }
}
