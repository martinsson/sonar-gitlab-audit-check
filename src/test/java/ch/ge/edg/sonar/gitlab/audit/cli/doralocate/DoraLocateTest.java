package ch.ge.edg.sonar.gitlab.audit.cli.doralocate;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * DoraLocate end to end: a hand-written GitLab and Jira (there is no real instance to capture), and
 * the GoCD 25.3 captures.
 */
class DoraLocateTest {

  private static final Clock NOW =
      Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"), ZoneOffset.UTC);
  private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
  private static final String PROJECT =
      "{\"id\":7,\"path\":\"Hello-World\",\"path_with_namespace\":\"octocat/Hello-World\"}";
  private static final String P = "/api/v4/projects/7";

  @TempDir Path dir;
  private Path index;
  private FakeHttpServer gitlab;
  private FakeHttpServer jira;
  private FakeHttpServer gocd;
  private Response integration = Response.status(403, "{\"message\":\"403\"}");
  private String commits = commits(10, "ABC-12 corrige", "UTF-8 conversion");
  private String mrs =
      "[{\"title\":\"ABC-12 calcul\",\"source_branch\":\"feature/ABC-12\",\"description\":null}]";
  private String branches = "[{\"name\":\"feature/abc-14-x\"},{\"name\":\"main\"}]";
  private Function<FakeHttpServer.Request, Response> jiraProject =
      r ->
          switch (r.path()) {
            case "/rest/api/2/project/ABC" -> Response.json("{\"name\":\"Projet ABC\"}");
            case "/rest/api/2/project/XYZ" -> Response.json("{\"name\":\"Projet XYZ\"}");
            default -> Response.status(404, "{}");
          };

  @BeforeEach
  void servers() {
    index = dir.resolve("idx/gocd-index.json");
    gitlab = new FakeHttpServer();
    jira = new FakeHttpServer();
    gocd = GocdFixtures.serve(new FakeHttpServer());
    gitlab.json("/api/v4/projects/octocat%2FHello-World", PROJECT);
    gitlab.json(P, PROJECT);
    gitlab.on(P + "/integrations/jira", r -> integration);
    gitlab.on(P + "/repository/commits", r -> Response.json(commits));
    gitlab.on(P + "/merge_requests", r -> Response.json(mrs));
    gitlab.on(P + "/repository/branches", r -> Response.json(branches));
    jira.onPrefix("/rest/api/2/project/", r -> jiraProject.apply(r));
    for (String p :
        List.of("hello-build", "hello-deploy-recette", "hello-deploy-prod", "other-build")) {
      String capture = "go_api_pipelines_" + p.replace('-', '_') + "_history_page_size_100";
      gocd.on(
          "/go/api/pipelines/" + p + "/history", r -> Response.json(GocdFixtures.fixture(capture)));
    }
  }

  @AfterEach
  void close() {
    gitlab.close();
    jira.close();
    gocd.close();
  }

  /**
   * Twenty commits: {@code abc} citing ABC-12, one citing {@code other}, one whose only text is a
   * title citing XYZ-3, the rest without key.
   */
  private static String commits(int abc, String abcText, String other) {
    List<String> c = new ArrayList<>();
    c.addAll(Collections.nCopies(abc, "{\"message\":\"" + abcText + "\"}"));
    c.add("{\"message\":\"" + other + "\"}");
    c.add("{\"title\":\"XYZ-3 doc\"}");
    while (c.size() < 20) c.add("{\"message\":\"sans ticket\",\"title\":\"ABC-99\"}");
    return "[" + String.join(",", c) + "]";
  }

