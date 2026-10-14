package ch.ge.edg.sonar.gitlab.audit.cli;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheck.Attachments;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheck.Component;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheck.CurrentUser;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheck.Facet;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheck.FacetValue;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheck.IssuesSearch;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheck.Measure;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheck.Paging;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheck.Period;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheck.QualityGateResponse;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheck.ScmResponse;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheck.Verdict;
import ch.ge.edg.sonar.gitlab.audit.sonar.Sonar;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

/** Small pure helpers and the response records' accessors. */
class SonarAuditCheckHelpersTest {

  @Test
  void parseDateReadsColonOffsetsIntoTheLocalZone() {
    TimeZone old = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try {
      assertThat(SonarAuditCheck.parseDate("2026-08-16T05:43:07+02:00"))
          .isEqualTo(LocalDateTime.of(2026, 8, 16, 3, 43, 7));
      // Sonar writes "+0200": ISO_OFFSET_DATE_TIME rejects it, so the offset is dropped and
      // the wall-clock time is kept as is (the comment above parseDate says otherwise).
      assertThat(SonarAuditCheck.parseDate("2026-08-16T05:43:07+0200"))
          .isEqualTo(LocalDateTime.of(2026, 8, 16, 5, 43, 7));
    } finally {
      TimeZone.setDefault(old);
    }
  }

  @Test
  void parseDateFallsBackToTheFirstNineteenCharacters() {
    assertThat(SonarAuditCheck.parseDate("2026-08-16T05:43:07.123Zjunk"))
        .isEqualTo(LocalDateTime.of(2026, 8, 16, 5, 43, 7));
    assertThat(SonarAuditCheck.parseDate("2026-08-16T05:43:07"))
        .isEqualTo(LocalDateTime.of(2026, 8, 16, 5, 43, 7));
    assertThat(SonarAuditCheck.parseDate("2026-08-16T05:43:0")).isNull();
    assertThat(SonarAuditCheck.parseDate(null)).isNull();
    assertThat(SonarAuditCheck.parseDate("not-a-date-at-all-xx")).isNull();
  }

  @Test
  void historyPathSitsNextToTheInventory() {
    assertThat(SonarAuditCheck.historyPath(Path.of("out", "inv.csv")))
        .isEqualTo(Path.of("out", "inv-historique.csv"));
    assertThat(SonarAuditCheck.historyPath(Path.of("out", "a.b.csv")))
        .isEqualTo(Path.of("out", "a.b-historique.csv"));
    assertThat(SonarAuditCheck.historyPath(Path.of("inv")))
        .isEqualTo(Path.of("inv-historique.csv"));
    assertThat(SonarAuditCheck.historyPath(Path.of(".inv")))
        .isEqualTo(Path.of(".inv-historique.csv"));
  }

  @Test
  void decIsTwoDecimalsWithAPoint() {
    assertThat(SonarAuditCheck.dec(null)).isEmpty();
    assertThat(SonarAuditCheck.dec(1.234)).isEqualTo("1.23");
    assertThat(SonarAuditCheck.dec(1.236)).isEqualTo("1.24");
    assertThat(SonarAuditCheck.dec(-11.4449)).isEqualTo("-11.44");
    assertThat(SonarAuditCheck.dec(2.0)).isEqualTo("2.00");
  }

  @Test
  void paramsPairsKeysAndValuesAndDropsAnOddTail() {
    assertThat(SonarAuditCheck.params("a", "1", "b", "2", "c"))
        .containsExactly(Map.entry("a", "1"), Map.entry("b", "2"));
    assertThat(SonarAuditCheck.params()).isEmpty();
  }

  @Test
  void shortNameKeepsWhatFollowsTheLastColonUpToFifty() {
    assertThat(SonarAuditCheck.shortName("proj:src/A.java")).isEqualTo("src/A.java");
    assertThat(SonarAuditCheck.shortName("noColon")).isEqualTo("noColon");
    assertThat(SonarAuditCheck.shortName("p:" + "x".repeat(60))).isEqualTo("x".repeat(50));
  }

