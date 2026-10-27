package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import org.junit.jupiter.api.Test;

class PointSelectionTest {

  @Test
  void listsAndRangesAreSortedAndMayRepeat() throws Stop {
    assertThat(PointSelection.numbers(PointSelection.parse("3, 1,4-6")))
        .containsExactly(1, 3, 4, 5, 6);
    assertThat(PointSelection.numbers(PointSelection.parse("2,2"))).containsExactly(2, 2);
    assertThat(PointSelection.numbers(PointSelection.parse("1-15"))).hasSize(15);
  }

  @Test
  void anEmptyRangeSelectsNothing() throws Stop {
    assertThat(PointSelection.parse("5-3")).isEmpty();
  }

  @Test
  void whatIsNotAPointStopsTheCommand() {
    assertThatThrownBy(() -> PointSelection.parse("a"))
        .isInstanceOf(Stop.class)
        .hasMessageContaining("« a »");
    assertThatThrownBy(() -> PointSelection.parse("0")).isInstanceOf(Stop.class);
    assertThatThrownBy(() -> PointSelection.parse("1-16")).isInstanceOf(Stop.class);
  }
}
