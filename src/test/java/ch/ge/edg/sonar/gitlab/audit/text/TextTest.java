package ch.ge.edg.sonar.gitlab.audit.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextTest {

  @Test
  void blankAndAbsentText() {
    assertThat(Text.isBlank(null)).isTrue();
    assertThat(Text.isBlank(" ")).isTrue();
    assertThat(Text.isBlank("x")).isFalse();
    assertThat(Text.orEmpty(null)).isEmpty();
    assertThat(Text.orEmpty("x")).isEqualTo("x");
    assertThat(Text.orNa(" ")).isEqualTo("n/a");
    assertThat(Text.orNa("x")).isEqualTo("x");
  }

  @Test
  void cuttingWithAndWithoutAMark() {
    assertThat(Text.truncate("abcde", 4)).isEqualTo("abcd");
    assertThat(Text.truncate("abcd", 4)).isEqualTo("abcd");
    assertThat(Text.ellipsize("abcd", 4)).isEqualTo("abcd");
    assertThat(Text.ellipsize("abcde", 4)).isEqualTo("a...");
  }

  @Test
  void numbers() {
    assertThat(Text.decimal(0.5)).isEqualTo("0.50");
    assertThat(Text.decimal(0.125)).isEqualTo("0.13");
    assertThat(Text.decimal(null)).isEmpty();
    assertThat(Text.round(0.12345)).isEqualTo(0.123);
    assertThat(Text.pct(1, 3)).isEqualTo("33 %");
    assertThat(Text.pct(0, 0)).isEqualTo("—");
    assertThat(Text.pct(0.5)).isEqualTo(" 50 %");
  }
}
