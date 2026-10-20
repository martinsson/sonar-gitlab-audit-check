package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.csv.Column;
import ch.ge.edg.sonar.gitlab.audit.exchange.Crossing;
import ch.ge.edg.sonar.gitlab.audit.exchange.GitlabInventory;
import ch.ge.edg.sonar.gitlab.audit.exchange.GitlabPractices;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The two files GitlabActivityAudit writes, against the column names the crossing reads from them.
 */
class ExchangeContractTest {

  private static List<String> inventoryColumns() {
    return CsvFiles.inventory(30).stream().map(Column::name).toList();
  }

  private static List<String> practicesColumns() {
    return CsvFiles.practices(30).stream().map(Column::name).toList();
  }

  @Test
  void inventaireCarriesTheColumnsTheCrossingReads() {
    assertThat(inventoryColumns())
        .contains(
            GitlabInventory.ID,
            GitlabInventory.PATH,
            GitlabInventory.NAME,
            GitlabInventory.LAST_ACTIVITY_AT,
            GitlabInventory.SELECTED,
            GitlabPractices.COMMITS_WINDOW);
  }

  @Test
  void pratiquesCarriesTheColumnsTheCrossingReads() {
    List<String> required = List.of(GitlabPractices.required());
    assertThat(practicesColumns()).containsAll(required).containsAll(GitlabPractices.CROSSED);
    assertThat(practicesColumns())
        .contains(
            GitlabPractices.ID,
            GitlabPractices.LINES_CHANGED,
            GitlabPractices.SONAR_KEY,
            GitlabPractices.POM_KEY,
            GitlabPractices.POM_NAME);
    assertThat(Crossing.header(List.of())).contains(Crossing.gitlab(GitlabPractices.PATH));
  }
}
