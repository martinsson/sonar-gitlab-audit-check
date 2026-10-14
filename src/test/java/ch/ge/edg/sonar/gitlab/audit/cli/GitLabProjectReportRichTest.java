package ch.ge.edg.sonar.gitlab.audit.cli;

import static ch.ge.edg.sonar.gitlab.audit.cli.GitLabProjectReportFakeGitlab.API;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitLabProjectReportFakeGitlab.commit;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitLabProjectReportFakeGitlab.merge;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitLabProjectReportFakeGitlab.mr;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitLabProjectReportFakeGitlab.note;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitLabProjectReportFakeGitlab.user;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A busy, well-populated project: every section of the report has something to say. */
class GitLabProjectReportRichTest extends GitLabProjectReportHarness {

  static final String MON = "2024-03-04T10:00:00+02:00";
  static final String MON_EARLY = "2024-03-04T07:30:00+02:00";
  static final String TUE_LATE = "2024-03-05T20:15:00+01:00";
  static final String WED = "2024-03-06T14:00:00Z";
  static final String SAT = "2024-03-09T11:00:00+02:00";
  static final String SUN = "2024-03-10T09:00:00+02:00";

  List<Map<String, Object>> richCommits() {
    List<Map<String, Object>> c = new ArrayList<>();
    c.add(commit("Alice Martin", "alice@example.com", MON, fx.ago(1, 2), new int[] {10, 2}));
    c.add(commit("Alice Martin", "alice@example.com", MON_EARLY, fx.ago(2, 5), new int[] {40, 30}));
    c.add(commit("Alice Martin", "alice@example.com", TUE_LATE, fx.ago(4, 0), new int[] {3000, 1}));
    c.add(commit("Alice Martin", "alice@example.com", WED, fx.ago(4, 1), new int[] {5, 5}));
    c.add(commit("Alice Martin", "alice@example.com", SAT, fx.ago(10, 0), new int[] {1, 0}));
    c.add(commit("Alice Martin", "alice@example.com", WED, fx.ago(30, 0), null));
    c.add(commit("Alice M.", "Alice@other.example", WED, fx.ago(31, 0), new int[] {7, 7}));
    c.add(commit("Bob", "bob@example.com", MON, fx.ago(40, 0), new int[] {100, 50}));
    c.add(commit("Bob", "bob@example.com", SUN, fx.ago(41, 3), new int[] {20, 0}));
    c.add(commit("Bob", "bob@example.com", "not-a-date", fx.ago(60, 0), new int[] {2, 2}));
    c.add(commit("Carol", "", MON, fx.ago(70, 0), new int[] {9, 1}));
    c.add(commit("Dave", "dave@example.com", MON, fx.ago(150, 0), new int[] {50, 0}));
    c.add(commit("Erin", "erin@example.com", MON, fx.ago(151, 0), new int[] {50, 10}));
    c.add(
        commit(
            "Frank With An Exceptionally Long Display Name",
            "frank@example.com",
            MON,
            fx.ago(152, 0),
            new int[] {1, 1}));
    c.add(merge(commit("Maintainer", "maint@example.com", MON, fx.ago(5, 0), new int[] {900, 9})));
    c.add(commit("Renovate Bot", "renovate@example.com", MON, fx.ago(3, 0), new int[] {1, 1}));
    c.add(commit("ci", "project_42_bot_x@noreply.example", MON, fx.ago(6, 0), null));
    return c;
  }

  List<Map<String, Object>> richMrs() {
    Map<String, Object> m1 = mr(1, "merged", "alice", fx.ago(10, 0));
    m1.put("merged_at", fx.ago(9, 0));
    m1.put("merge_user", user("bob"));
    m1.put("user_notes_count", 3);
    m1.put("squash", true);
    Map<String, Object> m2 = mr(2, "merged", "bob", fx.ago(20, 0));
    m2.put("merged_at", fx.ago(17, 0));
    m2.put("merge_user", user("bob"));
    m2.put("user_notes_count", 1);
    Map<String, Object> m3 = mr(3, "merged", "alice", fx.ago(40, 0));
    m3.put("merged_at", fx.ago(35, 0));
    m3.put("merged_by", user("alice"));
    Map<String, Object> m4 = mr(4, "merged", "carol", fx.ago(50, 0));
    m4.put("merged_at", fx.ago(49, 23));
    Map<String, Object> m7 = mr(7, "merged", "dave", fx.ago(60, 0));
    m7.put("merged_at", fx.ago(61, 0));
    m7.put("merge_user", user("carol"));
    m7.put("squash", true);
    Map<String, Object> m5 = mr(5, "closed", "erin", fx.ago(70, 0));
    Map<String, Object> m6 = mr(6, "opened", "alice", fx.ago(2, 0));
    m6.put("user_notes_count", 2);
    m6.put("draft", true);
    return List.of(m1, m2, m3, m4, m7, m5, m6);
  }

