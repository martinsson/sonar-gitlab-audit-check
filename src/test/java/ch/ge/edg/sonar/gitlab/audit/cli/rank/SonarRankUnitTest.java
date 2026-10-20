package ch.ge.edg.sonar.gitlab.audit.cli.rank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.Printed;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** The pure pieces of {@link SonarRank}, and how it refuses bad input. */
class SonarRankUnitTest {

  @TempDir Path dir;
  private Locale locale;

  @BeforeEach
  void pinLocale() {
    locale = Locale.getDefault();
    Locale.setDefault(Locale.ROOT);
  }

  @AfterEach
  void restore() {
    Locale.setDefault(locale);
  }

  static InventoryRow row(String... kv) {
    Map<String, Integer> index = new HashMap<>();
    String[] cells = new String[kv.length / 2];
    for (int i = 0; i < kv.length; i += 2) {
      index.put(kv[i], i / 2);
      cells[i / 2] = kv[i + 1];
    }
    return new InventoryRow(new Csv.Row(index, cells));
  }

  @Test
  void percentile_is_the_mean_rank_of_the_value() {
    List<Double> sorted = List.of(1.0, 2.0, 2.0, 4.0);
    assertThat(VelocityRanking.percentileOf(0.5, sorted)).isZero();
    assertThat(VelocityRanking.percentileOf(1.0, sorted)).isEqualTo(12.5);
    assertThat(VelocityRanking.percentileOf(2.0, sorted)).isEqualTo(50.0);
    assertThat(VelocityRanking.percentileOf(3.0, sorted)).isEqualTo(75.0);
    assertThat(VelocityRanking.percentileOf(4.0, sorted)).isEqualTo(87.5);
    assertThat(VelocityRanking.percentileOf(9.0, sorted)).isEqualTo(100.0);
    assertThat(VelocityRanking.percentileOf(7.0, List.of(7.0))).isEqualTo(50.0);
  }

  @Test
  void buckets_are_half_open_and_anything_unmatched_is_large() {
    assertThat(Bucket.of(0)).isEqualTo(Bucket.XS);
    assertThat(Bucket.of(999)).isEqualTo(Bucket.XS);
    assertThat(Bucket.of(1_000)).isEqualTo(Bucket.S);
    assertThat(Bucket.of(9_999)).isEqualTo(Bucket.S);
    assertThat(Bucket.of(10_000)).isEqualTo(Bucket.M);
    assertThat(Bucket.of(99_999)).isEqualTo(Bucket.M);
    assertThat(Bucket.of(100_000)).isEqualTo(Bucket.L);
    assertThat(Bucket.of(Integer.MAX_VALUE)).isEqualTo(Bucket.L);
    assertThat(Bucket.of(-1)).isEqualTo(Bucket.L);
    assertThat(Bucket.of(Double.NaN)).isEqualTo(Bucket.L);
    assertThat(Bucket.S.label).isEqualTo("1k - 10k");
  }

  @Test
  void signals_point_the_same_way_and_absent_stays_absent() {
    InventoryRow full =
        row(
            "new_violations",
            "5",
            "new_lines",
            "250",
            "new_coverage",
            "70",
            "new_duplicated_lines_density",
            "3.5",
            "sqale_debt_ratio",
            "1.2");
    assertThat(Signal.INJECTION.severity(full)).isCloseTo(20.0, within(1e-9));
    assertThat(Signal.NEW_COVERAGE.severity(full)).isEqualTo(-70.0);
    assertThat(Signal.NEW_DUPLICATION.severity(full)).isEqualTo(3.5);
    assertThat(Signal.DEBT_RATIO.severity(full)).isEqualTo(1.2);
    assertThat(Signal.DEBT_RATIO.weight).isEqualTo(0.5);
    assertThat(Signal.INJECTION.weight).isEqualTo(1.0);

    InventoryRow empty = row();
    for (Signal s : Signal.values()) {
      assertThat(s.severity(empty)).as(s.name()).isNull();
    }
    assertThat(Signal.INJECTION.severity(row("new_violations", "5"))).isNull();
    assertThat(Signal.INJECTION.severity(row("new_violations", "5", "new_lines", "0"))).isNull();
    assertThat(Signal.INJECTION.severity(row("new_violations", "5", "new_lines", "-3"))).isNull();
  }

