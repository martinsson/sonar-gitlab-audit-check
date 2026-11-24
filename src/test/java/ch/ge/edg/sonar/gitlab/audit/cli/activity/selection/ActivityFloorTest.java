package ch.ge.edg.sonar.gitlab.audit.cli.activity.selection;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActivityFloorTest {

  @Test
  void theActivityFloorHasAMinimum() {
    assertThat(ActivityFloor.MIN_FLOOR).isEqualTo(5);
  }
}
