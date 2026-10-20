package ch.ge.edg.sonar.gitlab.audit.cli.mapper;

import static ch.ge.edg.sonar.gitlab.audit.cli.mapper.ProjectMapperFixtures.run;
import static ch.ge.edg.sonar.gitlab.audit.cli.mapper.ProjectMapperFixtures.stable;
import static ch.ge.edg.sonar.gitlab.audit.cli.mapper.ProjectMapperFixtures.write;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Methods given on the command line instead of the preset, with every tuning option. */
class ProjectMapperCustomCliTest {

  static final String LEFT =
      """
      nom;ref;url
      Alpha Billing;R1;
      beta-reports;R2;X/Beta-Reports
      gamma_store;;
      delta-hub;R4;
      epsilon;;x/epsilon
      Zeta Portal V2;;
      theta;R7;
      """;

  static final String RIGHT =
      """
      code;libelle;ref_externe;kind;urls
      alpha-billing;Alpha Billing;R1;main;
      beta;Reports Beta;R2;fork;
      beta-legacy;Beta;R2;main;git@git.example.test:x/beta-reports.git
      gamma-store;Gamma Store;;;
      hub-one;Delta Hub;R4;main;
      hub-two;Delta Hub Two;R4;main;
      eps;Epsilon Service;;;https://git.example.test/x/epsilon
      zeta2;Zeta Portal;;;
      theta;Theta;R9;main;
      """;

  static final String[] METHODS = {
    "--left-key", "nom",
    "--right-key", "code",
    "--exact", "ref:ref=ref_externe?kind=main",
    "--derived", "nd:nom=code",
    "--link", "url:url=urls",
  };

  @TempDir Path dir;

  private String[] args(Path left, Path right, String... extra) {
    String[] base = {"--left", left.toString(), "--right", right.toString()};
    String[] all = new String[base.length + METHODS.length + extra.length];
    System.arraycopy(base, 0, all, 0, base.length);
    System.arraycopy(METHODS, 0, all, base.length, METHODS.length);
    System.arraycopy(extra, 0, all, base.length + METHODS.length, extra.length);
    return all;
  }

  @Test
  void custom_methods_with_plain_names_comma_output_and_tuned_thresholds() throws Exception {
    Path left = write(dir, "a.csv", LEFT);
    Path right = write(dir, "b.csv", RIGHT);
    Path out = dir.resolve("map.csv");
    Path report = dir.resolve("rep.json");

    Cli.Result r =
        run(
            args(
                left,
                right,
                "--names",
                "nom=libelle, code",
                "--plain-names",
                "--seuil-nom",
                "0.4",
                "--marge-nom",
                "0.05",
                "--comma",
                "--examples",
                "2",
                "--out",
                out.toString(),
                "--report",
                report.toString()));

    assertThat(r.exitCode()).isZero();
    expectSelfie(stable(r.out(), dir)).toMatchDisk("stdout");
    expectSelfie(Files.readString(out)).toMatchDisk("mapping");
    expectSelfie(Files.readString(report)).toMatchDisk("report");
  }

  @Test
  void names_as_paths_score_the_last_segment_double() throws Exception {
    Path left = write(dir, "a.csv", "nom\ngrp/alpha-billing\nbilling/other\n");
    Path right = write(dir, "b.csv", "code;libelle\nx1;alpha billing\nx2;billing other\n");

    Cli.Result r =
        run(
            "--left",
            left.toString(),
            "--right",
            right.toString(),
            "--left-key",
            "nom",
            "--right-key",
            "code",
            "--names",
            "nom=libelle",
            "--out",
            dir.resolve("m.csv").toString(),
            "--comma");

    assertThat(r.exitCode()).isZero();
    expectSelfie(Files.readString(dir.resolve("m.csv"))).toMatchDisk();
  }

