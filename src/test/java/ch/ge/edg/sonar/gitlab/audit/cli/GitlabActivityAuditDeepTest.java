package ch.ge.edg.sonar.gitlab.audit.cli;

import static ch.ge.edg.sonar.gitlab.audit.cli.GitlabAuditFake.FUTURE;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitlabAuditFake.commit;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitlabAuditFake.daily;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitlabAuditFake.merge;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitlabAuditFake.project;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitlabAuditFake.stats;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Request;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class GitlabActivityAuditDeepTest extends GitlabActivityAuditTestBase {

  private Cli.Result deep(String... extra) {
    List<String> args =
        new ArrayList<>(
            List.of(
                "--group",
                "team-a",
                "--deep",
                "--out-dir",
                tmp.toString(),
                "--graphql=false",
                "--floor",
                "0",
                "--concurrency",
                "1"));
    args.addAll(List.of(extra));
    return fake.run(args.toArray(String[]::new));
  }

  /** 25 human commits with stats, plus a bot, a merge and a commit without stats. */
  private static List<ObjectNode> busyHistory() {
    List<ObjectNode> commits = new ArrayList<>();
    for (ObjectNode c : daily("h", "alice", 25)) commits.add(stats(c, 10, 5));
    commits.add(stats(commit("bot1", "renovate", FUTURE), 1000, 0));
    commits.add(stats(merge(commit("m1", "bob", FUTURE)), 500, 500));
    commits.add(commit("nostat", "bob", FUTURE).put("title", "Revert \"x\""));
    return commits;
  }

  private static String mr(
      int iid, @Nullable String created, @Nullable String merged, String people) {
    return "{\"iid\":"
        + iid
        + (created == null ? "" : ",\"created_at\":\"" + created + "\"")
        + (merged == null ? "" : ",\"merged_at\":\"" + merged + "\"")
        + people
        + "}";
  }

  private void enterpriseAlpha() {
    fake.json(
            "/api/v4/projects/1/protected_branches",
            "[{\"name\":\"develop\"},"
                + "{\"name\":\"main\",\"push_access_levels\":[{\"access_level\":0}]}]")
        .json(
            "/api/v4/projects/1/merge_requests",
            "["
                + mr(
                    1,
                    "2099-01-01T00:00:00Z",
                    "2099-01-03T00:00:00Z",
                    ",\"author\":{\"id\":1},\"merged_by\":{\"id\":1},\"user_notes_count\":4")
                + ","
                + mr(
                    2,
                    "2099-01-01T00:00:00Z",
                    "2099-01-02T12:00:00Z",
                    ",\"author\":{\"id\":2},\"merge_user\":{\"id\":3}")
                + ","
                + mr(3, "2099-01-01T00:00:00Z", null, ",\"user_notes_count\":2")
                + "]")
        .json(
            "/api/v4/projects/1/merge_requests/1/approvals",
            "{\"approved_by\":[{\"user\":{\"id\":1}},{\"user\":{\"id\":9}}]}")
        .json("/api/v4/projects/1/merge_requests/2/approvals", "{\"approved_by\":[]}")
        .json(
            "/api/v4/projects/1/pipelines",
            "[{\"status\":\"failed\",\"created_at\":\"2099-01-03T00:00:00Z\"},"
                + "{\"status\":\"success\",\"created_at\":\"2099-01-01T00:00:00Z\"},"
                + "{\"status\":\"failed\",\"created_at\":\"2099-01-02T00:00:00Z\"},"
                + "{\"status\":\"failed\",\"created_at\":\"2099-01-02T06:00:00Z\"},"
                + "{\"status\":\"canceled\",\"created_at\":\"2099-01-02T07:00:00Z\"},"
                + "{\"status\":\"success\",\"created_at\":\"2099-01-02T12:30:00Z\"},"
                + "{\"status\":\"running\"},{\"created_at\":\"2099-01-04T00:00:00Z\"}]")
        .route(
            "/api/v4/projects/1/environments",
            r -> Response.json("[{}]").withHeader("X-Total", "2"))
        .json("/api/v4/projects/1/dora/metrics", "[{\"value\":2},{\"value\":3.5},{}]")
        .commits(1, busyHistory())
        .tree(1, "README.md", "CODEOWNERS", "Dockerfile", "src", "renovate.json");
  }

  @Test
  void anEnterpriseProjectGetsEveryPracticeSignal() {
    ObjectNode witness = project(4, "team-a/witness");
    fake.enterprise(true)
        .group(
            "team-a",
            project(1, "team-a/alpha"),
            project(2, "team-a/beta"),
            project(3, "team-a/gamma"),
            witness);
    enterpriseAlpha();
    fake.commits(2, daily("b", "bob", 4)).commits(3, daily("c", "carol", 3));
    fake.commits(4, commit("w1", "dave", FUTURE), commit("w2", "erin", FUTURE));

    Cli.Result r = deep("--top", "10");

    assertOk(r);
    expectSelfie(out(r)).toMatchDisk("stdout");
    expectSelfie(file(tmp.resolve("pratiques.csv"))).toMatchDisk("pratiques");
    expectSelfie(file(tmp.resolve("inventaire.csv"))).toMatchDisk("inventaire");
    assertThat(fake.server.requests("/api/v4/projects/1/merge_requests/3/approvals")).hasSize(1);
  }

  @Test
  void theDeepPassAsksWhatItShould() {
    fake.enterprise(true).group("team-a", project(1, "team-a/alpha"));
    enterpriseAlpha();
    deep();
    Request mrs = fake.server.requests("/api/v4/projects/1/merge_requests").getFirst();
    assertThat(mrs.param("state")).isEqualTo("merged");
    assertThat(mrs.param("target_branch")).isEqualTo("main");
    assertThat(mrs.param("updated_after")).isNotBlank();
    assertThat(mrs.param("page")).isEqualTo("1");
    Request pl = fake.server.requests("/api/v4/projects/1/pipelines").getFirst();
    assertThat(pl.param("ref")).isEqualTo("main");
    assertThat(pl.param("per_page")).isEqualTo("100");
    Request dora = fake.server.requests("/api/v4/projects/1/dora/metrics").getFirst();
    assertThat(dora.param("metric")).isEqualTo("deployment_frequency");
    assertThat(dora.param("interval")).isEqualTo("monthly");
    assertThat(dora.param("start_date")).matches("\\d{4}-\\d{2}-\\d{2}");
    assertThat(fake.server.requests("/api/v4/projects/1/environments").getFirst().param("per_page"))
        .isEqualTo("1");
    assertThat(fake.server.requests("/api/v4/projects/1/repository/commits"))
        .filteredOn(q -> "true".equals(q.param("with_stats")))
        .hasSize(1);
    assertThat(fake.server.requests("/api/v4/projects/1/repository/tree").getFirst().param("ref"))
        .isEqualTo("main");
  }

  @Test
  void aCommunityProjectWithNothingReadable() {
    fake.enterprise(false)
        .group("team-a", project(1, "team-a/alpha"))
        .json("/api/v4/projects/1/environments", "[]")
        .route(
            "/api/v4/projects/1/repository/commits",
            r ->
                "true".equals(r.param("with_stats"))
                    ? Response.status(403, "{}")
                    : Response.json(
                        GitlabAuditFake.array(daily("h", "alice", 25).toArray(ObjectNode[]::new))));

    Cli.Result r = deep();

    assertOk(r);
    expectSelfie(out(r)).toMatchDisk("stdout");
    expectSelfie(file(tmp.resolve("pratiques.csv"))).toMatchDisk("pratiques");
    assertThat(fake.server.requests("/api/v4/projects/1/dora/metrics")).isEmpty();
    assertThat(fake.server.requests()).noneMatch(q -> q.path().endsWith("/approvals"));
  }

  @Test
  void manyMergeRequestsArePagedThreeTimesAndTenAreSampled() {
    fake.enterprise(true)
        .group("team-a", project(1, "team-a/alpha"))
        .commits(1, daily("h", "alice", 3))
        .route(
            "/api/v4/projects/1/merge_requests",
            r -> {
              StringBuilder b = new StringBuilder("[");
              for (int i = 0; i < 100; i++) {
                b.append(i == 0 ? "" : ",").append(mr(i, null, null, ",\"author\":{\"id\":5}"));
              }
              return Response.json(b.append("]").toString());
            })
        .prefix(
            "/api/v4/projects/1/merge_requests/",
            r -> Response.json("{\"approved_by\":[{\"user\":{\"id\":5}}]}"))
        .json("/api/v4/projects/1/environments", "[]")
        .route("/api/v4/projects/1/dora/metrics", r -> Response.status(403, "{}"));

    Cli.Result r = deep();

    assertOk(r);
    assertThat(fake.server.requests("/api/v4/projects/1/merge_requests"))
        .extracting(q -> q.param("page"))
        .containsExactly("1", "2", "3");
    assertThat(fake.server.requests()).filteredOn(q -> q.path().endsWith("/approvals")).hasSize(10);
    assertThat(fake.server.requests("/api/v4/projects/1/dora/metrics")).isEmpty();
    expectSelfie(file(tmp.resolve("pratiques.csv"))).toMatchDisk();
  }

  @Test
  void doraRefusedIsMarkedUnavailable() {
    fake.enterprise(true)
        .group("team-a", project(1, "team-a/alpha"), project(2, "team-a/beta"))
        .commits(1, daily("h", "alice", 3))
        .commits(2, daily("b", "bob", 3))
        .route("/api/v4/projects/1/dora/metrics", r -> Response.status(403, "{}"))
        .route("/api/v4/projects/2/dora/metrics", r -> Response.status(500, "{}"));
    deep();
    expectSelfie(file(tmp.resolve("pratiques.csv"))).toMatchDisk();
  }

  @Test
  void doraAnswerThatIsNotAListIsIgnored() {
    fake.enterprise(true)
        .group("team-a", project(1, "team-a/alpha"))
        .commits(1, daily("h", "alice", 3))
        .json("/api/v4/projects/1/dora/metrics", "{\"not\":\"an array\"}");
    deep();
    assertThat(fake.server.requests("/api/v4/projects/1/dora/metrics")).hasSize(1);
    expectSelfie(file(tmp.resolve("pratiques.csv"))).toMatchDisk();
  }

  @Test
  void churnStopsAtTheCommitPageCap() {
    fake.enterprise(false)
        .group("team-a", project(1, "team-a/alpha"))
        .commits(1, daily("h", "alice", 100).stream().map(c -> stats(c, 1, 1)).toList());
    deep("--max-commit-pages", "1");
    expectSelfie(file(tmp.resolve("pratiques.csv"))).toMatchDisk();
  }

  @Test
  void noSelectedProjectMeansNoPracticesFile() {
    fake.enterprise(false).group("team-a", project(1, "team-a/alpha"));
    Cli.Result r = deep();
    assertOk(r);
    assertThat(r.out())
        .contains("    pratiques  : non écrit — aucun projet sélectionné.\n")
        .doesNotContain("6. Signaux de pratique");
    assertThat(tmp.resolve("pratiques.csv")).doesNotExist();
    assertThat(tmp.resolve(".sonar-keys.json")).doesNotExist();
  }
}
