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
    assertThat(JaroWinkler.similarity("martha", "marhta")).isCloseTo(0.961111111, within(1e-6));
    assertThat(JaroWinkler.similarity("dwayne", "duane")).isCloseTo(0.84, within(1e-6));
    assertThat(JaroWinkler.similarity("dixon", "dicksonx")).isCloseTo(0.813333333, within(1e-6));
    assertThat(JaroWinkler.similarity("alpha", "alpine")).isCloseTo(0.79, within(1e-6));
  }

  @Test
  void jaro_winkler_degenerate_cases() {
    assertThat(JaroWinkler.similarity("same", "same")).isEqualTo(1.0);
    assertThat(JaroWinkler.similarity("", "")).isEqualTo(1.0);
    assertThat(JaroWinkler.similarity("", "a")).isZero();
    assertThat(JaroWinkler.similarity("abc", "xyz")).isZero();
  }

  @Test
  void jaro_winkler_prefix_bonus_stops_at_four_letters() {
    double four = JaroWinkler.similarity("abcdefgh", "abcdxxxx");
    double five = JaroWinkler.similarity("abcdefgh", "abcdexxx");
    double jaroFour = (4 / 8.0 + 4 / 8.0 + 1) / 3;
    double jaroFive = (5 / 8.0 + 5 / 8.0 + 1) / 3;
    assertThat(four).isCloseTo(jaroFour + 0.4 * (1 - jaroFour), within(EPS));
    assertThat(five).isCloseTo(jaroFive + 0.4 * (1 - jaroFive), within(EPS));
  }

  @Test
  void jaro_winkler_counts_half_transpositions() {
    // abcd / abdc : four matches, two out of place -> one transposition, prefix 2.
    double jaro = (1 + 1 + (4 - 1) / 4.0) / 3;
    assertThat(JaroWinkler.similarity("abcd", "abdc"))
        .isCloseTo(jaro + 0.2 * (1 - jaro), within(EPS));
    // ab / ba : window 0, nothing matches.
    assertThat(JaroWinkler.similarity("ab", "ba")).isZero();
  }

  @Test
  void jaro_winkler_matching_window_is_half_the_longer_length_minus_one() {
    // window = 6/2 - 1 = 2 : the 'a' three places away does not match.
    assertThat(JaroWinkler.similarity("axxxxx", "yyyayy")).isZero();
    double inWindow = JaroWinkler.similarity("axxxxx", "yyayyy");
    assertThat(inWindow).isCloseTo((1 / 6.0 + 1 / 6.0 + 1) / 3, within(EPS));
  }

  @Test
  void token_similarity_is_identity_or_a_close_long_word() {
    assertThat(SoftTfIdf.tokenSimilarity("api", "api")).isEqualTo(1.0);
    assertThat(SoftTfIdf.tokenSimilarity("apis", "apix")).isZero();
    assertThat(SoftTfIdf.tokenSimilarity("abc", "abcd")).isZero();
    assertThat(SoftTfIdf.tokenSimilarity("abcd", "abc")).isZero();
    assertThat(SoftTfIdf.tokenSimilarity("paiement", "paiment"))
        .isCloseTo(JaroWinkler.similarity("paiement", "paiment"), within(EPS))
        .isGreaterThanOrEqualTo(SoftTfIdf.TOKEN_SIMILARITY);
    assertThat(SoftTfIdf.tokenSimilarity("alpha", "alpine")).isZero();
  }

  @Test
  void token_similarity_accepts_exactly_at_the_threshold_length() {
    // Four letters, one differing at the end: 0.8833 < 0.9, so refused.
    assertThat(SoftTfIdf.tokenSimilarity("abcd", "abce")).isZero();
    // Five letters, last differing: jaro 0.8667, +0.4*(0.1333) = 0.92.
    assertThat(SoftTfIdf.tokenSimilarity("abcde", "abcdf")).isCloseTo(0.92, within(1e-6));
  }

  @Test
  void four_letter_words_are_long_enough_for_resemblance() {
    assertThat(SoftTfIdf.tokenSimilarity("abcd", "abdc")).isCloseTo(0.933333333, within(1e-6));
    assertThat(SoftTfIdf.tokenSimilarity("core", "cores")).isCloseTo(0.96, within(1e-6));
    assertThat(SoftTfIdf.tokenSimilarity("cores", "core")).isCloseTo(0.96, within(1e-6));
  }

  @Test
  void a_resemblance_of_exactly_the_threshold_is_accepted() {
    double jw = JaroWinkler.similarity("abcd", "abcdxxxx");
    assertThat(jw).isEqualTo(SoftTfIdf.TOKEN_SIMILARITY);
    assertThat(SoftTfIdf.tokenSimilarity("abcd", "abcdxxxx")).isEqualTo(jw);
  }

  @Test
  void numbers_conflict_only_when_both_sides_carry_different_numbers() {
    assertThat(SoftTfIdf.numbersConflict(Set.of("1"), Set.of("2"))).isTrue();
    assertThat(SoftTfIdf.numbersConflict(Set.of("1"), Set.of("1", "2"))).isTrue();
    assertThat(SoftTfIdf.numbersConflict(Set.of("1"), Set.of("1"))).isFalse();
    assertThat(SoftTfIdf.numbersConflict(Set.of(), Set.of("2"))).isFalse();
    assertThat(SoftTfIdf.numbersConflict(Set.of("1"), Set.of())).isFalse();
  }

  @Test
  void similarity_is_bounded_to_one() {
    double h = Math.sqrt(0.5);
    SoftTfIdf.Doc a = new SoftTfIdf.Doc(Map.of("paiement", h, "paiment", h), Set.of());
    SoftTfIdf.Doc b = new SoftTfIdf.Doc(Map.of("paiement", 1.0), Set.of());
    assertThat(SoftTfIdf.similarity(a, b)).isEqualTo(1.0);
  }

  @Test
  void similarity_takes_the_best_target_word_for_each_source_word() {
    SoftTfIdf.Doc a = new SoftTfIdf.Doc(Map.of("alpha", 0.6, "beta", 0.8), Set.of());
    SoftTfIdf.Doc b = new SoftTfIdf.Doc(Map.of("alpha", 0.5, "gamma", 0.5), Set.of());
    assertThat(SoftTfIdf.similarity(a, b)).isCloseTo(0.3, within(EPS));
    SoftTfIdf.Doc empty = new SoftTfIdf.Doc(Map.of(), Set.of());
    assertThat(SoftTfIdf.similarity(empty, b)).isZero();
    assertThat(SoftTfIdf.similarity(a, empty)).isZero();
  }

  @Test
  void similarity_weighs_a_fuzzy_word_by_its_resemblance() {
    SoftTfIdf.Doc a = new SoftTfIdf.Doc(Map.of("paiment", 1.0), Set.of());
    SoftTfIdf.Doc b = new SoftTfIdf.Doc(Map.of("paiement", 0.5, "zzzz", 0.1), Set.of());
    double jw = JaroWinkler.similarity("paiment", "paiement");
    assertThat(SoftTfIdf.similarity(a, b)).isCloseTo(0.5 * jw, within(EPS));
  }
}
