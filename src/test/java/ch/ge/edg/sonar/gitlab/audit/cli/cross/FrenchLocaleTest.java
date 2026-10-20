package ch.ge.edg.sonar.gitlab.audit.cli.cross;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.io.IOException;
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
  void crossAuditPrintsTheIssueSlopeWithAPoint() throws IOException {
    Path sonar = CrossAuditFixtures.sonar(dir);
    Path gitlab = CrossAuditFixtures.gitlab(dir);
    CrossAuditFixtures.inventory(dir);
    String[] args = {
      "--sonar", sonar.toString(),
      "--gitlab", gitlab.toString(),
      "--out", dir.resolve("croisement.csv").toString(),
      "--color", "never"
    };
    Cli.Result r = Cli.run(CrossAudit::new, args);

    assertThat(r.out()).contains("team-a/alpha-service — +18.0 issues / kLOC modifié");
  }
}
