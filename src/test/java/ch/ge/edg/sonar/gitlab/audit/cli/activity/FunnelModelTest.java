package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.csv.Column;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Un projet de l'inventaire, ce que l'entonnoir en apprend, et ses lignes de CSV. */
class FunnelModelTest {

  private static GitlabProject project(String json) throws Exception {
    return GitlabProject.of(Gitlab.MAPPER.readTree(json));
  }

  private static AuditedProject audited(String json) throws Exception {
    return AuditedProject.entering(project(json));
  }

  private static <T> String row(List<Column<T>> columns, T row) {
    return columns.stream().map(c -> c.cell().apply(row)).collect(Collectors.joining("|"));
  }

  @Test
  void projectReadsEveryField() throws Exception {
    GitlabProject p =
        project(
            "{\"id\":7,\"path_with_namespace\":\"team-a/alpha\",\"name\":\"Alpha\","
                + "\"namespace\":{\"full_path\":\"team-a\"},\"default_branch\":\"main\","
                + "\"visibility\":\"internal\",\"created_at\":\"2020-01-01T00:00:00Z\","
                + "\"last_activity_at\":\"2099-01-01T00:00:00Z\",\"archived\":true,"
                + "\"empty_repo\":true,\"mirror\":true,"
                + "\"forked_from_project\":{\"path_with_namespace\":\"up/alpha\"},"
                + "\"statistics\":{\"commit_count\":12,\"repository_size\":3400}}");
    assertThat(p)
        .isEqualTo(
            new GitlabProject(
                7,
                "team-a/alpha",
                "Alpha",
                "team-a",
                "internal",
                new GitlabProject.Dates(
                    OffsetDateTime.parse("2020-01-01T00:00:00Z"),
                    OffsetDateTime.parse("2099-01-01T00:00:00Z")),
                new GitlabProject.Repository("main", true, true, true, "up/alpha", 12L, 3400L)));
  }

  @Test
  void projectToleratesMissingFields() throws Exception {
    GitlabProject p =
        project("{\"forked_from_project\":null,\"statistics\":{\"commit_count\":null}}");
    assertThat(p)
        .isEqualTo(
            new GitlabProject(
                0,
                "",
                "",
                "",
                "",
                new GitlabProject.Dates(null, null),
                new GitlabProject.Repository("", false, false, false, null, null, null)));
    assertThat(project("{\"forked_from_project\":{}}").repository().forkedFrom()).isNull();
  }

  @Test
  void perWeekNeedsCommitsAndAWindow() throws Exception {
    AuditedProject p = audited("{}");
    assertThat(p.perWeek(7)).isZero();
    p =
        p.with(
            new CommitMeasure.Counted(
                CommitMeasure.Head.unknown(), new CommitMeasure.Volume(14, 0, 0, 0, 0, 0), false));
    assertThat(p.perWeek(7)).isEqualTo(14.0);
    assertThat(p.perWeek(28)).isEqualTo(3.5);
    assertThat(p.perWeek(0)).isZero();
    assertThat(p.perWeek(-1)).isZero();
  }

  @Test
  void anEmptyProjRowKeepsUnknownsBlank() throws Exception {
    assertThat(row(CsvFiles.inventory(90), audited("{}")))
        .isEqualTo("0||||||||false|false|false||||||||||||false||false|false|");
  }
}
