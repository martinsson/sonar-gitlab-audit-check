package ch.ge.edg.sonar.gitlab.audit.cli;

import static ch.ge.edg.sonar.gitlab.audit.cli.GitlabAuditFake.PAST;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitlabAuditFake.daily;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitlabAuditFake.project;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import ch.ge.edg.sonar.gitlab.audit.testing.SuspectedBug;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Odd answers from GitLab, one branch at a time. */
class GitlabActivityAuditEdgeCasesTest extends GitlabActivityAuditTestBase {

  private static List<ObjectNode> withReverts(String prefix, int n, int reverts) {
    List<ObjectNode> commits = daily(prefix, "dev-" + prefix, n);
    for (int i = 0; i < reverts; i++) commits.get(i).put("title", "Revert \"" + i + "\"");
    return commits;
  }

  private static String pipelines(String... statusAndDate) {
    StringBuilder b = new StringBuilder("[");
    for (int i = 0; i < statusAndDate.length; i += 2) {
      b.append(i == 0 ? "" : ",")
          .append("{\"status\":\"")
          .append(statusAndDate[i])
          .append("\",\"created_at\":\"")
          .append(statusAndDate[i + 1])
          .append("\"}");
    }
    return b.append("]").toString();
  }

  private void oddProjects() {
    fake.json(
            "/api/v4/projects/1/protected_branches",
            "[{\"name\":\"main\",\"push_access_levels\":[]}]")
        .json("/api/v4/projects/1/merge_requests", "{\"x\":1}")
        .json("/api/v4/projects/1/pipelines", "[]")
        .commits(1, withReverts("a", 25, 2))
        .tree(1, "sonar-project.properties", "pom.xml");
    fake.json(
            "/api/v4/projects/2/protected_branches",
            "[{\"name\":\"main\",\"push_access_levels\":[{\"access_level\":40}]}]")
        .json("/api/v4/projects/2/merge_requests", "[]")
        .json("/api/v4/projects/2/pipelines", "{\"x\":1}")
        .route("/api/v4/projects/2/ci/lint", r -> Response.status(401, "{}"))
        .raw(
            2,
            ".gitlab-ci.yml",
            """
            include:
              - project: shared/no-id
                file: n.yml
              - project: shared/tpl
                file: a.yml
            """)
        .commits(2, daily("b", "bob", 3))
        .tree(2, ".gitlab-ci.yml");
    moreOddProjects();
  }

  private void moreOddProjects() {
    fake.json("/api/v4/projects/3/pipelines", pipelines("canceled", "2099-01-01T00:00:00Z"))
        .raw(
            3,
            ".gitlab-ci.yml",
            """
            include:
              - project: shared/tpl
                file: a.yml
              - project: shared/no-id
                file: n.yml
            """)
        .commits(3, withReverts("c", 25, 1))
        .tree(3, ".gitlab-ci.yml");
    fake.json(
            "/api/v4/projects/4/pipelines",
            pipelines("failed", "2099-01-01T00:00:00Z", "success", "2099-01-01T02:00:00Z"))
        .commits(4, daily("d", "dave", 3));
    fake.json(
            "/api/v4/projects/5/pipelines",
            pipelines("failed", "2099-01-01T00:00:00Z", "success", "2099-01-01T05:00:00Z"))
        .commits(5, daily("e", "erin", 3));
    fake.json("/api/v4/projects/shared%2Fno-id", "{\"name\":\"no id\"}")
        .json("/api/v4/projects/shared%2Ftpl", "{\"id\":77}")
        .raw(77, "a.yml", "sonar.projectKey: tpl-key\n");
  }

  @Test
  void oddPracticeAnswersAreReadCautiously() {
    ObjectNode[] ps = new ObjectNode[5];
    for (int i = 0; i < 5; i++) ps[i] = project(1 + i, "team-a/e" + (1 + i));
    fake.enterprise(false).group("team-a", ps);
    oddProjects();

    Cli.Result r =
        fake.run(
            "--group",
            "team-a",
            "--deep",
            "--out-dir",
            tmp.toString(),
            "--graphql=false",
            "--floor",
            "0",
            "--concurrency",
            "1");

    assertOk(r);
    expectSelfie(out(r)).toMatchDisk("stdout");
    expectSelfie(file(tmp.resolve("pratiques.csv"))).toMatchDisk("pratiques");
    assertThat(fake.server.requests()).filteredOn(q -> q.path().endsWith("/ci/lint")).hasSize(1);
    assertThat(fake.server.requests("/api/v4/projects/shared%2Ftpl")).hasSize(1);
    assertThat(fake.server.requests("/api/v4/projects/shared%2Fno-id")).hasSize(1);
    assertThat(fake.server.requests("/api/v4/projects/77/repository/files/a.yml/raw")).hasSize(1);
  }

