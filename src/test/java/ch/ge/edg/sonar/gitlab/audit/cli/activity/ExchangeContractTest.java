package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.exchange.Crossing;
import ch.ge.edg.sonar.gitlab.audit.exchange.GitlabInventory;
import ch.ge.edg.sonar.gitlab.audit.exchange.GitlabPractices;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The two files GitlabActivityAudit writes, against the column names the crossing reads from them.
 */
class ExchangeContractTest {

  @Test
  void inventaire_carries_the_columns_the_crossing_reads() {
    assertThat(CsvFiles.INVENTORY)
        .contains(
            GitlabInventory.ID,
            GitlabInventory.PATH,
            GitlabInventory.NAME,
            GitlabInventory.LAST_ACTIVITY_AT,
            GitlabInventory.SELECTED,
            GitlabPractices.COMMITS_WINDOW);
  }

  @Test
  void pratiques_carries_the_columns_the_crossing_reads() {
    List<String> required = List.of(GitlabPractices.required());
    assertThat(CsvFiles.PRATIQUES).containsAll(required).containsAll(GitlabPractices.CROSSED);
    assertThat(CsvFiles.PRATIQUES)
        .contains(
            GitlabPractices.ID,
            GitlabPractices.LINES_CHANGED,
            GitlabPractices.SONAR_KEY,
            GitlabPractices.POM_KEY,
            GitlabPractices.POM_NAME);
    assertThat(Crossing.header(List.of())).contains(Crossing.gitlab(GitlabPractices.PATH));
  }
}
