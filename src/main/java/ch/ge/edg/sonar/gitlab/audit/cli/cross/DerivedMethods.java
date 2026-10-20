package ch.ge.edg.sonar.gitlab.audit.cli.cross;

import ch.ge.edg.sonar.gitlab.audit.cli.cross.GitlabInventoryIndex.Step;
import ch.ge.edg.sonar.gitlab.audit.cli.cross.SonarToGitlab.Check;
import ch.ge.edg.sonar.gitlab.audit.cli.cross.SonarToGitlab.Pair;
import ch.ge.edg.sonar.gitlab.audit.cli.cross.SonarToGitlab.Rejections;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.exchange.SonarInventory;
import ch.ge.edg.sonar.gitlab.audit.matching.Confidence;
import ch.ge.edg.sonar.gitlab.audit.matching.similarity.NameMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 3. Les méthodes dérivées de {@link SonarToGitlab} : appliquées à ce qui reste, contrôlées sur les
 * paires exactes. Une méthode n'apparie que si elle ne désigne qu'un dépôt ; plusieurs dépôts du
 * même nom ne donnent qu'une suggestion, départagée par la ressemblance des noms ou, à défaut, par
 * l'activité.
 */
final class DerivedMethods {

  /** Le dépôt retenu parmi des homonymes, et ce qui l'a départagé. */
  private record Pick(int gitlab, String how) {}

  private final SonarFates fates;
  private final GitlabInventoryIndex inventory;
  private final NameMatcher matcher;
  private final Rejections rejected;
  private final double margin;

  DerivedMethods(
      SonarFates fates,
      GitlabInventoryIndex inventory,
      NameMatcher matcher,
      Rejections rejected,
      double margin) {
    this.fates = fates;
    this.inventory = inventory;
    this.matcher = matcher;
    this.rejected = rejected;
    this.margin = margin;
  }

  void run() {
    for (int s = 0; s < fates.size(); s++) {
      Pair known = fates.get(s);
      boolean exact = known != null && known.confidence() == Confidence.EXACT;
      if (known != null && !exact) continue;
      Csv.Row r = fates.sonar(s);
      for (Step step : inventory.steps(r)) {
        Set<Integer> cands = new LinkedHashSet<>(step.candidates());
        cands.removeIf(g -> rejected.pair(r.str(SonarInventory.KEY), inventory.path(g)));
        if (cands.isEmpty()) continue;
        if (known != null) check(s, step, cands, known);
        else apply(s, step, cands);
        break;
      }
    }
  }

  /** Sur une paire exacte : la méthode aurait-elle trouvé le même dépôt ? */
  private void check(int s, Step step, Set<Integer> cands, Pair exact) {
    if (cands.size() == 1) {
      int g = cands.iterator().next();
      fates.checks.add(new Check(step.label(), inventory.path(g).equals(exact.path())));
      return;
    }
    List<Integer> ranked = byNames(cands, s);
    fates.checks.add(
        new Check(
            step.label() + " (homonymes)", inventory.path(ranked.get(0)).equals(exact.path())));
  }

  private void apply(int s, Step step, Set<Integer> cands) {
    Csv.Row r = fates.sonar(s);
    if (cands.size() == 1) {
      int g = cands.iterator().next();
      fates.set(
          s,
          new Pair(
              r, inventory.row(g), step.label(), Confidence.DERIVED, matcher.score(g, s), "", ""));
      return;
    }
    List<Integer> ranked = byNames(cands, s);
    String names =
        String.join(
            ", ",
            ranked.stream()
                .limit(5)
                .map(g -> inventory.described(g, matcher.score(g, s)))
                .toList());
    Optional<Pick> pick =
        tieBreak(ranked, cands, s)
            .filter(p -> !rejected.paths().contains(inventory.path(p.gitlab())));
    if (pick.isEmpty()) {
      fates.ambiguous.put(s, "homonymes non départagés (" + step.label() + ")\n" + names);
      return;
    }
    int g = pick.get().gitlab();
    fates.set(
        s,
        new Pair(
            r,
            inventory.row(g),
            "%s (%d homonymes, départagés par %s)"
                .formatted(step.label(), cands.size(), pick.get().how()),
            Confidence.SUGGESTION,
            matcher.score(g, s),
            names,
            ""));
  }

  private List<Integer> byNames(Set<Integer> cands, int s) {
    List<Integer> ranked = new ArrayList<>(cands);
    ranked.sort(Comparator.comparingDouble((Integer g) -> matcher.score(g, s)).reversed());
    return ranked;
  }

  private Optional<Pick> tieBreak(List<Integer> ranked, Set<Integer> cands, int s) {
    if (matcher.score(ranked.get(0), s) - matcher.score(ranked.get(1), s) >= margin) {
      return Optional.of(new Pick(ranked.get(0), "ressemblance des noms"));
    }
    // Le même nom à plusieurs endroits : la copie sur laquelle on
    // travaille vraiment est la plus probable. Deux copies mortes
    // ne se départagent pas.
    List<Integer> byActivity = new ArrayList<>(cands);
    byActivity.sort(inventory.activityOrder().reversed());
    Csv.Row a = inventory.row(byActivity.get(0)), b = inventory.row(byActivity.get(1));
    boolean tie =
        GitlabInventoryIndex.original(a) == GitlabInventoryIndex.original(b)
            && GitlabInventoryIndex.commits(a) == GitlabInventoryIndex.commits(b)
            && GitlabInventoryIndex.commits(a) == 0;
    return tie ? Optional.empty() : Optional.of(new Pick(byActivity.get(0), "activité GitLab"));
  }
}
