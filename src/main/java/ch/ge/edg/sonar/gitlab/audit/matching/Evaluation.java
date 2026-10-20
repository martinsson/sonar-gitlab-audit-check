package ch.ge.edg.sonar.gitlab.audit.matching;

import ch.ge.edg.sonar.gitlab.audit.matching.Report.Agreement;
import ch.ge.edg.sonar.gitlab.audit.matching.Report.Example;
import ch.ge.edg.sonar.gitlab.audit.matching.Report.LinkStats;
import ch.ge.edg.sonar.gitlab.audit.matching.Report.MethodStats;
import ch.ge.edg.sonar.gitlab.audit.matching.Report.NameStats;
import ch.ge.edg.sonar.gitlab.audit.matching.Report.Summary;
import ch.ge.edg.sonar.gitlab.audit.matching.similarity.NameMatcher;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Chaque méthode évaluée seule, sur toutes les lignes : ce qu'elle propose, ce qu'elle seule
 * apporte, où elle contredit les autres. La ressemblance de noms est jugée à part, dans {@link
 * NameEvaluation}.
 */
public final class Evaluation {

  private final Result result;
  private final Spec spec;
  private final List<Match> matches;
  private final int examples;

  /** Id → libellé des méthodes évaluées, dans l'ordre, les noms en dernier. */
  private final Map<String, String> labels = new LinkedHashMap<>();

  /** Par ligne : ce que chaque méthode, seule, propose. */
  private final List<Map<String, List<String>>> proposals = new ArrayList<>();

  private Evaluation(Result result, int examples) {
    this.result = result;
    this.spec = result.spec();
    this.matches = result.matches();
    this.examples = examples;
  }

  public static Report evaluate(Result result, int examples) {
    return new Evaluation(result, examples).report();
  }

  private Report report() {
    Summary summary = Summary.of(result);
    collectProposals();
    List<MethodStats> methods = new ArrayList<>();
    labels.forEach((id, label) -> methods.add(methodStats(id, label)));
    List<Example> disagreements = new ArrayList<>();
    List<Agreement> agreements = agreements(disagreements);
    return new Report(
        summary,
        result.unavailable(),
        methods,
        agreements,
        disagreements,
        linkStats(),
        nameStats());
  }

  /** Chaque paire de méthodes, celles qui se recoupent seulement ; les désaccords s'accumulent. */
  private List<Agreement> agreements(List<Example> disagreements) {
    List<Agreement> agreements = new ArrayList<>();
    List<String> ids = new ArrayList<>(labels.keySet());
    for (int a = 0; a < ids.size(); a++) {
      for (int b = a + 1; b < ids.size(); b++) {
        Agreement ag = agreement(ids.get(a), ids.get(b), disagreements);
        if (ag.both() > 0) agreements.add(ag);
      }
    }
    return agreements;
  }

  private List<LinkStats> linkStats() {
    return result.available().stream()
        .filter(k -> k.kind() == Kind.LINK)
        .map(this::linkStats)
        .toList();
  }

  private @Nullable NameStats nameStats() {
    NameMatcher matcher = result.matcher();
    NameSpec names = spec.names();
    if (matcher == null || names == null) return null;
    return new NameEvaluation(result, matcher, names, examples).stats();
  }

  /**
   * Ce que chaque méthode propose, seule. Les noms comptent pour ce qu'ils auraient proposé contre
   * toute la droite, garde-fous compris.
   */
  private void collectProposals() {
    result.available().forEach(m -> labels.put(m.id(), m.label()));
    if (result.matcher() != null) labels.put(Spec.NAMES_ID, Spec.NAMES_LABEL);
    for (Match m : matches) {
      Map<String, List<String>> p = new LinkedHashMap<>(m.found());
      if (result.matcher() != null) {
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
    KeyMethod km =
        result.available().stream().filter(k -> k.id().equals(id)).findFirst().orElse(null);
    int proposes = 0;
    int ambiguous = 0;
    int wins = 0;
    int alone = 0;
    for (int i = 0; i < matches.size(); i++) {
      List<String> mine = proposed(i, id);
      if (id.equals(matches.get(i).method())) wins++;
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
    return result.available().stream()
        .anyMatch(k -> k.joins() && !k.id().equals(id) && !proposed(row, k.id()).isEmpty());
  }

  private Agreement agreement(String a, String b, List<Example> disagreements) {
    int both = 0;
    int agree = 0;
    int disagree = 0;
    for (int i = 0; i < matches.size(); i++) {
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
                matches.get(i).left().str(spec.leftKey()),
                Text.format(
                    "%s → %s ; %s → %s", a, String.join(", ", pa), b, String.join(", ", pb))));
      }
    }
    return new Agreement(a, b, both, agree, disagree);
  }

  private LinkStats linkStats(KeyMethod k) {
    int withLinks =
        (int)
            result.right().rows().stream()
                .filter(x -> !Keys.linkedPaths(x.str(k.rightColumn())).isEmpty())
                .count();
    int pointed = 0;
    int confirm = 0;
    int confirmGuess = 0;
    int contradict = 0;
    int wouldJoin = 0;
    for (Match m : matches) {
      List<String> f = m.found().getOrDefault(k.id(), List.of());
      if (f.isEmpty()) continue;
      pointed++;
      if (m.right() == null) wouldJoin++;
      else if (!f.contains(m.rightKey(spec))) contradict++;
      else if (m.confidence() == Confidence.SUGGESTION) confirmGuess++;
      else confirm++;
    }
    return new LinkStats(k.id(), withLinks, pointed, confirm, confirmGuess, contradict, wouldJoin);
  }
}
