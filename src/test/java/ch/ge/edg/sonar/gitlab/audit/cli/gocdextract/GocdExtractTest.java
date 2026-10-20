package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** GocdExtract end to end, on the GoCD 25.3 captures and on hand-made runs. */
class GocdExtractTest {

  private static final Clock DAY_AFTER =
      Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"), ZoneOffset.UTC);
  private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

  @TempDir Path dir;

  private Cli.Result extract(FakeHttpServer gocd, String... args) {
    List<String> all =
        new java.util.ArrayList<>(
            List.of("--url", gocd.url(), "--token", "pat", "--out-dir", dir.toString()));
    all.addAll(List.of(args));
    return Cli.run(DAY_AFTER.withZone(ZURICH), GocdExtract::new, all.toArray(String[]::new));
  }

  /** The file without BOM, quotes or CRLF: what Excel shows. */
  private List<String> lines(String file) throws IOException {
    String text = Files.readString(dir.resolve(file), StandardCharsets.UTF_8);
    assertThat(text).startsWith("﻿").contains("\r\n");
    return text.substring(1).replace("\"", "").lines().toList();
  }

  @Test
  void aGroupOnTheCapturedGocdGivesWhatTheJbangPrototypeGave() throws IOException {
    try (FakeHttpServer gocd = GocdFixtures.serve(new FakeHttpServer())) {
      Cli.Result r = extract(gocd, "--group", "hello", "--durations");
      assertThat(r.exitCode()).isZero();
      assertThat(lines("gocd-runs.csv"))
          .containsExactly(
              "pipeline;groupe;plateforme_supposee;compteur;label;planifie_le;declenchement;resultat;stages;depots_git;amont;nb_commits;premier_commit_le;dernier_commit_le;delai_premier_commit_h;cles_jira",
              "hello-build;hello;;3;3;2026-10-20 22:43:26;forcé;succès;build:Passed;https://github.com/octocat/Hello-World.git;;0;;;;",
              "hello-build;hello;;2;2;2026-10-20 22:42:51;forcé;succès;build:Passed;https://github.com/octocat/Hello-World.git;;0;;;;",
              "hello-build;hello;;1;1;2026-10-20 22:42:16;forcé;succès;build:Passed;https://github.com/octocat/Hello-World.git;;1;2012-03-07 00:06:50;2012-03-07 00:06:50;128181,6;",
              "hello-deploy-recette;hello;recette;3;3;2026-10-20 22:43:39;automatique;succès;deploy:Passed;;hello-build/3/build/1;0;;;;",
              "hello-deploy-recette;hello;recette;2;2;2026-10-20 22:43:09;automatique;succès;deploy:Passed;;hello-build/2/build/1;0;;;;",
              "hello-deploy-recette;hello;recette;1;1;2026-10-20 22:42:39;automatique;succès;deploy:Passed;;hello-build/1/build/1;1;2012-03-07 00:06:50;2012-03-07 00:06:50;128181,6;",
              "hello-deploy-prod;hello;prod;1;1;2026-10-20 22:44:31;forcé;échec;deploy:Failed;;hello-deploy-recette/3/deploy/1;0;;;;");
      assertThat(lines("gocd-commits.csv"))
          .containsExactly(
              "pipeline;compteur;via;depot;revision;commit_le;cles_jira",
              "hello-build;1;direct;https://github.com/octocat/Hello-World.git;7fd1a60b01f91b314f59955a4e4d4e80d8edf11d;2012-03-07 00:06:50;",
              "hello-deploy-recette;1;hello-build/1;https://github.com/octocat/Hello-World.git;7fd1a60b01f91b314f59955a4e4d4e80d8edf11d;2012-03-07 00:06:50;");
      assertThat(lines("gocd-stages.csv"))
          .hasSize(8)
          .contains(
              "hello-build;3;build;1;;O;Passed;success;N;2026-10-20 22:43:26;2026-10-20 22:43:38;0,2",
              "hello-build;2;build;1;;O;Passed;success;N;2026-10-20 22:42:51;;",
              "hello-deploy-prod;1;deploy;1;prod;O;Failed;manual;N;2026-10-20 22:44:31;2026-10-20 22:44:46;0,2");
      assertThat(Files.readAllLines(dir.resolve("gocd-runs.jsonl"))).hasSize(7);
      assertThat(r.err())
          .contains("hello-build : 3 runs", "hello-deploy-prod : 1 runs")
          .contains(dir + " : 7 runs, 7 stages, 2 commits")
          .contains("11 requêtes GET.");
      assertThat(gocd.requests().get(1).header("Authorization")).isEqualTo("Bearer pat");
    }
  }

