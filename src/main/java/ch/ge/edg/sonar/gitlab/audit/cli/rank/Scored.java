package ch.ge.edg.sonar.gitlab.audit.cli.rank;

import java.util.EnumMap;
import java.util.Map;

/** Un projet en cours de classement : sa strate, ses signaux, son score, son sort. */
final class Scored {
  final InventoryRow row;
  final Bucket bucket;
  final Map<Signal, Double> severities = new EnumMap<>(Signal.class);
  final Map<Signal, Double> percentiles = new EnumMap<>(Signal.class);
  Exclusion exclusion = Exclusion.NONE;
  double score;
  boolean shortlisted;

  Scored(InventoryRow row) {
    this.row = row;
    this.bucket = Bucket.of(row.ncloc());
  }

  int signalsPresent() {
    return severities.size();
  }

  boolean ranked() {
    return exclusion == Exclusion.NONE;
  }
}
