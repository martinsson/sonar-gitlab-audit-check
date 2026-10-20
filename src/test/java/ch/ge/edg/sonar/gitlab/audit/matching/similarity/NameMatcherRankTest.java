package ch.ge.edg.sonar.gitlab.audit.matching.similarity;

import static ch.ge.edg.sonar.gitlab.audit.matching.similarity.NameMatcherFixtures.matcher;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Ranking candidates both ways, the blocking that proposes them and the number veto. */
class NameMatcherRankTest {

  private static final List<String> TARGETS =
      List.of(
          "billing-api", // 0
          "payments", // 1
          "app1", // 2
          "app2", // 3
          "paiement-core", // 4
          "alpine", // 5
          "billing-front"); // 6

  private static List<Integer> indexes(List<NameMatcher.Scored> ranked) {
    return ranked.stream().map(NameMatcher.Scored::index).toList();
  }

  @Test
  void movedWordsScoreAsIdentical() {
    NameMatcher m = matcher(List.of("api-billing"), TARGETS);
    List<NameMatcher.Scored> ranked = m.rank(0);
    assertThat(indexes(ranked)).containsExactly(0, 6);
    assertThat(ranked.get(0).score()).isCloseTo(1.0, within(1e-9));
    assertThat(ranked.get(1).score()).isBetween(0.0, 0.99);
    assertThat(ranked.get(1).score()).isEqualTo(m.score(0, 6));
  }

  @Test
  void aTypoOnALongWordStillFindsItsCandidate() {
    NameMatcher m = matcher(List.of("paiment-core"), TARGETS);
    List<NameMatcher.Scored> ranked = m.rank(0);
    assertThat(indexes(ranked)).containsExactly(4);
    assertThat(ranked.get(0).score()).isBetween(0.9, 1.0);
  }

  @Test
  void aSharedPrefixWithoutResemblanceScoresZeroAndIsDropped() {
    NameMatcher m = matcher(List.of("alpha"), TARGETS);
    assertThat(m.rank(0)).isEmpty();
    assertThat(m.hasNumberVeto(0)).isFalse();
    assertThat(m.score(0, 5)).isZero();
  }

  @Test
  void differentNumbersVetoACandidateButScoreIgnoresTheVeto() {
    NameMatcher m = matcher(List.of("app1", "app3", "app"), TARGETS);
    assertThat(indexes(m.rank(0))).containsExactly(2);
    assertThat(m.hasNumberVeto(0)).isTrue();
    assertThat(m.rank(1)).isEmpty();
    assertThat(m.hasNumberVeto(1)).isTrue();
    assertThat(indexes(m.rank(2))).containsExactlyInAnyOrder(2, 3);
    assertThat(m.hasNumberVeto(2)).isFalse();
    assertThat(m.score(1, 2)).isCloseTo(1.0, within(1e-9));
  }

  @Test
  void numbersWithoutAConflictingCandidateAreNoVeto() {
    NameMatcher m = matcher(List.of("payments2", "zzzz9"), TARGETS);
    assertThat(indexes(m.rank(0))).containsExactly(1);
    assertThat(m.hasNumberVeto(0)).isFalse();
    assertThat(m.hasNumberVeto(1)).isFalse();
  }

  @Test
  void theAllowedFilterHidesTargets() {
    NameMatcher m = matcher(List.of("billing"), TARGETS);
    assertThat(indexes(m.rank(0))).containsExactlyInAnyOrder(0, 6);
    assertThat(indexes(m.rank(0, i -> i != 0))).containsExactly(6);
    assertThat(m.rank(0, i -> false)).isEmpty();
  }

  @Test
  void ranksAreSortedBestFirst() {
    NameMatcher m = matcher(List.of("billing-front-web"), TARGETS);
    List<NameMatcher.Scored> ranked = m.rank(0);
    assertThat(indexes(ranked)).containsExactly(6, 0);
    assertThat(ranked.get(0).score()).isGreaterThan(ranked.get(1).score());
  }

  @Test
  void aWordCarriedByTooManyTargetsProposesNoCandidate() {
    List<String> targets = List.of("common-aa", "common-bb", "common-cc", "common-dd", "solo");
    NameMatcher m = matcher(List.of("common", "solo"), targets);
    assertThat(m.rank(0)).isEmpty();
    assertThat(m.score(0, 0)).isGreaterThan(0.0);
    assertThat(indexes(m.rank(1))).containsExactly(4);
  }

