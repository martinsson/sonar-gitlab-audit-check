package ch.ge.edg.sonar.gitlab.audit.cli.vues;

import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link Vues} end to end: a synthetic croisement.csv in, three views and a summary out. */
class VuesCliTest {

  @TempDir Path dir;

  /** Every column the views know, plus one they don't, in an order none of them uses. */
  static List<String> fullHeader() {
    Set<String> cols = new LinkedHashSet<>();
    cols.add("zz_extra");
    cols.addAll(Vues.UNPAIRED.reversed());
    cols.addAll(Vues.DRIFT.reversed());
    cols.addAll(Vues.ESSENTIAL_FIRST);
    cols.remove("signaux");
    return new ArrayList<>(cols);
  }

  static String csv(List<String> header, List<Map<String, String>> rows) {
    StringBuilder sb = new StringBuilder(String.join(",", header)).append('\n');
    for (Map<String, String> r : rows) {
      sb.append(String.join(",", header.stream().map(c -> r.getOrDefault(c, "")).toList()))
          .append('\n');
    }
    return sb.toString();
  }

  /** One row as {@code key=value} pairs separated by {@code ;}, an empty value allowed. */
  static Map<String, String> row(String spec) {
    Map<String, String> out = new LinkedHashMap<>();
    for (String kv : spec.split(";", -1)) {
      String[] parts = kv.split("=", 2);
      out.put(parts[0], parts[1]);
    }
    return out;
  }

  static List<Map<String, String>> sampleRows() {
    return List.of(
        row(
            "projet=alpha-service;confiance=exact;gl_commits_window=40;gl_mr_fusionnees=0;"
                + "sq_violations_kloc_pente_pct_mois=1.5;sq_alert_status=ERROR;zz_extra=x1"),
        row(
            "projet=beta-api;confiance=derived;sq_violations_kloc_pente_pct_mois=7;"
                + "gl_commits_window=3;gl_mr_fusionnees=2;gl_ci_sonar=true"),
        row(
            "projet=gamma-ui;confiance=manuel;sq_violations_kloc_pente_pct_mois=3;"
                + "issues_par_kloc_modifie=0.4;sq_new_coverage=20"),
        row("projet=delta-batch;confiance=exact"),
        row(
            "projet=epsilon-lib;confiance=exact;sq_violations_kloc_pente_pct_mois=9;"
                + "gl_ci_securite=false;sq_new_coverage=10"),
        row(
            "projet=team-a/zeta;confiance=;gl_commits_window=12;gl_lignes_modifiees=300;"
                + "candidat_nom=zeta-old;score_nom=0.7"),
        row(
            "projet=team-a/eta;confiance=name;gl_commits_window=12;gl_lignes_modifiees=900;"
                + "gl_cle_sonar=eta-key"),
        row("projet=team-b/theta;confiance=;gl_commits_window=50"),
        row("projet=team-b/iota;confiance=;gl_commits_window=0"),
        row("projet=team-b/kappa;confiance="));
  }

  Path writeInput(String name, String content) throws IOException {
    Path in = dir.resolve(name);
    Files.writeString(in, content, StandardCharsets.UTF_8);
    return in;
  }

  String read(String name) throws IOException {
    return Files.readString(dir.resolve(name), StandardCharsets.UTF_8);
  }

  String normalized(String out) {
    return out.replace(dir.toAbsolutePath().toString(), "<dir>");
  }

  @Test
  void writes_three_excel_views_and_summarizes_them() throws IOException {
    Path in = writeInput("croisement.csv", csv(fullHeader(), sampleRows()));

    Cli.Result r = Cli.run(Vues::new, "--in", in.toString(), "--color", "never");

    assertThat(r.exitCode()).isZero();
    assertThat(r.err()).isEmpty();
    assertThat(normalized(r.out()))
        .isEqualTo(
            """
              Vue essentiel : 10 lignes → <dir>/croisement-essentiel.csv
              Vue dérive    : 5 projets appariés → <dir>/croisement-derive.csv
                Triée par nombre de signaux, puis par pente de la densité d'issues.
              Vue non appariés : 3 projets actifs sans projet Sonar → <dir>/croisement-non-apparies.csv
                Les plus actifs d'abord. Seulement l'échantillon de pratiques.csv : \
            un projet hors sélection n'y est pas.
            """);
    String essential = read("croisement-essentiel.csv");
    assertThat(essential).startsWith("﻿\"projet\";\"confiance\";").contains("\r\n");
    expectSelfie(essential).toMatchDisk("essentiel");
    expectSelfie(read("croisement-derive.csv")).toMatchDisk("derive");
    expectSelfie(read("croisement-non-apparies.csv")).toMatchDisk("non-apparies");
  }

