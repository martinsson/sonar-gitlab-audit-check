package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import static ch.ge.edg.sonar.gitlab.audit.cli.gocdextract.GocdOnFake.gocd;
import static ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures.fixture;
import static ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures.node;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import com.fasterxml.jackson.databind.JsonNode;
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

  private static JsonNode stages(String... stages) {
    return node("{\"stages\":[" + String.join(",", stages) + "]}");
  }

  private static String stage(String result, boolean scheduled) {
    return "{\"result\":\"%s\",\"scheduled\":%s}".formatted(result, scheduled);
  }

  @Test
  void aFailedStageBeatsACancelledOneWhichBeatsOneStillRunning() {
    assertThat(Outcome.of(stages(stage("Passed", true), stage("Failed", true)))).isEqualTo("échec");
    assertThat(Outcome.of(stages(stage("Cancelled", true), stage("Failed", true))))
        .isEqualTo("échec");
    assertThat(Outcome.of(stages(stage("Cancelled", true), stage("Unknown", true))))
        .isEqualTo("annulé");
    assertThat(Outcome.of(stages(stage("Passed", true), stage("Unknown", true))))
        .isEqualTo("en cours");
    assertThat(Outcome.of(stages("{\"scheduled\":true}"))).isEqualTo("en cours");
    assertThat(Outcome.of(stages(stage("Passed", true), "{\"result\":\"Passed\"}")))
        .isEqualTo("succès");
    assertThat(Outcome.of(stages())).isEqualTo("succès");
  }

  @Test
  void aStageThatNeverRanDoesNotCount() {
    assertThat(Outcome.of(stages(stage("Passed", true), stage("Failed", false))))
        .isEqualTo("succès");
    assertThat(Outcome.of(stages(stage("Passed", true), stage("Unknown", false))))
        .isEqualTo("succès");
  }

  @Test
  void theCapturedFailedProdDeployRow() {
    JsonNode run = node(fixture("go_api_pipelines_hello_deploy_prod_history_page_size_100"));
    String[] cells =
        RunRow.cells("hello-deploy-prod", "hello", run.path("pipelines").get(0), List.of(), ZURICH);
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
    List<Commit> found =
        List.of(
            new Commit("up/3", "r", "b", Instant.parse("2026-01-02T08:00:00Z"), Set.of("A-1")),
            new Commit("up/3", "r", "a", Instant.parse("2026-01-01T06:00:00Z"), Set.of()),
            new Commit("up/2", "r", "n", null, Set.of("B-2")),
            new Commit("up/2", "r", "c", Instant.parse("2026-01-02T10:00:00Z"), Set.of("A-1")));
    String[] cells = RunRow.cells("app-deploy-rec", "g", node(json), found, ZoneId.of("UTC"));
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
    RunRow.Span s = RunRow.Span.of(List.of(new Commit("v", "r", "x", null, Set.of())));
    assertThat(s.first()).isNull();
    assertThat(s.last()).isNull();
    assertThat(s.keys()).isEmpty();
    Instant t = Instant.parse("2026-01-01T00:00:00Z");
    RunRow.Span same =
        RunRow.Span.of(
            List.of(
                new Commit("v", "r", "x", t, Set.of()), new Commit("v", "r", "y", t, Set.of())));
    assertThat(same.first()).isEqualTo(t);
    assertThat(same.last()).isEqualTo(t);
  }

  @Test
  void stageRowsReadTheEndOnlyWithDurationsAndOnlyForStagesThatRan() throws IOException, Stop {
    try (FakeHttpServer server = new FakeHttpServer()) {
      server.json(
          "/go/api/stages/hello-deploy-prod/1/deploy/1",
          fixture("go_api_stages_hello_deploy_prod_1_deploy_1"));
      JsonNode run =
          node(fixture("go_api_pipelines_hello_deploy_prod_history_page_size_100"))
              .path("pipelines")
              .get(0);
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
          new StageRows(gocd(server), true, ZoneId.of("UTC")).of("p q", node(json));
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
      JsonNode run = node("{\"counter\":1,\"stages\":[{\"name\":\"s\",\"counter\":\"1\"}]}");
      StageRows rows = new StageRows(gocd(server), true, ZURICH);
      Assertions.assertThatThrownBy(() -> rows.of("p", run))
          .isInstanceOf(Stop.class)
          .hasMessage(server.url() + " : jeton refusé (401)");
    }
  }

  @Test
  void theEndIsTheLastCompletedTransitionOfAnyJob() {
    JsonNode stage =
        node(
            """
            {"jobs":[
              {"job_state_transitions":[{"state":"Completed","state_change_time":3000},
                                        {"state":"Building","state_change_time":9000}]},
              {"job_state_transitions":[{"state":"Completed","state_change_time":5000},
                                        {"state":"Completed","state_change_time":4000},
                                        {"state":"Completed"}]}]}
            """);
    assertThat(StageRows.completed(stage)).isEqualTo(Instant.ofEpochMilli(5000));
    assertThat(StageRows.completed(node("{}"))).isNull();
    assertThat(StageRows.start(node("{}"))).isNull();
  }

  @Test
  void minutesHaveOneDecimalAndADecimalComma() {
    Instant t = Instant.parse("2026-01-01T00:00:00Z");
    assertThat(StageRows.minutes(t, t.plusSeconds(90))).isEqualTo("1,5");
    assertThat(StageRows.minutes(t, t.plusSeconds(3))).isEqualTo("0,1");
    assertThat(StageRows.minutes(t, null)).isEmpty();
    assertThat(StageRows.minutes(null, t)).isEmpty();
  }
}
