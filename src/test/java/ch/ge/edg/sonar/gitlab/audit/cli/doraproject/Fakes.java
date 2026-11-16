package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * One project told three ways: GitLab, GoCD and Jira, with dates that agree.
 *
 * <p>octocat/Hello-World (id 42). MR !1 (c1, merged as m1) is built by build/1 and deployed by
 * prod/1. MR !2 (c2, merged as m2) is built by build/2; prod/2 fails, prod/3 deploys it again
 * without a change, as GoCD records a rerun. MR !3 is merged but never deployed. In Jira, ABC-7 is
 * a bug opened before any deploy and still open, ABC-5 an incident after prod/3, fixed in four
 * hours, ABC-6 a story.
 *
 * <p>The same project merged in fast-forward, deployed by prod-ff, whose GoCD materials only name a
 * configuration repository: no commit of the project is known to GoCD. On main, besides the three
 * merge requests, d1 « ABC-9 » was pushed straight to main at 2026-09-10 10:10 (a push event), and
 * d2 arrived with no event at all (only its committer date says when). prod-ff/1 delivers !1,
 * prod-ff/2 fails, prod-ff/3 delivers !2, prod-ff/4 delivers d1 and d2.
 */
final class Fakes {

  static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");

  private Fakes() {}

  static FakeHttpServer gitlab() {
    FakeHttpServer gitlab = new FakeHttpServer();
    gitlab.json(
        "/api/v4/projects/octocat%2FHello-World",
        """
        {"id":42,"path_with_namespace":"octocat/Hello-World","name":"Hello-World",
         "namespace":{"full_path":"octocat"},"web_url":"https://gitlab.test/octocat/Hello-World",
         "default_branch":"main"}
        """);
    gitlab.json(
        "/api/v4/projects/42/merge_requests",
        "["
            + mr(3, "Troisième", "2026-10-09T08:00:00Z", "2026-10-10T08:00:00Z", "m3")
            + ","
            + mr(2, "Deuxième <b>", "2026-09-01T09:00:00Z", "2026-09-02T07:00:00Z", "m2")
            + ","
            + mr(1, "Premier l'essai", "2026-08-01T10:00:00Z", "2026-08-02T09:00:00Z", "m1")
            + ","
            + mr(9, "Trop ancienne", "2026-01-01T10:00:00Z", "2026-01-02T09:00:00Z", "m9")
            + "]");
    gitlab.json(
        "/api/v4/projects/42/merge_requests/1/commits", commits("c1", "2026-08-01T08:00:00Z"));
    gitlab.json(
        "/api/v4/projects/42/merge_requests/2/commits", commits("c2", "2026-09-01T08:00:00Z"));
    gitlab.json(
        "/api/v4/projects/42/merge_requests/3/commits", commits("c3", "2026-10-09T07:00:00Z"));
    gitlab.json(
        "/api/v4/projects/42/repository/commits",
        "["
            + mainCommit("c3", "Troisième", "2026-10-09T07:00:00Z", "2026-10-10T08:00:00Z")
            + ","
            + mainCommit("d2", "petit ménage", "2026-09-20T09:00:00Z", "2026-09-20T09:00:00Z")
            + ","
            + mainCommit("d1", "ABC-9 correctif", "2026-09-10T10:00:00Z", "2026-09-10T10:05:00Z")
            + ","
            + mainCommit("c2", "Deuxième", "2026-09-01T08:00:00Z", "2026-09-02T07:00:00Z")
            + ","
            + mainCommit("c1", "Premier", "2026-08-01T08:00:00Z", "2026-08-02T09:00:00Z")
            + "]");
    gitlab.json(
        "/api/v4/projects/42/events",
        "["
            + push("2026-09-10T10:10:00.000Z", "main", "c2", "d1")
            + ","
            + push("2026-09-09T10:00:00.000Z", "feature", "c2", "x9")
            + "]");
    return gitlab;
  }

  private static String mainCommit(String sha, String title, String authored, String committed) {
    return """
        {"id":"%s","title":"%s","authored_date":"%s","committed_date":"%s"}
        """
        .formatted(sha, title, authored, committed);
  }

  private static String push(String at, String ref, String from, String to) {
    return """
        {"action_name":"pushed to","created_at":"%s",
         "push_data":{"ref_type":"branch","ref":"%s","commit_from":"%s","commit_to":"%s",
                      "commit_count":1}}
        """
        .formatted(at, ref, from, to);
  }

