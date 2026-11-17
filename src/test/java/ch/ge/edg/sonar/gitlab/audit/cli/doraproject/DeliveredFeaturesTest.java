package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import static ch.ge.edg.sonar.gitlab.audit.cli.doraproject.ReleaseHistory.at;
import static ch.ge.edg.sonar.gitlab.audit.cli.doraproject.ReleaseHistory.commit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * What each deploy delivered, grouped into features, and their lead times: on {@link
 * ReleaseHistory}, then on small histories for the cases it does not have.
 */
class DeliveredFeaturesTest {

  private static final String WEB = "https://gitlab.test/g/p";

  private static Snapshot snapshot(List<Delivery> deliveries) {
    Snapshot.Project p =
        new Snapshot.Project(
            "1", "g/p", WEB, "main", Snapshot.DeploySource.TAGS, List.of(), List.of(), "");
    Snapshot.Releases releases = new Snapshot.Releases(DoraProject.DEFAULT_TAG_PATTERN, deliveries);
    return new Snapshot(
        p,
        at("11-11T12:00"),
        ReleaseHistory.NOW,
        List.of(),
        releases.deployments(WEB),
        List.of(),
        releases);
  }

  private static List<DeliveredFeatures.Shipment> history() {
    return DeliveredFeatures.of(snapshot(ReleaseHistory.deliveries()));
  }

  /** One delivery of {@code commits}, tagged on 11-13 at noon. */
  private static DeliveredFeatures.Shipment one(Delivery.Commit... commits) {
    Deploy tag =
        new Deploy(
            "v20261113.1.1",
            Deploy.PRODUCTION,
            at("11-13T12:00"),
            "tip",
            "g/p",
            Deploy.Kind.RELEASE_TAG);
    return DeliveredFeatures.of(snapshot(List.of(new Delivery(tag, null, List.of(commits), false))))
        .getFirst();
  }

  private static List<String> shas(DeliveredFeatures.Feature f) {
    return f.commits().stream().map(Delivery.Commit::sha).toList();
  }

