package ch.ge.edg.sonar.gitlab.audit.cli.rank;

import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
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

/** {@link SonarRank} end to end: a synthetic inventory in, the report and classement.csv out. */
class SonarRankCliTest {

  static final String HEADER =
      "key,name,ncloc,analysisDate,days_since_analysis,new_lines,new_violations,new_coverage,"
          + "coverage,new_duplicated_lines_density,sqale_debt_ratio,sqale_rating,"
          + "reliability_rating,security_rating,alert_status";

  /** Every cohort, every exclusion, every stratum, ties and partial signals. */
  static final List<String> INVENTORY =
      List.of(
          "never-a,Never A,2000,,,,,,,,,,,,",
          "never-b,,2000,,10,,,,,,,,,,",
          "stale-mid,Stale Mid,5000,2026-01-01,200,,,,,,7.0,B,A,A,OK",
          "stale-worse,Stale Worse,2000,2026-01-01,300,,,,,,12.5,C,A,A,ERROR",
          "stale-old,Stale Old,5000,2025-01-01,400,,,,,,20,,,,",
          "stale-small,Stale Small,100,2026-01-01,100,,,,,,30,,,,",
          "stale-nodebt,Stale Nodebt,5000,2026-01-01,100,,,,,,,,,,",
          "tiny,Tiny,300,2026-06-01,5,50,1,80,70,0,1,,,,",
          "carry-a,Carry A,3000,2026-06-01,10,0,,,60,,4,,,,",
          "carry-b,Carry B,3000,2026-06-01,90,,,,,,9,,,,",
          "carry-none,Carry None,3000,2026-06-01,10,0,,,,,,,,,",
          "few,Few Signals,3000,2026-06-01,10,100,,40,,,,,,,",
          "s-one,S One,2000,2026-06-01,3,1000,20,30,50,5,10,A,A,A,OK",
          "s-two,S Two,4000,2026-06-01,3,500,1,90,80,1,2,A,A,A,OK",
          "s-three,S Three,8000,2026-06-01,12.7,200,4,60,,3,5,A,B,A,ERROR",
          "m-one,,50000,2026-06-01,1,2000,,70,,2,,,,,",
          "l-one,Large project whose display name is longer than the column,200000,"
              + "2026-06-01,1,3000,30,10,20,8,25,E,C,B,ERROR",
          "xs-one,XS One,800,2026-06-01,1,100,0,100,100,0,0,A,A,A,OK");

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

  Path inventory(List<String> rows) throws IOException {
    Path in = dir.resolve("inventaire.csv");
    Files.writeString(in, HEADER + "\n" + String.join("\n", rows) + "\n", StandardCharsets.UTF_8);
    return in;
  }

  Cli.Result rank(List<String> rows, String... extra) throws IOException {
    Path in = inventory(rows);
    List<String> args =
        new ArrayList<>(
            List.of("--in", in.toString(), "--out", dir.resolve("classement.csv").toString()));
    args.addAll(List.of(extra));
    if (!args.contains("--color")) args.addAll(List.of("--color", "never"));
    return Cli.run(SonarRank::new, args.toArray(String[]::new));
  }

  String normalized(String s) {
    return s.replace(dir.toAbsolutePath().toString(), "<dir>");
  }

  String classement() throws IOException {
    return Files.readString(dir.resolve("classement.csv"), StandardCharsets.UTF_8);
  }

  @Test
  void ranks_the_full_inventory_with_default_thresholds() throws IOException {
    Cli.Result r = rank(INVENTORY);

    assertThat(r.exitCode()).isZero();
    assertThat(r.err()).isEmpty();
    expectSelfie(normalized(r.out())).toMatchDisk("stdout");
    String csv = classement();
    assertThat(csv).startsWith("﻿\"liste\";\"rang\";").contains("\r\n");
    expectSelfie(csv).toMatchDisk("csv");
  }

