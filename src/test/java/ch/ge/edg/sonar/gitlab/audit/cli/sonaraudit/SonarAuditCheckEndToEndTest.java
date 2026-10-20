package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/** Golden master of a whole run against the default synthetic park. */
class SonarAuditCheckEndToEndTest extends SonarAuditCheckTestBase {

  @Test
  void fullRunWritesTheInventoryTheHistoryAndTheReport() throws IOException {
    Cli.Result r = runCsv();

    assertThat(r.err()).isEmpty();
    assertThat(r.exitCode()).isZero();
    expectSelfie(stable(r.out())).toMatchDisk("stdout");
    expectSelfie(excelCsv(csv())).toMatchDisk("inventory");
    expectSelfie(excelCsv(historyCsv())).toMatchDisk("history");
  }

  @Test
  void withoutCsvNothingIsWrittenAndTheReportOnlyLosesTheFileLines() throws IOException {
    fake.start();
    Cli.Result r = fake.run(tmp);
    String withCsv = runAgainWithCsv();

    assertThat(r.exitCode()).isZero();
    try (var files = Files.list(tmp)) {
      assertThat(files.map(p -> p.getFileName().toString()))
          .containsExactlyInAnyOrder(
              "no-exclusions.txt", "inventaire.csv", "inventaire-historique.csv");
    }
    String expected =
        withCsv
            .replaceAll("\n  CSV écrit : .*\n", "")
            .replaceAll("  Format Excel .*\n", "")
            .replaceAll("  Historique écrit : .*\n", "");
    assertThat(stable(r.out())).isEqualTo(expected);
  }

  private String runAgainWithCsv() {
    return stable(fake.run(tmp, "--csv", csv().toString()).out());
  }

  @Test
  void eachProjectIsReadOnTheBranchItsLatestScanComesFrom() {
    runCsv("--concurrency", "1");
    FakeHttpServer server = fake.server();

    String keys = server.requests("/api/measures/search").getLast().param("metricKeys");
    assertThat(keys)
        .doesNotContain("test_errors")
        .startsWith("ncloc,files,")
        .endsWith(",alert_status");
    assertThat(server.requests("/api/measures/search").getLast().param("projectKeys"))
        .isEqualTo("alpha-service,delta-legacy");
    assertThat(
            server.requests("/api/measures/component").stream()
                .map(q -> q.param("component") + "@" + q.param("branch"))
                .toList())
        .containsExactly("beta-api@develop", "gamma-lib@develop", "alpha-service@null");
    assertThat(
            server.requests("/api/measures/search_history").stream()
                .filter(q -> q.param("from") != null)
                .map(q -> q.param("component") + "@" + q.param("branch") + ":" + q.param("metrics"))
                .toList())
        .containsExactly(
            "alpha-service@null:violations,ncloc,sqale_debt_ratio",
            "beta-api@develop:violations,ncloc,sqale_debt_ratio",
            "gamma-lib@develop:violations,ncloc,sqale_debt_ratio",
            "delta-legacy@null:violations,ncloc,sqale_debt_ratio",
            "alpha-service@null:ncloc,sqale_index");
  }

  @Test
  void pagesAreAskedInOrderWithTheExpectedParameters() {
    runCsv("--concurrency", "1");
    FakeHttpServer server = fake.server();

    assertThat(server.requests("/api/metrics/search").stream().map(q -> q.param("p")).toList())
        .containsExactly("1", "2");
    assertThat(server.requests("/api/metrics/search").getFirst().param("ps")).isEqualTo("500");
    assertThat(
            server.requests("/api/components/search_projects").stream()
                .map(q -> q.param("ps") + "/" + q.param("p") + "/" + q.param("f"))
                .toList())
        .containsExactly(
            "1/null/null",
            "1/null/null",
            "500/1/analysisDate,leakPeriodDate",
            "500/2/analysisDate,leakPeriodDate");
    assertThat(server.requests().getFirst().header("Authorization")).isEqualTo("Bearer tok-test");
  }

  @Test
  void historyWindowStartsActivityDaysAgo() {
    runCsv("--concurrency", "1", "--activity-days", "30");
    String since = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(30).toString();
    var history = fake.server().requests("/api/measures/search_history");
    assertThat(history.getFirst().param("from")).isNull();
    assertThat(history.getFirst().param("ps")).isEqualTo("5");
    assertThat(history.subList(1, history.size()))
        .allSatisfy(q -> assertThat(q.param("from")).isEqualTo(since))
        .allSatisfy(q -> assertThat(q.param("ps")).isEqualTo("1000"));
    var analyses = fake.server().requests("/api/project_analyses/search");
    assertThat(analyses.getLast().param("from")).isEqualTo(since);
    assertThat(analyses.getLast().param("ps")).isEqualTo("500");
    var issues = fake.server().requests("/api/issues/search");
    assertThat(issues.getLast().param("createdAfter")).isEqualTo(since);
  }
}