  @Test
  void aLintRefusedWith404IsAlsoRememberedButA500IsNot() {
    ObjectNode[] ps = new ObjectNode[4];
    for (int i = 0; i < 4; i++) {
      ps[i] = project(1 + i, "team-a/l" + (1 + i));
      fake.commits(1 + i, daily("x" + i, "dev" + i, 2)).tree(1 + i, ".gitlab-ci.yml");
    }
    fake.route("/api/v4/projects/1/ci/lint", r -> Response.status(500, "{}"))
        .route("/api/v4/projects/2/ci/lint", r -> Response.status(404, "{}"))
        .raw(3, ".gitlab-ci.yml", "x: 1\n");
    fake.enterprise(false).group("team-a", ps);
    Cli.Result r =
        fake.run(
            "--group", "team-a", "--deep", "--graphql=false", "--floor", "0", "--concurrency", "1");
    assertOk(r);
    assertThat(fake.server.requests())
        .filteredOn(q -> q.path().endsWith("/ci/lint"))
        .extracting(q -> q.path())
        .containsExactly("/api/v4/projects/1/ci/lint", "/api/v4/projects/2/ci/lint");
    assertThat(r.out())
        .contains("  CI lue : 0 par ci/lint, 1 par repli, 0 depuis le cache.\n")
        .contains("    ci/lint refusé (HTTP 404) : le jeton n'a pas le droit de \n");
  }

  @Test
  @SuspectedBug("the ci/lint refusal message should not end its first line with a space")
  void theLintRefusalMessageEndsItsFirstLineWithASpace() {
    fake.commits(1, daily("x", "dev", 2)).tree(1, ".gitlab-ci.yml");
    fake.route("/api/v4/projects/1/ci/lint", r -> Response.status(403, "{}"))
        .raw(1, ".gitlab-ci.yml", "x: 1\n");
    fake.enterprise(false).group("team-a", project(1, "team-a/l1"));
    Cli.Result r = fake.run("--group", "team-a", "--deep", "--graphql=false", "--floor", "0");
    assertOk(r);
    assertThat(r.out())
        .contains("    ci/lint refusé (HTTP 403) : le jeton n'a pas le droit de \n")
        .doesNotContain("    ci/lint refusé (HTTP 403) : le jeton n'a pas le droit de\n");
  }

  @Test
  void oddInventoryDates() {
    ObjectNode undatedFork = project(1, "team-a/fork");
    undatedFork.putObject("forked_from_project").put("path_with_namespace", "up/fork");
    undatedFork.remove("created_at");
    ObjectNode undatedForkActivity = project(2, "team-a/fork-2");
    undatedForkActivity.putObject("forked_from_project").put("path_with_namespace", "up/f2");
    undatedForkActivity.remove("last_activity_at");
    ObjectNode silent = project(3, "team-a/silent");
    silent.put("last_activity_at", "not a date");
    fake.enterprise(false)
        .group("team-a", undatedFork, undatedForkActivity, silent)
        .commits(1, daily("f", "fred", 6));
    Cli.Result r = fake.run("--group", "team-a", "--csv", tmp.resolve("i.csv").toString());
    assertOk(r);
    expectSelfie(file(tmp.resolve("i.csv"))).toMatchDisk();
  }

  @Test
  void aCountWithoutTotalAndAFailingFullPageIsZero() {
    ObjectNode sleepy = project(1, "team-a/sleepy").put("last_activity_at", PAST);
    fake.enterprise(false)
        .group("team-a", sleepy)
        .route(
            "/api/v4/projects/1/repository/commits",
            r ->
                "1".equals(r.param("per_page")) ? Response.json("[]") : Response.status(500, "{}"));
    Cli.Result r = fake.run("--group", "team-a");
    assertThat(r.out())
        .contains("Échantillon de contrôle sous la coupe : 1 projets, 0 faux négatif");
    List<String> perPage = new ArrayList<>();
    fake.server
        .requests("/api/v4/projects/1/repository/commits")
        .forEach(q -> perPage.add(q.param("per_page")));
    assertThat(perPage).containsExactly("1", "100");
  }

  @Test
  void aFullPageThatIsNotAListCountsZero() {
    ObjectNode sleepy = project(1, "team-a/sleepy").put("last_activity_at", PAST);
    fake.enterprise(false)
        .group("team-a", sleepy)
        .json("/api/v4/projects/1/repository/commits", "{\"message\":\"x\"}");
    Cli.Result r = fake.run("--group", "team-a");
    assertThat(r.out()).contains("1 projets, 0 faux négatif");
  }
}
