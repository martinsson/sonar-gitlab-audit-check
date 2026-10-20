package ch.ge.edg.sonar.gitlab.audit.dora;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RepoPathTest {

  @Test
  void http_and_ssh_forms_give_the_same_path() {
    assertThat(RepoPath.of("https://user:pw@gitlab.example.com/Group/Sub/Proj.git"))
        .isEqualTo("group/sub/proj");
    assertThat(RepoPath.of("git@gitlab.example.com:group/sub/proj.git"))
        .isEqualTo("group/sub/proj");
    assertThat(RepoPath.of("ssh://git@gitlab.example.com:2222/group/proj")).isEqualTo("group/proj");
    assertThat(RepoPath.of("https://gitlab.example.com/group/proj/-/tree/main"))
        .isEqualTo("group/proj");
    assertThat(RepoPath.of("https://gitlab.example.com/group/proj///")).isEqualTo("group/proj");
    assertThat(RepoPath.of(null)).isEmpty();
  }

  @Test
  void a_plain_path_or_id_is_kept_and_keep_case_keeps_case() {
    assertThat(RepoPath.keepCase("/Group/Proj/")).isEqualTo("Group/Proj");
    assertThat(RepoPath.keepCase("1234")).isEqualTo("1234");
    assertThat(RepoPath.keepCase("https://h/Octo/Hello-World")).isEqualTo("Octo/Hello-World");
  }

  @Test
  void matches_the_project_also_under_a_relative_url_root() {
    assertThat(RepoPath.matches("https://h/gitlab/group/proj.git", "Group/Proj")).isTrue();
    assertThat(RepoPath.matches("https://h/group/proj.git", "group/proj")).isTrue();
    assertThat(RepoPath.matches("https://h/othergroup/proj.git", "group/proj")).isFalse();
    assertThat(RepoPath.matches("https://h/group/proj-two.git", "group/proj")).isFalse();
    assertThat(RepoPath.matches("https://h/x", "")).isFalse();
  }
}
