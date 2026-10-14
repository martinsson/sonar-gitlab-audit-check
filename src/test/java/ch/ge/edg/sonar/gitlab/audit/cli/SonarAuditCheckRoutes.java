package ch.ge.edg.sonar.gitlab.audit.cli;

import static ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheckFake.SAMPLE;
import static ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheckFake.error;
import static ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheckFake.ok;

import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Request;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The SonarQube endpoints the inventory calls, answered from a {@link SonarAuditCheckFake}. */
final class SonarAuditCheckRoutes {

  private final SonarAuditCheckFake f;

  private SonarAuditCheckRoutes(SonarAuditCheckFake f) {
    this.f = f;
  }

  static void install(SonarAuditCheckFake f, FakeHttpServer s) {
    SonarAuditCheckRoutes r = new SonarAuditCheckRoutes(f);
    s.on(
        "/api/system/status", q -> ok(Map.of("id", "inst-1", "version", "10.4.1", "status", "UP")));
    s.on("/api/authentication/validate", q -> ok(Map.of("valid", true)));
    s.on("/api/users/current", q -> ok(currentUser()));
    s.on("/api/metrics/search", r::metrics);
    s.on("/api/components/search_projects", r::searchProjects);
    s.on("/api/projects/search", q -> r.realTotal());
    s.on("/api/project_branches/list", r::branches);
    s.on("/api/measures/search", r::measuresSearch);
    s.on("/api/measures/component", r::measuresComponent);
    s.on("/api/measures/search_history", r::history);
    s.on("/api/alm_settings/get_binding", r::binding);
    s.on("/api/project_links/search", r::links);
    SonarAuditCheckSampleRoutes.install(f, s);
  }

  private static Map<String, Object> currentUser() {
    return Map.of(
        "login",
        "alice",
        "name",
        "Alice Audit",
        "permissions",
        Map.of("global", List.of("scan", "provisioning")),
        "groups",
        List.of("team-a", "sonar-users"));
  }

  private Response metrics(Request q) {
    int page = Integer.parseInt(orDefault(q.param("p"), "1"));
    List<Map<String, String>> out = new ArrayList<>();
    for (int i = (page - 1) * 20; i < Math.min(page * 20, f.metrics.size()); i++) {
      out.add(Map.of("key", f.metrics.get(i), "type", "INT"));
    }
    if (!f.metricsPaging) return ok(Map.of("metrics", out));
    return ok(Map.of("paging", paging(page, 20, f.metrics.size()), "metrics", out));
  }

  private Response searchProjects(Request q) {
    int size = "1".equals(q.param("ps")) ? 1 : f.searchPageSize;
    int page = Integer.parseInt(orDefault(q.param("p"), "1"));
    if (q.param("p") != null && page == f.failingSearchPage) return error(500, "boom");
    if (q.param("p") == null && f.failingSearchPage < 0) return error(500, "boom");
    int from = Math.min((page - 1) * size, f.projects.size());
    List<Map<String, Object>> batch =
        f.projects.subList(from, Math.min(from + size, f.projects.size()));
    int total = f.searchTotal != null ? f.searchTotal : f.projects.size();
    return ok(Map.of("paging", paging(page, size, total), "components", batch));
  }

  private Response realTotal() {
    if (f.realTotal == null) return error(403, "Insufficient privileges");
    return ok(Map.of("paging", paging(1, 1, f.realTotal), "components", List.of()));
  }

  private Response branches(Request q) {
    Object b = f.branches.get(q.param("project"));
    if (b == null) return error(404, "Component not found");
    if (b instanceof Integer status) return error(status, "refused");
    return ok(Map.of("branches", b));
  }

  private Response measuresSearch(Request q) {
    List<String> asked = split(q.param("metricKeys"));
    List<Map<String, Object>> out = new ArrayList<>();
    for (String key : split(q.param("projectKeys"))) {
      f.measures.getOrDefault(key, Map.of()).entrySet().stream()
          .filter(e -> asked.contains(e.getKey()))
          .forEach(e -> out.add(measure(key, e.getKey(), e.getValue())));
    }
    return ok(Map.of("measures", out));
  }

  private Response measuresComponent(Request q) {
    String key = q.param("component");
    String branch = q.param("branch");
    String id = branch == null ? key : key + "@" + branch;
    if (branch == null && SAMPLE.equals(key)) id = SAMPLE + "@new-code";
    Map<String, String> m = f.measures.get(id);
    if (m == null) return error(404, "Component not found");
    List<String> asked = split(q.param("metricKeys"));
    List<Map<String, Object>> out = new ArrayList<>();
    m.forEach(
        (metric, v) -> {
          if (asked.contains(metric)) out.add(measure(null, metric, v));
        });
    return ok(Map.of("component", Map.of("key", key, "measures", out)));
  }

  private Response history(Request q) {
    String key = q.param("component");
    String branch = q.param("branch");
    Map<String, List<Object[]>> series = f.history.get(branch == null ? key : key + "@" + branch);
    if (series == null) return error(404, "Component not found");
    List<Map<String, Object>> out = new ArrayList<>();
    int longest = 0;
    for (String metric : split(q.param("metrics"))) {
      List<Map<String, String>> pts = new ArrayList<>();
      for (Object[] p : series.getOrDefault(metric, List.of())) {
        pts.add(Map.of("date", f.ago((Integer) p[0]), "value", (String) p[1]));
      }
      longest = Math.max(longest, pts.size());
      out.add(Map.of("metric", metric, "history", pts));
    }
    return ok(Map.of("paging", paging(1, 1000, longest), "measures", out));
  }

  private Response binding(Request q) {
    Response r = f.bindings.get(q.param("project"));
    return r != null ? r : error(404, "No binding");
  }

  private Response links(Request q) {
    Response r = f.links.get(q.param("projectKey"));
    return r != null ? r : ok(Map.of("links", List.of()));
  }

  static Map<String, Object> measure(String component, String metric, String v) {
    Map<String, Object> m = new LinkedHashMap<>();
    if (component != null) m.put("component", component);
    m.put("metric", metric);
    if (v.startsWith("period:")) {
      m.put("period", Map.of("index", 1, "value", v.substring(7), "bestValue", false));
    } else if (v.startsWith("periods:")) {
      m.put("periods", List.of(Map.of("index", 1, "value", v.substring(8))));
    } else {
      m.put("value", v);
    }
    return m;
  }

  static Map<String, Integer> paging(int page, int size, int total) {
    return Map.of("pageIndex", page, "pageSize", size, "total", total);
  }

  private static List<String> split(String csv) {
    return csv == null || csv.isEmpty() ? List.of() : Arrays.asList(csv.split(","));
  }

  private static String orDefault(String s, String d) {
    return s == null ? d : s;
  }
}
