package ch.ge.edg.sonar.gitlab.audit.gocd;

import static ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures.fixture;
import static ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures.node;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.MissingNode;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** GoCD responses read into records: the captured shapes, and what absent or odd fields give. */
class GocdRecordsTest {

  @Test
  void theCapturedDashboardGivesEachPipelineItsGroupInDashboardOrder() {
    Map<String, String> groupOf = Dashboard.of(node(fixture("go_api_dashboard"))).groupOf();
    assertThat(groupOf)
        .containsExactly(
            Map.entry("other-build", "other"),
            Map.entry("hello-build", "hello"),
            Map.entry("hello-deploy-recette", "hello"),
            Map.entry("hello-deploy-prod", "hello"));
    assertThat(Dashboard.of(node("{}")).groupOf()).isEmpty();
  }

  @Test
  void theCapturedRunKeepsWhatTheToolReads() {
    Run run = Run.of(node(fixture("go_api_pipelines_hello_build_3")));
    assertThat(run.counter()).isEqualTo(3);
    assertThat(run.label()).isEqualTo("3");
    assertThat(run.scheduledDate()).isEqualTo(Instant.ofEpochMilli(1792529006376L));
    assertThat(run.buildCause().triggerForced()).isTrue();
    Run.MaterialRevision mr = run.buildCause().materialRevisions().get(0);
    assertThat(mr.changed()).isFalse();
    assertThat(mr.material().gitUrl()).isEqualTo("https://github.com/octocat/Hello-World.git");
    assertThat(mr.modifications().get(0).modifiedTime())
        .isEqualTo(Instant.ofEpochMilli(1331075210000L));
    Stage stage = run.stages().get(0);
    assertThat(stage.name()).isEqualTo("build");
    assertThat(stage.counter()).isEqualTo(1);
    assertThat(stage.approvalType()).isEqualTo("success");
    assertThat(stage.start()).isEqualTo(Instant.ofEpochMilli(1792529006376L));
  }

  @Test
  void absentOrNullFieldsGiveEmptyTextsListsAndObjects() {
    Run run = Run.of(node("{\"label\":null,\"build_cause\":{\"material_revisions\":[{}]}}"));
    assertThat(run.counter()).isZero();
    assertThat(run.label()).isEmpty();
    assertThat(run.scheduledDate()).isNull();
    assertThat(run.stages()).isEmpty();
    Run.MaterialRevision mr = run.buildCause().materialRevisions().get(0);
    assertThat(mr.material().isGit()).isFalse();
    assertThat(mr.modifications()).isEmpty();
    assertThat(Run.of(MissingNode.getInstance()).buildCause().materialRevisions()).isEmpty();
    Stage stage = Stage.of(node("{\"jobs\":[{}]}"));
    assertThat(stage.counter()).isEqualTo(1);
    assertThat(stage.launched()).isTrue();
    assertThat(stage.start()).isNull();
    assertThat(stage.completed()).isNull();
  }

  @Test
  void datesAreEpochMillisOrIsoAndAnUnreadableOneIsNull() {
    assertThat(Run.of(node("{\"scheduled_date\":\"2026-01-02T12:00:00+01:00\"}")).scheduledDate())
        .isEqualTo(Instant.parse("2026-01-02T11:00:00Z"));
    assertThat(Run.of(node("{\"scheduled_date\":\"hier\"}")).scheduledDate()).isNull();
    assertThat(Run.of(node("{\"scheduled_date\":\" \"}")).scheduledDate()).isNull();
  }

  @Test
  void aFieldOfTheWrongShapeStopsRatherThanReadsWrong() {
    assertThatThrownBy(() -> Run.of(node("{\"counter\":\"douze\"}")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("Réponse GoCD inattendue pour Run");
  }

  @Test
  void aStageApprovalOrRerunAndItsEnd() {
    assertThat(Stage.of(node("{\"scheduled\":false}")).launched()).isFalse();
    assertThat(Stage.of(node("{\"counter\":\"2\"}")).counter()).isEqualTo(2);
    Stage stage =
        Stage.of(
            node(
                """
                {"jobs":[
                  {"job_state_transitions":[{"state":"Completed","state_change_time":3000},
                                            {"state":"Building","state_change_time":9000}]},
                  {"job_state_transitions":[{"state":"Completed","state_change_time":5000},
                                            {"state":"Completed","state_change_time":4000},
                                            {"state":"Completed"}]}]}
                """));
    assertThat(stage.completed()).isEqualTo(Instant.ofEpochMilli(5000));
  }

  @Test
  void aHistoryPageEndsWithoutNextLinkOrWithoutRuns() {
    assertThat(HistoryPage.of(node("{\"pipelines\":[{}]}")).next()).isNull();
    assertThat(HistoryPage.of(node("{\"_links\":{\"next\":{}},\"pipelines\":[{}]}")).next())
        .isNull();
    assertThat(HistoryPage.of(node("{\"_links\":{\"next\":{\"href\":\"/n\"}}}")).next()).isNull();
    assertThat(
            HistoryPage.of(node("{\"_links\":{\"next\":{\"href\":\"/n\"}},\"pipelines\":[{}]}"))
                .next())
        .isEqualTo("/n");
  }
}