  /** prod-ff: its only material is a configuration repository, never the project. */
  static FakeHttpServer gocdWithoutProjectCommits() {
    FakeHttpServer gocd = new FakeHttpServer();
    String config = GocdFixtures.git(true, "git@gitlab.test:octocat/deploy-config.git", "cfg", "");
    gocd.json(
        "/go/api/pipelines/prod-ff/history",
        GocdFixtures.history(
            run("prod-ff", 4, "2026-09-21T10:00:00Z", "Passed", config),
            run("prod-ff", 3, "2026-09-02T12:00:00Z", "Passed", config),
            run("prod-ff", 2, "2026-09-02T08:00:00Z", "Failed", config),
            run("prod-ff", 1, "2026-08-03T10:00:00Z", "Passed", config)));
    return gocd;
  }

  private static String mr(int iid, String title, String created, String merged, String sha) {
    return """
        {"iid":%d,"title":"%s","author":{"username":"dev%d"},"created_at":"%s","merged_at":"%s",
         "merge_commit_sha":"%s","squash_commit_sha":null,"sha":"h%d",
         "web_url":"https://gitlab.test/octocat/Hello-World/-/merge_requests/%d"}
        """
        .formatted(iid, title, iid, created, merged, sha, iid, iid);
  }

  private static String commits(String sha, String authored) {
    return "[{\"id\":\"%s\",\"authored_date\":\"%s\"}]".formatted(sha, authored);
  }

  static FakeHttpServer gocd() {
    FakeHttpServer gocd = new FakeHttpServer();
    gocd.json(
        "/go/api/pipelines/build/1",
        run(
            "build",
            1,
            "2026-08-02T09:30:00Z",
            "Passed",
            git("c1", "2026-08-01T08:00:00Z", "m1", "2026-08-02T09:00:00Z")));
    gocd.json(
        "/go/api/pipelines/build/2",
        run(
            "build",
            2,
            "2026-09-02T07:30:00Z",
            "Passed",
            git("c2", "2026-09-01T08:00:00Z", "m2", "2026-09-02T07:00:00Z")));
    gocd.json(
        "/go/api/pipelines/prod/history",
        GocdFixtures.history(
            run(
                "prod",
                3,
                "2026-09-02T12:00:00Z",
                "Passed",
                GocdFixtures.upstream(false, "build", 2)),
            run(
                "prod",
                2,
                "2026-09-02T08:00:00Z",
                "Failed",
                GocdFixtures.upstream(true, "build", 2)),
            run(
                "prod",
                1,
                "2026-08-03T10:00:00Z",
                "Passed",
                GocdFixtures.upstream(true, "build", 1)),
            run("prod", 0, "2026-01-03T10:00:00Z", "Passed")));
    return gocd;
  }

  private static String run(
      String name, int counter, String at, String result, String... materials) {
    return GocdFixtures.run(name, counter, Instant.parse(at).toEpochMilli(), materials)
        .replace("\"Passed\"", "\"" + result + "\"");
  }

  /** A changed git material of the project: revision, author date, revision, author date… */
  private static String git(String... revisionsAndDates) {
    StringBuilder mods = new StringBuilder();
    for (int i = 0; i < revisionsAndDates.length; i += 2) {
      if (!mods.isEmpty()) mods.append(',');
      mods.append(
          "{\"revision\":\"%s\",\"modified_time\":%d,\"comment\":\"\"}"
              .formatted(
                  revisionsAndDates[i], Instant.parse(revisionsAndDates[i + 1]).toEpochMilli()));
    }
    return """
        {"changed":true,"material":{"type":"Git",
         "description":"URL: git@gitlab.test:octocat/Hello-World.git, Branch: main"},
         "modifications":[%s]}
        """
        .formatted(mods);
  }

  static FakeHttpServer jira() {
    FakeHttpServer jira = new FakeHttpServer();
    jira.json(
        "/rest/api/2/search",
        "{\"startAt\":0,\"total\":3,\"issues\":["
            + issue("ABC-7", "Bug", "Ancien bug", "2026-07-25T10:00:00.000+0200", null)
            + ","
            + issue("ABC-6", "Story", "Une histoire", "2026-09-03T10:00:00.000+0200", null)
            + ","
            + issue(
                "ABC-5",
                "Incident",
                "Paiement \\\"KO\\\"",
                "2026-09-02T16:00:00.000+0200",
                "2026-09-02T20:00:00.000+0200")
            + "]}");
    return jira;
  }

  private static String issue(
      String key, String type, String title, String created, @Nullable String resolved) {
    return """
        {"key":"%s","fields":{"summary":"%s","issuetype":{"name":"%s"},
         "created":"%s","resolutiondate":%s}}
        """
        .formatted(key, title, type, created, resolved == null ? "null" : "\"" + resolved + "\"");
  }
}
