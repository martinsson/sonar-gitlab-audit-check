package ch.ge.edg.sonar.gitlab.audit.cli.activity.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CellFormatTest {

  @Test
  void pctRoundsToAWholePercentAndDashesAnEmptyTotal() {
    assertThat(CellFormat.pct(1, 3)).isEqualTo("33 %");
    assertThat(CellFormat.pct(2, 3)).isEqualTo("67 %");
    assertThat(CellFormat.pct(0, 5)).isEqualTo("0 %");
    assertThat(CellFormat.pct(5, 0)).isEqualTo("—");
  }

  @Test
  void medianOfOddEvenEmptyAndUnsorted() {
    assertThat(CellFormat.median(List.of(3.0, 1.0, 2.0))).isEqualTo(2.0);
    assertThat(CellFormat.median(List.of(4.0, 1.0, 3.0, 2.0))).isEqualTo(2.5);
    assertThat(CellFormat.median(List.of(7.0))).isEqualTo(7.0);
    assertThat(CellFormat.median(List.of())).isNull();
    assertThat(CellFormat.median(null)).isNull();
  }

  @Test
  void medianDoesNotSortItsInput() {
    List<Double> in = new ArrayList<>(Arrays.asList(3.0, 1.0, 2.0));
    CellFormat.median(in);
    assertThat(in).containsExactly(3.0, 1.0, 2.0);
  }

  @Test
  void formattingHelpers() {
    assertThat(CellFormat.iso(null)).isEmpty();
    assertThat(CellFormat.iso(OffsetDateTime.parse("2099-01-02T03:04:05Z")))
        .isEqualTo("2099-01-02T03:04:05Z");
    assertThat(CellFormat.num(null)).isEmpty();
    assertThat(CellFormat.num(42L)).isEqualTo("42");
    assertThat(CellFormat.dec(null)).isEmpty();
    assertThat(CellFormat.dec(2.0 / 3)).isEqualTo("0.67");
    assertThat(CellFormat.orEmpty(null)).isEmpty();
    assertThat(CellFormat.orEmpty("x")).isEqualTo("x");
  }
}
