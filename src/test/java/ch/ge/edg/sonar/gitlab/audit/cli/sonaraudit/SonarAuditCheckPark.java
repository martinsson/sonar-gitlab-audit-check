package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarAuditCheckFake.branch;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarAuditCheckFake.error;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarAuditCheckFake.ok;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarAuditCheckFake.points;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The default synthetic park. Four visible projects out of six:
 *
 * <ul>
 *   <li>alpha-service: main analysed 100 days ago (stale), full measures, GitLab binding, links, a
 *       usable trend; it is also the sample project of sections 2 and 4.
 *   <li>beta-api: main analysed 120 days ago, but develop 5 days ago: read on develop.
 *   <li>gamma-lib: main never analysed, develop 20 days ago: rescued by the branch pass.
 *   <li>delta-legacy: never analysed, branch list refused, binding refused.
 * </ul>
 *
 * <p>The instance does not know {@code test_errors}.
 */
final class SonarAuditCheckPark {

  static final List<String> ALL_METRICS =
      List.of(
          "ncloc",
          "files",
          "complexity",
          "cognitive_complexity",
          "comment_lines_density",
          "duplicated_lines_density",
          "new_duplicated_lines_density",
          "coverage",
          "line_coverage",
          "branch_coverage",
          "new_coverage",
          "lines_to_cover",
          "uncovered_lines",
          "tests",
          "test_failures",
          "test_errors",
          "skipped_tests",
          "test_success_density",
          "sqale_index",
          "sqale_debt_ratio",
          "sqale_rating",
          "bugs",
          "reliability_rating",
          "vulnerabilities",
          "security_rating",
          "security_hotspots",
          "security_hotspots_reviewed",
          "code_smells",
          "violations",
          "new_lines",
          "new_violations",
          "new_bugs",
          "new_vulnerabilities",
          "new_code_smells",
          "alert_status");

  private SonarAuditCheckPark() {}

  static void populate(SonarAuditCheckFake f) {
    f.metrics.addAll(ALL_METRICS.stream().filter(m -> !m.equals("test_errors")).toList());
    f.metrics.addAll(List.of("lines", "statements", "custom_metric"));
    f.project("alpha-service", "Alpha Service", f.ago(100));
    f.project("beta-api", "Beta API", f.ago(120));
    f.project("gamma-lib", "Gamma Lib", null);
    f.project("delta-legacy", "Delta; \"Legacy\"", null);
    branches(f);
    measures(f);
    history(f);
    attachments(f);
  }

  private static void branches(SonarAuditCheckFake f) {
    f.branches.put(
        "alpha-service",
        List.of(
            branch("main", true, f.ago(100)),
            branch("feature/x", false, f.ago(150)),
            branch("feature/y", false, null),
            branch("feature/z", false, "not-a-date-at-all-xx")));
    f.branches.put(
        "beta-api", List.of(branch("main", true, f.ago(120)), branch("develop", false, f.ago(5))));
    f.branches.put(
        "gamma-lib", List.of(branch("main", true, null), branch("develop", false, f.ago(20))));
    f.branches.put("delta-legacy", 403);
  }

  private static void measures(SonarAuditCheckFake f) {
    Map<String, String> alpha = new LinkedHashMap<>();
    alpha.put("ncloc", "1200");
    alpha.put("files", "40");
    alpha.put("coverage", "81.5");
    alpha.put("new_coverage", "period:70.0");
    alpha.put("new_lines", "periods:40");
    alpha.put("tests", "120");
    alpha.put("test_errors", "9");
    alpha.put("sqale_index", "650");
    alpha.put("sqale_rating", "1.0");
    alpha.put("violations", "80");
    alpha.put("new_violations", "period:3");
    alpha.put("alert_status", "OK");
    f.measures.put("alpha-service", alpha);
    f.measures.put("delta-legacy", Map.of("ncloc", "300", "bugs", "0"));
    f.measures.put("beta-api", Map.of("ncloc", "999", "coverage", "1.0"));
    f.measures.put(
        "beta-api@develop",
        Map.of("ncloc", "800", "coverage", "55.0", "bugs", "3", "alert_status", "ERROR"));
    f.measures.put("gamma-lib@develop", Map.of("ncloc", "50", "coverage", "0.0"));
    // What section 4 reads for the sample (measures/component without a branch).
    f.measures.put(
        "alpha-service@new-code",
        Map.of("new_lines", "period:40", "new_violations", "periods:3", "ncloc", "1200"));
  }

  private static void history(SonarAuditCheckFake f) {
    Map<String, List<Object[]>> alpha = new LinkedHashMap<>();
    alpha.put("violations", points(60, "100", 40, "90", 20, "85", 10, "80"));
    alpha.put("ncloc", points(60, "1000", 40, "1000", 20, "1100", 10, "1200"));
    alpha.put("sqale_debt_ratio", points(60, "2.0", 40, "x", 20, "", 10, "2.4"));
    alpha.put("sqale_index", points(60, "500", 10, "650"));
    f.history.put("alpha-service", alpha);
    Map<String, List<Object[]>> beta = new LinkedHashMap<>();
    beta.put("violations", points(30, "10", 5, "20", 15, "15"));
    beta.put("ncloc", points(30, "500", 15, "500", 5, "500"));
    f.history.put("beta-api@develop", beta);
    f.history.put("gamma-lib@develop", Map.of("violations", points(20, "5", 10, "5")));
  }

  private static void attachments(SonarAuditCheckFake f) {
    f.bindings.put(
        "alpha-service",
        ok(Map.of("key", "gl", "alm", "GITLAB", "repository", "101", "monorepo", false)));
    f.bindings.put("gamma-lib", ok(Map.of("key", "gh", "alm", "github")));
    f.bindings.put("delta-legacy", error(403, "Insufficient privileges"));
    f.links.put(
        "alpha-service",
        ok(
            Map.of(
                "links",
                List.of(
                    Map.of("type", "scm", "url", " https://git.example.test/team-a/alpha "),
                    Map.of("type", "", "url", "https://wiki.example.test/alpha"),
                    Map.of("type", "homepage", "url", " ")))));
    f.links.put("delta-legacy", error(403, "no"));
  }
}
