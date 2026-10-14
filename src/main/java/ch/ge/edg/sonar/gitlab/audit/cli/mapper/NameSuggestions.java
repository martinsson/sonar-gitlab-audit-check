package ch.ge.edg.sonar.gitlab.audit.cli.mapper;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.naming.NameMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntPredicate;
import org.jspecify.annotations.Nullable;

/** La ressemblance de noms, appliquée après les clés : en suggestion seulement. */
final class NameSuggestions {

  private final Spec spec;
  private final NameMatcher matcher;
  private final List<Csv.Row> rights;
  private final Map<String, Integer> rightIndex;

  NameSuggestions(
      Spec spec, NameMatcher matcher, List<Csv.Row> rights, Map<String, Integer> rightIndex) {
    this.spec = spec;
    this.matcher = matcher;
    this.rights = rights;
    this.rightIndex = rightIndex;
  }

  /**
   * La ressemblance de noms, sur les lignes restées sans jointure et les lignes de droite que
   * personne n'a prises. Trois garde-fous : seuil, marge avec le suivant, et un pour un — une ligne
   * de droite ne se propose qu'une fois, au meilleur score ; les autres apprennent qui l'a prise.
   *
   * <p>Chaque ligne reçoit d'abord sa ressemblance contre toute la droite, pour l'évaluation.
   */
  void suggest(List<Match> matches, List<Integer> unresolved, Set<Integer> taken) {
    for (int i = 0; i < matches.size(); i++) {
      Match m = matches.get(i);
      matches.set(i, m.with(m.choice(), null, guess(i, j -> true)));
    }
    record Proposal(int row, NameGuess guess) {}
    List<Proposal> proposals = new ArrayList<>();
    for (int i : unresolved) {
      NameGuess g = guess(i, j -> !taken.contains(j));
      if (g.accepted()) proposals.add(new Proposal(i, g));
      else setGuess(matches, i, null, g);
    }
    proposals.sort(Comparator.comparingDouble((Proposal p) -> p.guess().score()).reversed());
    Map<String, String> claimedBy = new HashMap<>();
    for (Proposal p : proposals) {
      NameGuess g = p.guess();
      String winner =
          claimedBy.putIfAbsent(g.candidate(), matches.get(p.row()).left().str(spec.leftKey()));
      if (winner == null) {
        // Un candidat accepté n'est jamais vide, donc toujours dans l'index.
        setGuess(
            matches, p.row(), rights.get(Objects.requireNonNull(rightIndex.get(g.candidate()))), g);
      } else {
        setGuess(matches, p.row(), null, g.rejected("déjà proposé à " + winner));
      }
    }
  }

  private NameGuess guess(int i, IntPredicate allowed) {
    List<NameMatcher.Scored> ranked = matcher.rank(i, allowed);
    if (ranked.isEmpty()) {
      return new NameGuess(
          "", 0, "", 0, matcher.hasNumberVeto(i) ? "numéros différents" : "aucun candidat");
    }
    NameMatcher.Scored best = ranked.get(0);
    NameMatcher.Scored second = ranked.size() > 1 ? ranked.get(1) : null;
    String rejection =
        best.score() < spec.threshold()
            ? "sous le seuil"
            : second != null && best.score() - second.score() < spec.margin() ? "ambigu" : null;
    return new NameGuess(
        rights.get(best.index()).str(spec.rightKey()),
        best.score(),
        second == null ? "" : rights.get(second.index()).str(spec.rightKey()),
        second == null ? 0 : second.score(),
        rejection);
  }

  private static void setGuess(List<Match> matches, int i, Csv.@Nullable Row right, NameGuess g) {
    Match m = matches.get(i);
    Choice c =
        right == null
            ? Choice.none()
            : new Choice(right, Spec.NAMES_ID, Spec.NAMES_LABEL, Confidence.SUGGESTION);
    matches.set(i, m.with(c, g, m.free()));
  }
}
