package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.dora.RunOutcome;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The « reached main » rule, second step: each feature goes to a deploy. */
class FeatureDeliveriesTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  private static Instant h(int hours) {
    return T0.plus(Duration.ofHours(hours));
  }

  /** Merge requests only: nothing else reached main. */
  private static Snapshot snapshot(
      List<Snapshot.MergeRequest> mrs, List<Snapshot.Deployment> deployments) {
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
    return new Snapshot(p, T0, h(24 * 30), mrs, deployments, List.of(), null);
  }

  private static Snapshot.Deployment deploy(int counter, int at, RunOutcome outcome) {
    return new Snapshot.Deployment("prod", counter, h(at), outcome, false, List.of(), "u");
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
    Snapshot s = snapshot(List.of(early, onTime, late), List.of(failed, first));
    Map<Snapshot.Deployment, List<Features.Feature>> d =
        Features.deliveries(Features.of(s), List.of(first));
    assertThat(d).containsOnlyKeys(first);
    assertThat(d.get(first)).extracting(Features.Feature::key).containsExactly("!1", "!2");
    Metrics m = Metrics.of(s);
    assertThat(m.links().delivered().get(first)).containsExactly("a", "b");
    assertThat(m.leads()).containsExactly(Duration.ofHours(12), Duration.ofHours(12));
    assertThat(m.links().deployedBy()).doesNotContainKey(3);
  }
}
