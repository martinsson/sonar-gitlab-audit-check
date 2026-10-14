package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Paging;
import ch.ge.edg.sonar.gitlab.audit.sonar.Sonar;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

/** Small pure helpers: dates, parameters, null-safe accessors, verdicts. */
class HelpersTest {

  @Test
  void parseDateReadsColonOffsetsIntoTheLocalZone() {
    TimeZone old = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try {
      assertThat(Values.parseDate("2026-08-16T05:43:07+02:00"))
          .isEqualTo(LocalDateTime.of(2026, 8, 16, 3, 43, 7));
      // Sonar writes "+0200": ISO_OFFSET_DATE_TIME rejects it, so the offset is dropped and
      // the wall-clock time is kept as is (the comment above parseDate says otherwise).
      assertThat(Values.parseDate("2026-08-16T05:43:07+0200"))
          .isEqualTo(LocalDateTime.of(2026, 8, 16, 5, 43, 7));
    } finally {
      TimeZone.setDefault(old);
    }
  }

  @Test
  void parseDateFallsBackToTheFirstNineteenCharacters() {
    assertThat(Values.parseDate("2026-08-16T05:43:07.123Zjunk"))
        .isEqualTo(LocalDateTime.of(2026, 8, 16, 5, 43, 7));
    assertThat(Values.parseDate("2026-08-16T05:43:07"))
        .isEqualTo(LocalDateTime.of(2026, 8, 16, 5, 43, 7));
    assertThat(Values.parseDate("2026-08-16T05:43:0")).isNull();
    assertThat(Values.parseDate(null)).isNull();
    assertThat(Values.parseDate("not-a-date-at-all-xx")).isNull();
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
    assertThat(Values.dec(null)).isEmpty();
    assertThat(Values.dec(1.234)).isEqualTo("1.23");
    assertThat(Values.dec(1.236)).isEqualTo("1.24");
    assertThat(Values.dec(-11.4449)).isEqualTo("-11.44");
    assertThat(Values.dec(2.0)).isEqualTo("2.00");
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
    assertThat(Values.isBlank(" ")).isTrue();
    assertThat(Values.isBlank("a")).isFalse();
    assertThat(Values.notEmpty(null)).isFalse();
    assertThat(Values.notEmpty(List.of())).isFalse();
    assertThat(Values.notEmpty(List.of(1))).isTrue();
    assertThat(Values.orEmptyList(null)).isEmpty();
    assertThat(Values.orEmpty(null)).isEmpty();
    assertThat(Values.orNa(" ")).isEqualTo("n/a");
    assertThat(Values.orNa(null)).isEqualTo("n/a");
    assertThat(Values.orNa("v")).isEqualTo("v");
    assertThat(Values.truncate("abcd", 4)).isEqualTo("abcd");
    assertThat(Values.truncate("abcde", 4)).isEqualTo("abcd");
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