  /** The usual arguments; an option given in {@code extra} replaces its usual value. */
  private Cli.Result locate(
      @Nullable FakeHttpServer withJira, @Nullable FakeHttpServer withGocd, String... extra) {
    Map<String, String> options = new LinkedHashMap<>();
    options.put("--gitlab-project", "octocat/Hello-World");
    options.put("--gitlab-url", gitlab.url());
    options.put("--gitlab-token", "gl");
    options.put("--jira-url", withJira == null ? "" : withJira.url());
    options.put("--jira-token", "good");
    options.put("--gocd-url", withGocd == null ? "" : withGocd.url());
    options.put("--gocd-token", "pat");
    options.put("--gocd-index", index.toString());
    List<String> flags = new ArrayList<>();
    for (int i = 0; i < extra.length; i++) {
      if (options.containsKey(extra[i]) || "--max-commits".equals(extra[i])) {
        options.put(extra[i], extra[++i]);
      } else {
        flags.add(extra[i]);
      }
    }
    List<String> args = new ArrayList<>(flags);
    options.forEach((k, v) -> args.addAll(List.of(k, v)));
    return Cli.run(new DoraLocate(NOW, ZURICH), args.toArray(String[]::new));
  }

  /** A prefix line: commits, MR, branches, tickets, then the Jira project. */
  private static String row(String prefix, String name, int... counts) {
    return "  %-12s %8d %5d %8d %8d  %s"
        .formatted(prefix, counts[0], counts[1], counts[2], counts[3], name);
  }

  private static String pipe(String name, String role, String platform, String last) {
    return "  %-40s %-20s %-18s %-12s %s".formatted(name, "hello", role, platform, last);
  }

  @Test
  void a_project_gets_its_jira_project_its_pipelines_and_the_commands_to_extract_them()
      throws IOException {
    Cli.Result r = locate(jira, gocd);
    assertThat(r.exitCode()).isZero();
    assertThat(r.out().lines())
        .containsExactly(
            "Projet GitLab : octocat/Hello-World (id 7)",
            "",
            "== Jira",
            "  Intégration Jira de GitLab : illisible (HTTP 403, droit Maintainer requis)",
            "  Lus : 20 commits, 1 MR, 2 branches sur 365 jours",
            "  %-12s %8s %5s %8s %8s  %s"
                .formatted("préfixe", "commits", "MR", "branches", "tickets", "projet Jira"),
            row("ABC", "Projet ABC", 10, 1, 1, 2),
            row("UTF", "(pas un projet Jira visible)", 1, 0, 0, 1),
            row("XYZ", "Projet XYZ", 1, 0, 0, 1),
            "  Le préfixe le plus cité l'est dans 50 % des commits.",
            "",
            "== GoCD",
            "  Index : 4 pipelines visibles (dont 0 sans run lisible, donc sans matériau connu),"
                + " construit le 2026-10-21 02:00:00",
            "  %-40s %-20s %-18s %-12s %s"
                .formatted("pipeline", "groupe", "rôle", "plateforme?", "dernier run"),
            pipe("hello-build", "dépôt direct", "", "2026-10-20 22:43:26"),
            pipe("hello-deploy-recette", "aval niveau 1", "recette", "2026-10-20 22:43:39"),
            pipe("hello-deploy-prod", "aval niveau 2", "prod", "2026-10-20 22:44:31"),
            "  Tous dans le groupe « hello » : --group hello prendrait aussi les autres pipelines"
                + " du groupe.",
            "",
            "== Extractions proposées (à vérifier avant de lancer)",
            "  jbang dora/JiraExtract.java --project ABC --out-dir extraction/Hello-World/jira",
            "  jbang dora/GocdExtract.java --pipeline"
                + " hello-build,hello-deploy-recette,hello-deploy-prod"
                + " --out-dir extraction/Hello-World/gocd");
    FakeHttpServer.Request commits = gitlab.requests(P + "/repository/commits").get(0);
    assertThat(commits.param("since")).isEqualTo("2025-10-21T00:00:00Z");
    assertThat(commits.param("all")).isEqualTo("true");
    assertThat(commits.header("PRIVATE-TOKEN")).isEqualTo("gl");
    assertThat(gitlab.requests(P + "/merge_requests").get(0).param("updated_after"))
        .isEqualTo("2025-10-21T00:00:00Z");
    assertThat(jira.requests().get(0).header("Authorization")).isEqualTo("Bearer good");
    assertThat(jira.requests()).hasSize(3);
  }

