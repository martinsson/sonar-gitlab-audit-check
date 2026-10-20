package ch.ge.edg.sonar.gitlab.audit.matching;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.matching.Report.TokenCount;
import ch.ge.edg.sonar.gitlab.audit.matching.Report.Vocabulary;
import ch.ge.edg.sonar.gitlab.audit.matching.similarity.NameMatcher;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Les mots des lignes restées seules, de chaque côté : ce qui dit quelle règle de nom manque. */
final class LeftoverWords {

  private LeftoverWords() {}

  static Vocabulary of(Result r, NameSpec names) {
    return new Vocabulary(leftTokens(r, names), rightPrefixes(r));
  }

  /**
   * Les mots des lignes restées seules, et s'ils existent de l'autre côté. Un mot fréquent absent à
   * droite est un nom qui ne s'écrit pas pareil des deux côtés : un synonyme, une abréviation, une
   * règle à ajouter.
   */
  private static List<TokenCount> leftTokens(Result r, NameSpec names) {
    Set<String> rightTokens = new HashSet<>();
    for (Csv.Row x : r.right().rows()) {
      for (String col : names.rightColumns()) rightTokens.addAll(NameMatcher.tokens(x.str(col)));
    }
    Map<String, Integer> leftCounts = new HashMap<>();
    for (Match m : r.matches()) {
      if (m.right() != null) continue;
      String v = m.left().str(names.leftColumn());
      if (names.pathLike()) v = v.substring(v.lastIndexOf('/') + 1);
      for (String t : new HashSet<>(NameMatcher.tokens(v))) leftCounts.merge(t, 1, Integer::sum);
    }
    return top(leftCounts, 20).stream()
        .map(e -> new TokenCount(e.getKey(), e.getValue(), rightTokens.contains(e.getKey())))
        .toList();
  }

  /**
   * Côté droit, les préfixes des clés restées seules : une convention de nommage ({@code
   * ch.ge.afc.}, {@code dsi_}) s'y lit d'un coup d'œil.
   */
  private static List<TokenCount> rightPrefixes(Result r) {
    Spec s = r.spec();
    Set<String> used =
        r.matches().stream()
            .filter(m -> m.right() != null)
            .map(m -> m.rightKey(s))
            .collect(Collectors.toSet());
    Map<String, Integer> prefixes = new HashMap<>();
    for (Csv.Row x : r.right().rows()) {
      String k = x.str(s.rightKey());
      if (k.isEmpty() || used.contains(k)) continue;
      prefixes.merge(Keys.prefix(k), 1, Integer::sum);
    }
    return top(prefixes, 15).stream()
        .map(e -> new TokenCount(e.getKey(), e.getValue(), true))
        .toList();
  }

  private static List<Map.Entry<String, Integer>> top(Map<String, Integer> counts, int n) {
    return counts.entrySet().stream()
        .sorted(
            Map.Entry.<String, Integer>comparingByValue()
                .reversed()
                .thenComparing(Map.Entry.comparingByKey()))
        .limit(n)
        .toList();
  }
}
