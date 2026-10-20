package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.GitLabProjectReportFakeGitlab.commit;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The transport options: raw dumps, TLS leniency, timeout. */
class GitLabProjectReportOptionsTest extends GitLabProjectReportHarness {

  static final String NO_HOSTNAME_CHECK = "jdk.internal.httpclient.disableHostnameVerification";

  @TempDir Path dir;

  void smallProject() {
    project(fx.project(ID, PATH, "main", false));
    commits(List.of(commit("Alice", "alice@example.com", null, fx.ago(1, 0), new int[] {1, 0})));
    contributors(1);
    server.json(GitLabProjectReportFakeGitlab.API + ID + "/merge_requests", "[]");
  }

  @Test
  void raw_answers_are_dumped_one_file_per_call() throws IOException {
    smallProject();
    Path dumps = dir.resolve("dumps");

    Cli.Result r = run("--dump-dir", dumps.toString());

    assertThat(r.exitCode()).isZero();
    try (Stream<Path> files = Files.list(dumps)) {
      List<String> names = files.map(p -> p.getFileName().toString()).sorted().toList();
      // Four answers, named after their request, and the index that keeps their order.
      assertThat(names).hasSize(5).contains("index.txt");
      assertThat(names).anyMatch(n -> n.startsWith("v4_projects_team_a_2Falpha_service-"));
      assertThat(names).anyMatch(n -> n.startsWith("v4_projects_42_merge_requests-"));
    }
    assertThat(r.out()).contains("  4 appels API.");
  }

  @Test
  void a_blank_bot_pattern_keeps_the_default_one() {
    project(fx.project(ID, PATH, "main", false));
    commits(
        List.of(
            commit("Alice", "alice@example.com", null, fx.ago(1, 0), new int[] {1, 0}),
            commit("dependabot", "dependabot@example.com", null, fx.ago(2, 0), new int[] {1, 0})));
    contributors(1);
    server.json(GitLabProjectReportFakeGitlab.API + ID + "/merge_requests", "[]");

    Cli.Result r = run("--bot-pattern", " ");

    assertThat(r.out()).contains("  Commits de bots écartés         : 1");
  }

  @Test
  void insecure_and_timeout_still_read_a_plain_http_instance() {
    smallProject();
    String before = System.getProperty(NO_HOSTNAME_CHECK);
    try {
      Cli.Result r = run("--insecure", "--timeout", "5");

      assertThat(r.exitCode()).isZero();
      assertThat(System.getProperty(NO_HOSTNAME_CHECK)).isEqualTo("true");
    } finally {
      if (before == null) {
        System.clearProperty(NO_HOSTNAME_CHECK);
      } else {
        System.setProperty(NO_HOSTNAME_CHECK, before);
      }
    }
  }
}