  @Test
  void nullSafeLittleHelpers() {
    assertThat(SonarAuditCheck.orZero(null)).isZero();
    assertThat(SonarAuditCheck.orZero(4)).isEqualTo(4);
    assertThat(SonarAuditCheck.totalOf(null)).isZero();
    assertThat(SonarAuditCheck.totalOf(new Paging(1, 1, null))).isZero();
    assertThat(SonarAuditCheck.totalOf(new Paging(1, 1, 7))).isEqualTo(7);
    assertThat(SonarAuditCheck.total(null)).isEqualTo("?");
    assertThat(SonarAuditCheck.total(new Paging(1, 1, null))).isEqualTo("?");
    assertThat(SonarAuditCheck.total(new Paging(1, 1, 7))).isEqualTo("7");
    assertThat(SonarAuditCheck.size(null)).isZero();
    assertThat(SonarAuditCheck.size(List.of(1, 2))).isEqualTo(2);
    assertThat(SonarAuditCheck.isBlank(" ")).isTrue();
    assertThat(SonarAuditCheck.isBlank("a")).isFalse();
    assertThat(SonarAuditCheck.notEmpty(null)).isFalse();
    assertThat(SonarAuditCheck.notEmpty(List.of())).isFalse();
    assertThat(SonarAuditCheck.notEmpty(List.of(1))).isTrue();
    assertThat(SonarAuditCheck.orEmptyList(null)).isEmpty();
    assertThat(SonarAuditCheck.orEmpty(null)).isEmpty();
    assertThat(SonarAuditCheck.orNa(" ")).isEqualTo("n/a");
    assertThat(SonarAuditCheck.orNa(null)).isEqualTo("n/a");
    assertThat(SonarAuditCheck.orNa("v")).isEqualTo("v");
    assertThat(SonarAuditCheck.truncate("abcd", 4)).isEqualTo("abcd");
    assertThat(SonarAuditCheck.truncate("abcde", 4)).isEqualTo("abcd");
  }

  @Test
  void verdictFollowsTheStatus() {
    assertThat(
            Arrays.stream(new int[] {200, 401, 403, 404, 0, 500, 201})
                .mapToObj(s -> SonarAuditCheck.verdict(new Sonar.Response(s, "")))
                .toList())
        .containsExactly(
            Verdict.OK,
            Verdict.DENIED,
            Verdict.DENIED,
            Verdict.MISSING,
            Verdict.ERROR,
            Verdict.ERROR,
            Verdict.ERROR);
    assertThat(Verdict.EMPTY.tag).isEqualTo("VIDE    ");
    assertThat(Verdict.EMPTY.color).isEqualTo(SonarAuditCheck.YELLOW);
  }

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

  @Test
  void componentOnBranchKeepsTheMainDate() {
    Component c = new Component("k", "n", "TRK", "d-main", "leak", null, null, null);
    assertThat(c.onOtherBranch()).isFalse();
    Component b = c.onBranch("dev", false, "d-dev");
    assertThat(b)
        .isEqualTo(new Component("k", "n", "TRK", "d-dev", "leak", "dev", false, "d-main"));
    assertThat(b.onOtherBranch()).isTrue();
    assertThat(c.onBranch("main", true, "d").onOtherBranch()).isFalse();
    assertThat(new Component("k", null, null, null, null, "dev", null, null).onOtherBranch())
        .isFalse();
  }

  @Test
  void recordAccessorsWithMissingParts() {
    assertThat(new Attachments("illisible (HTTP 403)", "", "").refused()).isTrue();
    assertThat(new Attachments("gitlab", "", "").refused()).isFalse();
    assertThat(new CurrentUser("u", null, null, null).globalPermissions()).isEmpty();
    assertThat(
            new CurrentUser("u", null, new CurrentUser.Permissions(null), null).globalPermissions())
        .isEmpty();
    assertThat(new QualityGateResponse(null).gateName()).isEmpty();
    assertThat(
            new QualityGateResponse(new QualityGateResponse.QualityGate("1", null, true))
                .gateName())
        .isEmpty();
  }
}
