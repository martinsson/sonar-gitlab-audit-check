package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.dora.RunOutcome;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The DevLake import: its quoting, its DORA bands, and how our rules land in commits_diffs. */
class DevlakeSqlTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  private static Instant h(int hours) {
    return T0.plus(Duration.ofHours(hours));
  }

  private static Snapshot.Deployment deploy(
      int counter, int at, RunOutcome outcome, String... shas) {
    List<Snapshot.Change> commits =
        java.util.Arrays.stream(shas).map(sha -> new Snapshot.Change(sha, T0)).toList();
    return new Snapshot.Deployment("prod", counter, h(at), outcome, false, commits, "u");
  }

  private static Snapshot snapshot(
      List<Snapshot.MergeRequest> mrs, List<Snapshot.Deployment> deployments) {
    Snapshot.Project p =
        new Snapshot.Project(
            "7",
            "g/l'appli",
            "https://gitlab.test/g/appli",
            "main",
            Snapshot.DeploySource.GOCD,
            List.of("prod"),
            List.of("ABC"),
            "");
    Snapshot.Incident i =
        new Snapshot.Incident("ABC-1", "Panne \\ totale", "Bug", h(13), h(15), "j");
    return new Snapshot(p, T0, h(48), mrs, deployments, List.of(i), null, null);
  }

  @Test
  void mysqlTextEscapesQuotesAndBackslashesAndTimesAreUtc() {
    assertThat(DevlakeSql.text("l'a\\b")).isEqualTo("'l''a\\\\b'");
    assertThat(DevlakeSql.time(Instant.parse("2026-03-04T05:06:07.890Z")))
        .isEqualTo("'2026-03-04 05:06:07.890'");
    assertThat(DevlakeSql.time(null)).isEqualTo("NULL");
    assertThat(DevlakeModel.result(RunOutcome.CANCELLED)).containsExactly("", "DONE");
    assertThat(DevlakeModel.result(RunOutcome.PENDING)).containsExactly("", "IN_PROGRESS");
  }

  @Test
  void aFailedRunsCommitsAreDiffedAgainstTheLastSuccessByTheNextOne() {
    Snapshot.MergeRequest mr =
        new Snapshot.MergeRequest(3, "t", "dev", h(1), h(2), h(0), List.of("x", "m"), "u");
    Snapshot s =
        snapshot(
            List.of(mr),
            List.of(
                deploy(1, 5, RunOutcome.SUCCESS, "a"),
                deploy(2, 10, RunOutcome.FAILURE, "x", "m"),
                deploy(3, 12, RunOutcome.SUCCESS)));
    String sql = DevlakeSql.render(s, "appli");
    String zero = DevlakeModel.NO_COMMIT;
    assertThat(sql)
        .contains("VALUES ((SELECT name FROM projects WHERE name = 'appli'), 'repos',")
        .contains("'dora-import:7:gocd:prod:reference', 'dora-import:7:gocd:prod',")
        .contains("'1970-01-01 00:00:00.000'")
        // prod/1 is diffed against the reference, prod/3 carries what prod/2 failed to deploy.
        .contains("('a', '" + zero + "', 'a', 0)")
        .contains("('m', 'a', 'x', 0),\n  ('m', 'a', 'm', 1);")
        .contains("'g/l''appli'")
        .contains("'Panne \\\\ totale', 'INCIDENT', 'Bug', 'DONE', 'resolved'")
        .contains(", 120, 'ABC', NOW(3), NOW(3));")
        .contains("('dora-import:7:jira:ABC', 'dora-import:7:jira:ABC-1', NOW(3), NOW(3));")
        .endsWith("COMMIT;\n");
    // The merge request's merge commit is one of its commits that prod/3 put in production.
    assertThat(sql).contains("'dora-import:7:mr:3', 'dora-import:7', 'dora-import:7', 'main', 'x'");
    assertThat(DevlakeSql.defaultProject(s)).isEqualTo("l'appli");
  }

  @Test
  void aRunWithNoCommitRedeploysThePreviousHeadAndAPipelineThatNeverSucceededHasNoReference() {
    Snapshot s =
        snapshot(
            List.of(),
            List.of(deploy(1, 5, RunOutcome.FAILURE), deploy(2, 6, RunOutcome.CANCELLED)));
    String sql = DevlakeSql.render(s, "p");
    assertThat(sql)
        .doesNotContain(":reference")
        .doesNotContain("INSERT IGNORE INTO commits_diffs")
        .contains("'prod #2', 'prod #2', 'u', '', 'CANCELLED', 'DONE', 'CANCELLED'");
  }

  @Test
  void bandsFollowTheDora2023ThresholdsDevlakeUses() {
    assertThat(Bands.frequency(5)).isEqualTo(Bands.Band.ELITE);
    assertThat(Bands.frequency(1)).isEqualTo(Bands.Band.HIGH);
    assertThat(Bands.frequency(0.25)).isEqualTo(Bands.Band.MEDIUM);
    assertThat(Bands.frequency(0.2)).isEqualTo(Bands.Band.LOW);
    assertThat(Bands.leadTime(null)).isNull();
    assertThat(Bands.leadTime(Duration.ofHours(23))).isEqualTo(Bands.Band.ELITE);
    assertThat(Bands.leadTime(Duration.ofDays(1))).isEqualTo(Bands.Band.HIGH);
    assertThat(Bands.leadTime(Duration.ofDays(29))).isEqualTo(Bands.Band.MEDIUM);
    assertThat(Bands.leadTime(Duration.ofDays(30))).isEqualTo(Bands.Band.LOW);
    assertThat(Bands.failureRate(null)).isNull();
    assertThat(Bands.failureRate(0.05)).isEqualTo(Bands.Band.ELITE);
    assertThat(Bands.failureRate(0.10)).isEqualTo(Bands.Band.HIGH);
    assertThat(Bands.failureRate(0.15)).isEqualTo(Bands.Band.MEDIUM);
    assertThat(Bands.failureRate(0.16)).isEqualTo(Bands.Band.LOW);
    assertThat(Bands.restore(null)).isNull();
    assertThat(Bands.restore(Duration.ofMinutes(59))).isEqualTo(Bands.Band.ELITE);
    assertThat(Bands.restore(Duration.ofHours(23))).isEqualTo(Bands.Band.HIGH);
    assertThat(Bands.restore(Duration.ofDays(6))).isEqualTo(Bands.Band.MEDIUM);
    assertThat(Bands.restore(Duration.ofDays(7))).isEqualTo(Bands.Band.LOW);
  }
}
