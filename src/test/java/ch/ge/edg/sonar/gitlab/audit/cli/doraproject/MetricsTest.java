package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.dora.RunOutcome;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The four measures and the links between deployments, merge requests and incidents. */
class MetricsTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  private static Instant h(int hours) {
    return T0.plus(Duration.ofHours(hours));
  }

  private static Snapshot.Deployment deploy(
      int counter, int at, RunOutcome outcome, Snapshot.Change... commits) {
    return new Snapshot.Deployment(
        "prod", counter, h(at), outcome, false, List.of(commits), "u" + counter);
  }

  private static Snapshot.Change change(String sha, @Nullable Integer at) {
    return new Snapshot.Change(sha, at == null ? null : h(at));
  }

  private static Snapshot.Incident incident(String key, int created, @Nullable Integer resolved) {
    return new Snapshot.Incident(
        key, key, "Incident", h(created), resolved == null ? null : h(resolved), "u");
  }

  private static Snapshot snapshot(
      Snapshot.DeploySource source,
      List<String> jira,
      List<Snapshot.MergeRequest> mrs,
      List<Snapshot.Deployment> deployments,
      List<Snapshot.Incident> incidents) {
    Snapshot.Project p =
        new Snapshot.Project("1", "g/p", "w", "main", source, List.of("prod"), jira, "");
    return new Snapshot(p, T0, T0.plus(Duration.ofDays(14)), mrs, deployments, incidents);
  }

  @Test
  void commitsOfAFailedRunAreDeployedByTheNextSuccessOfTheSamePipeline() {
    Snapshot s =
        snapshot(
            Snapshot.DeploySource.GOCD,
            List.of("ABC"),
            List.of(),
            List.of(
                deploy(2, 10, RunOutcome.FAILURE, change("a", 2), change("b", null)),
                deploy(3, 12, RunOutcome.SUCCESS),
                deploy(1, 1, RunOutcome.SUCCESS, change("z", 0))),
            List.of());
    Metrics m = Metrics.of(s);
    assertThat(m.successes()).extracting(Snapshot.Deployment::counter).containsExactly(1, 3);
    assertThat(m.runs()).extracting(Snapshot.Deployment::counter).containsExactly(1, 2, 3);
    // a: written at 2 h, deployed by prod/3 at 12 h; b has no date; z: 1 h.
    assertThat(m.leads()).containsExactlyInAnyOrder(Duration.ofHours(10), Duration.ofHours(1));
    assertThat(m.leadTime()).isEqualTo(Duration.ofMinutes(330));
    assertThat(m.failedRunRate()).isEqualTo(1.0 / 3);
    assertThat(m.perWeek()).isEqualTo(1.0);
  }

  @Test
  void aMergeRequestIsDeployedByTheEarliestRunCarryingAnyOfItsCommits() {
    Snapshot.MergeRequest mr =
        new Snapshot.MergeRequest(7, "t", "a", h(1), h(2), h(0), List.of("x", "y"), "u");
    Snapshot.MergeRequest never =
        new Snapshot.MergeRequest(8, "t", "a", h(1), h(2), null, List.of("q"), "u");
    Snapshot s =
        snapshot(
            Snapshot.DeploySource.GOCD,
            List.of(),
            List.of(mr, never),
            List.of(
                deploy(2, 9, RunOutcome.SUCCESS, change("x", 0)),
                deploy(1, 5, RunOutcome.SUCCESS, change("y", 0))),
            List.of());
    Metrics m = Metrics.of(s);
    assertThat(m.links().deployedBy())
        .extractingByKey(7)
        .extracting(Snapshot.Deployment::counter)
        .isEqualTo(1);
    assertThat(m.links().deployedBy()).doesNotContainKey(8);
  }

  @Test
  void anIncidentCountsAgainstTheLastDeployBeforeItWhateverItsOutcome() {
    Snapshot s =
        snapshot(
            Snapshot.DeploySource.GOCD,
            List.of("ABC"),
            List.of(),
            List.of(
                deploy(1, 10, RunOutcome.SUCCESS),
                deploy(2, 20, RunOutcome.FAILURE),
                deploy(3, 30, RunOutcome.SUCCESS)),
            List.of(
                incident("A-1", 5, 6),
                incident("A-2", 20, 23),
                incident("A-3", 21, null),
                incident("A-4", 35, 34)));
    Metrics m = Metrics.of(s);
    assertThat(m.links().precededBy()).doesNotContainKey("A-1");
    assertThat(m.links().precededBy())
        .extractingByKey("A-2")
        .extracting(Snapshot.Deployment::counter)
        .isEqualTo(2);
    assertThat(m.links().incidentsAfter().get(m.runs().get(1)))
        .extracting(Snapshot.Incident::key)
        .containsExactly("A-2", "A-3");
    assertThat(m.failedChanges()).isEqualTo(2);
    assertThat(m.changeFailureRate()).isEqualTo(2.0 / 3);
    // A-4 resolved before it was created: no duration.
    assertThat(m.recoveries()).containsExactly(Duration.ofHours(1), Duration.ofHours(3));
    assertThat(m.timeToRestore()).isEqualTo(Duration.ofHours(2));
  }

  @Test
  void withoutJiraOrDeployThereIsNoFailureRate() {
    Snapshot noJira =
        snapshot(
            Snapshot.DeploySource.GOCD,
            List.of(),
            List.of(),
            List.of(deploy(1, 1, RunOutcome.SUCCESS)),
            List.of());
    assertThat(Metrics.of(noJira).changeFailureRate()).isNull();
    assertThat(Metrics.of(noJira).incidentsRead()).isFalse();
    Snapshot nothing =
        snapshot(Snapshot.DeploySource.GOCD, List.of("ABC"), List.of(), List.of(), List.of());
    Metrics m = Metrics.of(nothing);
    assertThat(m.changeFailureRate()).isNull();
    assertThat(m.failedRunRate()).isNull();
    assertThat(m.leadTime()).isNull();
    assertThat(m.timeToRestore()).isNull();
    assertThat(m.perWeek()).isZero();
  }

  @Test
  void withoutGocdTheLeadTimeRunsFromTheFirstCommitOrTheOpeningToTheMerge() {
    Snapshot.MergeRequest written =
        new Snapshot.MergeRequest(1, "t", "a", h(2), h(5), h(1), List.of("x"), "u");
    Snapshot.MergeRequest opened =
        new Snapshot.MergeRequest(2, "t", "a", h(2), h(5), null, List.of("y"), "u");
    Snapshot.MergeRequest backwards =
        new Snapshot.MergeRequest(3, "t", "a", h(9), h(5), null, List.of("z"), "u");
    List<Snapshot.MergeRequest> mrs = List.of(written, opened, backwards);
    Snapshot s =
        snapshot(Snapshot.DeploySource.MERGE, List.of(), mrs, Merges.asDeployments(mrs), List.of());
    Metrics m = Metrics.of(s);
    assertThat(m.leads()).containsExactly(Duration.ofHours(4), Duration.ofHours(3));
    assertThat(m.links().deployedBy())
        .extractingByKey(2)
        .extracting(Snapshot.Deployment::at)
        .isEqualTo(h(5));
    assertThat(m.successes()).hasSize(3);
  }

  @Test
  void theMedianOfAnEvenListIsTheMeanOfTheTwoInTheMiddle() {
    assertThat(Metrics.median(List.of())).isNull();
    assertThat(Metrics.median(List.of(Duration.ofHours(3)))).isEqualTo(Duration.ofHours(3));
    assertThat(
            Metrics.median(List.of(Duration.ofHours(9), Duration.ofHours(1), Duration.ofHours(2))))
        .isEqualTo(Duration.ofHours(2));
    assertThat(
            Metrics.median(
                List.of(
                    Duration.ofHours(9),
                    Duration.ofHours(1),
                    Duration.ofHours(2),
                    Duration.ofHours(4))))
        .isEqualTo(Duration.ofHours(3));
  }

  @Test
  void aPeriodShorterThanADayCountsAsOneDay() {
    Snapshot.Project p =
        new Snapshot.Project(
            "1", "g/p", "w", "main", Snapshot.DeploySource.GOCD, List.of(), List.of(), "");
    Snapshot s = new Snapshot(p, T0, h(3), List.of(), List.of(), List.of());
    assertThat(Metrics.of(s).days()).isEqualTo(1);
  }
}
