package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Paging;
import ch.ge.edg.sonar.gitlab.audit.sonar.Sonar;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

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
  void paramsPairsKeysAndValuesAndDropsAnOddTail() {
    assertThat(Values.params("a", "1", "b", "2", "c"))
        .containsExactly(Map.entry("a", "1"), Map.entry("b", "2"));
    assertThat(Values.params()).isEmpty();
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
    assertThat(Text.isBlank(" ")).isTrue();
    assertThat(Text.isBlank("a")).isFalse();
    assertThat(Values.notEmpty(null)).isFalse();
    assertThat(Values.notEmpty(List.of())).isFalse();
    assertThat(Values.notEmpty(List.of(1))).isTrue();
    assertThat(Values.orEmptyList(null)).isEmpty();
    assertThat(Text.orEmpty(null)).isEmpty();
    assertThat(Text.orNa(" ")).isEqualTo("n/a");
    assertThat(Text.orNa(null)).isEqualTo("n/a");
    assertThat(Text.orNa("v")).isEqualTo("v");
    assertThat(Text.truncate("abcd", 4)).isEqualTo("abcd");
    assertThat(Text.truncate("abcde", 4)).isEqualTo("abcd");
  }

  @Test
  void verdictFollowsTheStatus() {
    assertThat(
            Arrays.stream(new int[] {200, 401, 403, 404, 0, 500, 201})
                .mapToObj(s -> Verdict.of(new Sonar.Response(s, "")))
                .toList())
        .containsExactly(
            Verdict.OK,
            Verdict.DENIED,
            Verdict.DENIED,
            Verdict.MISSING,
            Verdict.ERROR,
            Verdict.ERROR,
            Verdict.ERROR);
    assertThat(Verdict.EMPTY.tag).isEqualTo("VIDE    ");
    assertThat(Verdict.EMPTY.color).isEqualTo(Presentation.YELLOW);
  }
}
