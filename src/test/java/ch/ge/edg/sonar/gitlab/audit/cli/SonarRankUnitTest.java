package ch.ge.edg.sonar.gitlab.audit.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ch.ge.edg.sonar.gitlab.audit.console.ConsoleOut;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
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
    ConsoleOut.colorMode("never");
  }

  static SonarRank.Row row(String... kv) {
    Map<String, Integer> index = new HashMap<>();
    String[] cells = new String[kv.length / 2];
    for (int i = 0; i < kv.length; i += 2) {
      index.put(kv[i], i / 2);
      cells[i / 2] = kv[i + 1];
    }
    return new SonarRank.Row(new Csv.Row(index, cells));
  }

  @Test
  void percentile_is_the_mean_rank_of_the_value() {
    List<Double> sorted = List.of(1.0, 2.0, 2.0, 4.0);
    assertThat(SonarRank.percentileOf(0.5, sorted)).isZero();
    assertThat(SonarRank.percentileOf(1.0, sorted)).isEqualTo(12.5);
    assertThat(SonarRank.percentileOf(2.0, sorted)).isEqualTo(50.0);
    assertThat(SonarRank.percentileOf(3.0, sorted)).isEqualTo(75.0);
    assertThat(SonarRank.percentileOf(4.0, sorted)).isEqualTo(87.5);
    assertThat(SonarRank.percentileOf(9.0, sorted)).isEqualTo(100.0);
    assertThat(SonarRank.percentileOf(7.0, List.of(7.0))).isEqualTo(50.0);
  }

  @Test
  void buckets_are_half_open_and_anything_unmatched_is_large() {
    assertThat(SonarRank.Bucket.of(0)).isEqualTo(SonarRank.Bucket.XS);
    assertThat(SonarRank.Bucket.of(999)).isEqualTo(SonarRank.Bucket.XS);
    assertThat(SonarRank.Bucket.of(1_000)).isEqualTo(SonarRank.Bucket.S);
    assertThat(SonarRank.Bucket.of(9_999)).isEqualTo(SonarRank.Bucket.S);
    assertThat(SonarRank.Bucket.of(10_000)).isEqualTo(SonarRank.Bucket.M);
    assertThat(SonarRank.Bucket.of(99_999)).isEqualTo(SonarRank.Bucket.M);
    assertThat(SonarRank.Bucket.of(100_000)).isEqualTo(SonarRank.Bucket.L);
    assertThat(SonarRank.Bucket.of(Integer.MAX_VALUE)).isEqualTo(SonarRank.Bucket.L);
    assertThat(SonarRank.Bucket.of(-1)).isEqualTo(SonarRank.Bucket.L);
    assertThat(SonarRank.Bucket.of(Double.NaN)).isEqualTo(SonarRank.Bucket.L);
    assertThat(SonarRank.Bucket.S.label).isEqualTo("1k - 10k");
  }

  @Test
  void signals_point_the_same_way_and_absent_stays_absent() {
    SonarRank.Row full =
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
    assertThat(SonarRank.Signal.INJECTION.severity.apply(full)).isCloseTo(20.0, within(1e-9));
    assertThat(SonarRank.Signal.NEW_COVERAGE.severity.apply(full)).isEqualTo(-70.0);
    assertThat(SonarRank.Signal.NEW_DUPLICATION.severity.apply(full)).isEqualTo(3.5);
    assertThat(SonarRank.Signal.DEBT_RATIO.severity.apply(full)).isEqualTo(1.2);
    assertThat(SonarRank.Signal.DEBT_RATIO.weight).isEqualTo(0.5);
    assertThat(SonarRank.Signal.INJECTION.weight).isEqualTo(1.0);

    SonarRank.Row empty = row();
    for (SonarRank.Signal s : SonarRank.Signal.values()) {
      assertThat(s.severity.apply(empty)).as(s.name()).isNull();
    }
    assertThat(SonarRank.Signal.INJECTION.severity.apply(row("new_violations", "5"))).isNull();
    assertThat(
            SonarRank.Signal.INJECTION.severity.apply(row("new_violations", "5", "new_lines", "0")))
        .isNull();
    assertThat(
            SonarRank.Signal.INJECTION.severity.apply(
                row("new_violations", "5", "new_lines", "-3")))
        .isNull();
  }

  @Test
  void a_row_reads_its_identity_size_and_age() {
    SonarRank.Row r =
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
    ConsoleOut.colorMode("always");
    assertThat(SonarRank.c("x", "")).isEqualTo("x");
    assertThat(SonarRank.c("x", SonarRank.RED)).isEqualTo("\033[31mx\033[0m");
    assertThat(SonarRank.pct(3, 0, "")).isEqualTo("3");
    assertThat(SonarRank.pct(1, 3, "")).isEqualTo("1 (33%)");
    assertThat(SonarRank.pct(2, 3, SonarRank.YELLOW)).isEqualTo("\033[33m2 (67%)\033[0m");
    assertThat(SonarRank.label(row("key", "k", "name", ""))).isEqualTo("k");
    assertThat(SonarRank.label(row("key", "k", "name", "n"))).isEqualTo("n");
    assertThat(SonarRank.truncate("abcd", 4)).isEqualTo("abcd");
    assertThat(SonarRank.truncate("abcde", 4)).isEqualTo("abc...");
    assertThat(SonarRank.RESET).isEqualTo("\033[0m");
    assertThat(SonarRank.BOLD).isEqualTo("\033[1m");
    assertThat(SonarRank.DIM).isEqualTo("\033[2m");
  }

  @Test
  void defaults_are_those_documented() {
    SonarRank cmd = new SonarRank();
    new CommandLine(cmd).parseArgs("--in", "x.csv");
    assertThat(cmd.out).isEqualTo(Path.of("classement.csv"));
    assertThat(cmd.staleDays).isEqualTo(90);
    assertThat(cmd.abandonedDays).isEqualTo(365);
    assertThat(cmd.minNcloc).isEqualTo(500);
    assertThat(cmd.topPercent).isEqualTo(10);
    assertThat(cmd.minSignals).isEqualTo(2);
    assertThat(cmd.comma).isFalse();
    assertThat(cmd.colorMode).isEqualTo("auto");
  }

  Cli.Result runOn(String content) throws IOException {
    Path in = dir.resolve("in.csv");
    Files.writeString(in, content, StandardCharsets.UTF_8);
    return Cli.run(
        new SonarRank(),
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
        Cli.run(new SonarRank(), "--in", dir.resolve("nope.csv").toString(), "--color", "never");

    assertThat(r.exitCode()).isEqualTo(1);
    assertThat(r.err()).contains("NoSuchFileException");
  }
}
