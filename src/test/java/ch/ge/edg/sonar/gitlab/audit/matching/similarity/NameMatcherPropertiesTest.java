package ch.ge.edg.sonar.gitlab.audit.matching.similarity;

import static ch.ge.edg.sonar.gitlab.audit.matching.similarity.NameMatcherFixtures.matcher;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.NumericChars;
import net.jqwik.api.constraints.StringLength;

/** Invariants of tokenisation, Jaro-Winkler and ranking over generated names. */
class NameMatcherPropertiesTest {

  @Provide
  Arbitrary<String> words() {
    return Arbitraries.strings().withCharRange('a', 'f').ofMinLength(1).ofMaxLength(8);
  }

  @Provide
  Arbitrary<List<String>> wordLists() {
    return words().list().ofMaxSize(5);
  }

  @Provide
  Arbitrary<String> names() {
    Arbitrary<String> word =
        Arbitraries.strings().withCharRange('a', 'e').ofMinLength(2).ofMaxLength(7);
    Arbitrary<String> num = Arbitraries.integers().between(0, 3).map(i -> i == 0 ? "" : "" + i);
    return word.list()
        .ofMinSize(1)
        .ofMaxSize(3)
        .map(l -> String.join("-", l))
        .flatMap(n -> num.map(x -> n + x));
  }

  @Provide
  Arbitrary<List<String>> corpus() {
    return names().list().ofMinSize(1).ofMaxSize(12);
  }

  @Property
  void tokensAreLowercaseAsciiWordsOfTwoLettersOrMore(@ForAll String s) {
    assertThat(NameMatcher.tokens(s)).allSatisfy(t -> assertThat(t).matches("[a-z]{2,}"));
  }

  @Property
  void tokensOfLowercaseWordsAreTheWordsOfTwoLettersOrMore(@ForAll("wordLists") List<String> ws) {
    List<String> expected = ws.stream().filter(w -> w.length() >= 2).toList();
    assertThat(NameMatcher.tokens(String.join("_", ws))).isEqualTo(expected);
    assertThat(NameMatcher.tokens(String.join(" . ", ws))).isEqualTo(expected);
  }

  @Property
  void tokensIgnoreCaseForCapitalisedWords(@ForAll("wordLists") List<String> ws) {
    String capitalised =
        String.join(
            "-",
            ws.stream()
                .map(w -> w.substring(0, 1).toUpperCase(Locale.ROOT) + w.substring(1))
                .toList());
    assertThat(NameMatcher.tokens(capitalised)).isEqualTo(NameMatcher.tokens(String.join("-", ws)));
  }

  @Property
  void numbersAreDigitRunsWithoutLeadingZeros(@ForAll String s) {
    assertThat(NameMatcher.numbers(s)).allSatisfy(n -> assertThat(n).matches("0|[1-9]\\d*"));
  }

  @Property
  void leadingZerosDoNotChangeANumber(
      @ForAll @NumericChars @StringLength(min = 1, max = 6) String digits) {
    assertThat(NameMatcher.numbers("x00" + digits + "y"))
        .isEqualTo(NameMatcher.numbers("x" + digits));
  }

  @Property
  void jaroWinklerIsBetweenZeroAndOneAndOneOnIdentity(
      @ForAll("words") String a, @ForAll("words") String b) {
    assertThat(JaroWinkler.similarity(a, b)).isBetween(0.0, 1.0 + 1e-12);
    assertThat(JaroWinkler.similarity(a, a)).isEqualTo(1.0);
  }

  @Property
  void jaroWinklerIsSymmetric(@ForAll("words") String a, @ForAll("words") String b) {
    assertThat(JaroWinkler.similarity(a, b)).isCloseTo(JaroWinkler.similarity(b, a), within(1e-12));
  }

  @Property
  void tokenSimilarityIsZeroOrAboveTheThreshold(
      @ForAll @AlphaChars @StringLength(max = 8) String a,
      @ForAll @AlphaChars @StringLength(max = 8) String b) {
    double s = SoftTfIdf.tokenSimilarity(a, b);
    assertThat(s == 0 || s >= SoftTfIdf.TOKEN_SIMILARITY).isTrue();
    assertThat(s).isLessThanOrEqualTo(1.0);
  }

  @Property
  void scoresAreBetweenZeroAndOne(@ForAll("corpus") List<String> sources) {
    NameMatcher m = matcher(sources, sources);
    for (int i = 0; i < sources.size(); i++) {
      for (int j = 0; j < sources.size(); j++) {
        assertThat(m.score(i, j)).isBetween(0.0, 1.0);
      }
    }
  }

  @Property
  void aNameScoresOneAgainstItself(@ForAll("corpus") List<String> names) {
    NameMatcher m = matcher(names, names);
    for (int i = 0; i < names.size(); i++) {
      assertThat(m.score(i, i)).isCloseTo(1.0, within(1e-9));
    }
  }

  @Property
  void ranksAreSortedPositiveAllowedAndFreeOfNumberConflicts(
      @ForAll("corpus") List<String> sources, @ForAll("corpus") List<String> targets) {
    NameMatcher m = matcher(sources, targets);
    for (int i = 0; i < sources.size(); i++) {
      List<NameMatcher.Scored> ranked = m.rank(i, j -> j % 2 == 0);
      checkRanked(ranked, sources.get(i), targets);
      for (NameMatcher.Scored s : ranked) {
        assertThat(s.index() % 2).isZero();
        assertThat(s.score()).isEqualTo(m.score(i, s.index()));
      }
    }
  }

  @Property
  void rankSourcesAreSortedPositiveAndFreeOfNumberConflicts(
      @ForAll("corpus") List<String> sources, @ForAll("corpus") List<String> targets) {
    NameMatcher m = matcher(sources, targets);
    for (int j = 0; j < targets.size(); j++) {
      List<NameMatcher.Scored> ranked = m.rankSources(j, i -> true);
      checkRanked(ranked, targets.get(j), sources);
      for (NameMatcher.Scored s : ranked) {
        assertThat(s.score()).isEqualTo(m.score(s.index(), j));
      }
    }
  }

  @Property
  void aNumberVetoMeansAConflictingCandidateExists(
      @ForAll("corpus") List<String> sources, @ForAll("corpus") List<String> targets) {
    NameMatcher m = matcher(sources, targets);
    for (int i = 0; i < sources.size(); i++) {
      if (!m.hasNumberVeto(i)) continue;
      String src = sources.get(i);
      boolean conflict =
          targets.stream()
              .anyMatch(
                  t -> SoftTfIdf.numbersConflict(NameMatcher.numbers(src), NameMatcher.numbers(t)));
      assertThat(conflict).isTrue();
    }
  }

  private static void checkRanked(
      List<NameMatcher.Scored> ranked, String from, List<String> others) {
    double[] scores = ranked.stream().mapToDouble(NameMatcher.Scored::score).toArray();
    double[] sorted = scores.clone();
    Arrays.sort(sorted);
    for (int k = 0; k < scores.length; k++) {
      assertThat(scores[k]).isPositive().isEqualTo(sorted[scores.length - 1 - k]);
    }
    for (NameMatcher.Scored s : ranked) {
      String other = others.get(s.index());
      assertThat(SoftTfIdf.numbersConflict(NameMatcher.numbers(from), NameMatcher.numbers(other)))
          .isFalse();
    }
  }
}