  @Test
  void the_index_keeps_each_pipeline_with_its_materials_and_last_run() throws IOException {
    assertThat(locate(null, gocd).exitCode()).isZero();
    JsonNode saved = new ObjectMapper().readTree(index.toFile());
    assertThat(saved.path("built_at").asText()).isEqualTo("2026-10-21 02:00:00");
    assertThat(saved.path("pipelines").path("hello-deploy-recette").toString())
        .isEqualTo(
            "{\"group\":\"hello\",\"git\":[],\"upstream\":[\"hello-build\"],"
                + "\"last_run\":\"2026-10-20 22:43:39\"}");
    assertThat(saved.path("pipelines").path("other-build").path("git").get(0).asText())
        .isEqualTo("https://github.com/octocat/Spoon-Knife.git");
    assertThat(gocd.requests("/go/api/pipelines/hello-build/history").get(0).param("page_size"))
        .isEqualTo("10");
  }

  @Test
  void an_index_younger_than_a_day_is_reused_an_older_one_rebuilt() throws IOException {
    assertThat(locate(null, gocd).exitCode()).isZero();
    int built = gocd.requests().size();

    Files.setLastModifiedTime(index, FileTime.from(NOW.instant().minus(Duration.ofHours(23))));
    Cli.Result reused = locate(null, gocd);
    assertThat(reused.out()).contains("construit le 2026-10-21 02:00:00", "aval niveau 2");
    assertThat(gocd.requests()).hasSize(built);

    Cli.Result refreshed = locate(null, gocd, "--refresh-index");
    assertThat(refreshed.exitCode()).isZero();
    assertThat(gocd.requests()).hasSize(2 * built);

    Files.setLastModifiedTime(index, FileTime.from(NOW.instant().minus(Duration.ofHours(25))));
    assertThat(locate(null, gocd).exitCode()).isZero();
    assertThat(gocd.requests()).hasSize(3 * built);
  }

  @Test
  void an_unreadable_index_is_rebuilt_and_an_unwritable_one_does_not_stop_the_search()
      throws IOException {
    Files.createDirectories(index.getParent());
    Files.writeString(index, "{ pas du json");
    Files.setLastModifiedTime(index, FileTime.from(NOW.instant().minus(Duration.ofHours(1))));
    Cli.Result r = locate(null, gocd);
    assertThat(r.exitCode()).isZero();
    assertThat(r.err()).contains("Index illisible, reconstruit : ");
    assertThat(r.out()).contains("aval niveau 2");

    Path blocked = dir.resolve("file");
    Files.writeString(blocked, "");
    Cli.Result w = locate(null, gocd, "--gocd-index", blocked.resolve("idx.json").toString());
    assertThat(w.exitCode()).isZero();
    assertThat(w.err()).contains("Index non écrit : ");
    assertThat(w.out()).contains("aval niveau 2");
  }

  @Test
  void without_jira_nor_gocd_only_the_project_is_shown() {
    Cli.Result r = locate(null, null);
    assertThat(r.exitCode()).isZero();
    assertThat(r.out().lines())
        .containsExactly(
            "Projet GitLab : octocat/Hello-World (id 7)",
            "",
            "(Jira ignoré : JIRA_URL / JIRA_TOKEN absents)",
            "",
            "(GoCD ignoré : GOCD_URL / GOCD_TOKEN absents)");
    Cli.Result noToken = locate(jira, gocd, "--jira-token", " ", "--gocd-token", "");
    assertThat(noToken.out()).contains("(Jira ignoré", "(GoCD ignoré");
  }

  @Test
  void the_gitlab_jira_integration_when_readable() {
    integration =
        Response.json(
            "{\"active\":true,\"properties\":{\"project_keys\":[\"ABC\"],\"project_key\":\"ABC\"}}");
    assertThat(locate(jira, null).out())
        .contains(
            "  Intégration Jira active dans GitLab : clé(s) [\"ABC\"] ABC "
                + System.lineSeparator());
  }

