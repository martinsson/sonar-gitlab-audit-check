package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

/** Combien de points de contrôle ont reçu chaque verdict. */
record Tally(Map<Verdict, Integer> counts) {

  static Tally of(Stream<InspectionPoint> points) {
    Map<Verdict, Integer> counts = new EnumMap<>(Verdict.class);
    points.forEach(p -> counts.merge(p.verdict(), 1, Integer::sum));
    return new Tally(counts);
  }

  int of(Verdict verdict) {
    return counts.getOrDefault(verdict, 0);
  }

  /** Les points qui ne sont pas respectés en entier : partiels et non respectés. */
  int problems() {
    return of(Verdict.PARTIEL) + of(Verdict.NON_RESPECTE);
  }
}
