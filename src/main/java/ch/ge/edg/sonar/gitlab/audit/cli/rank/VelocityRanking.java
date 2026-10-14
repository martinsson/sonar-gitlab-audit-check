package ch.ge.edg.sonar.gitlab.audit.cli.rank;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Le classement vélocité des projets actifs : par percentile *à taille comparable*, sur les quatre
 * {@link Signal signaux}. Une métrique absente est retirée de la moyenne, jamais remplacée par 0.
 */
public final class VelocityRanking {

  private VelocityRanking() {}

  /** Les classés d'abord, du pire au moins pire, puis les écartés dans l'ordre d'entrée. */
  static List<Scored> rank(List<InventoryRow> active, RankSettings settings) {
    List<Scored> all = active.stream().map(Scored::new).toList();

    // Une exclusion ne pénalise pas le score : elle retire du classement.
    for (Scored s : all) {
      if (s.row.ncloc() < settings.minNcloc()) s.exclusion = Exclusion.TOO_SMALL;
      else if (s.row.noNewCode()) s.exclusion = Exclusion.NO_NEW_CODE;
    }

    List<Scored> candidates = all.stream().filter(Scored::ranked).toList();
    for (Scored s : candidates) measure(s);

    Map<Bucket, List<Scored>> byBucket = byBucket(candidates);
    byBucket.values().forEach(VelocityRanking::percentiles);
    for (Scored s : candidates) score(s, settings.minSignals());

    List<Scored> ranked =
        candidates.stream()
            .filter(Scored::ranked)
            .sorted(Comparator.comparingDouble((Scored s) -> s.score).reversed())
            .collect(Collectors.toCollection(ArrayList::new));
    shortlist(ranked, byBucket, settings.topPercent());

    List<Scored> result = new ArrayList<>(ranked);
    all.stream().filter(s -> !s.ranked()).forEach(result::add);
    return result;
  }

  private static void measure(Scored s) {
    for (Signal sig : Signal.values()) {
      Double v = sig.severity(s.row);
      if (v != null) s.severities.put(sig, v);
    }
  }

  private static Map<Bucket, List<Scored>> byBucket(List<Scored> candidates) {
    Map<Bucket, List<Scored>> byBucket = new EnumMap<>(Bucket.class);
    for (Scored s : candidates) {
      byBucket.computeIfAbsent(s.bucket, b -> new ArrayList<>()).add(s);
    }
    return byBucket;
  }

  /** Percentiles calculés strate par strate, signal par signal. */
  private static void percentiles(List<Scored> stratum) {
    for (Signal sig : Signal.values()) {
      List<Double> values =
          stratum.stream()
              .map(s -> s.severities.get(sig))
              .filter(Objects::nonNull)
              .sorted()
              .toList();
      if (values.isEmpty()) continue;
      for (Scored s : stratum) {
        Double v = s.severities.get(sig);
        if (v != null) s.percentiles.put(sig, percentileOf(v, values));
      }
    }
  }

  /** Moyenne pondérée des percentiles présents ; trop peu de signaux écarte du classement. */
  private static void score(Scored s, int minSignals) {
    if (s.signalsPresent() < minSignals) {
      s.exclusion = Exclusion.TOO_FEW_SIGNALS;
      return;
    }
    double num = 0;
    double den = 0;
    for (Map.Entry<Signal, Double> e : s.percentiles.entrySet()) {
      num += e.getValue() * e.getKey().weight;
      den += e.getKey().weight;
    }
    s.score = num / den;
  }

  /** Décile haut de *chaque* strate : la shortlist reste répartie sur les tailles. */
  private static void shortlist(
      List<Scored> ranked, Map<Bucket, List<Scored>> byBucket, int topPercent) {
    Map<Bucket, Integer> taken = new EnumMap<>(Bucket.class);
    for (Scored s : ranked) {
      int stratumSize = byBucket.getOrDefault(s.bucket, List.of()).size();
      int quota = Math.max(1, (int) Math.round(stratumSize * topPercent / 100.0));
      int used = taken.getOrDefault(s.bucket, 0);
      if (used < quota) {
        s.shortlisted = true;
        taken.put(s.bucket, used + 1);
      }
    }
  }

  /**
   * Percentile par rang moyen : la part de la strate que ce projet dépasse en sévérité. Robuste aux
   * valeurs extrêmes, et sans unité à réconcilier entre des signaux qui n'en partagent aucune.
   */
  public static double percentileOf(double v, List<Double> sorted) {
    int less = 0;
    int equal = 0;
    for (double x : sorted) {
      if (x < v) less++;
      else if (x == v) equal++;
    }
    return 100.0 * (less + 0.5 * equal) / sorted.size();
  }
}
