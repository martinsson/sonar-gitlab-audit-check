package ch.ge.edg.sonar.gitlab.audit.cli;

import static ch.ge.edg.sonar.gitlab.audit.cli.CrossAuditFixtures.read;
import static ch.ge.edg.sonar.gitlab.audit.cli.SonarToGitlabFixtures.inventory;
import static ch.ge.edg.sonar.gitlab.audit.cli.SonarToGitlabFixtures.sonar;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.cli.ProjectMapper.Confidence;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarToGitlab.Check;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarToGitlab.Pair;
import ch.ge.edg.sonar.gitlab.audit.console.ConsoleOut;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SonarToGitlabOutputTest {

  @TempDir Path dir;

  private boolean savedColors;

  @BeforeEach
  void colorsOff() {
    savedColors = ConsoleOut.colors();
    ConsoleOut.colorMode("never");
  }

  @AfterEach
  void restoreColors() {
    ConsoleOut.colorMode(savedColors ? "always" : "never");
  }

  private SonarToGitlab.Result result() throws IOException {
    List<Csv.Row> s =
        sonar(
                dir,
                "k1,One,,,2026-01-01,100",
                "k2,Two,,,2026-01-02,200",
                "k3,Three,,,,",
                "k4,Four,,,,",
                "k5,Five,,,,",
                "k6,Six,,,,",
                "k7,Seven,,,,")
            .rows();
    List<Csv.Row> g =
        inventory(
                dir,
                "team-a/shared,shared,1,true,50,2026-01-01,false,false,",
                "team-b/out-active,out-active,2,false,10,2026-01-02,false,false,oui",
                "team-c/out-quiet,out-quiet,3,FALSE,9,2026-01-03,false,false,",
                "team-d/suggested,suggested,4,false,99,2026-01-04,false,false,")
            .rows();
    return new SonarToGitlab.Result(
        List.of(
            new Pair(s.get(0), g.get(0), "croisement : x", Confidence.EXACT, null, "", ""),
            new Pair(s.get(1), g.get(0), "liaison", Confidence.MANUEL, 0.5, "c, d", ""),
            new Pair(s.get(2), g.get(1), "derived", Confidence.DERIVED, 1.0, "", ""),
            new Pair(s.get(3), g.get(2), "derived", Confidence.DERIVED, 0.123, "", ""),
            new Pair(s.get(4), g.get(3), "noms", Confidence.SUGGESTION, 0.987, "z (0.1)", ""),
            new Pair(s.get(5), null, "aucune", Confidence.NONE, null, "a (0.4), b", "ambigu"),
            new Pair(s.get(6), null, "aucune", Confidence.NONE, null, "x", "déjà proposé à k1")),
        List.of(new Check("m1", true), new Check("m1", false), new Check("m0", true)));
  }

  private static String captured(Runnable r) {
    PrintStream old = System.out;
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
    try {
      r.run();
    } finally {
      System.setOut(old);
    }
    return buf.toString(StandardCharsets.UTF_8);
  }

  @Test
  void write_gives_one_row_per_sonar_project_with_its_repository_counts() throws IOException {
    Path file = dir.resolve("deep").resolve("sg.csv");
    SonarToGitlabCsv.write(result(), file, true);
    expectSelfie(read(file)).toMatchDisk();
  }

  @Test
  void write_in_excel_format_by_default() throws IOException {
    Path file = dir.resolve("sg.csv");
    SonarToGitlab.Result r = new SonarToGitlab.Result(List.of(result().pairs().get(5)), List.of());
    SonarToGitlabCsv.write(r, file, false);
    assertThat(read(file))
        .startsWith("﻿\"sonar_key\";\"sonar_name\";\"gitlab_path\"")
        .endsWith(
            "\r\n\"k6\";\"Six\";\"\";\"\";\"aucune\";\"none\";\"\";\"a (0.4), b\";\"ambigu\";\"\";\"\";\"\";\"\";\"\";\"\";\"\"\r\n");
  }

  @Test
  void print_sums_up_by_confidence_outside_selection_reasons_and_checks() throws IOException {
    SonarToGitlab.Result r = result();
    Path file = dir.resolve("sg.csv");
    String out = captured(() -> new SonarToGitlabReport(System.out).print(r, file));
    expectSelfie(CrossAuditFixtures.scrub(out, dir)).toMatchDisk();
  }

  @Test
  void print_of_nothing() {
    SonarToGitlab.Result r = new SonarToGitlab.Result(List.of(), List.of());
    String out =
        captured(() -> new SonarToGitlabReport(System.out).print(r, dir.resolve("sg.csv")));
    expectSelfie(CrossAuditFixtures.scrub(out, dir)).toMatchDisk();
  }

  @Test
  void a_pair_is_joined_only_with_a_repository_and_a_sure_confidence() throws IOException {
    Csv.Row s = sonar(dir, "k,K,,,,").rows().get(0);
    Csv.Row g = inventory(dir, "a/b,b,1,true,1,,false,false,").rows().get(0);
    for (Confidence c : Confidence.values()) {
      boolean sure = c == Confidence.EXACT || c == Confidence.DERIVED || c == Confidence.MANUEL;
      assertThat(new Pair(s, g, "m", c, null, "", "").joined()).as(c.name()).isEqualTo(sure);
      assertThat(new Pair(s, null, "m", c, null, "", "").joined()).as(c.name()).isFalse();
    }
    assertThat(SonarToGitlab.NONE).isEqualTo("aucune");
  }
}
