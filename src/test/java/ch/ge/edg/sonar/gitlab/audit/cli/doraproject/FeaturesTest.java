package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The « reached main » rule, first step: when each commit reached main, grouped into features. */
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
    return new Snapshot(
        p,
        T0,
        h(24 * 30),
        mrs,
        deployments,
        List.of(),
        new Snapshot.MainBranch(newestFirst, pushes),
        null);
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
}