  @Test
  void drift_view_is_ordered_by_signal_count_then_density_slope() throws IOException {
    Path in = writeInput("croisement.csv", csv(fullHeader(), sampleRows()));

    Cli.run(Vues::new, "--in", in.toString(), "--comma", "--color", "never");

    List<String> firstCells =
        read("croisement-derive.csv").lines().map(l -> l.substring(0, l.indexOf(','))).toList();
    assertThat(firstCells)
        .containsExactly(
            "\"projet\"",
            "\"epsilon-lib\"",
            "\"gamma-ui\"",
            "\"alpha-service\"",
            "\"beta-api\"",
            "\"delta-batch\"");
    assertThat(read("croisement-derive.csv"))
        .contains("\"gamma-ui\",\"densité en hausse, modifs ajoutent des issues, code neuf");
  }

  @Test
  void unpaired_view_keeps_active_projects_most_active_first() throws IOException {
    Path in = writeInput("croisement.csv", csv(fullHeader(), sampleRows()));

    Cli.run(Vues::new, "--in", in.toString(), "--comma", "--color", "never");

    assertThat(read("croisement-non-apparies.csv").lines().map(l -> l.split(",", -1)[0]).toList())
        .containsExactly("\"projet\"", "\"team-b/theta\"", "\"team-a/eta\"", "\"team-a/zeta\"");
  }

  @Test
  void comma_mode_writes_plain_csv_without_bom() throws IOException {
    Path in = writeInput("croisement.csv", csv(fullHeader(), sampleRows()));

    Cli.run(Vues::new, "--in", in.toString(), "--comma", "--color", "never");

    expectSelfie(read("croisement-essentiel.csv")).toMatchDisk("essentiel");
    expectSelfie(read("croisement-derive.csv")).toMatchDisk("derive");
    expectSelfie(read("croisement-non-apparies.csv")).toMatchDisk("non-apparies");
  }

  @Test
  void an_older_crossing_without_the_drift_columns_is_read_and_the_gap_said() throws IOException {
    Path in = writeInput("old.csv", "projet;confiance;gl_commits_window\nalpha;exact;3\nbeta;;4\n");

    Cli.Result r = Cli.run(Vues::new, "--in", in.toString(), "--color", "never");

    assertThat(r.exitCode()).isZero();
    expectSelfie(normalized(r.out())).toMatchDisk("stdout");
    expectSelfie(read("old-essentiel.csv")).toMatchDisk("essentiel");
    expectSelfie(read("old-derive.csv")).toMatchDisk("derive");
    expectSelfie(read("old-non-apparies.csv")).toMatchDisk("non-apparies");
  }

  @Test
  void colors_wrap_the_hints_when_asked() throws IOException {
    Path in = writeInput("old.csv", "projet,confiance\nalpha,exact\n");

    Cli.Result r = Cli.run(Vues::new, "--in", in.toString(), "--color", "always");

    assertThat(r.out())
        .contains("\033[2m    Triée par nombre de signaux")
        .contains("\033[33m  Colonnes absentes du croisement, laissées vides : sq_violations")
        .contains("\033[0m");
  }

  @Test
  void a_file_without_the_crossing_columns_is_refused() throws IOException {
    Path in = writeInput("wrong.csv", "key,name\nalpha,Alpha\n");

    Cli.Result r = Cli.run(Vues::new, "--in", in.toString(), "--color", "never");

    assertThat(r.exitCode()).isEqualTo(1);
    assertThat(r.out()).isEmpty();
    assertThat(r.err()).contains("colonne « projet » absente de " + in, "CrossAudit --out");
    assertThat(Files.exists(dir.resolve("wrong-essentiel.csv"))).isFalse();
  }

  @Test
  void the_input_option_is_required() {
    Cli.Result r = Cli.run(Vues::new);

    assertThat(r.exitCode()).isEqualTo(2);
    assertThat(r.err()).contains("Missing required option: '--in=<in>'");
  }
}
