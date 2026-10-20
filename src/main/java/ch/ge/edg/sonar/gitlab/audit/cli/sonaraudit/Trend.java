package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.numeric;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.parseDate;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.HistoryPoint;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.SearchHistory;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * Pentes par régression linéaire sur toutes les analyses de la fenêtre, ramenées à 30 jours.
 *
 * <p>Régression plutôt que (dernier − premier) : les analyses tombent quand la CI tourne, pas au
 * calendrier, et un seul point aberrant en bout de série — un profil qualité durci la veille —
 * ferait à lui seul la tendance.
 *
 * <p>Les pentes « pct » sont relatives : pente divisée par la moyenne de la série. C'est ce qui
 * rend comparables un projet de 2 000 lignes et un de 400 000. Null, jamais zéro, quand la série ne
 * permet pas de conclure.
 */
record Trend(
    int points,
    long spanDays,
    @Nullable Double violationsPerMonth,
    @Nullable Double densityPctPerMonth,
    @Nullable Double debtRatioPctPerMonth) {

  static final int MIN_POINTS = 3;
  static final int MIN_SPAN_DAYS = 14;
  static final double FLAT_PCT = 2;

  /** Pente absolue des issues, par jour — ce que CrossAudit multiplie par sa fenêtre. */
  @Nullable Double violationsPerDay() {
    return violationsPerMonth == null ? null : violationsPerMonth / 30;
  }

  static Trend of(SearchHistory h, ZoneId zone) {
    SortedMap<LocalDateTime, Double> violations = series(h.pointsFor("violations"), zone);
    SortedMap<LocalDateTime, Double> ncloc = series(h.pointsFor("ncloc"), zone);
    SortedMap<LocalDateTime, Double> ratio = series(h.pointsFor("sqale_debt_ratio"), zone);

    // Densité : seulement aux dates où les deux mesures existent.
    SortedMap<LocalDateTime, Double> density = new TreeMap<>();
    for (Map.Entry<LocalDateTime, Double> e : violations.entrySet()) {
      Double n = ncloc.get(e.getKey());
      if (n != null && n > 0) density.put(e.getKey(), e.getValue() / n * 1000);
    }

    SortedSet<LocalDateTime> dates = new TreeSet<>();
    dates.addAll(violations.keySet());
    dates.addAll(ncloc.keySet());
    dates.addAll(ratio.keySet());
    long span = dates.size() < 2 ? 0 : ChronoUnit.DAYS.between(dates.first(), dates.last());

    Double vSlope = slopePerDay(violations);
    return new Trend(
        dates.size(),
        span,
        vSlope == null ? null : vSlope * 30,
        relativePerMonth(density),
        relativePerMonth(ratio));
  }

  private static SortedMap<LocalDateTime, Double> series(List<HistoryPoint> points, ZoneId zone) {
    SortedMap<LocalDateTime, Double> out = new TreeMap<>();
    for (HistoryPoint p : points) {
      LocalDateTime d = parseDate(p.date(), zone);
      Double v = numeric(p.value());
      if (d != null && v != null) out.put(d, v);
    }
    return out;
  }

  /** Moindres carrés, x en jours. Null sous les seuils : trop peu pour une pente. */
  static @Nullable Double slopePerDay(SortedMap<LocalDateTime, Double> s) {
    if (s.size() < MIN_POINTS) return null;
    LocalDateTime t0 = s.firstKey();
    if (ChronoUnit.DAYS.between(t0, s.lastKey()) < MIN_SPAN_DAYS) return null;
    double n = s.size();
    double sx = 0;
    double sy = 0;
    double sxx = 0;
    double sxy = 0;
    for (Map.Entry<LocalDateTime, Double> e : s.entrySet()) {
      double x = ChronoUnit.MINUTES.between(t0, e.getKey()) / 1440.0;
      double y = e.getValue();
      sx += x;
      sy += y;
      sxx += x * x;
      sxy += x * y;
    }
    double var = n * sxx - sx * sx;
    return var == 0 ? null : (n * sxy - sx * sy) / var;
  }

  /** Pente sur 30 j en % de la moyenne ; null si la moyenne est nulle. */
  static @Nullable Double relativePerMonth(SortedMap<LocalDateTime, Double> s) {
    Double slope = slopePerDay(s);
    if (slope == null) return null;
    double mean = s.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
    return mean <= 0 ? null : slope * 30 / mean * 100;
  }
}