  @Test
  void a_row_reads_its_identity_size_and_age() {
    InventoryRow r =
        row(
            "key",
            "k1",
            "name",
            "N1",
            "ncloc",
            "1200",
            "analysisDate",
            "2026-01-01",
            "days_since_analysis",
            "12.9");
    assertThat(r.key()).isEqualTo("k1");
    assertThat(r.name()).isEqualTo("N1");
    assertThat(r.ncloc()).isEqualTo(1200.0);
    assertThat(r.daysSinceAnalysis()).isEqualTo(12);
    assertThat(row().ncloc()).isZero();
    assertThat(row("days_since_analysis", "4").daysSinceAnalysis()).isNull();
    assertThat(row("analysisDate", "2026-01-01").daysSinceAnalysis()).isNull();
  }

  @Test
  void presentation_helpers() {
    RankReport report =
        new RankReport(Printed.colored().console(), new RankSettings(365, 730, 0, 10, 2));
    assertThat(report.paint("x", "")).isEqualTo("x");
    assertThat(report.paint("x", RankReport.RED)).isEqualTo("\033[31mx\033[0m");
    assertThat(report.pct(3, 0, "")).isEqualTo("3");
    assertThat(report.pct(1, 3, "")).isEqualTo("1 (33%)");
    assertThat(report.pct(2, 3, RankReport.YELLOW)).isEqualTo("\033[33m2 (67%)\033[0m");
    assertThat(row("key", "k", "name", "").label()).isEqualTo("k");
    assertThat(row("key", "k", "name", "n").label()).isEqualTo("n");
    assertThat(Text.ellipsize("abcd", 4)).isEqualTo("abcd");
    assertThat(Text.ellipsize("abcde", 4)).isEqualTo("a...");
    assertThat(RankReport.RESET).isEqualTo("\033[0m");
    assertThat(RankReport.BOLD).isEqualTo("\033[1m");
    assertThat(RankReport.DIM).isEqualTo("\033[2m");
  }

  @Test
  void defaults_are_those_documented() {
    SonarRank cmd = new SonarRank(Cli.host());
    new CommandLine(cmd).parseArgs("--in", "x.csv");
    assertThat(cmd.out).isEqualTo(Path.of("classement.csv"));
    assertThat(cmd.staleDays).isEqualTo(90);
    assertThat(cmd.abandonedDays).isEqualTo(365);
    assertThat(cmd.minNcloc).isEqualTo(500);
    assertThat(cmd.topPercent).isEqualTo(10);
    assertThat(cmd.minSignals).isEqualTo(2);
    assertThat(cmd.style.comma()).isFalse();
    assertThat(cmd.style.colorMode()).isEqualTo("auto");
  }

  Cli.Result runOn(String content) throws IOException {
    Path in = dir.resolve("in.csv");
    Files.writeString(in, content, StandardCharsets.UTF_8);
    return Cli.run(
        SonarRank::new,
        "--in",
        in.toString(),
        "--out",
        dir.resolve("out.csv").toString(),
        "--color",
        "never");
  }

  @Test
  void an_inventory_with_only_a_header_is_refused() throws IOException {
    Cli.Result r = runOn("key,ncloc,analysisDate\n");

    assertThat(r.exitCode()).isEqualTo(1);
    assertThat(r.out()).isEmpty();
    assertThat(r.err()).isEqualTo("Aucune ligne exploitable dans " + dir.resolve("in.csv") + "\n");
    assertThat(Files.exists(dir.resolve("out.csv"))).isFalse();
  }

  @Test
  void an_empty_file_is_refused_for_its_missing_columns() throws IOException {
    Cli.Result r = runOn("");

    assertThat(r.exitCode()).isEqualTo(1);
    assertThat(r.err()).contains("colonne « key » absente de", "SonarAuditCheck --csv");
  }

  @Test
  void a_file_that_is_not_an_inventory_is_refused() throws IOException {
    Cli.Result r = runOn("key,ncloc\nalpha,10\n");

    assertThat(r.exitCode()).isEqualTo(1);
    assertThat(r.err()).contains("colonne « analysisDate » absente de");
    assertThat(Files.exists(dir.resolve("out.csv"))).isFalse();
  }

  @Test
  void a_missing_input_file_fails() {
    Cli.Result r =
        Cli.run(SonarRank::new, "--in", dir.resolve("nope.csv").toString(), "--color", "never");

    assertThat(r.exitCode()).isEqualTo(1);
    assertThat(r.err()).contains("NoSuchFileException");
  }
}
