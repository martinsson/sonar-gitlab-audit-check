package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.GitLabProjectReportFakeGitlab.API;
import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.GitLabProjectReportFakeGitlab.json;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Shared set-up of the {@link GitLabProjectReport} tests: a fake GitLab, a pinned locale and time
 * zone (the report formats numbers with the default locale and reads dates in the default zone),
 * and a runner that always passes --url, --token and --color so the environment never leaks in.
 */
abstract class GitLabProjectReportHarness {

  static final long ID = 42;
  static final String PATH = "team-a/alpha-service";

  FakeHttpServer server;
  GitLabProjectReportFakeGitlab fx;
  private TimeZone zone;

  @BeforeEach
  void startFakeGitlab() {
    zone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    server = new FakeHttpServer();
    fx = new GitLabProjectReportFakeGitlab();
  }

  @AfterEach
  void stopFakeGitlab() {
    server.close();
    TimeZone.setDefault(zone);
  }

  String projectRoute() {
    return API + "team-a%2Falpha-service";
  }

  void project(Map<String, Object> p) {
    server.json(projectRoute(), json(p));
  }

  void commits(List<?> items) {
    server.on(
        API + ID + "/repository/commits", r -> GitLabProjectReportFakeGitlab.paged(r, items, 100));
  }

  void contributors(int n) {
    List<Object> items = new ArrayList<>();
    for (int i = 0; i < n; i++)
      items.add(GitLabProjectReportFakeGitlab.obj("name", "c" + i, "commits", i));
    server.json(API + ID + "/repository/contributors", json(items));
  }

  void mrs(List<?> items) {
    server.on(
        API + ID + "/merge_requests", r -> GitLabProjectReportFakeGitlab.paged(r, items, 100));
  }

  void notes(long iid, List<?> items) {
    server.json(API + ID + "/merge_requests/" + iid + "/notes", json(items));
  }

  void fail(String route, int status, String body) {
    server.on(route, r -> Response.status(status, body));
  }

  /** Runs the report on {@link #PATH}, with a token unless the arguments name one. */
  Cli.Result run(String... extra) {
    List<String> args = new ArrayList<>(List.of("--url", server.url(), "--path", PATH));
    args.addAll(List.of(extra));
    if (!args.contains("--token")) args.addAll(List.of("--token", "glpat-test"));
    if (!args.contains("--color")) args.addAll(List.of("--color", "never"));
    return Cli.run(fx.clock(), GitLabProjectReport::new, args.toArray(String[]::new));
  }

  /** The output with the dates that move with the clock replaced by names. */
  String normalized(String out) {
    return out.replace(fx.dateAgo(400, 6), "<created>").replace(fx.dateAgo(3, 6), "<active>");
  }
}
