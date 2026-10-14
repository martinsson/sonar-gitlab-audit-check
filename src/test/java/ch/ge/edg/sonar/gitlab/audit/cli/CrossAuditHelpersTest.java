package ch.ge.edg.sonar.gitlab.audit.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ch.ge.edg.sonar.gitlab.audit.cli.CrossAudit.Pair;
import ch.ge.edg.sonar.gitlab.audit.cli.ProjectMapper.Confidence;
import ch.ge.edg.sonar.gitlab.audit.console.ConsoleOut;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
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

  private static Pair pair(Csv.Row gl, Csv.Row sq, Confidence c) {
    return new Pair(SonarToGitlabFixtures.match(gl, sq, c, "label"));
  }

  @Test
  void issues_over_the_window_is_the_monthly_slope_times_the_window() throws IOException {
    Pair p = pair(gl("45,2000,4,false"), sq("6"), Confidence.EXACT);
    assertThat(CrossAudit.issuesOverWindow(p)).isCloseTo(9.0, within(1e-9));
    assertThat(CrossAudit.perKlocChanged(p)).isCloseTo(4.5, within(1e-9));
    assertThat(CrossAudit.perCommit(p)).isCloseTo(2.25, within(1e-9));
    assertThat(p.joined()).isTrue();
    assertThat(p.gl()).isSameAs(p.m().left());
    assertThat(p.sq()).isSameAs(p.m().right());
  }

  @Test
  void only_sure_pairs_have_a_trend() throws IOException {
    Csv.Row g = gl("30,1000,10,false");
    Csv.Row s = sq("3");
    assertThat(CrossAudit.issuesOverWindow(pair(g, s, Confidence.DERIVED))).isEqualTo(3.0);
    assertThat(CrossAudit.issuesOverWindow(pair(g, s, Confidence.MANUEL))).isEqualTo(3.0);
    assertThat(CrossAudit.issuesOverWindow(pair(g, s, Confidence.SUGGESTION))).isNull();
    assertThat(CrossAudit.issuesOverWindow(pair(g, null, Confidence.EXACT))).isNull();
    assertThat(CrossAudit.perKlocChanged(pair(g, s, Confidence.NONE))).isNull();
    assertThat(CrossAudit.perCommit(pair(g, s, Confidence.REJETE))).isNull();
  }

  @Test
  void any_missing_piece_or_a_truncated_count_gives_no_trend() throws IOException {
    assertThat(CrossAudit.issuesOverWindow(pair(gl("30,1,1,false"), sq(""), Confidence.EXACT)))
        .isNull();
    assertThat(CrossAudit.issuesOverWindow(pair(gl(",1,1,false"), sq("3"), Confidence.EXACT)))
        .isNull();
    Pair truncated = pair(gl("30,1000,10,TRUE"), sq("3"), Confidence.EXACT);
    assertThat(CrossAudit.issuesOverWindow(truncated)).isNull();
    assertThat(CrossAudit.perKlocChanged(truncated)).isNull();
    assertThat(CrossAudit.perCommit(truncated)).isNull();
  }

  @Test
  void zero_or_missing_denominators_give_no_ratio() throws IOException {
    Pair zero = pair(gl("30,0,0,false"), sq("3"), Confidence.EXACT);
    assertThat(CrossAudit.issuesOverWindow(zero)).isEqualTo(3.0);
    assertThat(CrossAudit.perKlocChanged(zero)).isNull();
    assertThat(CrossAudit.perCommit(zero)).isNull();
    Pair empty = pair(gl("30,,,false"), sq("3"), Confidence.EXACT);
    assertThat(CrossAudit.perKlocChanged(empty)).isNull();
    assertThat(CrossAudit.perCommit(empty)).isNull();
    Pair negative = pair(gl("30,500,2,false"), sq("-3"), Confidence.EXACT);
    assertThat(CrossAudit.perKlocChanged(negative)).isEqualTo(-6.0);
    assertThat(CrossAudit.perCommit(negative)).isEqualTo(-1.5);
  }

  @Test
  void num_keeps_absent_apart_from_zero_and_nz_brings_it_back_to_zero() throws IOException {
    Csv.Row s = sq("");
    assertThat(CrossAudit.num(null, SQ)).isNull();
    assertThat(CrossAudit.num(s, SQ)).isNull();
    assertThat(CrossAudit.nz(null, SQ)).isZero();
    assertThat(CrossAudit.nz(s, SQ)).isZero();
    assertThat(CrossAudit.num(sq("\"2,5\""), SQ)).isEqualTo(2.5);
    assertThat(CrossAudit.nz(sq("-1.5"), SQ)).isEqualTo(-1.5);
  }

  @Test
  void dec_is_two_decimals_with_a_point_whatever_the_locale() {
    Locale saved = Locale.getDefault();
    Locale.setDefault(Locale.FRANCE);
    try {
      assertThat(CrossAudit.dec(null)).isEmpty();
      assertThat(CrossAudit.dec(1.004)).isEqualTo("1.00");
      assertThat(CrossAudit.dec(2.0 / 3)).isEqualTo("0.67");
      assertThat(CrossAudit.dec(-12.5)).isEqualTo("-12.50");
    } finally {
      Locale.setDefault(saved);
    }
  }

  @Test
  void pct_rounds_and_has_a_dash_for_no_total() {
    assertThat(CrossAudit.pct(0, 0)).isEqualTo("—");
    assertThat(CrossAudit.pct(1, 3)).isEqualTo("33 %");
    assertThat(CrossAudit.pct(2, 3)).isEqualTo("67 %");
    assertThat(CrossAudit.pct(3, 3)).isEqualTo("100 %");
    assertThat(CrossAudit.pct(0, 5)).isEqualTo("0 %");
  }

  @Test
  void c_and_title_follow_the_color_switch() {
    ConsoleOut.colorMode("always");
    assertThat(CrossAudit.c("x", CrossAudit.YELLOW)).isEqualTo("\033[33mx\033[0m");
    assertThat(CrossAudit.BOLD + CrossAudit.DIM + CrossAudit.GREEN)
        .isEqualTo("\033[1m\033[2m\033[32m");
    ConsoleOut.colorMode("never");
    assertThat(CrossAudit.c("x", CrossAudit.YELLOW)).isEqualTo("x");
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
      CrossAudit.title(text);
    } finally {
      System.setOut(old);
    }
    return buf.toString(StandardCharsets.UTF_8).replace(System.lineSeparator(), "\n");
  }
}
