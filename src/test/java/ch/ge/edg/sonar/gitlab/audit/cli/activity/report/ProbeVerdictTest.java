package ch.ge.edg.sonar.gitlab.audit.cli.activity.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProbeVerdictTest {

  @ParameterizedTest
  @CsvSource({"OK,OK", "DENIED,REFUSÉ", "MISSING,ABSENT", "EMPTY,VIDE", "ERROR,ERREUR"})
  void verdictsCarryTheirTag(ProbeVerdict verdict, String tag) {
    assertThat(verdict.tag).isEqualTo(tag);
  }

  @Test
  void verdictsCarryTheirColour() {
    assertThat(ProbeVerdict.OK.color).isEqualTo(Screen.GREEN);
    assertThat(ProbeVerdict.DENIED.color).isEqualTo(Screen.RED);
    assertThat(ProbeVerdict.MISSING.color).isEqualTo(Screen.YELLOW);
    assertThat(ProbeVerdict.EMPTY.color).isEqualTo(Screen.YELLOW);
    assertThat(ProbeVerdict.ERROR.color).isEqualTo(Screen.RED);
  }
}
