package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;

/**
 * Les réglages de l'entonnoir pour cette course : les options, maintenant, le début de la fenêtre
 * et le motif des robots. Rien ici ne change en cours de route ; les effectifs vont dans {@link
 * Counters}, l'instance et l'écran sont passés à part.
 *
 * @param now l'instant de la course, dans le fuseau de l'horloge
 * @param windowStart {@code now} moins --since jours
 */
record Funnel(FunnelOptions options, OffsetDateTime now, OffsetDateTime windowStart, Pattern bots) {

  /** La fenêtre part de maintenant et remonte de --since jours. */
  static Funnel start(FunnelOptions options, Clock clock) {
    OffsetDateTime now = OffsetDateTime.now(clock);
    return new Funnel(
        options, now, now.minusDays(options.sinceDays), Pattern.compile(options.botPattern));
  }

  int sinceDays() {
    return options.sinceDays;
  }

  int concurrency() {
    return options.concurrency;
  }
}
