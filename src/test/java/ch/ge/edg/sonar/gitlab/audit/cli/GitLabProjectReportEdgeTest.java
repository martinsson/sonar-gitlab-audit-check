package ch.ge.edg.sonar.gitlab.audit.cli;

import static ch.ge.edg.sonar.gitlab.audit.cli.GitLabProjectReportFakeGitlab.API;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitLabProjectReportFakeGitlab.commit;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitLabProjectReportFakeGitlab.merge;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitLabProjectReportFakeGitlab.mr;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitLabProjectReportFakeGitlab.obj;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitLabProjectReportFakeGitlab.user;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Thin, empty, refused or capped answers: what the report says when GitLab gives little. */
class GitLabProjectReportEdgeTest extends GitLabProjectReportHarness {

  static final String MON = "2024-03-04T10:00:00+02:00";

  Map<String, Object> oneCommit(@Nullable String name, @Nullable String email, int daysAgo) {
    return commit(name, email, MON, fx.ago(daysAgo, 0), new int[] {3, 1});
  }

  @Test
  void a_bare_project_seen_anonymously() {
    Map<String, Object> p = fx.project(ID, PATH, "", false);
    p.remove("statistics");
    p.put("visibility", null);
    p.put("created_at", "garbage");
    p.remove("last_activity_at");
    project(p);
    fail(API + ID + "/repository/contributors", 403, "{\"message\":\"403 Forbidden\"}");
    mrs(List.of());

    Cli.Result r = run("--token", "");

    assertThat(r.exitCode()).isZero();
    assertThat(server.requests(projectRoute()).getFirst().header("PRIVATE-TOKEN")).isNull();
    assertThat(server.requests(API + ID + "/repository/commits")).isEmpty();
    expectSelfie(normalized(r.out())).toMatchDisk();
  }

  @Test
  void an_unknown_project_is_a_404_with_an_explanation() {
    fail(projectRoute(), 404, "{\"message\":\"404 Project Not Found\"}");

    Cli.Result r = run();

    assertThat(r.exitCode()).isEqualTo(2);
    assertThat(r.out()).isEmpty();
    expectSelfie(r.err()).toMatchDisk();
  }

  @Test
  void a_server_error_on_the_project_has_no_404_explanation() {
    fail(projectRoute(), 500, "{\"error\":\"boom\"}");

    Cli.Result r = run();

    assertThat(r.exitCode()).isEqualTo(2);
    assertThat(r.err()).isEqualTo("Projet introuvable : team-a/alpha-service\n  HTTP 500 — boom\n");
  }

  @Test
  void a_project_answer_without_an_id_is_not_found() {
    server.json(projectRoute(), "{\"name\":\"x\"}");

    Cli.Result r = run();

    assertThat(r.exitCode()).isEqualTo(2);
    assertThat(r.err()).startsWith("Projet introuvable : team-a/alpha-service\n  HTTP 200 — ");
  }

  @Test
  void an_unreachable_instance_is_reported() {
    String url = server.url();
    server.close();

    Cli.Result r =
        Cli.run(
            new GitLabProjectReport(),
            "--url",
            url,
            "--path",
            PATH,
            "--token",
            "t",
            "--color",
            "never",
            "--timeout",
            "2");

    assertThat(r.exitCode()).isEqualTo(2);
    assertThat(r.err()).startsWith("Projet introuvable : team-a/alpha-service\n  HTTP 0 — ");
  }

  @Test
  void the_path_is_normalized_from_a_browser_url() {
    project(fx.project(ID, PATH, "", false));
    mrs(List.of());
    contributors(0);

    Cli.Result r =
        Cli.run(
            new GitLabProjectReport(),
            "--url",
            server.url(),
            "--path",
            " https://gitlab.example/team-a/alpha-service.git/-/tree/main ",
            "--token",
            "t",
            "--color",
            "never");

    assertThat(r.exitCode()).isZero();
    assertThat(server.requests(projectRoute())).hasSize(1);
  }

  @Test
  void refused_commits_and_merge_requests_are_said() {
    project(fx.project(ID, PATH, "main", false));
    fail(API + ID + "/repository/commits", 403, "{\"message\":\"403 Forbidden\"}");
    fail(API + ID + "/merge_requests", 500, "oops");
    contributors(0);

    Cli.Result r = run();

    assertThat(r.exitCode()).isZero();
    expectSelfie(normalized(r.out())).toMatchDisk();
  }

  @Test
  void bots_only() {
    project(fx.project(ID, PATH, "main", false));
    commits(
        List.of(
            oneCommit("Renovate Bot", "renovate@example.com", 1),
            oneCommit("", "gitlab-ci-token@example.com", 2)));
    contributors(0);
    mrs(List.of());

    Cli.Result r = run();

    expectSelfie(normalized(r.out())).toMatchDisk();
  }

