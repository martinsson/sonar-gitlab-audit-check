package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.jspecify.annotations.Nullable;

/**
 * GitLab date en ISO-8601 avec décalage : « 2026-08-18T09:12:33.000+02:00 » ou en Z. On garde
 * l'OffsetDateTime tel quel là où le décalage porte du sens — l'heure de la journée est celle de la
 * personne qui a commité — et on ramène au fuseau de l'horloge pour les calculs d'âge.
 */
final class Dates {

  private final ZoneId zone;
  private final LocalDateTime now;

  /** {@code clock} : l'instant de référence, lu une fois, et le fuseau de toutes les dates. */
  Dates(Clock clock) {
    this.zone = clock.getZone();
    this.now = LocalDateTime.now(clock);
  }

  LocalDateTime now() {
    return now;
  }

  static @Nullable OffsetDateTime parseOffset(@Nullable String s) {
    return Gitlab.parse(s);
  }

  @Nullable LocalDateTime parseLocal(@Nullable String s) {
    OffsetDateTime o = parseOffset(s);
    return o == null ? null : o.atZoneSameInstant(zone).toLocalDateTime();
  }

  String dateOnly(@Nullable String s) {
    LocalDateTime d = parseLocal(s);
    return d == null ? "n/a" : d.toLocalDate().toString();
  }
}
