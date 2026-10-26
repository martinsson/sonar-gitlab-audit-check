package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** DoraProject end to end, on fake GitLab, GoCD and Jira servers telling the same story. */
class DoraProjectTest {

  private static final Clock CLOCK = Clock.fixed(Fakes.NOW, ZoneId.of("Europe/Zurich"));

  @TempDir Path dir;

  private Cli.Result run(List<String> args) {
    List<String> all = new ArrayList<>(List.of("--out-dir", dir.toString()));
    all.addAll(args);
    return Cli.run(CLOCK, DoraProject::new, all.toArray(String[]::new));
  }

  private Cli.Result run(String... args) {
    return run(List.of(args));
  }

  private static List<String> gitlab(FakeHttpServer gitlab) {
    return List.of(
        "--gitlab-project",
        "octocat/Hello-World",
        "--gitlab-url",
        gitlab.url(),
        "--gitlab-token",
        "glpat");
  }

  private static List<String> gocd(FakeHttpServer gocd, String pipeline) {
    return List.of("--gocd-pipeline", pipeline, "--gocd-url", gocd.url(), "--gocd-token", "gocd");
  }

  private static List<String> jira(FakeHttpServer jira) {
    return List.of("--jira", "ABC", "--jira-url", jira.url(), "--jira-token", "jira");
  }

  @SafeVarargs
  private static List<String> args(List<String>... parts) {
    List<String> all = new ArrayList<>();
    for (List<String> part : parts) all.addAll(part);
    return all;
  }

  private String file(String name) throws IOException {
    return Files.readString(dir.resolve(name), StandardCharsets.UTF_8);
  }

  /** The fake servers' URLs, whose port changes from run to run, as fixed names. */
  private static String fixed(String text, FakeHttpServer gocd, FakeHttpServer jira) {
    return text.replace(gocd.url(), "https://gocd.test").replace(jira.url(), "https://jira.test");
  }

  @Test
  void gitlabGocdAndJiraGiveTheFourMeasuresThePageAndTheImport() throws IOException {
    try (FakeHttpServer gitlab = Fakes.gitlab();
        FakeHttpServer gocd = Fakes.gocd();
        FakeHttpServer jira = Fakes.jira()) {
      Cli.Result r = run(args(gitlab(gitlab), gocd(gocd, "prod"), jira(jira)));
      assertThat(r.exitCode()).as(r.err()).isZero();
      expectSelfie(r.out().replace(dir.toString(), "<dir>")).toMatchDisk("stdout");
      assertThat(r.err())
          .contains("Projet GitLab : octocat/Hello-World (id 42)")
          .contains("3 merge requests fusionnées sur main")
          .contains("prod : 3 runs")
          .contains(
              "Jira : 3 tickets lus, 2 incidents retenus. Types vus : {Bug=1, Incident=1, Story=1}");
      expectSelfie(fixed(file("middleware-import.sql"), gocd, jira)).toMatchDisk("sql");
      expectSelfie(fixed(file("dora-donnees.json"), gocd, jira)).toMatchDisk("json");
      String html = fixed(file("dora.html"), gocd, jira);
      thePageShowsTheMeasuresAndEscapesTheText(html);
      expectSelfie(html).toMatchDisk("html");
      assertThat(jira.requests().get(0).param("jql"))
          .isEqualTo("project in (\"ABC\") AND created >= -90d ORDER BY created ASC");
      assertThat(gitlab.requests().get(1).param("updated_after")).isEqualTo("2026-07-23T00:00:00Z");
    }
  }

  private static void thePageShowsTheMeasuresAndEscapesTheText(String html) {
    assertThat(html)
        .contains("<title>DORA octocat/Hello-World</title>")
        .contains("<div class=\"v\">0,2 / sem.</div>")
        .contains("<div class=\"v\">26,5 h</div>")
        .contains("<div class=\"v\">33 %</div>")
        .contains("<div class=\"v\">4,0 h</div>")
        .contains("Deuxième &lt;b&gt;")
        .contains("Premier l&#39;essai")
        .contains("un ticket de type Incident ou Bug créé dans ABC")
        .contains("<script type=\"application/json\" id=\"donnees\">")
        .doesNotContain("Trop ancienne");
  }

