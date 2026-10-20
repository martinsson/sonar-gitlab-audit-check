package ch.ge.edg.sonar.gitlab.audit.matching.similarity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds a {@link NameMatcher} from plain names, the way a caller would. */
final class NameMatcherFixtures {

  private NameMatcherFixtures() {}

  static NameMatcher matcher(List<String> sources, List<String> targets) {
    return new NameMatcher(tf(sources), nums(sources), tf(targets), nums(targets));
  }

  static List<Map<String, Double>> tf(List<String> names) {
    List<Map<String, Double>> out = new ArrayList<>();
    for (String n : names) {
      Map<String, Double> tf = new HashMap<>();
      NameMatcher.addTokens(tf, n, 1.0);
      out.add(tf);
    }
    return out;
  }

  static List<Set<String>> nums(List<String> names) {
    return names.stream().map(NameMatcher::numbers).toList();
  }
}
