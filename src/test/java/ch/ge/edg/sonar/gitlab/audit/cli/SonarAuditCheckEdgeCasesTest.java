package ch.ge.edg.sonar.gitlab.audit.cli;

import static ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheckFake.error;
import static ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheckFake.ok;
import static ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheckFake.points;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Less common answers that each take a branch of their own. */
class SonarAuditCheckEdgeCasesTest extends SonarAuditCheckTestBase {

  private static final String[] LEAN = {"--main-branch-only", "--no-bindings", "--no-trend"};

  @Test
  void noFirstVisibleProjectMeansNoSampleSections() {
    fake.failingSearchPage = -1;
    Cli.Result r = runCsv(LEAN);
    assertThat(r.out())
        .contains("  Aucun projet visible : impossible de tester les endpoints projet.\n")
        .contains("  Projets visibles avec ce token : 4\n")
        .doesNotContain("=== 4.");
  }

  @Test
  void metricsAnswerWithoutTheListStopsTheNegotiation() {
    fake.override("/api/metrics/search", q -> ok(Map.of("paging", Map.of("total", 3))));
    runCsv(LEAN);
    assertThat(fake.server().requests("/api/metrics/search")).hasSize(1);
    assertThat(fake.server().requests("/api/measures/search").getLast().param("metricKeys"))
        .contains("test_errors");
  }

  @Test
  void metricsPagingWithoutTotalKeepsReading() {
    fake.override(
        "/api/metrics/search",
        q ->
            "1".equals(q.param("p"))
                ? ok(
                    Map.of(
                        "paging",
                        Map.of("pageIndex", 1),
                        "metrics",
                        List.of(Map.of("key", "ncloc"))))
                : ok(Map.of("metrics", List.of())));
    Cli.Result r = runCsv(LEAN);
    assertThat(fake.server().requests("/api/metrics/search")).hasSize(2);
    assertThat(fake.server().requests("/api/measures/search").getLast().param("metricKeys"))
        .isEqualTo("ncloc");
    assertThat(r.out()).contains("  Métriques inconnues de cette instance : files, complexity, ");
  }

  @Test
  void anInstanceThatKnowsEveryMetricSaysNothingAboutThem() {
    fake.metrics.add("test_errors");
    assertThat(runCsv(LEAN).out()).doesNotContain("Métriques inconnues");
  }

  @Test
  void refusedTreeMeansNoFileToBlame() {
    fake.override("/api/components/tree", q -> error(403, "no"));
    assertThat(runCsv(LEAN).out())
        .contains("  REFUSE   Arbre de composants (components/tree)      HTTP 403 no\n")
        .contains(
            "  VIDE     Blame SCM (sources/scm)                    aucun fichier accessible pour tester\n");
  }

  @Test
  void missingBranchMeasuresLeaveTheRowEmpty() throws IOException {
    fake.measures.remove("beta-api@develop");
    runCsv("--no-bindings", "--no-trend");
    assertThat(cell("beta-api", "ncloc")).isEmpty();
    assertThat(cell("beta-api", "coverage")).isEmpty();
  }

  @Test
  void aFlatTrendIsStable() {
    fake.history
        .get("alpha-service")
        .put("violations", points(60, "100", 40, "101", 20, "100", 10, "101"));
    fake.history
        .get("alpha-service")
        .put("ncloc", points(60, "1000", 40, "1000", 20, "1000", 10, "1000"));
    Cli.Result r = runCsv("--no-bindings");
    assertThat(r.out())
        .contains(
            "    en hausse (> +2 %/mois)    : 1\n    en baisse (< -2 %/mois)    : 0\n    stables                      : 1\n");
  }

  @Test
  void duplicatedNewCodeMeasuresKeepTheFirst() {
    String twice =
        "{\"component\":{\"key\":\"alpha-service\",\"measures\":["
            + "{\"metric\":\"new_lines\",\"value\":\"7\"},{\"metric\":\"new_lines\",\"value\":\"8\"}]}}";
    fake.override("/api/measures/component", q -> Response.json(twice));
    assertThat(runCsv(LEAN).out()).contains("  Lignes neuves (new code)        : 7\n");
  }

  @Test
  void onlyRetriedCallsAreReportedToo() {
    AtomicInteger gw = new AtomicInteger();
    fake.override(
        "/api/qualityprofiles/search",
        q ->
            gw.getAndIncrement() == 0
                ? Response.status(502, "")
                : ok(Map.of("profiles", List.of())));
    assertThat(runCsv(LEAN).out()).contains("  dont 0 ralenti(s) (429) et 1 repris (5xx).\n");
  }
}
