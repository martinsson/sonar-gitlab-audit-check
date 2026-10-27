package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

import static ch.ge.edg.sonar.gitlab.audit.cli.storyinspection.StoryFixtures.GOOD_DESCRIPTION;
import static ch.ge.edg.sonar.gitlab.audit.cli.storyinspection.StoryFixtures.issueJson;
import static ch.ge.edg.sonar.gitlab.audit.cli.storyinspection.StoryFixtures.searchJson;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoryInspectionTest {

  @TempDir Path dir;

  private static final String DESCRIPTION = GOOD_DESCRIPTION.strip().replace("\n", " ");
  private static final String PAGE_1 =
      searchJson(
          3,
          issueJson("DEMO-1", "Déposer une demande en ligne", DESCRIPTION),
          issueJson("DEMO-2", "", ""));
  private static final String PAGE_2 = searchJson(3, issueJson("DEMO-3", "Court", DESCRIPTION));

  private Cli.Result run(String... args) {
    return Cli.run(StoryInspection::new, args);
  }

  private FakeHttpServer jira() {
    FakeHttpServer jira = new FakeHttpServer();
    jira.on(
        "/rest/api/2/search", r -> Response.json("0".equals(r.param("startAt")) ? PAGE_1 : PAGE_2));
    return jira;
  }

  @Test
  void inspectsTheProjectOverTwoPagesAndWritesTheReport() throws IOException {
    Path out = dir.resolve("rapport.md");
    try (FakeHttpServer jira = jira()) {
      Cli.Result r =
          run(
              "--url",
              jira.url(),
              "--token",
              "t",
              "--project-key",
              "DEMO",
              "--max-results",
              "2",
              "--out",
              out.toString(),
              "--dump-dir",
              dir.resolve("dump").toString());
      assertThat(r.exitCode()).isZero();
      assertThat(r.err()).isEmpty();
      assertThat(r.out())
          .startsWith("Projet : DEMO\nPoints : [1, 2, 3, 4, 5, 6]\n\n# Rapport d'Inspection")
          .contains("Rapport écrit dans: " + out)
          .contains("=== Résumé ===\nTotal stories: 3\n100% conformes: 0\n")
          .endsWith("Non-conformités critiques: 3\nNon-conformités mineures: 0\n");
      assertThat(jira.requests("/rest/api/2/search")).hasSize(2);
    }
    assertThat(Files.readString(out)).contains("## Story : DEMO-3 - Court");
    assertThat(dir.resolve("dump").resolve("search_0.json")).hasContent(PAGE_1);
    assertThat(dir.resolve("dump").resolve("search_2.json")).hasContent(PAGE_2);
  }

  @Test
  void replaysADumpWithoutCallingJiraNorNeedingCredentials() throws IOException {
    Files.writeString(dir.resolve("search_0.json"), PAGE_1);
    Files.writeString(dir.resolve("search_2.json"), PAGE_2);
    Files.writeString(dir.resolve("autre.json"), "pas une page");
    Cli.Result r = run("--project-key", "DEMO", "--replay-dir", dir.toString(), "--points", "1,2");
    assertThat(r.exitCode()).isZero();
    assertThat(r.out())
        .contains("Mode replay depuis: " + dir)
        .contains("Points : [1, 2]")
        .contains("Total stories: 3");
  }

  @Test
  void anEmptyReplayDirectoryStopsTheCommand() {
    Cli.Result r = run("--project-key", "DEMO", "--replay-dir", dir.toString());
    assertThat(r.exitCode()).isEqualTo(2);
    assertThat(r.err()).isEqualTo("Arrêt : Aucun fichier de replay trouvé dans " + dir + "\n");
  }

  @Test
  void missingCredentialsStopTheCommand() {
    Cli.Result r = run("--project-key", "DEMO");
    assertThat(r.exitCode()).isEqualTo(2);
    assertThat(r.err()).isEqualTo("Arrêt : JIRA_URL manquant\n");
  }

  @Test
  void aBadPointsOptionStopsTheCommand() {
    Cli.Result r = run("--project-key", "DEMO", "--points", "x");
    assertThat(r.exitCode()).isEqualTo(2);
    assertThat(r.err()).contains("--points");
  }

  @Test
  void noPointToCheckExitsWithOne() {
    Cli.Result r = run("--project-key", "DEMO", "--points", "5-3");
    assertThat(r.exitCode()).isEqualTo(1);
    assertThat(r.out()).isEqualTo("Aucun point à vérifier.\n");
  }

  @Test
  void aRefusedSearchIsReportedAndLeavesNoStory() {
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.on("/rest/api/2/search", r -> Response.status(403, "{\"message\":\"interdit\"}"));
      Cli.Result r = run("--url", jira.url(), "--token", "t", "--project-key", "DEMO");
      assertThat(r.exitCode()).isEqualTo(1);
      assertThat(r.err()).contains("Erreur Jira: HTTP 403 interdit").contains("Aucune story");
    }
  }
}
