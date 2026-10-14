package ch.ge.edg.sonar.gitlab.audit.cli;

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
    assertThat(GitlabActivityAudit.params("a", "1", "b", null, "c", "3", "dangling"))
        .containsExactly(java.util.Map.entry("a", "1"), java.util.Map.entry("c", "3"));
    assertThat(GitlabActivityAudit.params()).isEmpty();
  }

  @Test
  void pctRoundsToAWholePercentAndDashesAnEmptyTotal() {
    assertThat(GitlabActivityAudit.pct(1, 3)).isEqualTo("33 %");
    assertThat(GitlabActivityAudit.pct(2, 3)).isEqualTo("67 %");
    assertThat(GitlabActivityAudit.pct(0, 5)).isEqualTo("0 %");
    assertThat(GitlabActivityAudit.pct(5, 0)).isEqualTo("—");
  }

  @Test
  void decimalsFollowTheDefaultLocale() {
    Locale.setDefault(Locale.FRANCE);
    assertThat(GitlabActivityAudit.dec(1.5)).isEqualTo("1,50");
    assertThat(GitlabActivityAudit.pct(1, 3)).isEqualTo("33 %");
  }

  @Test
  void medianOfOddEvenEmptyAndUnsorted() {
    assertThat(GitlabActivityAudit.median(List.of(3.0, 1.0, 2.0))).isEqualTo(2.0);
    assertThat(GitlabActivityAudit.median(List.of(4.0, 1.0, 3.0, 2.0))).isEqualTo(2.5);
    assertThat(GitlabActivityAudit.median(List.of(7.0))).isEqualTo(7.0);
    assertThat(GitlabActivityAudit.median(List.of())).isNull();
    assertThat(GitlabActivityAudit.median(null)).isNull();
  }

  @Test
  void medianDoesNotSortItsInput() {
    List<Double> in = new ArrayList<>(Arrays.asList(3.0, 1.0, 2.0));
    GitlabActivityAudit.median(in);
    assertThat(in).containsExactly(3.0, 1.0, 2.0);
  }

  @Test
  void formattingHelpers() {
    assertThat(GitlabActivityAudit.iso(null)).isEmpty();
    assertThat(GitlabActivityAudit.iso(OffsetDateTime.parse("2099-01-02T03:04:05Z")))
        .isEqualTo("2099-01-02T03:04:05Z");
    assertThat(GitlabActivityAudit.num(null)).isEmpty();
    assertThat(GitlabActivityAudit.num(42L)).isEqualTo("42");
    assertThat(GitlabActivityAudit.dec(null)).isEmpty();
    assertThat(GitlabActivityAudit.dec(2.0 / 3)).isEqualTo("0.67");
    assertThat(GitlabActivityAudit.orEmpty(null)).isEmpty();
    assertThat(GitlabActivityAudit.orEmpty("x")).isEqualTo("x");
    assertThat(GitlabActivityAudit.isBlank(null)).isTrue();
    assertThat(GitlabActivityAudit.isBlank(" \t")).isTrue();
    assertThat(GitlabActivityAudit.isBlank(" x ")).isFalse();
  }

  @Test
  void truncateAddsAnEllipsis() {
    assertThat(GitlabActivityAudit.truncate(null, 3)).isEmpty();
    assertThat(GitlabActivityAudit.truncate("abc", 3)).isEqualTo("abc");
    assertThat(GitlabActivityAudit.truncate("abcd", 3)).isEqualTo("ab…");
  }

  @Test
  void textReadsAFieldAsTextOrEmpty() throws Exception {
    var n = Gitlab.MAPPER.readTree("{\"a\":\"x\",\"b\":3,\"c\":null}");
    assertThat(GitlabActivityAudit.text(n, "a")).isEqualTo("x");
    assertThat(GitlabActivityAudit.text(n, "b")).isEqualTo("3");
    assertThat(GitlabActivityAudit.text(n, "c")).isEmpty();
    assertThat(GitlabActivityAudit.text(n, "missing")).isEmpty();
    assertThat(GitlabActivityAudit.text(null, "a")).isEmpty();
  }

  @Test
  void parseAndEncDelegateToGitlab() {
    assertThat(GitlabActivityAudit.parse("2099-01-01T00:00:00+02:00").getOffset().getTotalSeconds())
        .isEqualTo(7200);
    assertThat(GitlabActivityAudit.parse("nope")).isNull();
    assertThat(GitlabActivityAudit.enc("a/b c")).isEqualTo("a%2Fb+c");
  }

  @Test
  void revertIsAnchoredAtTheStartOfTheTitle() {
    assertThat(GitlabActivityAudit.REVERT.matcher("Revert \"x\"").find()).isTrue();
    assertThat(GitlabActivityAudit.REVERT.matcher("  revert: y").find()).isTrue();
    assertThat(GitlabActivityAudit.REVERT.matcher("REVERT").find()).isTrue();
    assertThat(GitlabActivityAudit.REVERT.matcher("fix revert").find()).isFalse();
    assertThat(GitlabActivityAudit.REVERT.matcher("reverted").find()).isFalse();
    assertThat(GitlabActivityAudit.MIN_FLOOR).isEqualTo(5);
  }

  @Test
  void verdictsCarryTheirTagAndColour() {
    assertThat(GitlabActivityAudit.Verdict.OK.tag).isEqualTo("OK");
    assertThat(GitlabActivityAudit.Verdict.DENIED.tag).isEqualTo("REFUSÉ");
    assertThat(GitlabActivityAudit.Verdict.MISSING.tag).isEqualTo("ABSENT");
    assertThat(GitlabActivityAudit.Verdict.EMPTY.tag).isEqualTo("VIDE");
    assertThat(GitlabActivityAudit.Verdict.ERROR.tag).isEqualTo("ERREUR");
    assertThat(GitlabActivityAudit.Verdict.OK.color).isEqualTo(GitlabActivityAudit.GREEN);
    assertThat(GitlabActivityAudit.Verdict.DENIED.color).isEqualTo(GitlabActivityAudit.RED);
    assertThat(GitlabActivityAudit.Verdict.MISSING.color).isEqualTo(GitlabActivityAudit.YELLOW);
    assertThat(GitlabActivityAudit.Verdict.EMPTY.color).isEqualTo(GitlabActivityAudit.YELLOW);
    assertThat(GitlabActivityAudit.Verdict.ERROR.color).isEqualTo(GitlabActivityAudit.RED);
  }

  @Test
  void titleUnderlinesUpTo72Characters() {
    assertThat(printed(() -> GitlabActivityAudit.title("Abc"))).isEqualTo("\nAbc\n---\n");
    String longTitle = "x".repeat(80);
    assertThat(printed(() -> GitlabActivityAudit.title(longTitle)))
        .isEqualTo("\n" + longTitle + "\n" + "-".repeat(72) + "\n");
  }

  @Test
  void lineAlignsLabelVerdictAndDetail() {
    assertThat(printed(() -> GitlabActivityAudit.line("api", GitlabActivityAudit.Verdict.OK, "d")))
        .isEqualTo("  api" + " ".repeat(30) + "[OK]  d\n");
    assertThat(printed(() -> GitlabActivityAudit.line("api", GitlabActivityAudit.Verdict.OK, null)))
        .isEqualTo("  api" + " ".repeat(30) + "[OK]  \n");
  }

  @Test
  void colourCodesWhenColoursAreOn() {
    ConsoleOut.colorMode("always");
    assertThat(GitlabActivityAudit.c("x", GitlabActivityAudit.BOLD)).isEqualTo("\033[1mx\033[0m");
    assertThat(GitlabActivityAudit.RESET).isEqualTo("\033[0m");
    assertThat(GitlabActivityAudit.DIM).isEqualTo("\033[2m");
  }

  private static String printed(Runnable r) {
    PrintStream old = System.out;
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    try {
      System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
      r.run();
    } finally {
      System.setOut(old);
    }
    return buf.toString(StandardCharsets.UTF_8);
  }
}
