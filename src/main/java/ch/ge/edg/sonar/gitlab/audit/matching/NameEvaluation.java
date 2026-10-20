package ch.ge.edg.sonar.gitlab.audit.matching;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.matching.Report.Bucket;
import ch.ge.edg.sonar.gitlab.audit.matching.Report.Example;
import ch.ge.edg.sonar.gitlab.audit.matching.Report.NameStats;
import ch.ge.edg.sonar.gitlab.audit.matching.Report.ThresholdPoint;
import ch.ge.edg.sonar.gitlab.audit.matching.similarity.NameMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * La ressemblance de noms jugée là où l'on connaît la réponse : les paires sûres. Ce n'est pas une
 * vérité absolue — une clé normalisée peut se tromper — mais c'est la meilleure disponible, et elle
 * dit à quel seuil les suggestions deviennent fiables.
 */
final class NameEvaluation {

  private final Result r;
  private final Spec s;
  private final NameMatcher matcher;
  private final NameSpec names;
  private final int examples;
  private final List<Match> ms;
  private final List<Match> sure;

  NameEvaluation(Result r, NameMatcher matcher, NameSpec names, int examples) {
    this.r = r;
    this.s = r.spec();
    this.matcher = matcher;
    this.names = names;
    this.examples = examples;
    this.ms = r.matches();
    this.sure = ms.stream().filter(Match::joined).toList();
  }

  /** Une paire sûre et le score de ressemblance de son vrai homologue. */
  private record Scored(Match m, double trueScore) {}

  /** Par tranche de score du meilleur candidat libre : justes et faux, sur les paires sûres. */
  private final int[] correct = new int[10];

  private final int[] wrong = new int[10];
  private final List<Example> wrongOnSure = new ArrayList<>();
  private final List<Scored> divergentAll = new ArrayList<>();

  NameStats stats() {
    sure.forEach(this::tally);
    List<Bucket> buckets = new ArrayList<>();
    for (int b = 0; b < 10; b++) {
      buckets.add(
          new Bucket(
              String.format(Locale.ROOT, "%.1f–%.1f", b / 10.0, (b + 1) / 10.0),
              correct[b],
              wrong[b]));
    }
    return new NameStats(
        sure.size(),
        buckets,
        thresholds(),
        rejections(),
        wrongOnSure,
        divergent(),
        nearMisses(),
        LeftoverWords.of(r, names));
  }

  /** Une paire sûre : son vrai score, et ce que la ressemblance libre en a pensé. */
  private void tally(Match m) {
    Integer j = r.rightIndex().get(m.rightKey(s));
    double ts = j == null ? 0 : matcher.score(m.index(), j);
    if (ts < 0.3) divergentAll.add(new Scored(m, ts));
    NameGuess f = m.free();
    if (f == null || f.candidate().isEmpty()) return;
    int b = Math.min(9, (int) (f.score() * 10));
    if (f.candidate().equals(m.rightKey(s))) {
      correct[b]++;
      return;
    }
    wrong[b]++;
    if (f.accepted() && wrongOnSure.size() < examples) wrongOnSure.add(wrongExample(m, f, ts));
  }

  private Example wrongExample(Match m, NameGuess f, double ts) {
    return new Example(
        m.left().str(s.leftKey()),
        "proposé %s (%s), vrai %s (%s, par %s)"
            .formatted(
                f.candidate(), Fmt.score(f.score()), m.rightKey(s), Fmt.score(ts), m.method()));
  }

  private List<ThresholdPoint> thresholds() {
    List<ThresholdPoint> points = new ArrayList<>();
    for (int t = 3; t <= 9; t++) {
      double th = t / 10.0;
      int proposed = 0;
      int ok = 0;
      for (Match m : sure) {
        NameGuess f = m.free();
        if (f == null || !proposedAt(f, th)) continue;
        proposed++;
        if (f.candidate().equals(m.rightKey(s))) ok++;
      }
      points.add(
          new ThresholdPoint(
              th,
              proposed,
              ok,
              proposed == 0 ? null : Fmt.round(ok / (double) proposed),
              sure.isEmpty() ? 0 : Fmt.round(ok / (double) sure.size())));
    }
    return points;
  }

  /** La suggestion libre aurait-elle été faite à ce seuil, avec la marge actuelle ? */
  private boolean proposedAt(NameGuess f, double th) {
    if (f.candidate().isEmpty() || f.score() < th) return false;
    return f.runnerUp().isEmpty() || f.score() - f.runnerUpScore() >= s.margin();
  }

  private List<Example> divergent() {
    List<Example> divergent = new ArrayList<>();
    divergentAll.sort(Comparator.comparingDouble(Scored::trueScore));
    for (Scored d : divergentAll.subList(0, Math.min(examples, divergentAll.size()))) {
      Match m = d.m();
      Csv.Row x = m.right();
      String shown =
          names.rightColumns().stream()
              .map(col -> x == null ? "" : x.str(col))
              .filter(v -> !v.isEmpty())
              .distinct()
              .collect(Collectors.joining(" / "));
      divergent.add(
          new Example(
              m.left().str(s.leftKey()),
              "%s — score %s, apparié par %s"
                  .formatted(shown, Fmt.score(d.trueScore()), m.method())));
    }
    return divergent;
  }

  private Map<String, Integer> rejections() {
    Map<String, Integer> rejections = new LinkedHashMap<>();
    for (Match m : ms) {
      NameGuess g = m.guess();
      String rejection = g == null ? null : g.rejection();
      if (rejection == null) continue;
      String reason = rejection.startsWith("déjà proposé") ? "déjà proposé" : rejection;
      rejections.merge(reason, 1, Integer::sum);
    }
    return rejections;
  }

  private List<Example> nearMisses() {
    return ms.stream()
        .filter(m -> m.right() == null)
        .flatMap(m -> guessed(m).stream())
        .filter(n -> !n.g().candidate().isEmpty())
        .filter(n -> n.g().score() >= s.threshold() - 0.15)
        .sorted(Comparator.comparingDouble((Near n) -> n.g().score()).reversed())
        .limit(examples)
        .map(n -> nearMiss(n.m(), n.g()))
        .toList();
  }

  private record Near(Match m, NameGuess g) {}

  private static Optional<Near> guessed(Match m) {
    NameGuess g = m.guess();
    return g == null ? Optional.empty() : Optional.of(new Near(m, g));
  }

  private Example nearMiss(Match m, NameGuess g) {
    return new Example(
        m.left().str(s.leftKey()),
        "%s (%s) — %s%s"
            .formatted(
                g.candidate(),
                Fmt.score(g.score()),
                g.rejection(),
                g.runnerUp().isEmpty()
                    ? ""
                    : ", suivant %s (%s)".formatted(g.runnerUp(), Fmt.score(g.runnerUpScore()))));
  }
}