  @Test
  void aKeylessFixIsAttachedToTheBranchWhoseMergeBroughtItOntoMain() {
    DeliveredFeatures.Shipment first = history().getFirst();
    assertThat(first.features())
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.key()).isEqualTo("ABC-343");
              assertThat(f.origin()).isEqualTo(DeliveredFeatures.Origin.KEY);
              assertThat(f.source()).isEqualTo("clé du message, 1 par la branche de la fusion");
              assertThat(shas(f)).containsExactly("ab33886", "fefda95");
              assertThat(f.last().sha()).isEqualTo("fefda95");
              // 11-12 15:00 → 11-13 08:12; its committer date was rewritten at 07:30.
              assertThat(f.authorLead()).isEqualTo(Duration.ofMinutes(17 * 60 + 12));
              assertThat(f.committerLead()).isEqualTo(Duration.ofMinutes(42));
            });
    assertThat(first.merges()).isEqualTo(1);
    assertThat(first.ciReleases()).isEqualTo(1);
    assertThat(first.excluded()).isEmpty();
  }

  @Test
  void mainMergedIntoABranchIsLeftOutAndAFeatureShipsInPieces() {
    List<DeliveredFeatures.Shipment> all = history();
    DeliveredFeatures.Shipment second = all.get(1);
    assertThat(second.features())
        .extracting(
            DeliveredFeatures.Feature::key,
            DeliveredFeatures.Feature::origin,
            DeliveredFeaturesTest::shas,
            DeliveredFeatures.Feature::authorLead)
        .containsExactly(
            tuple(
                "ABC-585",
                DeliveredFeatures.Origin.KEY,
                List.of("b702d84"),
                Duration.ofMinutes(3 * 60 + 2)),
            tuple(
                "ABC-483",
                DeliveredFeatures.Origin.KEY,
                List.of("1a1a1a1", "366100a"),
                Duration.ofMinutes(32)));
    assertThat(second.merges()).isEqualTo(2);
    assertThat(all.get(2).features())
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.key()).isEqualTo("ABC-483");
              assertThat(shas(f)).containsExactly("a36c9cc", "3a9ec64");
              assertThat(f.authorLead()).isEqualTo(Duration.ofMinutes(40 * 60 + 5));
              assertThat(f.committerLead()).isEqualTo(Duration.ofMinutes(24 * 60 + 5));
            });
  }

  @Test
  void aChoreStraightOnMainIsLeftOutAndATagOfCiCommitsDeliversNothing() {
    List<DeliveredFeatures.Shipment> all = history();
    assertThat(all.get(2).excluded()).isEqualTo(Map.of("chore", 1));
    DeliveredFeatures.Shipment fourth = all.get(3);
    assertThat(fourth.features()).isEmpty();
    assertThat(fourth.ciReleases()).isEqualTo(2);
    assertThat(DeliveredFeatures.excluded(all)).isEqualTo(Map.of("chore", 1));
  }

  @Test
  void theLeadTimeIsTheMedianOverFeaturesAndTagsAndEachTagIsASuccessfulDeploy() {
    Snapshot s = snapshot(ReleaseHistory.deliveries());
    Metrics m = Metrics.of(s);
    // 32 min, 3 h 02, 17 h 12, 40 h 05.
    assertThat(m.leads()).hasSize(4);
    assertThat(m.leadTime()).isEqualTo(Duration.ofMinutes((182 + 1032) / 2));
    assertThat(m.successes()).hasSize(4);
    Snapshot.Deployment second = m.runs().get(1);
    assertThat(s.deployName(second)).isEqualTo("v20261113.1.2");
    assertThat(second.url()).isEqualTo(WEB + "/-/tags/v20261113.1.2");
    assertThat(second.at()).isEqualTo(at("11-13T12:02"));
    assertThat(m.links().delivered().get(second)).containsExactly("b702d84", "1a1a1a1", "366100a");
    // Every commit the tag delivered points to it, merges included.
    assertThat(m.links().bySha()).containsEntry("21c3b99", second);
  }

  @Test
  void aKeylessFeatOrFixWithoutAKeyedBranchStandsAloneAndOtherTypesAreLeftOut() {
    String base = "x000000";
    DeliveredFeatures.Shipment t =
        one(
            commit("c1", "chore: tidy", "11-13T09:00", null, base),
            commit("f1", "fix: typo", "11-13T09:10", null, "c1"),
            commit(
                "m1",
                "Merge branch 'feature/cleanup' into 'main'",
                "11-13T10:00",
                null,
                base,
                "f1"),
            commit("d1", "fix: hotfix label", "11-13T10:30", null, "m1"),
            commit("e1", "test: flaky retry", "11-13T10:40", null, "d1"),
            commit("k1", "chore: [ABC-7] bump", "11-13T10:50", null, "e1"));
    assertThat(t.features())
        .extracting(
            DeliveredFeatures.Feature::key,
            DeliveredFeatures.Feature::story,
            DeliveredFeaturesTest::shas)
        .containsExactly(
            tuple("f1", false, List.of("f1")),
            tuple("d1", false, List.of("d1")),
            tuple("ABC-7", true, List.of("k1")));
    assertThat(t.features().getFirst().source()).isEqualTo("commit seul");
    assertThat(t.excluded()).isEqualTo(Map.of("chore", 1, "test", 1));
  }

  @Test
  void aChoreOnAKeyedBranchBelongsToItsFeatureButNotOneFastForwardedOntoMain() {
    String base = "x000000";
    DeliveredFeatures.Shipment t =
        one(
            commit("a1", "feat: [ABC-9] work", "11-13T08:00", null, base),
            commit("c1", "chore: cleanup", "11-13T09:00", null, "a1"),
            commit(
                "m1",
                "Merge branch 'feature/ABC-9_work' into 'main'",
                "11-13T10:00",
                null,
                base,
                "c1"),
            commit("r1", "refactor: rename", "11-13T10:30", null, "m1"));
    assertThat(t.features())
        .singleElement()
        .satisfies(
            f -> {
              assertThat(shas(f)).containsExactly("a1", "c1");
              assertThat(f.last().sha()).isEqualTo("c1");
              assertThat(f.byBranch()).isEqualTo(1);
            });
    assertThat(t.excluded()).isEqualTo(Map.of("refactor", 1));
  }
}
