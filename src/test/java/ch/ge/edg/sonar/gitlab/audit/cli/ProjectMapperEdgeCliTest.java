package ch.ge.edg.sonar.gitlab.audit.cli;

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

/** Single-method modes and the corners of the evaluation. */
class ProjectMapperEdgeCliTest {

  @TempDir Path dir;

  private Cli.Result map(Path left, Path right, String... extra) {
    String[] base = {
      "--left",
      left.toString(),
      "--right",
      right.toString(),
      "--left-key",
      "nom",
      "--right-key",
      "code",
      "--out",
      dir.resolve("m.csv").toString(),
      "--comma"
    };
    String[] all = new String[base.length + extra.length];
    System.arraycopy(base, 0, all, 0, base.length);
    System.arraycopy(extra, 0, all, base.length, extra.length);
    return run(all);
  }

  @Test
  void only_a_derived_method() throws Exception {
    Path left = write(dir, "a.csv", "nom\nAlpha One\nbeta\n");
    Path right = write(dir, "b.csv", "code\nalpha_one\ngamma\n");

    Cli.Result r = map(left, right, "--derived", "d:nom=code");

    assertThat(r.exitCode()).isZero();
    expectSelfie(Files.readString(dir.resolve("m.csv"))).toMatchDisk();
  }

  @Test
  void only_a_link_method_joins_nothing() throws Exception {
    Path left = write(dir, "a.csv", "nom\ngrp/alpha\n");
    Path right = write(dir, "b.csv", "code,urls\nk1,https://h/grp/alpha\n");

    Cli.Result r = map(left, right, "--link", "l:nom=urls");

    assertThat(r.exitCode()).isZero();
    expectSelfie(stable(r.out(), dir)).toMatchDisk("stdout");
    expectSelfie(Files.readString(dir.resolve("m.csv"))).toMatchDisk("mapping");
  }

  @Test
  void a_right_key_whose_short_name_normalises_to_itself_is_indexed_once() throws Exception {
    Path left = write(dir, "a.csv", "path\nteam-a/alpha\n");
    Path right = write(dir, "b.csv", "key\n-alpha\n");

    Cli.Result r =
        run(
            "--left",
            left.toString(),
            "--right",
            right.toString(),
            "--out",
            dir.resolve("m.csv").toString(),
            "--comma");

    assertThat(r.exitCode()).isZero();
    expectSelfie(Files.readString(dir.resolve("m.csv"))).toMatchDisk();
  }

  @Test
  void evaluation_corners_with_plain_names() throws Exception {
    Path left =
        write(
            dir,
            "a.csv",
            """
            nom,ref
            42,R42
            alpha beta,R5
            orphan words,
            """);
    Path right =
        write(
            dir,
            "b.csv",
            """
            code,libelle,ref_externe
            c42,,R42
            c5,zzzz,R5
            ab1,alpha gamma delta epsilon,
            """);
    Path report = dir.resolve("r.json");

    Cli.Result r =
        map(
            left,
            right,
            "--exact",
            "ref:ref=ref_externe",
            "--names",
            "nom=libelle",
            "--plain-names",
            "--report",
            report.toString());

    assertThat(r.exitCode()).isZero();
    expectSelfie(stable(r.out(), dir)).toMatchDisk("stdout");
    expectSelfie(Files.readString(dir.resolve("m.csv"))).toMatchDisk("mapping");
    expectSelfie(Files.readString(report)).toMatchDisk("report");
  }
}