  @Test
  void an_inactive_integration_says_so() {
    FakeHttpServer other = new FakeHttpServer();
    try (other) {
      other.json(P, PROJECT);
      other.json("/api/v4/projects/octocat%2FHello-World", PROJECT);
      other.json(P + "/integrations/jira", "{\"active\":false}");
      other.json(P + "/repository/commits", "[]");
      Cli.Result r =
          Cli.run(
              new DoraLocate(NOW, ZURICH),
              "--gitlab-project",
              "7",
              "--gitlab-url",
              other.url(),
              "--gitlab-token",
              "gl",
              "--jira-url",
              jira.url(),
              "--jira-token",
              "good",
              "--gocd-url",
              "",
              "--days",
              "30");
      assertThat(r.out())
          .contains("  Intégration Jira de GitLab : inactive")
          .contains("  Lus : 0 commits, 0 MR, 0 branches sur 30 jours")
          .contains("  Aucune clé de ticket trouvée.")
          .doesNotContain("Extractions proposées");
      assertThat(jira.requests()).isEmpty();
    }
  }

  @Test
  void a_prefix_cited_in_less_than_5_percent_of_commits_gets_no_command_and_15_are_shown() {
    List<String> c = new ArrayList<>();
    for (int i = 0; i < 17; i++) c.add("{\"message\":\"K%c-1\"}".formatted((char) ('A' + i)));
    c.add("{\"message\":\"ABC-1 ABC-2 XYZ-1\"}");
    c.add("{\"message\":\"ABC-3\"}");
    while (c.size() < 41) c.add("{\"message\":\"rien\"}");
    commits = "[" + String.join(",", c) + "]";
    mrs = "[]";
    jiraProject = r -> Response.json("{\"name\":\"Projet\"}");
    Cli.Result r = locate(jira, null, "--max-commits", "41");
    assertThat(r.out())
        .contains(row("ABC", "Projet", 2, 0, 1, 4))
        .contains("dans 5 % des commits")
        .doesNotContain("JiraExtract");
    assertThat(jira.requests()).hasSize(15);
    assertThat(r.out().lines().filter(l -> l.endsWith("  Projet"))).hasSize(15);
  }

  @Test
  void the_first_prefix_at_5_percent_gets_the_only_jira_command() {
    List<String> c = new ArrayList<>();
    c.add("{\"message\":\"ABC-1\"}");
    c.add("{\"message\":\"XYZ-1\"}");
    while (c.size() < 20) c.add("{\"message\":\"rien\"}");
    commits = "[" + String.join(",", c) + "]";
    mrs = "[]";
    branches = "[]";
    Cli.Result r = locate(jira, null);
    assertThat(r.out())
        .contains("--project ABC --out-dir extraction/Hello-World/jira")
        .doesNotContain("--project XYZ");
  }

  @Test
  void keys_cited_only_in_merge_requests_give_no_share_and_no_command() {
    commits = "[]";
    Cli.Result r = locate(jira, null);
    assertThat(r.out())
        .contains(row("ABC", "Projet ABC", 0, 1, 1, 2))
        .doesNotContain("Le préfixe le plus cité")
        .doesNotContain("JiraExtract");
  }

  @Test
  void commits_are_read_up_to_max_commits() {
    Cli.Result r = locate(jira, null, "--max-commits", "5");
    assertThat(r.out())
        .contains("  Lus : 5 commits, 1 MR, 2 branches", row("ABC", "Projet ABC", 5, 1, 1, 2));
  }

  @Test
  void jira_errors_are_shown_and_a_refused_token_stops() {
    jiraProject = r -> Response.status(500, "{}");
    assertThat(locate(jira, null).out()).contains(row("ABC", "HTTP 500", 10, 1, 1, 2));
    FakeHttpServer refusing = new FakeHttpServer();
    try (refusing) {
      refusing.onPrefix("/", r -> Response.status(401, "{}"));
      Cli.Result r = locate(refusing, null);
      assertThat(r.exitCode()).isEqualTo(2);
      assertThat(r.err()).contains("Arrêt : " + refusing.url() + " : jeton refusé (401)");
    }
  }

