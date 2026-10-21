package ch.ge.edg.sonar.gitlab.audit.cli.rank;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link SonarRank} behaviour believed wrong, each pinned on its own. */
class SonarRankSuspectedBugsTest {

  @TempDir Path dir;

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
  void ellipsizeReturnsAtMostMaxCharacters() {
    String cut = Text.ellipsize("abcdefghij", 6);

    assertThat(cut).isEqualTo("abc...").hasSize(6);
  }

  @Test
  void aProjectWithTooFewSignalsLeavesThePercentilesOfItsStratumAlone() throws IOException {
    rank(
        List.of(
            "full,Full,2000,2026-06-01,3,1000,20,30,50,5,10,A,A,A,OK",
            "few,Few,2000,2026-06-01,3,100,,,,,20,A,A,A,OK"));

    assertThat(velocityRow("few").str("exclusion")).isNotBlank();
    // Alone in its stratum once "few" is excluded, "full" is at the 50th percentile.
    assertThat(velocityRow("full").num("pct_ratio_dette")).isEqualTo(50.0);
  }

  @Test
  void aProjectWithTooFewSignalsDoesNotCountInTheShortlistQuota() throws IOException {
    rank(
        List.of(
            "one,One,2000,2026-06-01,3,1000,20,30,50,5,10,A,A,A,OK",
            "two,Two,2000,2026-06-01,3,500,1,90,80,1,2,A,A,A,OK",
            "few,Few,2000,2026-06-01,3,100,,,,,20,A,A,A,OK"),
        "--top-percent",
        "50");

    // Two ranked projects at 50 % give a quota of 1; "few" is not counted.
    assertThat(List.of(velocityRow("one").str("retenu"), velocityRow("two").str("retenu")))
        .containsExactlyInAnyOrder("O", "N");
  }

  @Test
  void aProjectAnalysedExactlyStaleDaysAgoCountsAsActiveAsItsLabelSays() throws IOException {
    Cli.Result r = rank(List.of("ninety,Ninety,3000,2026-06-01,90,100,1,80,70,0,1,A,A,A,OK"));

    assertThat(r.out()).contains("Actifs (≤ 90 j)                 : 1 (100%)");
    assertThat(r.out()).contains("Analysés, obsolètes (> 90 j)    : 0 (0%)");
  }
}
