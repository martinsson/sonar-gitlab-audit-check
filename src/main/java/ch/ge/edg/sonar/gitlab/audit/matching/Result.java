package ch.ge.edg.sonar.gitlab.audit.matching;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.matching.similarity.NameMatcher;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Un appariement : les méthodes qu'on a pu appliquer (et celles qu'on n'a pas pu, avec la raison),
 * une {@link Match} par ligne de gauche, la ressemblance de noms si elle a tourné, et la clé de
 * droite → sa première ligne.
 */
public record Result(
    Spec spec,
    Csv.Table left,
    Csv.Table right,
    List<KeyMethod> available,
    List<String> unavailable,
    List<Match> matches,
    @Nullable NameMatcher matcher,
    Map<String, Integer> rightIndex) {

  Result withMatches(List<Match> replaced) {
    return new Result(spec, left, right, available, unavailable, replaced, matcher, rightIndex);
  }
}
