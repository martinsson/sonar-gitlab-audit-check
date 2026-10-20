package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.exchange.Crossing;
import ch.ge.edg.sonar.gitlab.audit.exchange.SonarInventory;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Sonar inventory as written by SonarAuditCheck, against every column its readers name (rank,
 * cross, the sonarGitlab matching): a rename on either side fails here, not in a field run.
 */
class InventoryContractTest {

  @Test
  void the_written_inventory_carries_every_column_the_readers_name(@TempDir Path dir)
      throws Exception {
    Path file = dir.resolve("inventaire.csv");
    new InventoryCsv(true, Clock.systemUTC())
        .write(file, new InventoryCsv.Park(List.of(), Map.of(), Map.of(), Map.of()));

    Csv.Table written = Csv.read(file);

    written.require(file, "SonarAuditCheck --csv", SonarInventory.required());
    List<String> named =
        Arrays.stream(SonarInventory.class.getFields())
            .filter(f -> Modifier.isStatic(f.getModifiers()) && f.getType() == String.class)
            .map(
                f -> {
                  try {
                    return (String) f.get(null);
                  } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                  }
                })
            .toList();
    assertThat(named).isNotEmpty().allMatch(written::has);
    assertThat(Crossing.SONAR_SIDE).allMatch(written::has);
  }
}
