package ch.ge.edg.sonar.gitlab.audit.dora;

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

  private static final DateTimeFormatter OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final DateTimeFormatter JIRA =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

  private Times() {}

  /** Epoch en millisecondes (GoCD), ISO-8601, ou le format Jira 2025-03-04T10:15:30.000+0100. */
  public static @Nullable Instant parse(@Nullable JsonNode n) {
    if (n == null || n.isMissingNode() || n.isNull()) return null;
    if (n.isNumber()) return Instant.ofEpochMilli(n.asLong());
    return parse(n.asText(""));
  }

  public static @Nullable Instant parse(@Nullable String s) {
    if (s == null || s.isBlank()) return null;
    try {
      return OffsetDateTime.parse(s).toInstant();
    } catch (DateTimeParseException e) {
      try {
        return OffsetDateTime.parse(s, JIRA).toInstant();
      } catch (DateTimeParseException e2) {
        return null;
      }
    }
  }

  /** Heure locale du poste ; vide si absent. */
  public static String format(@Nullable Instant t, ZoneId zone) {
    return t == null ? "" : OUT.format(t.atZone(zone));
  }

  /** Durée en heures, une décimale, virgule décimale ; vide si inconnue ou négative. */
  public static String hours(@Nullable Instant from, @Nullable Instant to) {
    return decimal(from, to, 60.0, true);
  }

  /** Durée en jours, une décimale, virgule décimale ; vide si inconnue ou négative. */
  public static String days(@Nullable Instant from, @Nullable Instant to) {
    return decimal(from, to, 24.0, false);
  }

  private static String decimal(
      @Nullable Instant from, @Nullable Instant to, double per, boolean minutes) {
    if (from == null || to == null || to.isBefore(from)) return "";
    Duration d = Duration.between(from, to);
    double value = (minutes ? d.toMinutes() : d.toHours()) / per;
    return String.format(Locale.ROOT, "%.1f", value).replace('.', ',');
  }
}