  @Test
  void an_unknown_project_or_a_refused_gitlab_token_stops() {
    Cli.Result r = locate(null, null, "--gitlab-project", "https://h/nope/x.git");
    assertThat(r.exitCode()).isEqualTo(2);
    assertThat(r.err()).contains("Arrêt : /api/v4/projects/nope%2Fx : HTTP 404");

    integration = Response.status(401, "{}");
    Cli.Result refused = locate(jira, null, "--gitlab-project", "git@h:octocat/Hello-World.git");
    assertThat(refused.exitCode()).isEqualTo(2);
    assertThat(refused.err()).contains("Arrêt : " + gitlab.url() + " : jeton refusé (401)");

    Cli.Result noUrl = Cli.run(new DoraLocate(), "--gitlab-project", "a/b", "--gitlab-url", "");
    assertThat(noUrl.exitCode()).isEqualTo(2);
    assertThat(noUrl.err()).contains("Arrêt : GITLAB_URL manquant");
  }

  @Test
  void a_repository_no_pipeline_builds_is_said_so() {
    gitlab.json(
        "/api/v4/projects/octocat%2FSpoon",
        "{\"id\":8,\"path\":\"Spoon\",\"path_with_namespace\":\"octocat/Spoon\"}");
    Cli.Result r = locate(null, gocd, "--gitlab-project", "octocat/Spoon");
    assertThat(r.out())
        .contains("  Aucun pipeline ne prend octocat/spoon comme dépôt git.")
        .doesNotContain("Extractions proposées");
  }

  @Test
  void pipelines_in_several_groups_and_pipelines_that_never_ran() {
    FakeHttpServer other = new FakeHttpServer();
    try (other) {
      other.json(
          "/go/api/dashboard",
          """
          {"_embedded":{"pipeline_groups":[
            {"name":"build","pipelines":["hello-build","idle"]},
            {"name":"deploy","pipelines":["hello-deploy-recette","empty"]}]}}
          """);
      for (String p : List.of("hello-build", "hello-deploy-recette")) {
        String capture = "go_api_pipelines_" + p.replace('-', '_') + "_history_page_size_100";
        other.on(
            "/go/api/pipelines/" + p + "/history",
            req -> Response.json(GocdFixtures.fixture(capture)));
      }
      other.json("/go/api/pipelines/empty/history", "{\"pipelines\":[]}");
      Cli.Result r = locate(null, other);
      assertThat(r.out())
          .contains("Index : 4 pipelines visibles (dont 2 sans run lisible")
          .contains("aval niveau 1")
          .doesNotContain("Tous dans le groupe");
    }
  }

  @Test
  void a_large_gocd_shows_progress_and_a_broken_one_stops() {
    FakeHttpServer big = new FakeHttpServer();
    try (big) {
      List<String> names = new ArrayList<>();
      for (int i = 0; i < 100; i++) names.add("p" + i);
      AtomicReference<Response> dashboard =
          new AtomicReference<>(
              Response.json(GocdFixtures.dashboard("g", names.toArray(String[]::new))));
      big.on("/go/api/dashboard", req -> dashboard.get());
      Cli.Result r = locate(null, big);
      assertThat(r.err())
          .contains("  index GoCD : 50 / 100 pipelines", "  index GoCD : 100 / 100 pipelines")
          .doesNotContain("49 / 100");
      dashboard.set(Response.status(500, "{}"));
      Cli.Result broken = locate(null, big, "--refresh-index");
      assertThat(broken.exitCode()).isEqualTo(2);
      assertThat(broken.err()).contains("Arrêt : /go/api/dashboard : HTTP 500");
    }
    FakeHttpServer refusing = new FakeHttpServer();
    try (refusing) {
      refusing.onPrefix("/go/", req -> Response.status(401, "{}"));
      assertThat(locate(null, refusing, "--refresh-index").exitCode()).isEqualTo(2);
    }
  }
}
