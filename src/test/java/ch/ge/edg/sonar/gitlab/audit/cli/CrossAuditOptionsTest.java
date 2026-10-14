package ch.ge.edg.sonar.gitlab.audit.cli;

import static ch.ge.edg.sonar.gitlab.audit.cli.CrossAuditFixtures.GITLAB_HEADER;
import static ch.ge.edg.sonar.gitlab.audit.cli.CrossAuditFixtures.SONAR_HEADER;
import static ch.ge.edg.sonar.gitlab.audit.cli.CrossAuditFixtures.csv;
import static ch.ge.edg.sonar.gitlab.audit.cli.CrossAuditFixtures.read;
import static ch.ge.edg.sonar.gitlab.audit.cli.CrossAuditFixtures.run;
import static ch.ge.edg.sonar.gitlab.audit.cli.CrossAuditFixtures.scrub;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrossAuditOptionsTest {

  @TempDir Path dir;

  private static String[] files(Path dir) throws IOException {
    try (Stream<Path> s = Files.walk(dir)) {
      return s.filter(Files::isRegularFile)
          .map(p -> dir.relativize(p).toString())
          .sorted()
          .toArray(String[]::new);
    }
  }

  /** Three GitLab projects, no cle_sonar column: only names can pair, never surely. */
  private Path gitlabWithoutKeys() throws IOException {
    return csv(
        dir.resolve("pratiques.csv"),
        "path,name,id,commits_window,mr_fusionnees,ci_sonar",
        "team-a/alpha-service,alpha-service,1,30,0,true",
        "team-b/beta-batch,beta-batch,2,3,0,false",
        "team-c/gamma,gamma,3,,,");
  }

  private Path smallSonar() throws IOException {
    return csv(
        dir.resolve("projets.csv"),
        SONAR_HEADER,
        "alpha-service,Alpha Service,2026-01-01,100,,,,,,,,,",
        "other-thing,Other Thing,2026-01-01,100,,,,,,,,,");
  }

  @Test
  void without_cle_sonar_inventory_or_out_it_warns_and_writes_only_the_appariement()
      throws IOException {
    Cli.Result r =
        run(
            "--sonar", smallSonar().toString(),
            "--gitlab", gitlabWithoutKeys().toString(),
            "--color", "never");

    assertThat(r.exitCode()).isZero();
    assertThat(r.err()).isEmpty();
    expectSelfie(scrub(r.out(), dir)).toMatchDisk("stdout");
    assertThat(files(dir)).containsExactly("appariement.csv", "pratiques.csv", "projets.csv");
    expectSelfie(read(dir.resolve("appariement.csv"))).toMatchDisk("appariement");
  }

  @Test
  void every_path_option_and_the_report_with_comma_separated_files() throws IOException {
    Path in = dir.resolve("in");
    Path sonar = CrossAuditFixtures.sonar(in);
    Path gitlab = CrossAuditFixtures.gitlab(in);
    Path inv = CrossAuditFixtures.inventory(dir.resolve("elsewhere"));
    Path res = dir.resolve("res");
    Cli.Result r =
        run(
            "--sonar",
            sonar.toString(),
            "--gitlab",
            gitlab.toString(),
            "--out",
            res.resolve("a").resolve("x.csv").toString(),
            "--inventaire",
            inv.toString(),
            "--sonar-gitlab",
            res.resolve("b").resolve("y.csv").toString(),
            "--appariement",
            res.resolve("c").resolve("z.csv").toString(),
            "--report",
            res.resolve("report.json").toString(),
            "--examples",
            "1",
            "--seuil-nom",
            "0.6",
            "--marge-nom",
            "0.3",
            "--comma",
            "--color",
            "never");

    assertThat(r.exitCode()).isZero();
    assertThat(r.err()).isEmpty();
    expectSelfie(scrub(r.out(), dir)).toMatchDisk("stdout");
    assertThat(files(res)).containsExactly("a/x.csv", "b/y.csv", "c/z.csv", "report.json");
    assertThat(files(in)).containsExactly("pratiques.csv", "projets.csv");
    expectSelfie(read(res.resolve("a/x.csv"))).toMatchDisk("croisement");
    expectSelfie(read(res.resolve("b/y.csv"))).toMatchDisk("sonarGitlab");
    expectSelfie(read(res.resolve("c/z.csv"))).toMatchDisk("appariement");
    expectSelfie(read(res.resolve("report.json"))).toMatchDisk("report");
  }

  @Test
  void without_out_the_sonar_gitlab_file_goes_next_to_pratiques() throws IOException {
    Path sonar = CrossAuditFixtures.sonar(dir);
    Path gitlab = CrossAuditFixtures.gitlab(dir);
    CrossAuditFixtures.inventory(dir);
    Cli.Result r = run("--sonar", sonar.toString(), "--gitlab", gitlab.toString());

    assertThat(r.exitCode()).isZero();
    assertThat(files(dir))
        .containsExactly(
            "appariement.csv",
            "inventaire.csv",
            "pratiques.csv",
            "projets.csv",
            "sonar-gitlab.csv");
    assertThat(r.out())
        .contains("Sonar × tout GitLab : 9 lignes → " + dir.resolve("sonar-gitlab.csv"));
  }

  @Test
  void colors_always_wraps_titles_warnings_and_counts() throws IOException {
    Cli.Result r =
        run(
            "--sonar", smallSonar().toString(),
            "--gitlab", gitlabWithoutKeys().toString(),
            "--out", dir.resolve("c.csv").toString(),
            "--color", "always");

    assertThat(r.exitCode()).isZero();
    expectSelfie(scrub(r.out(), dir)).toMatchDisk("stdout");
  }

  @Test
  void exactly_half_paired_is_not_a_warning_less_than_half_is() throws IOException {
    Path sonar =
        csv(dir.resolve("projets.csv"), SONAR_HEADER, "k1,Zulu Quebec,2026-01-01,1,,,,,,,,,");
    Path half =
        csv(
            dir.resolve("half/pratiques.csv"),
            GITLAB_HEADER,
            "g/one,one,1,,1,0,false,k1,,,,,,",
            "g/two,two,2,,1,0,false,,,,,,,");
    Path less =
        csv(
            dir.resolve("less/pratiques.csv"),
            GITLAB_HEADER,
            "g/one,one,1,,1,0,false,k1,,,,,,",
            "g/two,two,2,,1,0,false,,,,,,,",
            "g/three,three,3,,1,0,false,,,,,,,");
    String warning = "Moins d'un projet sur deux apparié.";
    Cli.Result h =
        run("--sonar", sonar.toString(), "--gitlab", half.toString(), "--color", "never");
    Cli.Result l =
        run("--sonar", sonar.toString(), "--gitlab", less.toString(), "--color", "never");
    assertThat(h.out())
        .doesNotContain(warning)
        .contains("Projets Sonar sans dépôt GitLab apparié : 0");
    assertThat(l.out()).contains(warning);
  }

  @Test
  void an_explicit_inventory_that_does_not_exist_fails() throws IOException {
    Path missing = dir.resolve("nope.csv");
    Cli.Result r =
        run(
            "--sonar", smallSonar().toString(),
            "--gitlab", gitlabWithoutKeys().toString(),
            "--inventaire", missing.toString());
    assertThat(r.exitCode()).isEqualTo(1);
    assertThat(r.err()).contains("java.io.IOException: introuvable : " + missing);
  }

  @Test
  void an_inventory_without_selectionne_is_refused() throws IOException {
    Path inv = csv(dir.resolve("inventaire.csv"), "path,id", "team-a/alpha-service,1");
    Cli.Result r =
        run("--sonar", smallSonar().toString(), "--gitlab", gitlabWithoutKeys().toString());
    assertThat(r.exitCode()).isEqualTo(1);
    assertThat(r.err())
        .contains(
            "colonne « selectionne » absente de "
                + inv.toAbsolutePath()
                + " — ce fichier vient-il bien de GitlabActivityAudit (inventaire.csv) ?");
  }

  @Test
  void a_sonar_file_without_its_columns_is_refused_before_anything_is_written() throws IOException {
    Path sonar = csv(dir.resolve("projets.csv"), "key,name,ncloc", "alpha,Alpha,1");
    Cli.Result r = run("--sonar", sonar.toString(), "--gitlab", gitlabWithoutKeys().toString());
    assertThat(r.exitCode()).isEqualTo(1);
    assertThat(r.out()).isEmpty();
    assertThat(r.err()).contains("colonne « analysisDate » absente de " + sonar);
    assertThat(dir.resolve("appariement.csv")).doesNotExist();
  }

  @Test
  void a_gitlab_file_without_its_columns_is_refused() throws IOException {
    Path gitlab = csv(dir.resolve("pratiques.csv"), "path,name", "team-a/alpha,alpha");
    Cli.Result r = run("--sonar", smallSonar().toString(), "--gitlab", gitlab.toString());
    assertThat(r.exitCode()).isEqualTo(1);
    assertThat(r.err())
        .contains(
            "colonne « commits_window » absente de "
                + gitlab
                + " — ce fichier vient-il bien de GitlabActivityAudit --deep ?");
  }

  @Test
  void both_inputs_are_required() {
    Cli.Result r = run("--sonar", "x.csv");
    assertThat(r.exitCode()).isEqualTo(2);
    assertThat(r.err()).startsWith("Missing required option: '--gitlab=<gitlabCsv>'");
  }

  @Test
  void help_prints_the_custom_synopsis_options_and_footer() {
    Cli.Result r = run("--help");
    assertThat(r.exitCode()).isZero();
    expectSelfie(r.out()).toMatchDisk();
  }
}
