package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A French default locale changes nothing: numbers are formatted with Locale.ROOT. */
class FrenchLocaleTest {

  private Locale saved = Locale.ROOT;

  @BeforeEach
  void french() {
    saved = Locale.getDefault();
    Locale.setDefault(Locale.FRANCE);
  }

  @AfterEach
  void restore() {
    Locale.setDefault(saved);
  }

  @Test
  void csvDecimalsKeepAPoint() {
    assertThat(Fmt.dec(1.5)).isEqualTo("1.50");
  }
}
