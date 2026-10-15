package ch.ge.edg.sonar.gitlab.audit.cli;

import static ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheckFake.error;
import static ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheckFake.ok;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import ch.ge.edg.sonar.gitlab.audit.testing.SuspectedBug;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Section 3: pagination, scope gap, metric negotiation, measures, bindings. */
class SonarAuditCheckInventoryTest extends SonarAuditCheckTestBase {

  private static final String[] LEAN = {"--main-branch-only", "--no-bindings", "--no-trend"};

  @Test
  void noVisibleProjectWritesAHeaderOnlyCsvAndSkipsTheSampleSections() throws IOException {
    fake.projects.clear();
    fake.realTotal = 0;
    Cli.Result r = runCsv();

    assertThat(r.exitCode()).isZero();
    expectSelfie(stable(r.out())).toMatchDisk("stdout");
    assertThat(excelCsv(csv()).lines()).hasSize(1);
    assertThat(historyCsv()).doesNotExist();
  }

  @Test
  void aFailingPageStopsThePaginationAndKeepsWhatWasRead() throws IOException {
    fake.failingSearchPage = 2;
    Cli.Result r = runCsv(LEAN);

    assertThat(r.out())
        .contains(
            "  Échec de la pagination page 2 : HTTP 500\n  Projets visibles avec ce token : 2\n");
    assertThat(excelCsv(csv()).lines().skip(1).map(l -> l.substring(1, l.indexOf("\";"))))
        .containsExactly("alpha-service", "beta-api");
  }

  @Test
  @SuspectedBug(
      "the message should state the real limit (40 pages of the returned page size), not 20 000")
  void fortyPagesOfOneProjectAreReportedAsInterruptedAtTwentyThousandProjects() throws IOException {
    fake.projects.clear();
    for (int i = 0; i < 41; i++) fake.project("p-%02d".formatted(i), "P " + i, null);
    fake.searchPageSize = 1;
    Cli.Result r = runCsv(LEAN);

    assertThat(r.out())
        .contains(
            "  Pagination interrompue à 20 000 projets.\n  Projets visibles avec ce token : 40\n");
    assertThat(fake.server().requests("/api/components/search_projects")).hasSize(42);
    assertThat(excelCsv(csv()).lines()).hasSize(41);
  }

  @Test
  void anEmptyPageEndsThePaginationEvenIfTheTotalSaysMore() {
    fake.searchTotal = 10;
    Cli.Result r = runCsv(LEAN);
    assertThat(r.out()).contains("  Projets visibles avec ce token : 4\n");
    assertThat(fake.server().requests("/api/components/search_projects")).hasSize(5);
  }

  @Test
  void aMissingTotalStopsAfterTheFirstPage() {
    fake.override(
        "/api/components/search_projects",
        q -> ok(Map.of("components", List.of(Map.of("key", "alpha-service")))));
    Cli.Result r = runCsv(LEAN);
    assertThat(r.out()).contains("  Projets visibles avec ce token : 1\n");
    assertThat(r.out())
        .contains("  OK       Inventaire projets (search_projects)       ? projets visibles\n");
  }

  @Test
  void refusedRealTotalIsABlindSpotWarning() {
    fake.realTotal = null;
    Cli.Result r = runCsv(LEAN);
    assertThat(r.out())
        .contains(
            """
              Projets visibles avec ce token : 4
              Total réel indisponible (nécessite 'Administer System').
                Fais comparer ce chiffre à un admin : l'écart est le point aveugle
                de ton audit, et il n'apparaît dans aucune réponse d'erreur.
            """)
        .contains(
            "  REFUSE   Total réel des projets (admin uniquement)  HTTP 403 Insufficient privileges\n");
  }

  @Test
  void realTotalWithoutPagingIsAlsoUnavailable() {
    fake.override("/api/projects/search", q -> ok(Map.of("components", List.of())));
    assertThat(runCsv(LEAN).out()).contains("  Total réel indisponible");
  }

  @Test
  void equalOrSmallerRealTotalIsACompleteScope() {
    fake.realTotal = 4;
    assertThat(runCsv(LEAN).out())
        .contains("  Projets réellement présents    : 4\n  → Périmètre complet.\n");
  }

  @Test
  void smallerRealTotalIsACompleteScopeToo() {
    fake.realTotal = 3;
    assertThat(runCsv(LEAN).out())
        .contains("  Projets réellement présents    : 3\n  → Périmètre complet.\n");
  }

  @Test
  void oneHiddenProjectOutOfManyIsRoundedPercent() {
    fake.realTotal = 7;
    assertThat(runCsv(LEAN).out())
        .contains(
            "  → 3 projet(s) hors de ton périmètre. Ton classement sera tronqué\n    de 43% sans");
  }

  @Test
  void unanswerableMetricListKeepsEveryMetric() throws IOException {
    fake.override("/api/metrics/search", q -> error(403, "no"));
    Cli.Result r = runCsv(LEAN);

    assertThat(r.out()).doesNotContain("Métriques inconnues");
    assertThat(fake.server().requests("/api/measures/search").getLast().param("metricKeys"))
        .contains(",test_errors,");
    assertThat(cell("alpha-service", "test_errors")).isEqualTo("9");
    assertThat(fake.server().requests("/api/metrics/search")).hasSize(1);
  }

