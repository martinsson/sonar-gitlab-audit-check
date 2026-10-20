package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.csv.Column;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Une ligne de pratiques.csv. */
class PracticesCsvTest {

  private static AuditedProject audited(String json) throws Exception {
    return AuditedProject.entering(GitlabProject.of(Gitlab.MAPPER.readTree(json)));
  }

  private static <T> String row(List<Column<T>> columns, T row) {
    return columns.stream().map(c -> c.cell().apply(row)).collect(Collectors.joining("|"));
  }

  @Test
  void anEmptyPratRowKeepsUnknownsBlank() throws Exception {
    assertThat(
            row(
                CsvFiles.practices(30),
                new Assessed(
                    audited("{}"),
                    new Practices(
                        Practices.Protection.none(),
                        Practices.Review.none(),
                        Practices.Builds.none(),
                        new Practices.Deployment(null, null, false),
                        new Practices.Sonar(List.of(), new CiFacts(), false),
                        Practices.Lines.none()))))
        .isEqualTo("|||||false|false|0||0||||0|0|||||||false|false|false||||0|30|||false||");
  }
}
