package ch.ge.edg.sonar.gitlab.audit.cli.mapper;

import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Report.Agreement;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Report.Example;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Report.Guards;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Report.LinkStats;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Report.MethodStats;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Report.Summary;
import ch.ge.edg.sonar.gitlab.audit.naming.NameMatcher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Chaque méthode évaluée seule, sur toutes les lignes : ce qu'elle propose, ce qu'elle seule
 * apporte, où elle contredit les autres. La ressemblance de noms est jugée à part, dans {@link
 * NameEvaluation}.
 */
public final class Evaluation {

  private final Result r;
  private final Spec s;
  private final List<Match> ms;
  private final int examples;

  /** Id → libellé des méthodes évaluées, dans l'ordre, les noms en dernier. */
  private final Map<String, String> labels = new LinkedHashMap<>();

  /** Par ligne : ce que chaque méthode, seule, propose. */
  private final List<Map<String, List<String>>> proposals = new ArrayList<>();

  private Evaluation(Result r, int examples) {
    this.r = r;
    this.s = r.spec();
    this.ms = r.matches();
    this.examples = examples;
  }

  public static Report evaluate(Result r, int examples) {
    return new Evaluation(r, examples).report();
  }

  private Report report() {
    Summary summary = summary();
    collectProposals();
    List<MethodStats> methods = new ArrayList<>();
    labels.forEach((id, label) -> methods.add(methodStats(id, label)));
    List<Agreement> agreements = new ArrayList<>();
    List<Example> disagreements = new ArrayList<>();
    List<String> ids = new ArrayList<>(labels.keySet());
    for (int a = 0; a < ids.size(); a++) {
      for (int b = a + 1; b < ids.size(); b++) {
        Agreement ag = agreement(ids.get(a), ids.get(b), disagreements);
        if (ag.both() > 0) agreements.add(ag);
      }
    }
    List<LinkStats> links = new ArrayList<>();
    for (KeyMethod k : r.available()) {
      if (k.kind() == Kind.LINK) links.add(linkStats(k));
    }
    NameMatcher matcher = r.matcher();
    NameSpec names = s.names();
    return new Report(
        summary,
        r.unavailable(),
        methods,
        agreements,
        disagreements,
        links,
        matcher == null || names == null
            ? null
            : new NameEvaluation(r, matcher, names, examples).stats());
  }

  private Summary summary() {
    Map<String, Integer> byMethod = new LinkedHashMap<>();
    for (KeyMethod m : r.available()) if (m.joins()) byMethod.put(m.label(), 0);
    if (r.matcher() != null) byMethod.put(Spec.NAMES_LABEL, 0);
    byMethod.put(Spec.NONE_LABEL, 0);
    for (Match m : ms) byMethod.merge(m.label(), 1, Integer::sum);
    int joined = (int) ms.stream().filter(Match::joined).count();
    int suggestions =
        (int) ms.stream().filter(m -> m.confidence() == Confidence.SUGGESTION).count();
    Set<String> usedRight =
        ms.stream()
            .filter(m -> m.right() != null)
            .map(m -> m.rightKey(s))
            .collect(Collectors.toSet());
    int rightUnmatched =
        (int)
            r.right().rows().stream().filter(x -> !usedRight.contains(x.str(s.rightKey()))).count();
    return new Summary(
        ms.size(),
        r.right().rows().size(),
        byMethod,
        joined,
        suggestions,
        byMethod.getOrDefault(Spec.NONE_LABEL, 0),
        rightUnmatched,
        new Guards(s.threshold(), s.margin()));
  }

  /**
   * Ce que chaque méthode propose, seule. Les noms comptent pour ce qu'ils auraient proposé contre
   * toute la droite, garde-fous compris.
   */
  private void collectProposals() {
    r.available().forEach(m -> labels.put(m.id(), m.label()));
    if (r.matcher() != null) labels.put(Spec.NAMES_ID, Spec.NAMES_LABEL);
    for (Match m : ms) {
      Map<String, List<String>> p = new LinkedHashMap<>(m.found());
      if (r.matcher() != null) {
        NameGuess free = m.free();
        p.put(
            Spec.NAMES_ID, free != null && free.accepted() ? List.of(free.candidate()) : List.of());
      }
      proposals.add(p);
    }
  }

  private List<String> proposed(int row, String id) {
    return proposals.get(row).getOrDefault(id, List.of());
  }

  private MethodStats methodStats(String id, String label) {
    KeyMethod km = r.available().stream().filter(k -> k.id().equals(id)).findFirst().orElse(null);
    int proposes = 0;
    int ambiguous = 0;
    int wins = 0;
    int alone = 0;
    for (int i = 0; i < ms.size(); i++) {
      List<String> mine = proposed(i, id);
      if (id.equals(ms.get(i).method())) wins++;
      if (mine.isEmpty()) continue;
      proposes++;
      if (mine.size() > 1) ambiguous++;
      if (!otherSureProposes(i, id)) alone++;
    }
    return new MethodStats(
        id,
        label,
        km == null ? "names" : km.kind().name().toLowerCase(Locale.ROOT),
        proposes,
        ambiguous,
        wins,
        alone);
  }

  private boolean otherSureProposes(int row, String id) {
    return r.available().stream()
        .anyMatch(k -> k.joins() && !k.id().equals(id) && !proposed(row, k.id()).isEmpty());
  }

  private Agreement agreement(String a, String b, List<Example> disagreements) {
    int both = 0;
    int agree = 0;
    int disagree = 0;
    for (int i = 0; i < ms.size(); i++) {
      List<String> pa = proposed(i, a);
      List<String> pb = proposed(i, b);
      if (pa.isEmpty() || pb.isEmpty()) continue;
      both++;
      if (pa.stream().anyMatch(pb::contains)) {
        agree++;
        continue;
      }
      disagree++;
      if (disagreements.size() < examples) {
        disagreements.add(
            new Example(
                ms.get(i).left().str(s.leftKey()),
                "%s → %s ; %s → %s".formatted(a, String.join(", ", pa), b, String.join(", ", pb))));
      }
    }
    return new Agreement(a, b, both, agree, disagree);
  }

  private LinkStats linkStats(KeyMethod k) {
    int withLinks =
        (int)
            r.right().rows().stream()
                .filter(x -> !Keys.linkedPaths(x.str(k.rightColumn())).isEmpty())
                .count();
    int pointed = 0;
    int confirm = 0;
    int confirmGuess = 0;
    int contradict = 0;
    int wouldJoin = 0;
    for (Match m : ms) {
      List<String> f = m.found().getOrDefault(k.id(), List.of());
      if (f.isEmpty()) continue;
      pointed++;
      if (m.right() == null) wouldJoin++;
      else if (!f.contains(m.rightKey(s))) contradict++;
      else if (m.confidence() == Confidence.SUGGESTION) confirmGuess++;
      else confirm++;
    }
    return new LinkStats(k.id(), withLinks, pointed, confirm, confirmGuess, contradict, wouldJoin);
  }
}
