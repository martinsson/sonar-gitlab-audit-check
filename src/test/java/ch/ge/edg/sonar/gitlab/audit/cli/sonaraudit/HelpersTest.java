package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Paging;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Small pure helpers: dates, parameters, null-safe accessors, verdicts. */
class HelpersTest {

  @Test
  void parseDateReadsColonOffsetsIntoTheClockZone() {
    assertThat(Values.parseDate("2026-08-16T05:43:07+02:00", ZoneOffset.UTC))
        .isEqualTo(LocalDateTime.of(2026, 8, 16, 3, 43, 7));
  }

  @Test
  void parseDateReadsSonarsCompactOffsetIntoTheClockZone() {
    assertThat(Values.parseDate("2026-08-16T05:43:07+0200", ZoneOffset.UTC))
        .isEqualTo(LocalDateTime.of(2026, 8, 16, 3, 43, 7));
  }

  @Test
  void parseDateFallsBackToTheFirstNineteenCharacters() {
    assertThat(Values.parseDate("2026-08-16T05:43:07.123Zjunk", ZoneOffset.UTC))
        .isEqualTo(LocalDateTime.of(2026, 8, 16, 5, 43, 7));
    assertThat(Values.parseDate("2026-08-16T05:43:07", ZoneOffset.UTC))
        .isEqualTo(LocalDateTime.of(2026, 8, 16, 5, 43, 7));
    assertThat(Values.parseDate("2026-08-16T05:43:0", ZoneOffset.UTC)).isNull();
    assertThat(Values.parseDate(null, ZoneOffset.UTC)).isNull();
    assertThat(Values.parseDate("not-a-date-at-all-xx", ZoneOffset.UTC)).isNull();
  }

  @Test
  void historyPathSitsNextToTheInventory() {
    assertThat(InventoryCsv.historyPath(Path.of("out", "inv.csv")))
        .isEqualTo(Path.of("out", "inv-historique.csv"));
    assertThat(InventoryCsv.historyPath(Path.of("out", "a.b.csv")))
        .isEqualTo(Path.of("out", "a.b-historique.csv"));
    assertThat(InventoryCsv.historyPath(Path.of("inv"))).isEqualTo(Path.of("inv-historique.csv"));
    assertThat(InventoryCsv.historyPath(Path.of(".inv"))).isEqualTo(Path.of(".inv-historique.csv"));
  }

  @Test
  void decIsTwoDecimalsWithAPoint() {
    assertThat(Text.decimal(null)).isEmpty();
    assertThat(Text.decimal(1.234)).isEqualTo("1.23");
    assertThat(Text.decimal(1.236)).isEqualTo("1.24");
    assertThat(Text.decimal(-11.4449)).isEqualTo("-11.44");
    assertThat(Text.decimal(2.0)).isEqualTo("2.00");
  }

  @Test
  void shortNameKeepsWhatFollowsTheLastColonUpToFifty() {
    assertThat(Values.shortName("proj:src/A.java")).isEqualTo("src/A.java");
    assertThat(Values.shortName("noColon")).isEqualTo("noColon");
    assertThat(Values.shortName("p:" + "x".repeat(60))).isEqualTo("x".repeat(50));
  }

  @Test
  void nullSafeLittleHelpers() {
    assertThat(Values.orZero(null)).isZero();
    assertThat(Values.orZero(4)).isEqualTo(4);
    assertThat(Values.totalOf(null)).isZero();
    assertThat(Values.totalOf(new Paging(1, 1, null))).isZero();
    assertThat(Values.totalOf(new Paging(1, 1, 7))).isEqualTo(7);
    assertThat(Values.total(null)).isEqualTo("?");
    assertThat(Values.total(new Paging(1, 1, null))).isEqualTo("?");
    assertThat(Values.total(new Paging(1, 1, 7))).isEqualTo("7");
    assertThat(Values.size(null)).isZero();
    assertThat(Values.size(List.of(1, 2))).isEqualTo(2);
    assertThat(Values.notEmpty(null)).isFalse();
    assertThat(Values.notEmpty(List.of())).isFalse();
    assertThat(Values.notEmpty(List.of(1))).isTrue();
    assertThat(Values.orEmptyList(null)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "null",
      value = {"' ',n/a", "null,n/a", "v,v"})
  void orNaSaysNotAvailableForBlank(String raw, String expected) {
    assertThat(Text.orNa(raw)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"abcd,abcd", "abcde,abcd"})
  void truncateKeepsAtMostTheLimit(String raw, String expected) {
    assertThat(Text.truncate(raw, 4)).isEqualTo(expected);
  }

  @Test
  void verdictFollowsTheStatus() {
    assertThat(
            Arrays.stream(new int[] {200, 401, 403, 404, 0, 500, 201})
                .mapToObj(s -> CallVerdict.of(new RestResponse.Answered(s, "", Map.of())))
                .toList())
        .containsExactly(
            CallVerdict.OK,
            CallVerdict.DENIED,
            CallVerdict.DENIED,
            CallVerdict.MISSING,
            CallVerdict.ERROR,
            CallVerdict.ERROR,
            CallVerdict.ERROR);
    assertThat(CallVerdict.of(RestResponse.unreachable("x"))).isEqualTo(CallVerdict.ERROR);
    assertThat(CallVerdict.EMPTY.tag).isEqualTo("VIDE    ");
    assertThat(CallVerdict.EMPTY.color).isEqualTo(Presentation.YELLOW);
  }
}