  @Test
  void nothing_at_all_on_the_window() {
    project(fx.project(ID, PATH, "main", false));
    commits(List.of());
    contributors(0);
    mrs(List.of());

    Cli.Result r = run();

    assertThat(r.out())
        .contains("  Aucun commit humain sur la fenêtre.\n\n=== 3.")
        .contains("  Pas de commit sur la fenêtre : rien à mesurer.")
        .contains("  Rien de saillant sur les seuils retenus.");
  }

  @Test
  void merge_commits_only() {
    project(fx.project(ID, PATH, "main", false));
    commits(List.of(merge(oneCommit("Alice", "alice@example.com", 1))));
    contributors(0);
    mrs(List.of());

    Cli.Result r = run();

    expectSelfie(normalized(r.out())).toMatchDisk();
  }

  @Test
  void commits_without_stats_or_dates() {
    project(fx.project(ID, PATH, "main", false));
    commits(
        List.of(
            commit("Alice", "alice@example.com", null, null, null),
            commit("Alice", "alice@example.com", "x", fx.ago(1, 0), null)));
    contributors(0);
    mrs(List.of());

    Cli.Result r = run();

    expectSelfie(normalized(r.out())).toMatchDisk();
  }

  @Test
  void one_person_with_several_addresses_and_names() {
    project(fx.project(ID, PATH, "main", false));
    commits(
        List.of(
            oneCommit("Dev 0", "dev0@example.com", 1),
            oneCommit("Dev Zero", "dev0@other.example", 2),
            oneCommit("Dev 0", null, 3),
            oneCommit("Dev Z.", "Dev0@third.example", 5),
            oneCommit(null, null, 4)));
    contributors(0);
    mrs(List.of());

    Cli.Result r = run();

    expectSelfie(normalized(r.out())).toMatchDisk();
  }

  @Test
  void caps_are_reported_and_the_window_shrinks_to_what_was_read() {
    project(fx.project(ID, PATH, "main", false));
    commits(
        List.of(
            oneCommit("Alice", "alice@example.com", 1),
            oneCommit("Bob", "bob@example.com", 8),
            oneCommit("Alice", "alice@example.com", 14),
            oneCommit("Bob", "bob@example.com", 30)));
    contributors(0);
    mrs(
        List.of(
            mr(3, "opened", "alice", fx.ago(1, 0)),
            mr(2, "closed", "bob", fx.ago(20, 0)),
            mr(1, "opened", "bob", fx.ago(40, 0))));

    Cli.Result r = run("--max-commits", "3", "--max-mrs", "2");

    expectSelfie(normalized(r.out())).toMatchDisk();
  }

  @Test
  void refused_notes_stop_the_sample_at_the_first_refusal() {
    project(fx.project(ID, PATH, "main", false));
    commits(List.of(oneCommit("Alice", "alice@example.com", 1)));
    contributors(0);
    Map<String, Object> m1 = mr(1, "merged", "alice", fx.ago(5, 0));
    m1.put("merged_at", fx.ago(4, 0));
    Map<String, Object> m2 = mr(2, "merged", "alice", fx.ago(6, 0));
    m2.put("merged_at", fx.ago(5, 0));
    mrs(List.of(m1, m2));
    fail(API + ID + "/merge_requests/1/notes", 401, "{\"message\":\"401 Unauthorized\"}");

    Cli.Result r = run();

    assertThat(server.requests(API + ID + "/merge_requests/2/notes")).isEmpty();
    expectSelfie(normalized(r.out())).toMatchDisk();
  }

  @Test
  void merged_requests_without_merger_cannot_say_who_merged() {
    project(fx.project(ID, PATH, "main", false));
    commits(List.of(oneCommit("Alice", "alice@example.com", 1)));
    contributors(0);
    Map<String, Object> m1 = mr(1, "merged", "alice", fx.ago(5, 0));
    m1.put("merge_user", obj("id", 1L, "username", null));
    Map<String, Object> m2 = mr(2, "merged", "nobody", fx.ago(5, 0));
    m2.put("author", null);
    m2.put("merged_by", user("bob"));
    m2.put("merge_user", null);
    mrs(List.of(m1, m2));
    notes(1, List.of());
    notes(2, List.of());

    Cli.Result r = run();

    expectSelfie(normalized(r.out())).toMatchDisk();
  }

  @Test
  void self_merges_are_not_measurable_without_any_merger() {
    project(fx.project(ID, PATH, "main", false));
    commits(List.of(oneCommit("Alice", "alice@example.com", 1)));
    contributors(0);
    mrs(List.of(mr(1, "merged", "alice", fx.ago(5, 0))));
    notes(1, List.of());

    Cli.Result r = run();

    assertThat(r.out())
        .contains("  Auto-fusion                     : non mesurable (merge_user absent)\n")
        .contains("  Fusionnées sans relecture       : 1 (100 %)")
        .doesNotContain("Délai création → fusion")
        .doesNotContain("Délai avant première relecture");
  }
}
