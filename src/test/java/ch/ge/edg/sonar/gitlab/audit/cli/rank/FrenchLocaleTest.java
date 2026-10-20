package ch.ge.edg.sonar.gitlab.audit.cli.rank;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A French default locale changes nothing: numbers are formatted with Locale.ROOT. */
class FrenchLocaleTest {

  @TempDir Path dir;

  private Locale saved = Locale.ROOT;

  @BeforeEach
  void french() {
    saved = Locale.getDefault();
    Locale.setDefault(Locale.FRANCE);
  }

  @AfterEach
  void restore() {
    Locale.setDefault(saved);
  }

  @Test
  void sonarRankWritesScoresWithAPoint() throws IOException {
    Path in = dir.resolve("inventaire.csv");
    Files.writeString(
        in,
        SonarRankCliTest.HEADER + "\n" + String.join("\n", SonarRankCliTest.INVENTORY) + "\n",
        StandardCharsets.UTF_8);
    Path out = dir.resolve("classement.csv");
    String[] args = {"--in", in.toString(), "--out", out.toString(), "--color", "never"};
    Cli.Result r = Cli.run(SonarRank::new, args);

    assertThat(r.exitCode()).isZero();
    assertThat(Files.readString(out, StandardCharsets.UTF_8))
        .containsPattern("\";\"\\d+\\.\\d\";\"")
        .doesNotContainPattern("\";\"\\d+,\\d\";\"");
  }
}