  @Test
  void every_threshold_option_changes_the_ranking() throws IOException {
    Cli.Result r =
        rank(
            INVENTORY,
            "--stale-days",
            "150",
            "--abandoned-days",
            "250",
            "--min-ncloc",
            "2500",
            "--top-percent",
            "50",
            "--min-signals",
            "1",
            "--comma");

    assertThat(r.exitCode()).isZero();
    expectSelfie(normalized(r.out())).toMatchDisk("stdout");
    String csv = classement();
    assertThat(csv).startsWith("\"liste\",\"rang\",").doesNotContain("\r\n");
    expectSelfie(csv).toMatchDisk("csv");
  }

  @Test
  void side_lists_keep_thirty_projects_on_the_shortlist() throws IOException {
    List<String> rows =
        java.util.stream.IntStream.rangeClosed(1, 32)
            .mapToObj(
                i -> "carry-%02d,Carry %02d,3000,2026-06-01,10,0,,,,,%d,,,,".formatted(i, i, i))
            .toList();

    Cli.Result r = rank(rows, "--comma");

    assertThat(r.exitCode()).isZero();
    List<String> lines = classement().lines().toList();
    assertThat(lines).hasSize(65);
    assertThat(lines.get(1)).startsWith("\"VELOCITE\",\"\",\"N\",\"carry-01\"");
    assertThat(lines.get(33)).startsWith("\"DETTE_PORTEE\",\"1\",\"O\",\"carry-32\"");
    assertThat(lines.get(62)).startsWith("\"DETTE_PORTEE\",\"30\",\"O\",\"carry-03\"");
    assertThat(lines.get(63)).startsWith("\"DETTE_PORTEE\",\"31\",\"N\",\"carry-02\"");
    assertThat(lines.get(64)).startsWith("\"DETTE_PORTEE\",\"32\",\"N\",\"carry-01\"");
    expectSelfie(normalized(r.out())).toMatchDisk("stdout");
  }

  @Test
  void an_inventory_without_active_projects_has_nothing_to_rank() throws IOException {
    Cli.Result r = rank(List.of("never-a,Never A,2000,,,,,,,,,,,,"));

    assertThat(r.exitCode()).isZero();
    expectSelfie(normalized(r.out())).toMatchDisk("stdout");
    expectSelfie(classement()).toMatchDisk("csv");
  }

  @Test
  void active_projects_all_excluded_are_listed_as_such() throws IOException {
    Cli.Result r =
        rank(
            List.of(
                "tiny,Tiny,300,2026-06-01,5,50,1,80,70,0,1,,,,",
                "few,Few Signals,3000,2026-06-01,10,100,,40,,,,,,,"));

    assertThat(r.exitCode()).isZero();
    expectSelfie(normalized(r.out())).toMatchDisk("stdout");
  }

  @Test
  void a_full_ranking_on_all_four_signals_has_no_partial_note() throws IOException {
    Cli.Result r =
        rank(
            List.of(
                "s-one,S One,2000,2026-06-01,3,1000,20,30,50,5,10,A,A,A,OK",
                "s-two,S Two,4000,2026-06-01,3,500,1,90,80,1,2,A,A,A,OK"));

    assertThat(r.out()).doesNotContain("sous-ensemble de signaux").contains("Projets classés");
    assertThat(r.out()).doesNotContain("Écartés du classement").doesNotContain("Un projet analysé");
    expectSelfie(normalized(r.out())).toMatchDisk("stdout");
  }

  @Test
  void colors_are_used_when_asked() throws IOException {
    Cli.Result r = rank(INVENTORY, "--color", "always");

    assertThat(r.out())
        .contains("\033[1m1. Gouvernance (constats qui ne demandent aucun score)\033[0m")
        .contains("\033[33m2 (11%)\033[0m")
        .contains("  Actifs (< 90 j)                 : 11 (61%)\n")
        .contains("\033[31m")
        .contains("\033[2m");
  }
}