  void richNotes() {
    notes(
        1,
        List.of(
            note("bob", fx.ago(9, 23), true),
            note("alice", fx.ago(9, 22), false),
            note("bob", fx.ago(9, 19), false),
            note("carol", fx.ago(9, 10), false)));
    notes(2, List.of(note("bob", fx.ago(19, 0), false)));
    notes(3, List.of(note(null, fx.ago(39, 0), false), note("carol", fx.ago(37, 22), false)));
    notes(4, List.of());
    notes(7, List.of(note("erin", null, false)));
  }

  void richProject() {
    project(fx.project(ID, PATH, "main", false));
    commits(richCommits());
    contributors(9);
    mrs(richMrs());
    richNotes();
  }

  @Test
  void reports_cadence_people_and_review_of_a_busy_project() {
    richProject();

    Cli.Result r = run();

    assertThat(r.exitCode()).isZero();
    assertThat(r.err()).isEmpty();
    expectSelfie(normalized(r.out())).toMatchDisk();
  }

  @Test
  void asks_gitlab_for_the_window_on_the_default_branch() {
    richProject();

    run("--days", "30");

    FakeHttpServer.Request project = server.requests(projectRoute()).getFirst();
    assertThat(project.param("statistics")).isEqualTo("true");
    assertThat(project.header("PRIVATE-TOKEN")).isEqualTo("glpat-test");
    FakeHttpServer.Request commits = server.requests(API + ID + "/repository/commits").getFirst();
    assertThat(commits.param("ref_name")).isEqualTo("main");
    assertThat(commits.param("with_stats")).isEqualTo("true");
    assertThat(commits.param("since")).startsWith(fx.dateAgo(30, 0) + "T");
    assertThat(commits.param("per_page")).isEqualTo("100");
    FakeHttpServer.Request mrs = server.requests(API + ID + "/merge_requests").getFirst();
    assertThat(mrs.param("scope")).isEqualTo("all");
    assertThat(mrs.param("state")).isEqualTo("all");
    assertThat(mrs.param("created_after")).startsWith(fx.dateAgo(30, 0) + "T");
    assertThat(mrs.param("order_by")).isEqualTo("created_at");
    assertThat(mrs.param("sort")).isEqualTo("desc");
    FakeHttpServer.Request contrib =
        server.requests(API + ID + "/repository/contributors").getFirst();
    assertThat(contrib.param("order_by")).isEqualTo("commits");
    assertThat(contrib.param("sort")).isEqualTo("desc");
    FakeHttpServer.Request notes = server.requests(API + ID + "/merge_requests/1/notes").getFirst();
    assertThat(notes.param("sort")).isEqualTo("asc");
    assertThat(notes.param("order_by")).isEqualTo("created_at");
  }

  @Test
  void samples_only_the_first_merged_requests() {
    richProject();

    Cli.Result r = run("--sample", "2");

    assertThat(server.requests(API + ID + "/merge_requests/1/notes")).hasSize(1);
    assertThat(server.requests(API + ID + "/merge_requests/2/notes")).hasSize(1);
    assertThat(server.requests(API + ID + "/merge_requests/3/notes")).isEmpty();
    expectSelfie(normalized(r.out())).toMatchDisk();
  }

  @Test
  void a_zero_sample_reads_no_notes() {
    richProject();

    Cli.Result r = run("--sample", "0");

    assertThat(server.requests().stream().filter(q -> q.path().endsWith("/notes"))).isEmpty();
    assertThat(r.out()).doesNotContain("Échantillon de revue");
  }

  @Test
  void bots_can_be_counted_as_people() {
    richProject();

    Cli.Result r = run("--include-bots");

    expectSelfie(normalized(r.out())).toMatchDisk();
  }

  @Test
  void the_bot_pattern_can_be_replaced() {
    richProject();

    Cli.Result r = run("--bot-pattern", "^bob@");

    expectSelfie(normalized(r.out())).toMatchDisk();
  }

  @Test
  void colors_mark_titles_and_findings() {
    richProject();

    Cli.Result r = run("--color", "always");

    assertThat(r.out())
        .contains("\033[1m=== 1. Le projet \033[0m\033[1m" + "=".repeat(54) + "\033[0m")
        .contains("\033[1mteam-a/alpha-service\033[0m")
        .contains("\033[2m   (il y a 400 j)\033[0m")
        .contains("\033[32m  Rien de saillant sur les seuils retenus.\033[0m");
  }
}
