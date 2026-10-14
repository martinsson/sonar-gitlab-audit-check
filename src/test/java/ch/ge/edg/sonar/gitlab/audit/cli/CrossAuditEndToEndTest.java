package ch.ge.edg.sonar.gitlab.audit.cli;

import static ch.ge.edg.sonar.gitlab.audit.cli.CrossAuditFixtures.read;
import static ch.ge.edg.sonar.gitlab.audit.cli.CrossAuditFixtures.run;
import static ch.ge.edg.sonar.gitlab.audit.cli.CrossAuditFixtures.scrub;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Golden master of a whole CrossAudit run on the main synthetic scenario. */
class CrossAuditEndToEndTest {

  @TempDir Path dir;

  @Test
  void full_run_with_inventory_writes_croisement_sonar_gitlab_and_appariement() throws IOException {
    Path sonar = CrossAuditFixtures.sonar(dir);
    Path gitlab = CrossAuditFixtures.gitlab(dir);
    CrossAuditFixtures.inventory(dir);
    Path out = dir.resolve("out").resolve("croisement.csv");

    Cli.Result r =
        run(
            "--sonar", sonar.toString(),
            "--gitlab", gitlab.toString(),
            "--out", out.toString(),
            "--color", "never");

    assertThat(r.exitCode()).isZero();
    assertThat(r.err()).isEmpty();
    expectSelfie(scrub(r.out(), dir)).toMatchDisk("stdout");
    expectSelfie(read(out)).toMatchDisk("croisement");
    expectSelfie(read(dir.resolve("out").resolve("sonar-gitlab.csv"))).toMatchDisk("sonarGitlab");
    expectSelfie(read(dir.resolve("appariement.csv"))).toMatchDisk("appariement");
    assertThat(dir.resolve("appariement.csv.bak")).doesNotExist();
    assertThat(Files.list(dir.resolve("out")).count()).isEqualTo(2);
  }
}
