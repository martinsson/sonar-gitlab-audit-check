package ch.ge.edg.sonar.gitlab.audit.cli;

import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** One test per option: what it changes in the report, the files and the calls made. */
class SonarAuditCheckOptionsTest extends SonarAuditCheckTestBase {

  @Test
  void commaWritesMachineCsvWithoutBomAndWithLf() throws IOException {
    Cli.Result r = runCsv("--comma");

    assertThat(r.out()).contains("  Format machine (virgule, sans BOM).\n");
    String inventory = Files.readString(csv(), StandardCharsets.UTF_8);
    assertThat(inventory)
        .startsWith("\"key\",\"name\",")
        .doesNotContain("\r")
        .doesNotContain(";\"");
    expectSelfie(fake.normalize(inventory)).toMatchDisk("inventory");
    expectSelfie(fake.normalize(Files.readString(historyCsv(), StandardCharsets.UTF_8)))
        .toMatchDisk("history");
  }

  @Test
  void noBindingsSkipsBindingAndLinkCallsAndLeavesTheirColumnsEmpty() throws IOException {
    Cli.Result r = runCsv("--no-bindings", "--concurrency", "1");

    assertThat(r.out()).doesNotContain("Liés à une plateforme").doesNotContain("lien saisi");
    // Only the sample probe of section 2 still asks.
    assertThat(fake.server().requests("/api/alm_settings/get_binding")).hasSize(1);
    assertThat(fake.server().requests("/api/project_links/search")).hasSize(1);
    for (String key : new String[] {"alpha-service", "delta-legacy"}) {
      assertThat(cell(key, "alm")).isEmpty();
      assertThat(cell(key, "alm_repository")).isEmpty();
      assertThat(cell(key, "liens")).isEmpty();
    }
    assertThat(cell("alpha-service", "tendance_analyses")).isEqualTo("4");
  }

  @Test
  void noTrendSkipsHistoryCallsColumnsAndFile() throws IOException {
    Cli.Result r = runCsv("--no-trend", "--concurrency", "1");

    assertThat(r.out()).doesNotContain("Tendance des issues").doesNotContain("Historique écrit");
    assertThat(historyCsv()).doesNotExist();
    // The probe (section 2) and the debt velocity (section 4) only.
    assertThat(fake.server().requests("/api/measures/search_history")).hasSize(2);
    assertThat(cell("alpha-service", "tendance_analyses")).isEmpty();
    assertThat(cell("alpha-service", "violations_pente_mois")).isEmpty();
    assertThat(cell("alpha-service", "alm")).isEqualTo("gitlab");
  }

  @Test
  void mainBranchOnlyReadsSearchProjectsAsIs() throws IOException {
    Cli.Result r = runCsv("--main-branch-only");

    assertThat(fake.server().requests("/api/project_branches/list")).isEmpty();
    assertThat(r.out()).doesNotContain("branche non principale");
    expectSelfie(stable(r.out())).toMatchDisk("stdout");
    expectSelfie(excelCsv(csv())).toMatchDisk("inventory");
  }

  @Test
  void projectChoosesTheSampleInsteadOfTheFirstVisible() {
    Cli.Result r = runCsv("--project", "beta-api", "--concurrency", "1");

    assertThat(r.out())
        .contains("  Projet témoin : beta-api\n")
        .contains("=== 4. Signaux d'activité disponibles sans Git (sur beta-api) ");
    FakeHttpServer server = fake.server();
    assertThat(server.requests("/api/components/search_projects").getFirst().param("ps"))
        .isEqualTo("1");
    assertThat(server.requests("/api/components/search_projects")).hasSize(3);
    assertThat(server.requests("/api/qualitygates/get_by_project").getFirst().param("project"))
        .isEqualTo("beta-api");
  }

