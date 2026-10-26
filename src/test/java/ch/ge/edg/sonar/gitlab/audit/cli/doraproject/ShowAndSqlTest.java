package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.dora.RunOutcome;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** How the page writes numbers and text, and how the import quotes them. */
class ShowAndSqlTest {

  @Test
  void aDurationIsInMinutesThenHoursUpToTwoDaysThenDays() {
    assertThat(Show.duration(null)).isEqualTo("—");
    assertThat(Show.duration(Duration.ofSeconds(59))).isEqualTo("0 min");
    assertThat(Show.duration(Duration.ofMinutes(59))).isEqualTo("59 min");
    assertThat(Show.duration(Duration.ofMinutes(60))).isEqualTo("1,0 h");
    assertThat(Show.duration(Duration.ofMinutes(48 * 60 - 1))).isEqualTo("48,0 h");
    assertThat(Show.duration(Duration.ofDays(2))).isEqualTo("2,0 j");
    assertThat(Show.duration(Duration.ofHours(60))).isEqualTo("2,5 j");
  }

  @Test
  void percentsDatesAndHtml() {
    assertThat(Show.percent(null)).isEqualTo("—");
    assertThat(Show.percent(1.0 / 3)).isEqualTo("33 %");
    Instant at = Instant.parse("2026-01-01T23:30:00Z");
    assertThat(Show.dateTime(at, ZoneId.of("Europe/Zurich"))).isEqualTo("2026-01-02 00:30");
    assertThat(Show.dateTime(null, ZoneId.of("UTC"))).isEqualTo("—");
    assertThat(Show.day(at, ZoneId.of("UTC"))).isEqualTo("2026-01-01");
    assertThat(Show.html("<a href=\"x\">l'&</a>"))
        .isEqualTo("&lt;a href=&quot;x&quot;&gt;l&#39;&amp;&lt;/a&gt;");
    assertThat(Show.html(null)).isEmpty();
  }

  @Test
  void sqlTextDoublesItsQuotesAndJsonEscapesItsOwn() {
    assertThat(MiddlewareSql.text("l'essai")).isEqualTo("'l''essai'");
    assertThat(MiddlewareSql.json("a\"b\\c")).isEqualTo("\"a\\\"b\\\\c\"");
    assertThat(MiddlewareSql.workflowId("prod")).isEqualTo("gocd:prod");
    assertThat(MiddlewareSql.status(RunOutcome.CANCELLED)).isEqualTo("CANCELLED");
  }

  @Test
  void anImportWithoutRunsNorIncidentsStillDeclaresTheWorkflowAndTheService() {
    Snapshot.Project p =
        new Snapshot.Project(
            "7",
            "g/l'appli",
            "w",
            "main",
            Snapshot.DeploySource.GOCD,
            List.of("prod-a", "prod-b"),
            List.of("ABC", "XYZ"),
            "");
    Instant t = Instant.parse("2026-01-01T00:00:00Z");
    String sql = MiddlewareSql.render(new Snapshot(p, t, t, List.of(), List.of(), List.of()));
    assertThat(sql)
        .contains("idempotency_key = '7'")
        .contains("(g/l''appli)")
        .contains("  ('gocd:prod-a', 'prod-a'),\n  ('gocd:prod-b', 'prod-b')\n")
        .contains("  ('ABC', 'Jira ABC'),\n  ('XYZ', 'Jira XYZ')\n")
        .contains("o.key IN ('ABC', 'XYZ')")
        .doesNotContain("RepoWorkflowRuns")
        .doesNotContain("INSERT INTO \"Incident\"")
        .endsWith("COMMIT;\n");
  }
}
