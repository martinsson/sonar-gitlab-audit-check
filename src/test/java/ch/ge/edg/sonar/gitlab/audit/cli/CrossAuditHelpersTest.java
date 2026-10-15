package ch.ge.edg.sonar.gitlab.audit.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Confidence;
import ch.ge.edg.sonar.gitlab.audit.console.ConsoleOut;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrossAuditHelpersTest {

  private static final String GL = "fenetre_j,lignes_modifiees,commits_lignes,lignes_tronquees";
  private static final String SQ = "violations_pente_mois";
  private static final String SQ_HEADER = SQ + ",pad";

  @TempDir Path dir;

  private boolean savedColors;

  @BeforeEach
  void save() {
    savedColors = ConsoleOut.colors();
  }

  @AfterEach
  void restore() {
    ConsoleOut.colorMode(savedColors ? "always" : "never");
  }

  private Csv.Row gl(String row) throws IOException {
    return CrossAuditFixtures.table(dir, "gl.csv", GL, row).rows().get(0);
  }

  private Csv.Row sq(String row) throws IOException {
    return CrossAuditFixtures.table(dir, "sq.csv", SQ_HEADER, row + ",pad").rows().get(0);
  }

  private static CrossPair pair(Csv.Row gl, Csv.@Nullable Row sq, Confidence c) {
    return new CrossPair(SonarToGitlabFixtures.match(gl, sq, c, "label"));
  }

  @Test
  void issues_over_the_window_is_the_monthly_slope_times_the_window() throws IOException {
    CrossPair p = pair(gl("45,2000,4,false"), sq("6"), Confidence.EXACT);
    assertThat(CrossPair.issuesOverWindow(p)).isCloseTo(9.0, within(1e-9));
    assertThat(CrossPair.perKlocChanged(p)).isCloseTo(4.5, within(1e-9));
    assertThat(CrossPair.perCommit(p)).isCloseTo(2.25, within(1e-9));
    assertThat(p.joined()).isTrue();
    assertThat(p.gl()).isSameAs(p.m().left());
    assertThat(p.sq()).isSameAs(p.m().right());
  }

  @Test
  void only_sure_pairs_have_a_trend() throws IOException {
    Csv.Row g = gl("30,1000,10,false");
    Csv.Row s = sq("3");
    assertThat(CrossPair.issuesOverWindow(pair(g, s, Confidence.DERIVED))).isEqualTo(3.0);
    assertThat(CrossPair.issuesOverWindow(pair(g, s, Confidence.MANUEL))).isEqualTo(3.0);
    assertThat(CrossPair.issuesOverWindow(pair(g, s, Confidence.SUGGESTION))).isNull();
    assertThat(CrossPair.issuesOverWindow(pair(g, null, Confidence.EXACT))).isNull();
    assertThat(CrossPair.perKlocChanged(pair(g, s, Confidence.NONE))).isNull();
    assertThat(CrossPair.perCommit(pair(g, s, Confidence.REJETE))).isNull();
  }

  @Test
  void any_missing_piece_or_a_truncated_count_gives_no_trend() throws IOException {
    assertThat(CrossPair.issuesOverWindow(pair(gl("30,1,1,false"), sq(""), Confidence.EXACT)))
        .isNull();
    assertThat(CrossPair.issuesOverWindow(pair(gl(",1,1,false"), sq("3"), Confidence.EXACT)))
        .isNull();
    CrossPair truncated = pair(gl("30,1000,10,TRUE"), sq("3"), Confidence.EXACT);
    assertThat(CrossPair.issuesOverWindow(truncated)).isNull();
    assertThat(CrossPair.perKlocChanged(truncated)).isNull();
    assertThat(CrossPair.perCommit(truncated)).isNull();
  }

  @Test
  void zero_or_missing_denominators_give_no_ratio() throws IOException {
    CrossPair zero = pair(gl("30,0,0,false"), sq("3"), Confidence.EXACT);
    assertThat(CrossPair.issuesOverWindow(zero)).isEqualTo(3.0);
    assertThat(CrossPair.perKlocChanged(zero)).isNull();
    assertThat(CrossPair.perCommit(zero)).isNull();
    CrossPair empty = pair(gl("30,,,false"), sq("3"), Confidence.EXACT);
    assertThat(CrossPair.perKlocChanged(empty)).isNull();
    assertThat(CrossPair.perCommit(empty)).isNull();
    CrossPair negative = pair(gl("30,500,2,false"), sq("-3"), Confidence.EXACT);
    assertThat(CrossPair.perKlocChanged(negative)).isEqualTo(-6.0);
    assertThat(CrossPair.perCommit(negative)).isEqualTo(-1.5);
  }

  @Test
  void num_keeps_absent_apart_from_zero_and_nz_brings_it_back_to_zero() throws IOException {
    Csv.Row s = sq("");
    assertThat(CrossPair.num(null, SQ)).isNull();
    assertThat(CrossPair.num(s, SQ)).isNull();
    assertThat(CrossPair.nz(null, SQ)).isZero();
    assertThat(CrossPair.nz(s, SQ)).isZero();
    assertThat(CrossPair.num(sq("\"2,5\""), SQ)).isEqualTo(2.5);
    assertThat(CrossPair.nz(sq("-1.5"), SQ)).isEqualTo(-1.5);
  }

  @Test
  void dec_is_two_decimals_with_a_point_whatever_the_locale() {
    Locale saved = Locale.getDefault();
    Locale.setDefault(Locale.FRANCE);
    try {
      assertThat(CrossCsv.dec(null)).isEmpty();
      assertThat(CrossCsv.dec(1.004)).isEqualTo("1.00");
      assertThat(CrossCsv.dec(2.0 / 3)).isEqualTo("0.67");
      assertThat(CrossCsv.dec(-12.5)).isEqualTo("-12.50");
    } finally {
      Locale.setDefault(saved);
    }
  }

  @Test
  void pct_rounds_and_has_a_dash_for_no_total() {
    assertThat(CrossReport.pct(0, 0)).isEqualTo("—");
    assertThat(CrossReport.pct(1, 3)).isEqualTo("33 %");
    assertThat(CrossReport.pct(2, 3)).isEqualTo("67 %");
    assertThat(CrossReport.pct(3, 3)).isEqualTo("100 %");
    assertThat(CrossReport.pct(0, 5)).isEqualTo("0 %");
  }

  @Test
  void c_and_title_follow_the_color_switch() {
    ConsoleOut.colorMode("always");
    assertThat(CrossReport.c("x", CrossReport.YELLOW)).isEqualTo("\033[33mx\033[0m");
    assertThat(CrossReport.BOLD + CrossReport.DIM + CrossReport.GREEN)
        .isEqualTo("\033[1m\033[2m\033[32m");
    ConsoleOut.colorMode("never");
    assertThat(CrossReport.c("x", CrossReport.YELLOW)).isEqualTo("x");
    assertThat(title("Abc")).isEqualTo("\nAbc\n---\n");
    assertThat(title("x".repeat(80)))
        .isEqualTo("\n" + "x".repeat(80) + "\n" + "-".repeat(72) + "\n");
    ConsoleOut.colorMode("always");
    assertThat(title("Ab")).isEqualTo("\n\033[1mAb\033[0m\n\033[2m--\033[0m\n");
  }

  private static String title(String text) {
    PrintStream old = System.out;
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
    try {
      CrossReport.title(System.out, text);
    } finally {
      System.setOut(old);
    }
    return buf.toString(StandardCharsets.UTF_8).replace(System.lineSeparator(), "\n");
  }
}
