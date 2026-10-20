package ch.ge.edg.sonar.gitlab.audit.cli.vues;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.exchange.Crossing;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** croisement.csv as CrossAudit writes it, against every column Vues puts in its views. */
class CrossingContractTest {

  @Test
  void everyCrossedColumnAViewNamesIsWrittenByTheCrossing() {
    List<String> views = new ArrayList<>();
    views.addAll(Vues.ESSENTIAL_FIRST);
    views.addAll(Vues.DRIFT);
    views.addAll(Vues.UNPAIRED);
    List<String> written = Crossing.header(List.of());

    List<String> fromTheTwoSides =
        views.stream()
            .filter(c -> c.startsWith("gl_") || c.startsWith("sq_") || c.startsWith("issues_"))
            .toList();

    assertThat(fromTheTwoSides).isNotEmpty().allMatch(written::contains);
    assertThat(written).contains(Crossing.PROJECT, Crossing.CONFIDENCE);
  }
}