  @Test
  void withoutGocdEachMergeIsADeployAndWithoutJiraThereIsNoIncident() throws IOException {
    try (FakeHttpServer gitlab = Fakes.gitlab()) {
      Cli.Result r = run(gitlab(gitlab));
      assertThat(r.exitCode()).as(r.err()).isZero();
      expectSelfie(r.out().replace(dir.toString(), "<dir>")).toMatchDisk("stdout");
      String sql = file("middleware-import.sql");
      assertThat(sql)
          .contains("-- Pas de GoCD : Middleware compte les merge requests fusionnées (PR_MERGE).")
          .doesNotContain("RepoWorkflow")
          .doesNotContain("Incident");
      assertThat(file("dora.html"))
          .contains("une merge request fusionnée sur main (pas de GoCD)")
          .contains("Pas de projet Jira donné : ni taux d&#39;échec ni rétablissement.");
    }
  }

  @Test
  void onlyTheMostRecentMergeRequestsAreReadPastTheCap() throws IOException {
    try (FakeHttpServer gitlab = Fakes.gitlab()) {
      List<String> cap = List.of("--max-mrs", "2");
      Cli.Result r = run(args(gitlab(gitlab), cap));
      assertThat(r.exitCode()).as(r.err()).isZero();
      assertThat(r.err())
          .contains("3 merge requests fusionnées : seules les 2 plus récentes sont lues");
      assertThat(gitlab.requests("/api/v4/projects/42/merge_requests/1/commits")).isEmpty();
      assertThat(file("dora.html")).contains("Merge requests fusionnées (2)");
    }
  }

  @Test
  void aSnapshotIsRenderedAgainWithoutAnyCall() throws IOException {
    try (FakeHttpServer gitlab = Fakes.gitlab();
        FakeHttpServer gocd = Fakes.gocd();
        FakeHttpServer jira = Fakes.jira()) {
      List<String> query = List.of("--incident-jql", "project = ABC AND labels = prod");
      run(args(gitlab(gitlab), gocd(gocd, "prod"), jira(jira), query));
    }
    String html = file("dora.html");
    String sql = file("middleware-import.sql");
    assertThat(html).contains("les tickets de la requête « project = ABC AND labels = prod »");
    Path copy = dir.resolve("copie");
    Files.createDirectories(copy);
    Files.copy(dir.resolve("dora-donnees.json"), copy.resolve("dora-donnees.json"));
    Cli.Result again =
        Cli.run(CLOCK, DoraProject::new, "--from", copy.resolve("dora-donnees.json").toString());
    assertThat(again.exitCode()).as(again.err()).isZero();
    assertThat(Files.readString(copy.resolve("dora.html"))).isEqualTo(html);
    assertThat(Files.readString(copy.resolve("middleware-import.sql"))).isEqualTo(sql);
  }

  @Test
  void withoutAProjectOrAGitlabTokenNothingIsDone() {
    Cli.Result none = run();
    assertThat(none.exitCode()).isEqualTo(2);
    assertThat(none.err()).contains("Arrêt : donner --gitlab-project, ou --from");
    Cli.Result noToken = run("--gitlab-project", "a/b", "--gitlab-url", "http://127.0.0.1:9");
    assertThat(noToken.exitCode()).isEqualTo(2);
    assertThat(noToken.err()).contains("GITLAB_TOKEN");
  }

  @Test
  void anUnknownProjectStops() {
    try (FakeHttpServer gitlab = new FakeHttpServer()) {
      Cli.Result r =
          run("--gitlab-project", "x/y", "--gitlab-url", gitlab.url(), "--gitlab-token", "glpat");
      assertThat(r.exitCode()).isEqualTo(2);
      assertThat(r.err()).contains("Arrêt : /api/v4/projects/x%2Fy : HTTP 404");
    }
  }

  @Test
  void aJiraSearchRefusedStopsWithWhatJiraSaid() {
    try (FakeHttpServer gitlab = Fakes.gitlab();
        FakeHttpServer jira = new FakeHttpServer()) {
      jira.on(
          "/rest/api/2/search",
          q -> FakeHttpServer.Response.status(400, "{\"errorMessages\":[\"champ inconnu\"]}"));
      Cli.Result r = run(args(gitlab(gitlab), jira(jira)));
      assertThat(r.exitCode()).isEqualTo(2);
      assertThat(r.err()).contains("Arrêt : Jira search : ").contains("champ inconnu");
    }
  }

  @Test
  void anUnreadableGocdHistoryStops() {
    try (FakeHttpServer gitlab = Fakes.gitlab();
        FakeHttpServer gocd = new FakeHttpServer()) {
      Cli.Result r = run(args(gitlab(gitlab), gocd(gocd, "inconnu")));
      assertThat(r.exitCode()).isEqualTo(2);
      assertThat(r.err()).contains("Arrêt : inconnu : historique illisible");
    }
  }
}
