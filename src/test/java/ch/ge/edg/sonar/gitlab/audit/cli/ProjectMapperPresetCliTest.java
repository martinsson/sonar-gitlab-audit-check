package ch.ge.edg.sonar.gitlab.audit.cli;

import static ch.ge.edg.sonar.gitlab.audit.cli.ProjectMapperFixtures.PRESET_LEFT;
import static ch.ge.edg.sonar.gitlab.audit.cli.ProjectMapperFixtures.PRESET_RIGHT;
import static ch.ge.edg.sonar.gitlab.audit.cli.ProjectMapperFixtures.excelCsv;
import static ch.ge.edg.sonar.gitlab.audit.cli.ProjectMapperFixtures.run;
import static ch.ge.edg.sonar.gitlab.audit.cli.ProjectMapperFixtures.stable;
import static ch.ge.edg.sonar.gitlab.audit.cli.ProjectMapperFixtures.write;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Sonar × GitLab preset end to end: stdout, the mapping CSV and the JSON report. */
class ProjectMapperPresetCliTest {

  @TempDir Path dir;

  @Test
  void preset_maps_with_every_method_and_writes_mapping_and_report() throws Exception {
    Path left = write(dir, "pratiques.csv", PRESET_LEFT);
    Path right = write(dir, "inventaire.csv", PRESET_RIGHT);
    Path out = dir.resolve("sub/correspondances.csv");
    Path report = dir.resolve("sub/rapport.json");

    Cli.Result r =
        run(
            "--left", left.toString(),
            "--right", right.toString(),
            "--out", out.toString(),
            "--report", report.toString());

    assertThat(r.exitCode()).isZero();
    assertThat(r.err()).isEmpty();
    expectSelfie(stable(r.out(), dir)).toMatchDisk("stdout");
    expectSelfie(excelCsv(out)).toMatchDisk("mapping");
    expectSelfie(Files.readString(report)).toMatchDisk("report");
    assertThat(dir.resolve("sub/correspondances.csv.bak")).doesNotExist();
  }

  @Test
  void preset_without_outputs_only_prints() throws Exception {
    Path left = write(dir, "pratiques.csv", PRESET_LEFT);
    Path right = write(dir, "inventaire.csv", PRESET_RIGHT);

    Cli.Result r = run("--left", left.toString(), "--right", right.toString(), "--examples", "1");

    assertThat(r.exitCode()).isZero();
    expectSelfie(stable(r.out(), dir)).toMatchDisk();
    try (var files = Files.list(dir)) {
      assertThat(files).hasSize(2);
    }
  }

  @Test
  void zero_examples_lists_no_example_anywhere() throws Exception {
    Path left = write(dir, "pratiques.csv", PRESET_LEFT);
    Path right = write(dir, "inventaire.csv", PRESET_RIGHT);
    Path report = dir.resolve("r.json");

    Cli.Result r =
        run(
            "--left", left.toString(),
            "--right", right.toString(),
            "--examples", "0",
            "--report", report.toString());

    assertThat(r.exitCode()).isZero();
    expectSelfie(stable(r.out(), dir)).toMatchDisk("stdout");
    expectSelfie(Files.readString(report)).toMatchDisk("report");
  }
}