  @Test
  void aWordCarriedByUpToThreeTargetsStillProposes() {
    List<String> targets = List.of("common-aa", "common-bb", "common-cc", "solo");
    NameMatcher m = matcher(List.of("common"), targets);
    assertThat(indexes(m.rank(0))).containsExactlyInAnyOrder(0, 1, 2);
  }

  @Test
  void theBlockingLimitGrowsWithAFifthOfTheCorpus() {
    // 20 targets: limit = ceil(20 * 0.2) = 4, so a word in 4 targets proposes, in 5 does not.
    List<String> targets = new ArrayList<>();
    for (int i = 0; i < 4; i++) targets.add("four-x" + (char) ('a' + i) + "y");
    for (int i = 0; i < 5; i++) targets.add("five-z" + (char) ('a' + i) + "w");
    while (targets.size() < 20) targets.add("filler" + (char) ('a' + targets.size()) + "q");
    NameMatcher m = matcher(List.of("four", "five"), targets);
    assertThat(m.rank(0)).hasSize(4);
    assertThat(m.rank(1)).isEmpty();
  }

  @Test
  void rareWordsWeighMoreThanCommonOnes() {
    List<String> targets = List.of("shared-alpha", "shared-beta", "gamma");
    NameMatcher m = matcher(List.of("shared-alpha-extra", "shared-beta"), targets);
    assertThat(indexes(m.rank(0))).containsExactly(0, 1);
    assertThat(m.score(0, 0)).isGreaterThan(m.score(0, 1) * 2);
  }

  @Test
  void rankSourcesLooksTheOtherWayWithTheSameScore() {
    List<String> sources = List.of("team-a/billing-api", "team-b/payments", "team-c/app2");
    NameMatcher m = matcher(sources, TARGETS);
    List<NameMatcher.Scored> back = m.rankSources(0, i -> true);
    assertThat(indexes(back)).containsExactly(0);
    assertThat(back.get(0).score()).isEqualTo(m.score(0, 0));
    assertThat(indexes(m.rankSources(1, i -> true))).containsExactly(1);
    assertThat(m.rankSources(2, i -> true)).isEmpty();
    assertThat(indexes(m.rankSources(3, i -> true))).containsExactly(2);
    assertThat(m.rankSources(3, i -> i != 2)).isEmpty();
  }

  @Test
  void rankSourcesBlocksOnTheSourceSideFrequencies() {
    List<String> sources = List.of("core-aa", "core-bb", "core-cc", "core-dd", "zeta");
    NameMatcher m = matcher(sources, List.of("core", "zeta"));
    assertThat(m.rankSources(0, i -> true)).isEmpty();
    assertThat(indexes(m.rankSources(1, i -> true))).containsExactly(4);
    // Asked twice, the lazily built source blocks are reused.
    assertThat(indexes(m.rankSources(1, i -> true))).containsExactly(4);
  }

  @Test
  void aFourLetterTargetIsFoundByItsFirstThreeLetters() {
    NameMatcher m = matcher(List.of("cores"), List.of("core", "other"));
    assertThat(indexes(m.rank(0))).containsExactly(0);
    assertThat(indexes(m.rankSources(0, i -> true))).containsExactly(0);
  }

  @Test
  void weightsAreTfIdfOverBothSidesNormalised() {
    // n = 4 documents; alpha in 2 of them (the source and t0), beta in 3.
    NameMatcher m = matcher(List.of("alpha-beta"), List.of("alpha", "beta", "beta-gamma"));
    double a = Math.log(5.0 / 3.0) + 1;
    double b = Math.log(5.0 / 4.0) + 1;
    assertThat(m.score(0, 0)).isCloseTo(a / Math.hypot(a, b), within(1e-12));
    assertThat(m.score(0, 1)).isCloseTo(b / Math.hypot(a, b), within(1e-12));
  }

  @Test
  void aNameWhoseWordsAllWeighZeroScoresZero() {
    Map<String, Double> zero = new HashMap<>();
    NameMatcher.addTokens(zero, "alpha", 0.0);
    NameMatcher m =
        new NameMatcher(List.of(zero), List.of(Set.of()), List.of(zero), List.of(Set.of()));
    assertThat(m.score(0, 0)).isZero();
    assertThat(m.rank(0)).isEmpty();
  }

  @Test
  void aSourceWithoutWordsHasNoCandidate() {
    NameMatcher m = matcher(List.of("42"), TARGETS);
    assertThat(m.rank(0)).isEmpty();
    assertThat(m.hasNumberVeto(0)).isFalse();
    assertThat(m.score(0, 2)).isZero();
  }
}
