package ch.ge.edg.sonar.gitlab.audit.naming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Jaro-Winkler, token similarity, the number veto and the bounded document similarity. */
class NameMatcherSimilarityTest {

  private static final double EPS = 1e-9;

  @Test
  void jaro_winkler_reference_values() {
    assertThat(NameMatcher.jaroWinkler("martha", "marhta")).isCloseTo(0.961111111, within(1e-6));
    assertThat(NameMatcher.jaroWinkler("dwayne", "duane")).isCloseTo(0.84, within(1e-6));
    assertThat(NameMatcher.jaroWinkler("dixon", "dicksonx")).isCloseTo(0.813333333, within(1e-6));
    assertThat(NameMatcher.jaroWinkler("alpha", "alpine")).isCloseTo(0.79, within(1e-6));
  }

  @Test
  void jaro_winkler_degenerate_cases() {
    assertThat(NameMatcher.jaroWinkler("same", "same")).isEqualTo(1.0);
    assertThat(NameMatcher.jaroWinkler("", "")).isEqualTo(1.0);
    assertThat(NameMatcher.jaroWinkler("", "a")).isZero();
    assertThat(NameMatcher.jaroWinkler("abc", "xyz")).isZero();
  }

  @Test
  void jaro_winkler_prefix_bonus_stops_at_four_letters() {
    double four = NameMatcher.jaroWinkler("abcdefgh", "abcdxxxx");
    double five = NameMatcher.jaroWinkler("abcdefgh", "abcdexxx");
    double jaroFour = (4 / 8.0 + 4 / 8.0 + 1) / 3;
    double jaroFive = (5 / 8.0 + 5 / 8.0 + 1) / 3;
    assertThat(four).isCloseTo(jaroFour + 0.4 * (1 - jaroFour), within(EPS));
    assertThat(five).isCloseTo(jaroFive + 0.4 * (1 - jaroFive), within(EPS));
  }

  @Test
  void jaro_winkler_counts_half_transpositions() {
    // abcd / abdc : four matches, two out of place -> one transposition, prefix 2.
    double jaro = (1 + 1 + (4 - 1) / 4.0) / 3;
    assertThat(NameMatcher.jaroWinkler("abcd", "abdc"))
        .isCloseTo(jaro + 0.2 * (1 - jaro), within(EPS));
    // ab / ba : window 0, nothing matches.
    assertThat(NameMatcher.jaroWinkler("ab", "ba")).isZero();
  }

  @Test
  void jaro_winkler_matching_window_is_half_the_longer_length_minus_one() {
    // window = 6/2 - 1 = 2 : the 'a' three places away does not match.
    assertThat(NameMatcher.jaroWinkler("axxxxx", "yyyayy")).isZero();
    double inWindow = NameMatcher.jaroWinkler("axxxxx", "yyayyy");
    assertThat(inWindow).isCloseTo((1 / 6.0 + 1 / 6.0 + 1) / 3, within(EPS));
  }

  @Test
  void token_similarity_is_identity_or_a_close_long_word() {
    assertThat(NameMatcher.tokenSimilarity("api", "api")).isEqualTo(1.0);
    assertThat(NameMatcher.tokenSimilarity("apis", "apix")).isZero();
    assertThat(NameMatcher.tokenSimilarity("abc", "abcd")).isZero();
    assertThat(NameMatcher.tokenSimilarity("abcd", "abc")).isZero();
    assertThat(NameMatcher.tokenSimilarity("paiement", "paiment"))
        .isCloseTo(NameMatcher.jaroWinkler("paiement", "paiment"), within(EPS))
        .isGreaterThanOrEqualTo(NameMatcher.TOKEN_SIMILARITY);
    assertThat(NameMatcher.tokenSimilarity("alpha", "alpine")).isZero();
  }

  @Test
  void token_similarity_accepts_exactly_at_the_threshold_length() {
    // Four letters, one differing at the end: 0.8833 < 0.9, so refused.
    assertThat(NameMatcher.tokenSimilarity("abcd", "abce")).isZero();
    // Five letters, last differing: jaro 0.8667, +0.4*(0.1333) = 0.92.
    assertThat(NameMatcher.tokenSimilarity("abcde", "abcdf")).isCloseTo(0.92, within(1e-6));
  }

  @Test
  void four_letter_words_are_long_enough_for_resemblance() {
    assertThat(NameMatcher.tokenSimilarity("abcd", "abdc")).isCloseTo(0.933333333, within(1e-6));
    assertThat(NameMatcher.tokenSimilarity("core", "cores")).isCloseTo(0.96, within(1e-6));
    assertThat(NameMatcher.tokenSimilarity("cores", "core")).isCloseTo(0.96, within(1e-6));
  }

  @Test
  void a_resemblance_of_exactly_the_threshold_is_accepted() {
    double jw = NameMatcher.jaroWinkler("abcd", "abcdxxxx");
    assertThat(jw).isEqualTo(NameMatcher.TOKEN_SIMILARITY);
    assertThat(NameMatcher.tokenSimilarity("abcd", "abcdxxxx")).isEqualTo(jw);
  }

  @Test
  void numbers_conflict_only_when_both_sides_carry_different_numbers() {
    assertThat(NameMatcher.numbersConflict(Set.of("1"), Set.of("2"))).isTrue();
    assertThat(NameMatcher.numbersConflict(Set.of("1"), Set.of("1", "2"))).isTrue();
    assertThat(NameMatcher.numbersConflict(Set.of("1"), Set.of("1"))).isFalse();
    assertThat(NameMatcher.numbersConflict(Set.of(), Set.of("2"))).isFalse();
    assertThat(NameMatcher.numbersConflict(Set.of("1"), Set.of())).isFalse();
  }

  @Test
  void similarity_is_bounded_to_one() {
    double h = Math.sqrt(0.5);
    NameMatcher.Doc a = new NameMatcher.Doc(Map.of("paiement", h, "paiment", h), Set.of());
    NameMatcher.Doc b = new NameMatcher.Doc(Map.of("paiement", 1.0), Set.of());
    assertThat(NameMatcher.similarity(a, b)).isEqualTo(1.0);
  }

  @Test
  void similarity_takes_the_best_target_word_for_each_source_word() {
    NameMatcher.Doc a = new NameMatcher.Doc(Map.of("alpha", 0.6, "beta", 0.8), Set.of());
    NameMatcher.Doc b = new NameMatcher.Doc(Map.of("alpha", 0.5, "gamma", 0.5), Set.of());
    assertThat(NameMatcher.similarity(a, b)).isCloseTo(0.3, within(EPS));
    NameMatcher.Doc empty = new NameMatcher.Doc(Map.of(), Set.of());
    assertThat(NameMatcher.similarity(empty, b)).isZero();
    assertThat(NameMatcher.similarity(a, empty)).isZero();
  }

  @Test
  void similarity_weighs_a_fuzzy_word_by_its_resemblance() {
    NameMatcher.Doc a = new NameMatcher.Doc(Map.of("paiment", 1.0), Set.of());
    NameMatcher.Doc b = new NameMatcher.Doc(Map.of("paiement", 0.5, "zzzz", 0.1), Set.of());
    double jw = NameMatcher.jaroWinkler("paiment", "paiement");
    assertThat(NameMatcher.similarity(a, b)).isCloseTo(0.5 * jw, within(EPS));
  }
}
