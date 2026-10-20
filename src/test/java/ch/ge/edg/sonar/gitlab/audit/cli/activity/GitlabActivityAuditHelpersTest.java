package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.testing.Printed;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GitlabActivityAuditHelpersTest {

  private Locale saved;

  @BeforeEach
  void fixLocale() {
    saved = Locale.getDefault();
    Locale.setDefault(Locale.ROOT);
  }

  @AfterEach
  void restoreLocale() {
    Locale.setDefault(saved);
  }

  @Test
  void paramsDropsNullValuesAndAnOddTail() {
    assertThat(Api.params("a", "1", "b", null, "c", "3", "dangling"))
        .containsExactly(java.util.Map.entry("a", "1"), java.util.Map.entry("c", "3"));
    assertThat(Api.params()).isEmpty();
  }

  @Test
  void pctRoundsToAWholePercentAndDashesAnEmptyTotal() {
    assertThat(CellFormat.pct(1, 3)).isEqualTo("33 %");
    assertThat(CellFormat.pct(2, 3)).isEqualTo("67 %");
    assertThat(CellFormat.pct(0, 5)).isEqualTo("0 %");
    assertThat(CellFormat.pct(5, 0)).isEqualTo("—");
  }

  @Test
  void decimalsIgnoreTheDefaultLocale() {
    Locale.setDefault(Locale.FRANCE);
    assertThat(CellFormat.dec(1.5)).isEqualTo("1.50");
    assertThat(CellFormat.pct(1, 3)).isEqualTo("33 %");
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
    assertThat(CellFormat.isBlank(null)).isTrue();
    assertThat(CellFormat.isBlank(" \t")).isTrue();
    assertThat(CellFormat.isBlank(" x ")).isFalse();
  }

  @Test
  void truncateAddsAnEllipsis() {
    assertThat(CellFormat.truncate(null, 3)).isEmpty();
    assertThat(CellFormat.truncate("abc", 3)).isEqualTo("abc");
    assertThat(CellFormat.truncate("abcd", 3)).isEqualTo("ab…");
  }

  @Test
  void textReadsAFieldAsTextOrEmpty() throws Exception {
    var n = Gitlab.MAPPER.readTree("{\"a\":\"x\",\"b\":3,\"c\":null}");
    assertThat(Api.text(n, "a")).isEqualTo("x");
    assertThat(Api.text(n, "b")).isEqualTo("3");
    assertThat(Api.text(n, "c")).isEmpty();
    assertThat(Api.text(n, "missing")).isEmpty();
    assertThat(Api.text(null, "a")).isEmpty();
  }

  @Test
  void parseAndEncDelegateToGitlab() {
    assertThat(
            Objects.requireNonNull(Api.parse("2099-01-01T00:00:00+02:00"))
                .getOffset()
                .getTotalSeconds())
        .isEqualTo(7200);
    assertThat(Api.parse("nope")).isNull();
    assertThat(Api.enc("a/b c")).isEqualTo("a%2Fb+c");
  }

  @ParameterizedTest
  @CsvSource({
    "'Revert \"x\"',true",
    "'  revert: y',true",
    "REVERT,true",
    "fix revert,false",
    "reverted,false"
  })
  void revertIsAnchoredAtTheStartOfTheTitle(String title, boolean reverts) {
    assertThat(CommitTally.REVERT.matcher(title).find()).isEqualTo(reverts);
  }

  @Test
  void theActivityFloorHasAMinimum() {
    assertThat(ActivityFloor.MIN_FLOOR).isEqualTo(5);
  }

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

  @Test
  void titleUnderlinesUpTo72Characters() {
    assertThat(printed(con -> con.title("Abc"))).isEqualTo("\nAbc\n---\n");
    String longTitle = "x".repeat(80);
    assertThat(printed(con -> con.title(longTitle)))
        .isEqualTo("\n" + longTitle + "\n" + "-".repeat(72) + "\n");
  }

  @Test
  void lineAlignsLabelVerdictAndDetail() {
    assertThat(printed(con -> con.line("api", ProbeVerdict.OK, "d")))
        .isEqualTo("  api" + " ".repeat(30) + "[OK]  d\n");
    assertThat(printed(con -> con.line("api", ProbeVerdict.OK, null)))
        .isEqualTo("  api" + " ".repeat(30) + "[OK]  \n");
  }

  @Test
  void colourCodesWhenColoursAreOn() {
    Screen screen = new Screen(Printed.colored().console());
    assertThat(screen.paint("x", Screen.BOLD)).isEqualTo("\033[1mx\033[0m");
    assertThat(Screen.RESET).isEqualTo("\033[0m");
    assertThat(Screen.DIM).isEqualTo("\033[2m");
  }

  private static String printed(Consumer<Screen> r) {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(buf, true, StandardCharsets.UTF_8);
    r.accept(new Screen(new Console(out, out, false)));
    return buf.toString(StandardCharsets.UTF_8);
  }
}
