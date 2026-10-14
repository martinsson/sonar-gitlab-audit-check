package ch.ge.edg.sonar.gitlab.audit.cli;

import ch.ge.edg.sonar.gitlab.audit.cli.SonarToGitlab.NameThresholds;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarToGitlab.Pair;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarToGitlab.Rejections;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Confidence;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Spec;
import ch.ge.edg.sonar.gitlab.audit.naming.NameMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * 4. La ressemblance de noms de {@link SonarToGitlab}, un pour un, sur ce qui reste : avec le seuil
 * et la marge du croisement, et en suggestion seulement.
 */
final class NameRound {

  private record Proposal(int sonar, int gitlab, double score, String second) {}

  private final SonarFates fates;
  private final GitlabInventoryIndex inventory;
  private final NameMatcher matcher;
  private final Rejections rejected;
  private final NameThresholds thresholds;

  NameRound(
      SonarFates fates,
      GitlabInventoryIndex inventory,
      NameMatcher matcher,
      Rejections rejected,
      NameThresholds thresholds) {
    this.fates = fates;
    this.inventory = inventory;
    this.matcher = matcher;
    this.rejected = rejected;
    this.thresholds = thresholds;
  }

  void run() {
    Set<Integer> taken = taken();
    List<Proposal> proposals = new ArrayList<>();
    for (int s = 0; s < fates.size(); s++) {
      if (fates.get(s) != null || fates.ambiguous.containsKey(s)) continue;
      propose(s, taken).ifPresent(proposals::add);
    }
    proposals.sort(Comparator.comparingDouble(Proposal::score).reversed());
    award(proposals);
  }

  /** Les dépôts déjà désignés par une méthode plus sûre. */
  private Set<Integer> taken() {
    Set<Integer> taken = new HashSet<>();
    for (Pair p : fates.decided()) {
      if (p.gitlab() == null) continue;
      Integer g = inventory.byPath(p.path());
      if (g != null) taken.add(g);
    }
    return taken;
  }

  private Optional<Proposal> propose(int s, Set<Integer> taken) {
    String key = fates.sonar(s).str("key");
    List<NameMatcher.Scored> ranked =
        matcher.rankSources(
            s,
            g ->
                !taken.contains(g)
                    && !rejected.paths().contains(inventory.path(g))
                    && !rejected.pair(key, inventory.path(g)));
    if (ranked.isEmpty()) {
      fates.refused.put(s, "aucun candidat");
      return Optional.empty();
    }
    NameMatcher.Scored best = ranked.get(0);
    NameMatcher.Scored next = ranked.size() > 1 ? ranked.get(1) : null;
    String second = next == null ? "" : inventory.described(next.index(), next.score());
    String why = refusal(best, next);
    if (why != null) {
      String bestText = inventory.described(best.index(), best.score());
      fates.refused.put(s, why + "\n" + bestText + (second.isEmpty() ? "" : ", " + second));
      return Optional.empty();
    }
    return Optional.of(new Proposal(s, best.index(), best.score(), second));
  }

  /** {@code null} si le meilleur candidat est assez bon et assez loin du suivant. */
  private @Nullable String refusal(NameMatcher.Scored best, NameMatcher.@Nullable Scored next) {
    if (best.score() < thresholds.threshold()) return "sous le seuil";
    return next != null && best.score() - next.score() < thresholds.margin() ? "ambigu" : null;
  }

  /** Un dépôt ne va qu'au projet Sonar qui lui ressemble le plus ; les autres sont refusés. */
  private void award(List<Proposal> proposals) {
    Map<Integer, Integer> claimedBy = new HashMap<>();
    for (Proposal p : proposals) {
      Integer winner = claimedBy.putIfAbsent(p.gitlab(), p.sonar());
      if (winner == null) {
        fates.set(
            p.sonar(),
            new Pair(
                fates.sonar(p.sonar()),
                inventory.row(p.gitlab()),
                Spec.NAMES_LABEL,
                Confidence.SUGGESTION,
                p.score(),
                p.second(),
                ""));
      } else {
        fates.refused.put(
            p.sonar(),
            "déjà proposé à "
                + fates.sonar(winner).str("key")
                + "\n"
                + inventory.described(p.gitlab(), p.score()));
      }
    }
  }
}
