package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** What a commit title says of its feature: issue #51's conventional-commit rule, as written. */
class CommitTitleTest {

  @Test
  void theKeyIsTheBracketRightAfterTheType() {
    assertThat(CommitTitle.of("feat(api)!: [ABC-1] x")).isEqualTo(new CommitTitle("feat", "ABC-1"));
    assertThat(CommitTitle.of("Fix: [ABC-2]y")).isEqualTo(new CommitTitle("fix", "ABC-2"));
    assertThat(CommitTitle.of("refactor: no key")).isEqualTo(new CommitTitle("refactor", null));
    // Elsewhere in the title, a key is not read: the rule anchors it after the type.
    assertThat(CommitTitle.of("chore: bump [ABC-3] deps"))
        .isEqualTo(new CommitTitle("chore", null));
    assertThat(CommitTitle.of("Update UTF-8 readme"))
        .isEqualTo(new CommitTitle(CommitTitle.NO_TYPE, null));
  }

  @Test
  void onlyFeatAndFixCountWithoutAStoryAndTheCiReleaseCommitsAreKnown() {
    assertThat(CommitTitle.of("fix: x").work()).isTrue();
    assertThat(CommitTitle.of("feat!: x").work()).isTrue();
    assertThat(CommitTitle.of("test: [ABC-4] x").work()).isFalse();
    assertThat(CommitTitle.ciRelease("ci(release): set release version 1")).isTrue();
    assertThat(CommitTitle.ciRelease("ci(release): set next SNAPSHOT version 2")).isTrue();
    assertThat(CommitTitle.ciRelease("ci: cache maven")).isFalse();
  }

  @Test
  void onlyAMergeIntoTheDefaultBranchNamesABranchAndOnlyFeatureOrBugfixGiveAKey() {
    assertThat(CommitTitle.mergedBranch("Merge branch 'feature/ABC-483_xxx' into 'main'", "main"))
        .isEqualTo("feature/ABC-483_xxx");
    assertThat(CommitTitle.mergedBranch("Merge branch 'bugfix/ABC-9_y'", "main"))
        .isEqualTo("bugfix/ABC-9_y");
    assertThat(
            CommitTitle.mergedBranch(
                "Merge branch 'refs/heads/main' into feature/ABC-483_xxx", "main"))
        .isNull();
    assertThat(
            CommitTitle.mergedBranch(
                "Merge remote-tracking branch 'origin/main' into feature/ABC-1", "main"))
        .isNull();
    assertThat(CommitTitle.mergedBranch("Merge branch 'a' into 'develop'", "main")).isNull();
    assertThat(CommitTitle.mergedBranch("feat: [ABC-1] merge things", "main")).isNull();
    assertThat(CommitTitle.branchKey("feature/ABC-483_xxx")).isEqualTo("ABC-483");
    assertThat(CommitTitle.branchKey("bugfix/ABC-585")).isEqualTo("ABC-585");
    assertThat(CommitTitle.branchKey("feature/cleanup")).isNull();
    assertThat(CommitTitle.branchKey("hotfix/ABC-7_x")).isNull();
  }
}
