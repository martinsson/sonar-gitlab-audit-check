package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarAuditCheckFake.ok;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarAuditCheckRoutes.paging;

import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Request;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import java.util.List;
import java.util.Map;

/** Endpoints only the sample project is asked about: sections 2 (probes) and 4 (activity). */
final class SonarAuditCheckSampleRoutes {

  static final String SAMPLE_FILE = "alpha-service:src/main/java/org/example/App.java";

  private SonarAuditCheckSampleRoutes() {}

  static void install(SonarAuditCheckFake f, FakeHttpServer s) {
    s.on("/api/project_analyses/search", q -> analyses(f, q));
    s.on("/api/issues/search", SonarAuditCheckSampleRoutes::issues);
    s.on(
        "/api/settings/values",
        q ->
            ok(
                Map.of(
                    "settings",
                    List.of(Map.of("key", "sonar.exclusions", "values", List.of("**/gen/**"))))));
    s.on(
        "/api/qualitygates/get_by_project",
        q -> ok(Map.of("qualityGate", Map.of("id", "9", "name", "Team Gate", "isDefault", false))));
    s.on(
        "/api/qualityprofiles/search",
        q ->
            ok(
                Map.of(
                    "profiles",
                    List.of(Map.of("key", "p1", "language", "java"), Map.of("key", "p2")))));
    s.on(
        "/api/components/tree",
        q ->
            ok(
                Map.of(
                    "paging",
                    paging(1, 1, 57),
                    "components",
                    List.of(Map.of("key", SAMPLE_FILE)))));
    s.on("/api/sources/scm", q -> scm(f));
  }

  private static Response analyses(SonarAuditCheckFake f, Request q) {
    if ("5".equals(q.param("ps"))) {
      return ok(Map.of("paging", paging(1, 5, 12), "analyses", List.of()));
    }
    return ok(
        Map.of(
            "paging", paging(1, 500, 4),
            "analyses",
                List.of(
                    analysis(f.ago(3), List.of(event("VERSION", "1.3"), event("OTHER", "x"))),
                    analysis(f.ago(13), List.of(event("VERSION", "1.2"))),
                    analysis(f.ago(33), List.of(event("VERSION", "1.2"))),
                    Map.of("key", "a4", "date", "bad"))));
  }

  private static Map<String, Object> analysis(String date, List<Map<String, String>> events) {
    return Map.of("key", "a-" + date, "date", date, "events", events);
  }

  private static Map<String, String> event(String category, String name) {
    return Map.of("key", "e", "category", category, "name", name);
  }

  private static Response issues(Request q) {
    if ("severities".equals(q.param("facets"))) return ok(Map.of("total", 42));
    if (q.param("resolutions") != null) return ok(Map.of("paging", paging(1, 1, 3)));
    List<Map<String, Object>> authors =
        List.of(Map.of("val", "alice@example.test", "count", 8), Map.of("val", "bob", "count", 2));
    return ok(
        Map.of(
            "total",
            10,
            "facets",
            List.of(
                Map.of("property", "severities", "values", List.of()),
                Map.of("property", "author", "values", authors))));
  }

  private static Response scm(SonarAuditCheckFake f) {
    return ok(
        Map.of(
            "scm",
            List.of(
                List.of("1", "alice@example.test", f.ago(200), "r1"),
                List.of("5", "bob", f.ago(15), "r2"),
                List.of("9", "", f.ago(30), "r3"),
                List.of("12", "carol"))));
  }
}
