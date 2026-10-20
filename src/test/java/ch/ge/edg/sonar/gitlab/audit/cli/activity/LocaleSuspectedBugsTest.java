package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.SuspectedBug;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Decimals written under a French default locale: the other tests pin Locale.ROOT. */
class LocaleSuspectedBugsTest {

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
  @SuspectedBug("CSV decimals should be written 1.50 whatever the default locale")
  void decimalsUseACommaUnderAFrenchLocale() {
    assertThat(CellFormat.dec(1.5)).isEqualTo("1,50");
  }
}
