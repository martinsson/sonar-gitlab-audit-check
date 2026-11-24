package ch.ge.edg.sonar.gitlab.audit.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RepoPathTest {

  @Test
  void httpAndSshFormsGiveTheSamePathInItsOwnCase() {
    assertThat(RepoPath.of("https://user:pw@gitlab.example.com/Group/Sub/Proj.git"))
        .isEqualTo("Group/Sub/Proj");
    assertThat(RepoPath.of("git@gitlab.example.com:group/sub/proj.git"))
        .isEqualTo("group/sub/proj");
    assertThat(RepoPath.of("ssh://git@gitlab.example.com:2222/group/proj")).isEqualTo("group/proj");
    assertThat(RepoPath.of("https://gitlab.example.com/group/proj/-/tree/main"))
        .isEqualTo("group/proj");
    assertThat(RepoPath.of("https://gitlab.example.com/group/proj///")).isEqualTo("group/proj");
    assertThat(RepoPath.of(null)).isEmpty();
  }

  @Test
  void aPlainPathOrIdIsKept() {
    assertThat(RepoPath.of("/Group/Proj/")).isEqualTo("Group/Proj");
    assertThat(RepoPath.of("1234")).isEqualTo("1234");
    assertThat(RepoPath.of("https://h/Octo/Hello-World")).isEqualTo("Octo/Hello-World");
  }

  /**
   * Forms that the two former implementations (dora's RepoPath and matching.Keys.linkedPaths) read
   * differently. Each row is pinned to the one answer the unified function gives.
   */
  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "https://h/g/p?x=1#y | g/p",
        "git@h:G/P?x | G/P",
        "https://h/g/p?ref=/-/x | g/p",
        "x-y://h/g/p | g/p",
        "https://h/g/p.git// | g/p",
        "/G/P | G/P",
        "git@h:/g/p.git | g/p",
        "git@h/g/p | g/p",
        "https://h | ''"
      })
  void theFormsTheTwoImplementationsReadDifferentlyGiveOneAnswer(String url, String expected) {
    assertThat(RepoPath.of(url)).isEqualTo(expected);
  }

  @Test
  void matchesTheProjectAlsoUnderARelativeUrlRoot() {
    assertThat(RepoPath.matches("https://h/gitlab/group/proj.git", "Group/Proj")).isTrue();
    assertThat(RepoPath.matches("https://h/group/proj.git", "group/proj")).isTrue();
    assertThat(RepoPath.matches("https://h/othergroup/proj.git", "group/proj")).isFalse();
    assertThat(RepoPath.matches("https://h/group/proj-two.git", "group/proj")).isFalse();
    assertThat(RepoPath.matches("https://h/x", "")).isFalse();
  }
}