  @Test
  void aDeployAloneReadsItsUpstreamRunsOneByOne() throws IOException {
    try (FakeHttpServer gocd = GocdFixtures.serve(new FakeHttpServer())) {
      Cli.Result r = extract(gocd, "--pipeline", "hello-deploy-recette");
      assertThat(r.exitCode()).isZero();
      assertThat(lines("gocd-commits.csv"))
          .containsExactly(
              "pipeline;compteur;via;depot;revision;commit_le;cles_jira",
              "hello-deploy-recette;1;hello-build/1;https://github.com/octocat/Hello-World.git;7fd1a60b01f91b314f59955a4e4d4e80d8edf11d;2012-03-07 00:06:50;");
      assertThat(gocd.requests())
          .extracting(FakeHttpServer.Request::path)
          .containsExactly(
              "/go/api/dashboard",
              "/go/api/pipelines/hello-deploy-recette/history",
              "/go/api/pipelines/hello-build/3",
              "/go/api/pipelines/hello-build/2",
              "/go/api/pipelines/hello-build/1");
      assertThat(r.err()).contains("5 requêtes GET.");
    }
  }

  @Test
  void theUpstreamBudgetHoldsForTheCapturedProdDeploy() throws IOException {
    try (FakeHttpServer gocd = GocdFixtures.serve(new FakeHttpServer())) {
      assertThat(extract(gocd, "--pipeline", "hello-deploy-prod").exitCode()).isZero();
      assertThat(gocd.requests())
          .extracting(FakeHttpServer.Request::path)
          .contains(
              "/go/api/pipelines/hello-deploy-recette/3",
              "/go/api/pipelines/hello-deploy-recette/2",
              "/go/api/pipelines/hello-build/3");
    }
    try (FakeHttpServer gocd = GocdFixtures.serve(new FakeHttpServer())) {
      assertThat(extract(gocd, "--pipeline", "hello-deploy-prod", "--max-upstream", "1").exitCode())
          .isZero();
      assertThat(gocd.requests())
          .extracting(FakeHttpServer.Request::path)
          .containsExactly(
              "/go/api/dashboard",
              "/go/api/pipelines/hello-deploy-prod/history",
              "/go/api/pipelines/hello-deploy-recette/3");
    }
  }

  @Test
  void theRangeRuleEndToEndOnHandMadeRuns() throws IOException {
    try (FakeHttpServer gocd = new FakeHttpServer()) {
      GocdFixtures.chain(gocd);
      gocd.json("/go/api/dashboard", GocdFixtures.dashboard("g", "prod"));
      Cli.Result r = extract(gocd, "--pipeline", "prod", "--days", "40000", "--no-raw", "--comma");
      assertThat(r.exitCode()).isZero();
      String commits = Files.readString(dir.resolve("gocd-commits.csv"), StandardCharsets.UTF_8);
      assertThat(commits.replace("\"", "").lines())
          .containsExactly(
              "pipeline,compteur,via,depot,revision,commit_le,cles_jira",
              "prod,2,build/7," + GocdFixtures.HELLO + ",c7,2023-11-14 23:13:20,ABC-7",
              "prod,2,build/9," + GocdFixtures.HELLO + ",c9,2023-11-14 23:13:20,XYZ-9|ABC-7",
              "prod,1,build/6," + GocdFixtures.HELLO + ",c6,2023-11-14 23:13:20,ABC-6");
      assertThat(commits).doesNotStartWith("﻿");
      String runs = Files.readString(dir.resolve("gocd-runs.csv"), StandardCharsets.UTF_8);
      assertThat(runs.lines().skip(1).findFirst().orElseThrow().replace("\"", ""))
          .endsWith(",recette/5/s/1,2,2023-11-14 23:13:20,2023-11-14 23:13:20,,ABC-7|XYZ-9");
      assertThat(dir.resolve("gocd-runs.jsonl")).doesNotExist();
    }
  }

  @Test
  void runsOlderThanTheWindowAreLeftOut() throws IOException {
    try (FakeHttpServer gocd = GocdFixtures.serve(new FakeHttpServer())) {
      Cli.Result r = extract(gocd, "--group", "hello", "--days", "0");
      assertThat(r.exitCode()).isZero();
      assertThat(r.err())
          .contains("hello-build : 0 runs")
          .contains(" : 0 runs, 0 stages, 0 commits");
      assertThat(lines("gocd-runs.csv")).hasSize(1);
    }
  }

  @Test
  void nothingToExtractStopsWithExit2() {
    try (FakeHttpServer gocd = GocdFixtures.serve(new FakeHttpServer())) {
      Cli.Result r = extract(gocd, "--group", "nobody");
      assertThat(r.exitCode()).isEqualTo(2);
      assertThat(r.err())
          .contains("Groupe invisible ou inconnu : nobody")
          .contains("Arrêt : aucun pipeline : donner --pipeline ou --group");
    }
  }

  @Test
  void aRefusedTokenStopsWithExit2() {
    try (FakeHttpServer gocd = new FakeHttpServer()) {
      gocd.onPrefix("/go/", req -> Response.status(401, "{}"));
      Cli.Result r = extract(gocd, "--group", "hello");
      assertThat(r.exitCode()).isEqualTo(2);
      assertThat(r.err()).contains("Arrêt : " + gocd.url() + " : jeton refusé (401)");
    }
  }

  @Test
  void aMissingUrlStopsWithExit2() {
    Cli.Result r = Cli.run(GocdExtract::new, "--url", "", "--token", "t", "--pipeline", "p");
    assertThat(r.exitCode()).isEqualTo(2);
    assertThat(r.err()).contains("Arrêt : GOCD_URL manquant");
  }
}
