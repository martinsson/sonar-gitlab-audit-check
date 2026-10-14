package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Facet;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.FacetValue;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.IssuesSearch;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Measure;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Paging;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Period;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.ScmResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Accessors of the measure, issue and SCM response records. */
class MeasureRecordsTest {

  @Test
  void effectiveValuePrefersValueThenPeriodThenFirstPeriods() {
    Period p1 = new Period(1, "p1", null);
    Period p2 = new Period(2, "p2", null);
    assertThat(new Measure("c", "m", "v", p1, List.of(p2)).effectiveValue()).isEqualTo("v");
    assertThat(new Measure("c", "m", null, p1, List.of(p2)).effectiveValue()).isEqualTo("p1");
    assertThat(new Measure("c", "m", null, null, List.of(p2, p1)).effectiveValue()).isEqualTo("p2");
    assertThat(new Measure("c", "m", null, null, List.of()).effectiveValue()).isNull();
    assertThat(new Measure("c", "m", null, null, null).effectiveValue()).isNull();
  }

  @Test
  void issueCountPrefersTheRootTotal() {
    assertThat(new IssuesSearch(5, new Paging(1, 1, 9), null).issueCount()).isEqualTo("5");
    assertThat(new IssuesSearch(null, new Paging(1, 1, 9), null).issueCount()).isEqualTo("9");
    assertThat(new IssuesSearch(null, new Paging(1, 1, null), null).issueCount()).isEqualTo("?");
    assertThat(new IssuesSearch(null, null, null).issueCount()).isEqualTo("?");
  }

  @Test
  void facetValuesOfTheFirstMatchingFacet() {
    FacetValue a = new FacetValue("a", 1);
    IssuesSearch s =
        new IssuesSearch(
            null,
            null,
            List.of(
                new Facet("x", null),
                new Facet("author", List.of(a)),
                new Facet("author", List.of())));
    assertThat(s.facetValues("author")).containsExactly(a);
    assertThat(s.facetValues("x")).isEmpty();
    assertThat(s.facetValues("none")).isEmpty();
    assertThat(new IssuesSearch(null, null, null).facetValues("author")).isEmpty();
  }

  @Test
  void scmDescription() {
    assertThat(new ScmResponse(null).describe()).isEqualTo("aucun changeset");
    assertThat(new ScmResponse(List.of(List.of("1", "a"))).describe()).isEqualTo("1 changeset(s)");
    assertThat(new ScmResponse(List.of(List.of("1"), List.of("2", ""))).describe())
        .isEqualTo("2 changeset(s), aucun auteur (clone shallow ?)");
  }
}
