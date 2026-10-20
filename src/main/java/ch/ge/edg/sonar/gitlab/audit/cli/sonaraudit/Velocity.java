package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.numeric;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.orEmpty;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.parseDate;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.HistoryPoint;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Ce que l'historique dit, séparé de la façon de le dire.
 *
 * <p>{@code spanDays} n'est pas décoratif : deux analyses espacées de douze jours dans une fenêtre
 * de quatre-vingt-dix produisent un Δ parfaitement réel et parfaitement trompeur si on le lit comme
 * un trimestre.
 */
record Velocity(long deltaNcloc, long deltaDebt, long spanDays, String from, String to) {

  double debtPerLine() {
    return (double) deltaDebt / deltaNcloc;
  }

  /**
   * {@code null} dès qu'un des deux bouts manque — et surtout pas zéro.
   *
   * <p>L'ancienne version lisait la première et la dernière valeur avec un parseur qui rendait 0
   * sur tout ce qu'il ne comprenait pas. Un projet dont l'historique est illisible ressortait donc
   * « +0 ligne, +0 minute de dette » : le portrait exact d'une équipe irréprochable. C'est la
   * confusion absent / zéro que ce dépôt documente partout ailleurs, et elle devient dangereuse le
   * jour où ce calcul alimente une colonne de CSV plutôt qu'une ligne de console.
   */
  static @Nullable Velocity of(List<HistoryPoint> ncloc, List<HistoryPoint> debt, ZoneId zone) {
    Long dNcloc = delta(ncloc);
    Long dDebt = delta(debt);
    if (dNcloc == null || dDebt == null) return null;

    String first = ncloc.get(0).date();
    String last = ncloc.get(ncloc.size() - 1).date();
    LocalDateTime from = parseDate(first, zone);
    LocalDateTime to = parseDate(last, zone);
    long span = (from == null || to == null) ? 0 : ChronoUnit.DAYS.between(from, to);
    return new Velocity(dNcloc, dDebt, span, orEmpty(first), orEmpty(last));
  }

  /** Écart entre le premier et le dernier point chiffré, ou null s'il en manque un. */
  static @Nullable Long delta(@Nullable List<HistoryPoint> points) {
    if (points == null || points.size() < 2) return null;
    Double first = numeric(points.get(0).value());
    Double last = numeric(points.get(points.size() - 1).value());
    if (first == null || last == null) return null;
    return (long) (last - first);
  }
}