  @Test
  void methods_and_names_on_missing_columns_are_reported_unavailable() throws Exception {
    Path left = write(dir, "a.csv", "nom\nalpha\n");
    Path right = write(dir, "b.csv", "code\nalpha\n");

    Cli.Result r =
        run(
            "--left", left.toString(),
            "--right", right.toString(),
            "--left-key", "nom",
            "--right-key", "code",
            "--exact", "ref:ref=ref_externe?kind=main",
            "--derived", "nd:nom=code",
            "--names", "absent=libelle");

    assertThat(r.exitCode()).isZero();
    expectSelfie(stable(r.out(), dir)).toMatchDisk();
  }

  @Test
  void names_whose_right_columns_are_all_missing_are_unavailable() throws Exception {
    Path left = write(dir, "a.csv", "nom\nalpha\n");
    Path right = write(dir, "b.csv", "code\nalpha\n");

    Cli.Result r =
        run(
            "--left",
            left.toString(),
            "--right",
            right.toString(),
            "--left-key",
            "nom",
            "--right-key",
            "code",
            "--names",
            "nom=libelle,autre");

    assertThat(r.exitCode()).isZero();
    assertThat(r.out()).contains("  Non évaluée : noms ressemblants (TF-IDF) (colonne absente)\n");
  }

  @Test
  void many_disagreements_print_five_and_count_the_rest() throws Exception {
    StringBuilder l = new StringBuilder("nom,ref\n");
    StringBuilder rt = new StringBuilder("code,ref_externe,kind\n");
    for (int i = 0; i < 7; i++) {
      l.append("item-").append(i).append(",R").append(i).append('\n');
      rt.append("item-").append(i).append(",,\n");
      rt.append("other-").append(i).append(",R").append(i).append(",main\n");
    }
    Path left = write(dir, "a.csv", l.toString());
    Path right = write(dir, "b.csv", rt.toString());

    Cli.Result r = run(args(left, right, "--examples", "6"));

    assertThat(r.exitCode()).isZero();
    expectSelfie(stable(r.out(), dir)).toMatchDisk();
  }

  @Test
  void the_preset_keys_can_be_overridden() throws Exception {
    Path left = write(dir, "a.csv", "path,cle_sonar\nteam-a/alpha,Alpha\n");
    Path right = write(dir, "b.csv", "key,name\nk1,Alpha\n");

    Cli.Result r =
        run(
            "--left",
            left.toString(),
            "--right",
            right.toString(),
            "--left-key",
            "cle_sonar",
            "--right-key",
            " ",
            "--out",
            dir.resolve("m.csv").toString(),
            "--comma");

    assertThat(r.exitCode()).isZero();
    expectSelfie(stable(r.out(), dir)).toMatchDisk("stdout");
    expectSelfie(Files.readString(dir.resolve("m.csv"))).toMatchDisk("mapping");
  }

  @Test
  void only_a_right_key_override_keeps_the_preset_left_key() throws Exception {
    Path left = write(dir, "a.csv", "path\nteam-a/alpha\n");
    Path right = write(dir, "b.csv", "code,key\nteam-a-alpha,k\n");

    Cli.Result r =
        run(
            "--left",
            left.toString(),
            "--right",
            right.toString(),
            "--right-key",
            "code",
            "--out",
            dir.resolve("m.csv").toString(),
            "--comma");

    assertThat(r.exitCode()).isZero();
    expectSelfie(Files.readString(dir.resolve("m.csv"))).toMatchDisk();
  }

  @Test
  void empty_inputs_give_an_empty_report() throws Exception {
    Path left = write(dir, "a.csv", "path\n");
    Path right = write(dir, "b.csv", "key\n");

    Cli.Result r =
        run(
            "--left",
            left.toString(),
            "--right",
            right.toString(),
            "--report",
            dir.resolve("r.json").toString());

    assertThat(r.exitCode()).isZero();
    expectSelfie(stable(r.out(), dir)).toMatchDisk("stdout");
    expectSelfie(Files.readString(dir.resolve("r.json"))).toMatchDisk("report");
  }
}
