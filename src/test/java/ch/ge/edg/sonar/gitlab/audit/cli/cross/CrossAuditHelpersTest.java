package ch.ge.edg.sonar.gitlab.audit.cli.cross;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.matching.Confidence;
import ch.ge.edg.sonar.gitlab.audit.testing.Printed;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrossAuditHelpersTest {

  private static final String GL = "fenetre_j,lignes_modifiees,commits_lignes,lignes_tronquees";
  private static final String SQ = "violations_pente_mois";
  private static final String SQ_HEADER = SQ + ",pad";

  @TempDir Path dir;

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
  void issuesOverTheWindowIsTheMonthlySlopeTimesTheWindow() throws IOException {
    CrossPair p = pair(gl("45,2000,4,false"), sq("6"), Confidence.EXACT);
    assertThat(CrossPair.issuesOverWindow(p)).isCloseTo(9.0, within(1e-9));
    assertThat(CrossPair.perKlocChanged(p)).isCloseTo(4.5, within(1e-9));
    assertThat(CrossPair.perCommit(p)).isCloseTo(2.25, within(1e-9));
    assertThat(p.joined()).isTrue();
    assertThat(p.gl()).isSameAs(p.m().left());
    assertThat(p.sq()).isSameAs(p.m().right());
  }

  @Test
  void onlySurePairsHaveATrend() throws IOException {
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
  void anyMissingPieceOrATruncatedCountGivesNoTrend() throws IOException {
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
  void zeroOrMissingDenominatorsGiveNoRatio() throws IOException {
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
  void numKeepsAbsentApartFromZeroAndNzBringsItBackToZero() throws IOException {
    Csv.Row s = sq("");
    assertThat(CrossPair.num(null, SQ)).isNull();
    assertThat(CrossPair.num(s, SQ)).isNull();
    assertThat(CrossPair.nz(null, SQ)).isZero();
    assertThat(CrossPair.nz(s, SQ)).isZero();
    assertThat(CrossPair.num(sq("\"2,5\""), SQ)).isEqualTo(2.5);
    assertThat(CrossPair.nz(sq("-1.5"), SQ)).isEqualTo(-1.5);
  }

  @Test
  void decIsTwoDecimalsWithAPointWhateverTheLocale() {
    Locale saved = Locale.getDefault();
    Locale.setDefault(Locale.FRANCE);
    try {
      assertThat(Text.decimal(null)).isEmpty();
      assertThat(Text.decimal(1.004)).isEqualTo("1.00");
      assertThat(Text.decimal(2.0 / 3)).isEqualTo("0.67");
      assertThat(Text.decimal(-12.5)).isEqualTo("-12.50");
    } finally {
      Locale.setDefault(saved);
    }
  }

  @Test
  void pctRoundsAndHasADashForNoTotal() {
    assertThat(Text.pct(0, 0)).isEqualTo("—");
    assertThat(Text.pct(1, 3)).isEqualTo("33 %");
    assertThat(Text.pct(2, 3)).isEqualTo("67 %");
    assertThat(Text.pct(3, 3)).isEqualTo("100 %");
    assertThat(Text.pct(0, 5)).isEqualTo("0 %");
  }

  @Test
  void titleFollowsTheColorSwitch() {
    assertThat(CrossReport.BOLD + CrossReport.DIM + CrossReport.GREEN)
        .isEqualTo("\033[1m\033[2m\033[32m");
    assertThat(title(Printed.plain(), "Abc")).isEqualTo("\nAbc\n---\n");
    assertThat(title(Printed.plain(), "x".repeat(80)))
        .isEqualTo("\n" + "x".repeat(80) + "\n" + "-".repeat(72) + "\n");
    assertThat(title(Printed.colored(), "Ab")).isEqualTo("\n\033[1mAb\033[0m\n\033[2m--\033[0m\n");
  }

  private static String title(Printed printed, String text) {
    CrossReport.title(printed.console(), text);
    return printed.text();
  }
}
