package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import static ch.ge.edg.sonar.gitlab.audit.cli.gocdextract.GocdOnFake.gocd;
import static ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures.fixture;
import static ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures.latestRun;
import static ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures.runOf;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.dora.DeployedCommit;
import ch.ge.edg.sonar.gitlab.audit.dora.JiraKeys;
import ch.ge.edg.sonar.gitlab.audit.dora.RunOutcome;
import ch.ge.edg.sonar.gitlab.audit.gocd.Run;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** Run outcome, gocd-runs.csv and gocd-stages.csv rows. */
class RowsTest {

  private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

  private static Run stages(String... stages) {
    return runOf("{\"stages\":[" + String.join(",", stages) + "]}");
  }

  private static String stage(String result, boolean scheduled) {
    return "{\"result\":\"%s\",\"scheduled\":%s}".formatted(result, scheduled);
  }

  @Test
  void aFailedStageBeatsACancelledOneWhichBeatsOneStillRunning() {
    assertThat(RunOutcome.of(stages(stage("Passed", true), stage("Failed", true))).label())
        .isEqualTo("échec");
    assertThat(RunOutcome.of(stages(stage("Cancelled", true), stage("Failed", true))).label())
        .isEqualTo("échec");
    assertThat(RunOutcome.of(stages(stage("Cancelled", true), stage("Unknown", true))).label())
        .isEqualTo("annulé");
    assertThat(RunOutcome.of(stages(stage("Passed", true), stage("Unknown", true))).label())
        .isEqualTo("en cours");
    assertThat(RunOutcome.of(stages("{\"scheduled\":true}")).label()).isEqualTo("en cours");
    assertThat(RunOutcome.of(stages(stage("Passed", true), "{\"result\":\"Passed\"}")).label())
        .isEqualTo("succès");
    assertThat(RunOutcome.of(stages()).label()).isEqualTo("succès");
  }

  @Test
  void aStageThatNeverRanDoesNotCount() {
    assertThat(RunOutcome.of(stages(stage("Passed", true), stage("Failed", false))).label())
        .isEqualTo("succès");
    assertThat(RunOutcome.of(stages(stage("Passed", true), stage("Unknown", false))).label())
        .isEqualTo("succès");
  }

  @Test
  void theCapturedFailedProdDeployRow() {
    Run run = latestRun("go_api_pipelines_hello_deploy_prod_history_page_size_100");
    String[] cells = RunRow.cells("hello-deploy-prod", "hello", run, List.of(), ZURICH);
    assertThat(cells)
        .containsExactly(
            "hello-deploy-prod",
            "hello",
            "prod",
            "1",
            "1",
            "2026-10-20 22:44:31",
            "forcé",
            "échec",
            "deploy:Failed",
            "",
            "hello-deploy-recette/3/deploy/1",
            "0",
            "",
            "",
            "",
            "");
    assertThat(cells).hasSameSizeAs(RunRow.HEADER);
  }

  @Test
  void aRunRowSpansItsCommitsAndGathersTheirKeys() {
    String json =
        """
        {"counter":4,"label":"4-a","scheduled_date":"2026-01-02T12:00:00Z",
         "build_cause":{"trigger_forced":false,"material_revisions":[
           {"material":{"type":"Git","description":"URL: https://h/g/a.git, Branch: main"}},
           {"material":{"type":"git","description":"URL: https://h/g/a.git, Branch: dev"}},
           {"material":{"type":"Pipeline"},"modifications":[{"revision":"up/3/s/1"},{"revision":"up/2/s/1"}]}]},
         "stages":[{"name":"build","result":"Passed"},{"name":"prod","result":"Unknown","scheduled":false}]}
        """;
    List<DeployedCommit> found =
        List.of(
            new DeployedCommit(
                "up/3", "r", "b", Instant.parse("2026-01-02T08:00:00Z"), Set.of("A-1")),
            new DeployedCommit("up/3", "r", "a", Instant.parse("2026-01-01T06:00:00Z"), Set.of()),
            new DeployedCommit("up/2", "r", "n", null, Set.of("B-2")),
            new DeployedCommit(
                "up/2", "r", "c", Instant.parse("2026-01-02T10:00:00Z"), Set.of("A-1")));
    String[] cells = RunRow.cells("app-deploy-rec", "g", runOf(json), found, ZoneId.of("UTC"));
    assertThat(cells)
        .containsExactly(
            "app-deploy-rec",
            "g",
            "recette",
            "4",
            "4-a",
            "2026-01-02 12:00:00",
            "automatique",
            "succès",
            "build:Passed|prod:NonLancé",
            "https://h/g/a.git",
            "up/3/s/1|up/2/s/1",
            "4",
            "2026-01-01 06:00:00",
            "2026-01-02 10:00:00",
            "30,0",
            "A-1|B-2");
  }

