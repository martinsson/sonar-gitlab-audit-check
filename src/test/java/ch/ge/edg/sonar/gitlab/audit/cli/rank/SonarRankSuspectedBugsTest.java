package ch.ge.edg.sonar.gitlab.audit.cli.rank;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.SuspectedBug;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link SonarRank} behaviour believed wrong, each pinned on its own. */
class SonarRankSuspectedBugsTest {

  @TempDir Path dir;
  private Locale locale;

  @BeforeEach
  void pinLocale() {
    locale = Locale.getDefault();
    Locale.setDefault(Locale.ROOT);
  }

  @AfterEach
  void restoreLocale() {
    Locale.setDefault(locale);
  }

  Cli.Result rank(List<String> rows, String... extra) throws IOException {
    Path in = dir.resolve("inventaire.csv");
    Files.writeString(
        in,
        SonarRankCliTest.HEADER + "\n" + String.join("\n", rows) + "\n",
        StandardCharsets.UTF_8);
    List<String> args =
        new ArrayList<>(
            List.of(
                "--in",
                in.toString(),
                "--out",
                dir.resolve("classement.csv").toString(),
                "--comma",
                "--color",
                "never"));
    args.addAll(List.of(extra));
    return Cli.run(SonarRank::new, args.toArray(String[]::new));
  }

  Csv.Row velocityRow(String key) throws IOException {
    return Csv.read(dir.resolve("classement.csv")).rows().stream()
        .filter(r -> r.str("liste").equals("VELOCITE") && r.str("key").equals(key))
        .findFirst()
        .orElseThrow();
  }

  @Test
  @SuspectedBug("truncate should return at most max characters, not max + 2")
  void truncate_returns_two_characters_more_than_max() {
    String cut = Text.ellipsize("abcdefghij", 6);

    assertThat(cut).isEqualTo("abcde...").hasSize(6 + 2);
  }

  @Test
  @SuspectedBug(
      "a project excluded for too few signals should not count in its stratum's percentiles")
  void a_project_with_too_few_signals_still_lowers_the_percentiles_of_its_stratum()
      throws IOException {
    rank(
        List.of(
            "full,Full,2000,2026-06-01,3,1000,20,30,50,5,10,A,A,A,OK",
            "few,Few,2000,2026-06-01,3,100,,,,,20,A,A,A,OK"));

    assertThat(velocityRow("few").str("exclusion")).isNotBlank();
    // Alone in its stratum, "full" would be at the 50th percentile; "few" pulls it to 25.
    assertThat(velocityRow("full").num("pct_ratio_dette")).isEqualTo(25.0);
  }

  @Test
  @SuspectedBug("a project excluded for too few signals should not count in its stratum's quota")
  void a_project_with_too_few_signals_still_counts_in_the_shortlist_quota() throws IOException {
    rank(
        List.of(
            "one,One,2000,2026-06-01,3,1000,20,30,50,5,10,A,A,A,OK",
            "two,Two,2000,2026-06-01,3,500,1,90,80,1,2,A,A,A,OK",
            "few,Few,2000,2026-06-01,3,100,,,,,20,A,A,A,OK"),
        "--top-percent",
        "50");

    // Two ranked projects at 50 % give a quota of 1; counting "few" makes it round(1.5) = 2.
    assertThat(velocityRow("one").str("retenu")).isEqualTo("O");
    assertThat(velocityRow("two").str("retenu")).isEqualTo("O");
  }

  @Test
  @SuspectedBug("\"Actifs (< 90 j)\" should not include a project analysed exactly 90 days ago")
  void a_project_analysed_exactly_stale_days_ago_counts_as_active() throws IOException {
    Cli.Result r = rank(List.of("ninety,Ninety,3000,2026-06-01,90,100,1,80,70,0,1,A,A,A,OK"));

    assertThat(r.out()).contains("Actifs (< 90 j)                 : 1 (100%)");
    assertThat(r.out()).contains("Analysés, obsolètes (> 90 j)    : 0 (0%)");
  }
}
