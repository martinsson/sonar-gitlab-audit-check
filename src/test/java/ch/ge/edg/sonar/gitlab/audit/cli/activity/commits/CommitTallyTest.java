package ch.ge.edg.sonar.gitlab.audit.cli.activity.commits;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CommitTallyTest {

  @ParameterizedTest
  @CsvSource({
    "'Revert \"x\"',true",
    "'  revert: y',true",
    "REVERT,true",
    "fix revert,false",
    "reverted,false"
  })
  void revertIsAnchoredAtTheStartOfTheTitle(String title, boolean reverts) {
    assertThat(CommitTally.REVERT.matcher(title).find()).isEqualTo(reverts);
  }
}