  @Test
  void organizationIsSentWhereSonarCloudWantsIt() {
    runCsv("--organization", "org-a", "--concurrency", "1");
    FakeHttpServer server = fake.server();

    assertThat(server.requests("/api/components/search_projects"))
        .allSatisfy(q -> assertThat(q.param("organization")).isEqualTo("org-a"));
    assertThat(server.requests("/api/projects/search").getFirst().param("organization"))
        .isEqualTo("org-a");
    assertThat(server.requests("/api/measures/search"))
        .allSatisfy(q -> assertThat(q.param("organization")).isNull());
  }

  @Test
  void concurrencyOneDropsTheInFlightMention() {
    Cli.Result r = runCsv("--concurrency", "1");
    assertThat(r.out()).contains("  49 appels API effectués.\n");
  }

  @Test
  void concurrencyIsReportedInTheSummary() {
    Cli.Result r = runCsv("--concurrency", "3");
    assertThat(r.out()).contains("  49 appels API effectués (3 en vol).\n");
  }

  @Test
  void staleAndActivityDaysMoveTheThresholdsAndWindows() {
    Cli.Result r = runCsv("--stale-days", "100", "--activity-days", "45");

    assertThat(r.out())
        .contains("  Analysés il y a < 100 j        : 3\n")
        .contains("  Analysés il y a > 100 j        : 0\n")
        .contains("  Tendance des issues/kLOC sur 45 j (2 projets mesurables) :\n")
        .contains("  Analyses sur 45 j            : 4\n")
        .contains("  Auteurs distincts (45 j)      : 2\n")
        .contains("  Δ lignes sur 45 j (50 j mesurés) : +200\n")
        .contains("  Δ dette sur 45 j (min)       : +150\n");
  }

  @Test
  void staleThresholdIsStrict() {
    Cli.Result r = runCsv("--stale-days", "99");
    assertThat(r.out())
        .contains("  Analysés il y a < 99 j        : 2\n")
        .contains("  Analysés il y a > 99 j        : 1\n");
  }

  @Test
  void exclusionsFileRemovesProjectsAndSaysSo() throws IOException {
    Path ex = tmp.resolve("exclusions.txt");
    Files.writeString(ex, "# legacy\ndelta-*   # gone\n\n  gamma-lib\nnot-there\n");
    Cli.Result r = runCsv("--exclusions", ex.toString());

    assertThat(r.out())
        .contains("  Projets visibles avec ce token : 4\n  Projets réellement présents    : 6\n")
        .contains("  Exclus par " + ex + " : 2 (3 entrées)\n")
        .contains("  Liés à une plateforme DevOps   : 1 / 2  {gitlab=1}\n");
    assertThat(excelCsv(csv()).lines().map(l -> l.substring(0, l.indexOf(';'))))
        .containsExactly("\"key\"", "\"alpha-service\"", "\"beta-api\"");
  }

  @Test
  void insecureAndTimeoutOptionsAreAccepted() {
    Cli.Result r = runCsv("--insecure", "--timeout", "7");
    assertThat(r.exitCode()).isZero();
    assertThat(r.out()).contains("  OK       api/system/status ");
  }

  @Test
  void colorAlwaysPaintsTheReportAndShowsProgress() {
    Cli.Result r = runCsv("--color", "always", "--concurrency", "1");

    assertThat(r.out())
        .contains("\033[1m\nInstance : " + fake.server().url() + "\033[0m")
        .contains("  \033[32mOK      \033[0m api/system/status ")
        .contains("  Branches lues 0/4\r  Branches lues 4/4\r  Branches lues 4/4\n")
        .contains("  Mesures de branche 0/2\r  Mesures de branche 2/2\r  Mesures de branche 2/2\n")
        .contains("  Liaisons et liens 4/4\n")
        .contains("  Historiques lus 4/4\n");
    expectSelfie(stable(r.out())).toMatchDisk("stdout");
  }

  @Test
  void helpPrintsTheUsage() {
    Cli.Result r = Cli.run(new SonarAuditCheck(), "--help");
    assertThat(r.exitCode()).isZero();
    expectSelfie(r.out()).toMatchDisk("usage");
  }
}