  @Test
  void aSpanWithoutDatesIsEmpty() {
    RunRow.Span s = RunRow.Span.of(List.of(new DeployedCommit("v", "r", "x", null, Set.of())));
    assertThat(s.first()).isNull();
    assertThat(s.last()).isNull();
    assertThat(s.keys()).isEmpty();
    Instant t = Instant.parse("2026-01-01T00:00:00Z");
    RunRow.Span same =
        RunRow.Span.of(
            List.of(
                new DeployedCommit("v", "r", "x", t, Set.of()),
                new DeployedCommit("v", "r", "y", t, Set.of())));
    assertThat(same.first()).isEqualTo(t);
    assertThat(same.last()).isEqualTo(t);
  }

  @Test
  void stageRowsReadTheEndOnlyWithDurationsAndOnlyForStagesThatRan() throws IOException, Stop {
    try (FakeHttpServer server = new FakeHttpServer()) {
      server.json(
          "/go/api/stages/hello-deploy-prod/1/deploy/1",
          fixture("go_api_stages_hello_deploy_prod_1_deploy_1"));
      Run run = latestRun("go_api_pipelines_hello_deploy_prod_history_page_size_100");
      List<String[]> rows = new StageRows(gocd(server), true, ZURICH).of("hello-deploy-prod", run);
      assertThat(rows).hasSize(1);
      assertThat(rows.get(0))
          .containsExactly(
              "hello-deploy-prod",
              "1",
              "deploy",
              "1",
              "prod",
              "O",
              "Failed",
              "manual",
              "N",
              "2026-10-20 22:44:31",
              "2026-10-20 22:44:46",
              "0,2");
      assertThat(rows.get(0)).hasSameSizeAs(StageRows.HEADER);

      List<String[]> quick =
          new StageRows(gocd(server), false, ZURICH).of("hello-deploy-prod", run);
      assertThat(quick.get(0)[10]).isEmpty();
      assertThat(quick.get(0)[11]).isEmpty();
      assertThat(server.requests()).hasSize(1);
    }
  }

  @Test
  void aStageWaitingForApprovalOrRerunAndAnUnreadableStage() throws IOException, Stop {
    try (FakeHttpServer server = new FakeHttpServer()) {
      server.on("/go/api/stages/p%20q/3/build/2", r -> Response.status(500, "{}"));
      String json =
          """
          {"counter":3,"stages":[
            {"name":"build","counter":"2","result":"Passed","approval_type":"success",
             "jobs":[{"scheduled_date":2000},{"scheduled_date":1000},{}]},
            {"name":"deploy-prod","counter":"1","result":"Unknown","scheduled":false,
             "approval_type":"manual","jobs":[]}]}
          """;
      List<String[]> rows =
          new StageRows(gocd(server), true, ZoneId.of("UTC")).of("p q", runOf(json));
      assertThat(rows.get(0))
          .containsExactly(
              "p q",
              "3",
              "build",
              "2",
              "",
              "O",
              "Passed",
              "success",
              "O",
              "1970-01-01 00:00:01",
              "",
              "");
      assertThat(rows.get(1))
          .containsExactly(
              "p q", "3", "deploy-prod", "1", "prod", "N", "", "manual", "N", "", "", "");
      assertThat(server.requests()).hasSize(1);
    }
  }

  @Test
  void aRefusedTokenOnAStageStops() throws IOException {
    try (FakeHttpServer server = new FakeHttpServer()) {
      server.onPrefix("/go/api/stages/", r -> Response.status(401, "{}"));
      Run run = runOf("{\"counter\":1,\"stages\":[{\"name\":\"s\",\"counter\":\"1\"}]}");
      StageRows rows = new StageRows(gocd(server), true, ZURICH);
      Assertions.assertThatThrownBy(() -> rows.of("p", run))
          .isInstanceOf(Stop.class)
          .hasMessage(server.url() + " : jeton refusé (401)");
    }
  }

  @Test
  void minutesHaveOneDecimalAndADecimalComma() {
    Instant t = Instant.parse("2026-01-01T00:00:00Z");
    assertThat(StageRows.minutes(t, t.plusSeconds(90))).isEqualTo("1,5");
    assertThat(StageRows.minutes(t, t.plusSeconds(3))).isEqualTo("0,1");
    assertThat(StageRows.minutes(t, null)).isEmpty();
    assertThat(StageRows.minutes(null, t)).isEmpty();
  }

  @Test
  void aCommitRowWritesItsDateInTheGivenZoneAndItsKeysJoined() {
    String url = "https://github.com/octocat/Hello-World.git";
    DeployedCommit c =
        new DeployedCommit("build/7", url, "c7", Instant.parse("2026-01-02T03:04:05Z"), Set.of());
    assertThat(CommitRow.cells(c, "prod", 2, ZURICH))
        .containsExactly("prod", "2", "build/7", url, "c7", "2026-01-02 04:04:05", "");
    DeployedCommit k = new DeployedCommit("direct", url, "c8", null, JiraKeys.in("BB-1 AA-2"));
    assertThat(CommitRow.cells(k, "p", 1, java.time.ZoneOffset.UTC)[5]).isEmpty();
    assertThat(CommitRow.cells(k, "p", 1, java.time.ZoneOffset.UTC)[6]).isEqualTo("BB-1|AA-2");
  }
}
