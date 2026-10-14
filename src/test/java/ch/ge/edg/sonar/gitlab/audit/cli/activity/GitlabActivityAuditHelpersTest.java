package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.console.ConsoleOut;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GitlabActivityAuditHelpersTest {

  private Locale saved;

  @BeforeEach
  void fixLocale() {
    saved = Locale.getDefault();
    Locale.setDefault(Locale.ROOT);
    ConsoleOut.colorMode("never");
  }

  @AfterEach
  void restoreLocale() {
    Locale.setDefault(saved);
    ConsoleOut.colorMode("never");
  }

  @Test
  void paramsDropsNullValuesAndAnOddTail() {
    assertThat(Api.params("a", "1", "b", null, "c", "3", "dangling"))
        .containsExactly(java.util.Map.entry("a", "1"), java.util.Map.entry("c", "3"));
    assertThat(Api.params()).isEmpty();
  }

  @Test
  void pctRoundsToAWholePercentAndDashesAnEmptyTotal() {
    assertThat(Fmt.pct(1, 3)).isEqualTo("33 %");
    assertThat(Fmt.pct(2, 3)).isEqualTo("67 %");
    assertThat(Fmt.pct(0, 5)).isEqualTo("0 %");
    assertThat(Fmt.pct(5, 0)).isEqualTo("—");
  }

  @Test
  void decimalsFollowTheDefaultLocale() {
    Locale.setDefault(Locale.FRANCE);
    assertThat(Fmt.dec(1.5)).isEqualTo("1,50");
    assertThat(Fmt.pct(1, 3)).isEqualTo("33 %");
  }

  @Test
  void medianOfOddEvenEmptyAndUnsorted() {
    assertThat(Fmt.median(List.of(3.0, 1.0, 2.0))).isEqualTo(2.0);
    assertThat(Fmt.median(List.of(4.0, 1.0, 3.0, 2.0))).isEqualTo(2.5);
    assertThat(Fmt.median(List.of(7.0))).isEqualTo(7.0);
    assertThat(Fmt.median(List.of())).isNull();
    assertThat(Fmt.median(null)).isNull();
  }

  @Test
  void medianDoesNotSortItsInput() {
    List<Double> in = new ArrayList<>(Arrays.asList(3.0, 1.0, 2.0));
    Fmt.median(in);
    assertThat(in).containsExactly(3.0, 1.0, 2.0);
  }

  @Test
  void formattingHelpers() {
    assertThat(Fmt.iso(null)).isEmpty();
    assertThat(Fmt.iso(OffsetDateTime.parse("2099-01-02T03:04:05Z")))
        .isEqualTo("2099-01-02T03:04:05Z");
    assertThat(Fmt.num(null)).isEmpty();
    assertThat(Fmt.num(42L)).isEqualTo("42");
    assertThat(Fmt.dec(null)).isEmpty();
    assertThat(Fmt.dec(2.0 / 3)).isEqualTo("0.67");
    assertThat(Fmt.orEmpty(null)).isEmpty();
    assertThat(Fmt.orEmpty("x")).isEqualTo("x");
    assertThat(Fmt.isBlank(null)).isTrue();
    assertThat(Fmt.isBlank(" \t")).isTrue();
    assertThat(Fmt.isBlank(" x ")).isFalse();
  }

  @Test
  void truncateAddsAnEllipsis() {
    assertThat(Fmt.truncate(null, 3)).isEmpty();
    assertThat(Fmt.truncate("abc", 3)).isEqualTo("abc");
    assertThat(Fmt.truncate("abcd", 3)).isEqualTo("ab…");
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
    assertThat(Api.parse("2099-01-01T00:00:00+02:00").getOffset().getTotalSeconds())
        .isEqualTo(7200);
    assertThat(Api.parse("nope")).isNull();
    assertThat(Api.enc("a/b c")).isEqualTo("a%2Fb+c");
  }

  @Test
  void revertIsAnchoredAtTheStartOfTheTitle() {
    assertThat(CommitTally.REVERT.matcher("Revert \"x\"").find()).isTrue();
    assertThat(CommitTally.REVERT.matcher("  revert: y").find()).isTrue();
    assertThat(CommitTally.REVERT.matcher("REVERT").find()).isTrue();
    assertThat(CommitTally.REVERT.matcher("fix revert").find()).isFalse();
    assertThat(CommitTally.REVERT.matcher("reverted").find()).isFalse();
    assertThat(ActivityFloor.MIN_FLOOR).isEqualTo(5);
  }

  @Test
  void verdictsCarryTheirTagAndColour() {
    assertThat(Verdict.OK.tag).isEqualTo("OK");
    assertThat(Verdict.DENIED.tag).isEqualTo("REFUSÉ");
    assertThat(Verdict.MISSING.tag).isEqualTo("ABSENT");
    assertThat(Verdict.EMPTY.tag).isEqualTo("VIDE");
    assertThat(Verdict.ERROR.tag).isEqualTo("ERREUR");
    assertThat(Verdict.OK.color).isEqualTo(Console.GREEN);
    assertThat(Verdict.DENIED.color).isEqualTo(Console.RED);
    assertThat(Verdict.MISSING.color).isEqualTo(Console.YELLOW);
    assertThat(Verdict.EMPTY.color).isEqualTo(Console.YELLOW);
    assertThat(Verdict.ERROR.color).isEqualTo(Console.RED);
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
    assertThat(printed(con -> con.line("api", Verdict.OK, "d")))
        .isEqualTo("  api" + " ".repeat(30) + "[OK]  d\n");
    assertThat(printed(con -> con.line("api", Verdict.OK, null)))
        .isEqualTo("  api" + " ".repeat(30) + "[OK]  \n");
  }

  @Test
  void colourCodesWhenColoursAreOn() {
    ConsoleOut.colorMode("always");
    assertThat(Console.c("x", Console.BOLD)).isEqualTo("\033[1mx\033[0m");
    assertThat(Console.RESET).isEqualTo("\033[0m");
    assertThat(Console.DIM).isEqualTo("\033[2m");
  }

  private static String printed(Consumer<Console> r) {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    r.accept(new Console(new PrintStream(buf, true, StandardCharsets.UTF_8)));
    return buf.toString(StandardCharsets.UTF_8);
  }
}
