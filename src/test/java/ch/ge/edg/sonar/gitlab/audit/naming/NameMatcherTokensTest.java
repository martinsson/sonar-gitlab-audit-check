package ch.ge.edg.sonar.gitlab.audit.naming;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** From names to words: {@link NameMatcher#tokens}, {@link NameMatcher#numbers}, addTokens. */
class NameMatcherTokensTest {

  @Test
  void tokens_split_on_separators_camel_case_and_drop_numbers_and_single_letters() {
    assertThat(NameMatcher.tokens("MonApi_Service-v2")).containsExactly("mon", "api", "service");
    assertThat(NameMatcher.tokens("alpha.beta gamma/delta"))
        .containsExactly("alpha", "beta", "gamma", "delta");
    assertThat(NameMatcher.tokens("app2x")).containsExactly("app");
    assertThat(NameMatcher.tokens("a-b-c")).isEmpty();
  }

  @Test
  void tokens_split_an_acronym_from_the_following_word() {
    assertThat(NameMatcher.tokens("HTTPServer")).containsExactly("http", "server");
    assertThat(NameMatcher.tokens("myHTTPServer")).containsExactly("my", "http", "server");
    assertThat(NameMatcher.tokens("ALLCAPS")).containsExactly("allcaps");
  }

  @Test
  void tokens_remove_accents_and_ignore_case() {
    assertThat(NameMatcher.tokens("Élan-Café")).containsExactly("elan", "cafe");
    assertThat(NameMatcher.tokens("ÉLAN")).containsExactly("elan");
  }

  @Test
  void tokens_keep_duplicates_and_order() {
    assertThat(NameMatcher.tokens("api-api-core")).containsExactly("api", "api", "core");
  }

  @Test
  void tokens_of_null_or_empty_are_empty() {
    assertThat(NameMatcher.tokens(null)).isEmpty();
    assertThat(NameMatcher.tokens("")).isEmpty();
    assertThat(NameMatcher.tokens("123-456")).isEmpty();
  }

  @Test
  void tokens_drop_non_latin_letters_entirely() {
    assertThat(NameMatcher.tokens("straße")).containsExactly("stra");
    assertThat(NameMatcher.tokens("ßß-ok")).containsExactly("ok");
  }

  @Test
  void numbers_are_the_digit_runs_without_leading_zeros() {
    assertThat(NameMatcher.numbers("app-02")).containsExactly("2");
    assertThat(NameMatcher.numbers("app2")).containsExactly("2");
    assertThat(NameMatcher.numbers("v0")).containsExactly("0");
    assertThat(NameMatcher.numbers("x000")).containsExactly("0");
    assertThat(NameMatcher.numbers("a1b22c001")).containsExactlyInAnyOrder("1", "22");
    assertThat(NameMatcher.numbers("100")).containsExactly("100");
  }

  @Test
  void numbers_of_null_or_digitless_are_empty_and_mutable() {
    assertThat(NameMatcher.numbers(null)).isEmpty();
    assertThat(NameMatcher.numbers("alpha")).isEmpty();
    NameMatcher.numbers(null).add("x");
  }

  @Test
  void add_tokens_counts_each_word_with_its_weight() {
    Map<String, Double> tf = new HashMap<>();
    NameMatcher.addTokens(tf, "alpha-beta-alpha", 2.0);
    NameMatcher.addTokens(tf, "beta", 0.5);
    NameMatcher.addTokens(tf, null, 1.0);
    assertThat(tf).containsOnly(Map.entry("alpha", 4.0), Map.entry("beta", 2.5));
  }
}