  @Test
  void metricListWithoutPagingIsReadUntilAnEmptyPage() {
    fake.metricsPaging = false;
    runCsv(LEAN);
    assertThat(fake.server().requests("/api/metrics/search").stream().map(q -> q.param("p")))
        .containsExactly("1", "2", "3");
  }

  @Test
  void metricListIsReadAtMostTenPages() {
    fake.metricsPaging = false;
    for (int i = 0; i < 300; i++) fake.metrics.add("extra_" + i);
    runCsv(LEAN);
    assertThat(fake.server().requests("/api/metrics/search")).hasSize(10);
  }

  @Test
  void anEmptyMetricListKeepsEveryMetric() {
    fake.override("/api/metrics/search", q -> ok(Map.of("metrics", List.of())));
    runCsv(LEAN);
    assertThat(fake.server().requests("/api/measures/search").getLast().param("metricKeys"))
        .contains(",test_errors,");
  }

  @Test
  void withoutTrendMetricsThereIsNoTrendPass() throws IOException {
    fake.metrics.removeAll(List.of("violations", "ncloc", "sqale_debt_ratio"));
    Cli.Result r = runCsv("--concurrency", "1");
    assertThat(r.out()).doesNotContain("Tendance des issues");
    assertThat(historyCsv()).doesNotExist();
    assertThat(cell("alpha-service", "tendance_analyses")).isEmpty();
  }

  @Test
  void onlySomeTrendMetricsAreAskedWhenOnlySomeAreKnown() {
    fake.metrics.remove("sqale_debt_ratio");
    runCsv("--concurrency", "1", "--main-branch-only", "--no-bindings");
    assertThat(fake.server().requests("/api/measures/search_history").get(1).param("metrics"))
        .isEqualTo("violations,ncloc");
  }

  @Test
  void refusedMeasuresLeaveEveryMainBranchProjectWithoutCoverage() throws IOException {
    fake.override("/api/measures/search", q -> error(403, "no"));
    Cli.Result r = runCsv("--no-bindings", "--no-trend");
    assertThat(r.out()).contains("  Sans aucune donnée de couverture: 2\n");
    assertThat(cell("alpha-service", "ncloc")).isEmpty();
    assertThat(cell("beta-api", "ncloc")).isEqualTo("800");
  }

  @Test
  void measuresAreAskedTenProjectsAtATime() {
    fake.projects.clear();
    for (int i = 0; i < 12; i++) fake.project("p-%02d".formatted(i), "P " + i, null);
    runCsv(LEAN);
    assertThat(
            fake.server().requests("/api/measures/search").stream()
                .map(q -> q.param("projectKeys")))
        .containsExactly("p-00", "p-00,p-01,p-02,p-03,p-04,p-05,p-06,p-07,p-08,p-09", "p-10,p-11");
  }

  @Test
  void unreadableBranchMeasuresAreEmpty() throws IOException {
    fake.measures.remove("beta-api@develop");
    fake.override("/api/measures/component", q -> Response.json("{\"component\":null}"));
    runCsv("--no-bindings", "--no-trend");
    assertThat(cell("beta-api", "ncloc")).isEmpty();
    assertThat(cell("beta-api", "branch")).isEqualTo("develop");
  }

  @Test
  void branchListWithoutBranchesKeepsTheProjectAsIs() throws IOException {
    fake.override("/api/project_branches/list", q -> ok(Map.of()));
    Cli.Result r = runCsv("--no-bindings", "--no-trend");
    assertThat(r.out()).contains("  Dernier scan sur une branche non principale : 0\n");
    assertThat(r.out()).doesNotContain("dont 0 sans");
    assertThat(cell("beta-api", "branch")).isEmpty();
    assertThat(cell("beta-api", "is_main_branch")).isEmpty();
  }

  @Test
  void bindingVariantsAreNormalized() throws IOException {
    fake.bindings.put("alpha-service", Response.json("not json"));
    fake.bindings.put("beta-api", ok(Map.of("alm", "AzUrE")));
    fake.bindings.put("gamma-lib", Response.status(500, "boom"));
    fake.links.put("alpha-service", Response.json("[]"));
    fake.links.put(
        "beta-api", ok(Map.of("links", List.of(Map.of("url", "https://x.example.test")))));
    Cli.Result r = runCsv("--no-trend");

    assertThat(List.of(cell("alpha-service", "alm"), cell("alpha-service", "liens")))
        .containsExactly("", "");
    assertThat(
            List.of(
                cell("beta-api", "alm"),
                cell("beta-api", "alm_repository"),
                cell("beta-api", "liens")))
        .containsExactly("azure", "", "autre https://x.example.test");
    assertThat(cell("gamma-lib", "alm")).isEqualTo("illisible (HTTP 500)");
    assertThat(r.out())
        .contains("  Liés à une plateforme DevOps   : 1 / 4  {azure=1}\n")
        .contains("    dont 2 liaison(s) illisible(s) : lecture refusée, pas absente\n")
        .contains("  Avec au moins un lien saisi    : 1 / 4\n");
  }

  @Test
  void noBindingAnywhereShowsNoPlatformBreakdown() {
    fake.bindings.clear();
    fake.links.clear();
    Cli.Result r = runCsv("--no-trend");
    assertThat(r.out())
        .contains(
            "  Liés à une plateforme DevOps   : 0 / 4\n  Avec au moins un lien saisi    : 0 / 4\n");
  }
}
