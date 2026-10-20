package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/** The command line of {@link GitLabProjectReport}, and the path it accepts. */
class GitLabProjectReportUnitTest {

  @Test
  void paths_are_taken_in_any_of_the_forms_a_user_pastes() {
    assertThat(ProjectPath.normalize("team-a/alpha")).isEqualTo("team-a/alpha");
    assertThat(ProjectPath.normalize("  /team-a/alpha/  ")).isEqualTo("team-a/alpha");
    assertThat(ProjectPath.normalize("https://gitlab.example/team-a/sub/alpha.git"))
        .isEqualTo("team-a/sub/alpha");
    assertThat(ProjectPath.normalize("https://gitlab.example/team-a/alpha/-/issues/3"))
        .isEqualTo("team-a/alpha");
    assertThat(ProjectPath.normalize("https://gitlab.example")).isEmpty();
    assertThat(ProjectPath.normalize("http://h//a//")).isEqualTo("a");
    assertThat(ProjectPath.normalize("123")).isEqualTo("123");
    assertThat(ProjectPath.normalize("://host/a/b")).isEqualTo("a/b");
    assertThat(ProjectPath.normalize("a.git")).isEqualTo("a");
  }

  @Test
  void defaults_are_those_documented() {
    GitLabProjectReport cmd = new GitLabProjectReport(Cli.host());
    new CommandLine(cmd).parseArgs("--path", "a/b");
    assertThat(cmd.days).isEqualTo(180);
    assertThat(cmd.maxCommits).isEqualTo(5000);
    assertThat(cmd.maxMrs).isEqualTo(500);
    assertThat(cmd.sample).isEqualTo(30);
    assertThat(cmd.includeBots).isFalse();
    assertThat(cmd.botPattern).isNull();
    assertThat(cmd.gitlab.connectionOrAnonymous("http://u"))
        .isEqualTo(new Gitlab.Connection("http://u", null, 30, false, null));
    assertThat(cmd.color.mode()).isEqualTo("auto");
  }

  @Test
  void the_path_is_required() {
    Cli.Result r = Cli.run(GitLabProjectReport::new, "--url", "http://127.0.0.1:9");

    assertThat(r.exitCode()).isEqualTo(2);
    assertThat(r.err()).contains("Missing required option: '--path=<path>'");
  }
}
