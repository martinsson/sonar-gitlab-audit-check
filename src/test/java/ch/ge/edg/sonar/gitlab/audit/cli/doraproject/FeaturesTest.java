package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.dora.RunOutcome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The « reached main » rule: when each commit reached main, grouped into features, then deploys.
 */
class FeaturesTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  private static Instant h(int hours) {
    return T0.plus(Duration.ofHours(hours));
  }

  private static Snapshot.MainCommit commit(String sha, String title, int authored, int committed) {
    return new Snapshot.MainCommit(sha, title, h(authored), h(committed));
  }

  private static Snapshot.Push push(int at, @Nullable String from, String to) {
    return new Snapshot.Push(h(at), from, to, 1);
  }

  private static Snapshot snapshot(
      List<Snapshot.MergeRequest> mrs,
      List<Snapshot.Deployment> deployments,
      List<Snapshot.MainCommit> newestFirst,
      List<Snapshot.Push> pushes) {
    Snapshot.Project p =
        new Snapshot.Project(
            "1",
            "g/p",
            "w",
            "main",
            Snapshot.DeploySource.REACHABLE,
            List.of("prod"),
            List.of(),
            "");
    return new Snapshot(p, T0, h(24 * 30), mrs, deployments, List.of(), newestFirst, pushes);
  }

  private static Snapshot.Deployment deploy(int counter, int at, RunOutcome outcome) {
    return new Snapshot.Deployment("prod", counter, h(at), outcome, false, List.of(), "u");
  }

  @Test
  void aMergeRequestReachesMainWhenItIsMergedAndStartsAtItsFirstCommit() {
    Snapshot.MergeRequest mr =
        new Snapshot.MergeRequest(4, "t", "a", h(5), h(9), h(2), List.of("a", "b"), "u");
    Snapshot.MergeRequest unwritten =
        new Snapshot.MergeRequest(5, "t", "a", h(6), h(7), null, List.of("c"), "u");
    List<Features.Feature> features =
        Features.of(
            snapshot(
                List.of(mr, unwritten),
                List.of(),
                List.of(commit("b", "x", 3, 8), commit("a", "y", 2, 8)),
                List.of(push(9, "z", "b"))));
    assertThat(features)
        .containsExactly(
            new Features.Feature("!5", List.of("c"), h(6), h(7), Features.Reached.MERGE_REQUEST),
            new Features.Feature(
                "!4", List.of("a", "b"), h(2), h(9), Features.Reached.MERGE_REQUEST));
  }

  @Test
  void outsideMergeRequestsAPushDatesItsCommitsAndAJiraKeyGroupsThem() {
    // Oldest first on main: p1, k1, k2, q1. A push brings p1 and k1 at 10 h, another k2 at 20 h;
    // q1 has no push: its committer date (rewritten by a rebase) is all there is.
    List<Snapshot.MainCommit> newestFirst =
        List.of(
            commit("q1", "ménage", 21, 22),
            commit("k2", "ABC-1 suite", 15, 16),
            commit("k1", "ABC-1 début", 3, 4),
            commit("p1", "sans clé", 1, 2));
    List<Snapshot.Push> pushes =
        List.of(push(20, "k1", "k2"), push(10, null, "k1"), push(30, "x", "inconnu"));
    List<Features.Feature> features =
        Features.of(snapshot(List.of(), List.of(), newestFirst, pushes));
    assertThat(features)
        .containsExactly(
            new Features.Feature(
                "push " + h(10), List.of("p1"), h(1), h(10), Features.Reached.PUSH),
            new Features.Feature("ABC-1", List.of("k1", "k2"), h(3), h(20), Features.Reached.PUSH),
            new Features.Feature("q1", List.of("q1"), h(21), h(22), Features.Reached.COMMIT_DATE));
    assertThat(Features.sources(features))
        .isEqualTo(Map.of(Features.Reached.PUSH, 2, Features.Reached.COMMIT_DATE, 1));
  }

  @Test
  void aFeatureGoesToTheFirstSuccessfulDeployScheduledAtOrAfterItReachedMain() {
    Snapshot.MergeRequest early =
        new Snapshot.MergeRequest(1, "t", "a", h(1), h(10), h(0), List.of("a"), "u");
    Snapshot.MergeRequest onTime =
        new Snapshot.MergeRequest(2, "t", "a", h(1), h(12), h(0), List.of("b"), "u");
    Snapshot.MergeRequest late =
        new Snapshot.MergeRequest(3, "t", "a", h(1), h(40), h(0), List.of("c"), "u");
    Snapshot.Deployment failed = deploy(1, 11, RunOutcome.FAILURE);
    Snapshot.Deployment first = deploy(2, 12, RunOutcome.SUCCESS);
    Snapshot s =
        snapshot(List.of(early, onTime, late), List.of(failed, first), List.of(), List.of());
    Map<Snapshot.Deployment, List<Features.Feature>> d =
        Features.deliveries(Features.of(s), List.of(first));
    assertThat(d).containsOnlyKeys(first);
    assertThat(d.get(first)).extracting(Features.Feature::key).containsExactly("!1", "!2");
    Metrics m = Metrics.of(s);
    assertThat(m.links().delivered().get(first)).containsExactly("a", "b");
    assertThat(m.leads()).containsExactly(Duration.ofHours(12), Duration.ofHours(12));
    assertThat(m.links().deployedBy()).doesNotContainKey(3);
  }

  @Test
  void aSnapshotWrittenBeforeTheRuleReadsAsMaterialsWithNothingOnMain(@TempDir Path dir)
      throws IOException {
    Path file = dir.resolve(SnapshotFile.NAME);
    Files.writeString(
        file,
        """
        {"project":{"gitlabId":"1","path":"g/p","webUrl":"w","defaultBranch":"main",
          "deploySource":"GOCD","prodPipelines":[],"jiraProjects":[],"incidentRule":""},
         "from":"2026-01-01T00:00:00Z","to":"2026-01-02T00:00:00Z",
         "mergeRequests":[],"deployments":[],"incidents":[]}
        """);
    Snapshot s = SnapshotFile.read(file);
    assertThat(s.mainCommits()).isEmpty();
    assertThat(s.pushes()).isEmpty();
    assertThat(s.project().reachable()).isFalse();
    assertThat(Metrics.of(s).links().features()).isEmpty();
  }
}
