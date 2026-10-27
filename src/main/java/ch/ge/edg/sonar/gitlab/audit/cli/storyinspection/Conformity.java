package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

import java.util.List;

/**
 * Le classement des stories : conformes, avec un défaut critique, ou avec seulement des défauts
 * mineurs.
 */
record Conformity(int total, long conform, long critical, long minor) {

  static Conformity of(List<StoryResult> results) {
    return new Conformity(
        results.size(),
        results.stream().filter(r -> r.criticalCount() == 0 && r.minorCount() == 0).count(),
        results.stream().filter(r -> r.criticalCount() > 0).count(),
        results.stream().filter(r -> r.minorCount() > 0 && r.criticalCount() == 0).count());
  }

  /** La part de {@code count} dans le total, en pourcents arrondis. */
  int percent(long count) {
    return (int) Math.round((double) count / total * 100);
  }
}
