package ch.ge.edg.sonar.gitlab.audit.cli;

import ch.ge.edg.sonar.gitlab.audit.sonar.Sonar;
import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Request;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;

/**
 * A synthetic SonarQube park served by {@link FakeHttpServer}, for driving {@link SonarAuditCheck}
 * end to end. Every field is public test data a test may change before {@link #start()}.
 *
 * <p>Dates are relative to the moment the fake is built ({@link #ago}); {@link #normalize} turns
 * them back into stable tokens so whole outputs can be compared.
 */
final class SonarAuditCheckFake implements AutoCloseable {

  static final String SAMPLE = "alpha-service";

  private static final DateTimeFormatter SONAR_DATE =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxx");

  private final OffsetDateTime reference =
      OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
  // Filled from the server threads too (history dates): must be concurrent.
  private final Map<String, String> dateTokens = new ConcurrentSkipListMap<>();
  private final FakeHttpServer server = new FakeHttpServer();
  private final Map<String, Function<Request, Response>> overrides = new LinkedHashMap<>();

  /** Components of search_projects, in order. */
  final List<Map<String, Object>> projects = new ArrayList<>();

  /** search_projects page size the fake applies (the tool always asks for 500). */
  int searchPageSize = 2;

  /** search_projects page (p=) answered with a 500; 0 = none; -1 = the calls without p=. */
  int failingSearchPage;

  /** paging.total search_projects claims, when not the real count. */
  Integer searchTotal;

  /** Whether api/metrics/search sends its paging block. */
  boolean metricsPaging = true;

  /** Total of api/projects/search, or null for a 403. */
  Integer realTotal = 6;

  /** Metric keys api/metrics/search knows, served 20 per page. */
  final List<String> metrics = new ArrayList<>();

  /** project -> branches (list of maps), or an Integer HTTP status. */
  final Map<String, Object> branches = new LinkedHashMap<>();

  /** project or "project@branch" -> metric -> value; "period:" and "periods:" prefixes. */
  final Map<String, Map<String, String>> measures = new LinkedHashMap<>();

  /** project or "project@branch" -> metric -> [daysAgo, value] pairs. */
  final Map<String, Map<String, List<Object[]>>> history = new LinkedHashMap<>();

  /** project -> response of alm_settings/get_binding; missing = 404. */
  final Map<String, Response> bindings = new LinkedHashMap<>();

  /** project -> response of project_links/search; missing = no link. */
  final Map<String, Response> links = new LinkedHashMap<>();

  SonarAuditCheckFake() {
    SonarAuditCheckPark.populate(this);
  }

  /** Replaces the default handler of one path. */
  SonarAuditCheckFake override(String path, Function<Request, Response> handler) {
    overrides.put(path, handler);
    return this;
  }

  FakeHttpServer server() {
    return server;
  }

  FakeHttpServer start() {
    overrides.forEach(server::on);
    SonarAuditCheckRoutes.install(this, server);
    return server;
  }

  @Override
  public void close() {
    server.close();
  }

  /** A Sonar-formatted date {@code days} days (and one hour) before the reference instant. */
  String ago(int days) {
    OffsetDateTime d = reference.minusDays(days).minusHours(1);
    String full = d.format(SONAR_DATE);
    dateTokens.put(full, "<J-" + days + ">");
    dateTokens.put(full.substring(0, 10), "<D-" + days + ">");
    return full;
  }

  /**
   * Replaces the server URL by {@code <URL>} and every date handed out by {@link #ago} by its
   * token, full dates first.
   */
  String normalize(String text) {
    String out = text.replace(server.url(), "<URL>");
    for (Map.Entry<String, String> e : dateTokens.entrySet()) {
      if (e.getKey().length() > 10) out = out.replace(e.getKey(), e.getValue());
    }
    for (Map.Entry<String, String> e : dateTokens.entrySet()) {
      if (e.getKey().length() == 10) out = out.replace(e.getKey(), e.getValue());
    }
    return out;
  }

  /** Adds a search_projects component; a null date means never analysed. */
  void project(String key, String name, String analysisDate) {
    Map<String, Object> c = new LinkedHashMap<>();
    c.put("key", key);
    c.put("name", name);
    c.put("qualifier", "TRK");
    if (analysisDate != null) c.put("analysisDate", analysisDate);
    projects.add(c);
  }

  static Map<String, Object> branch(String name, boolean main, String analysisDate) {
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("name", name);
    b.put("isMain", main);
    b.put("type", "BRANCH");
    if (analysisDate != null) b.put("analysisDate", analysisDate);
    return b;
  }

  static List<Object[]> points(Object... daysAndValues) {
    List<Object[]> out = new ArrayList<>();
    for (int i = 0; i + 1 < daysAndValues.length; i += 2) {
      out.add(new Object[] {daysAndValues[i], daysAndValues[i + 1]});
    }
    return out;
  }

  static String json(Object value) {
    try {
      return Sonar.MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  static Response ok(Object value) {
    return Response.json(json(value));
  }

  static Response error(int status, String msg) {
    return Response.status(status, json(Map.of("errors", List.of(Map.of("msg", msg)))));
  }

  /**
   * Runs the command against this fake with --url, --token, --color never and an empty --exclusions
   * file (so a stray exclusions.txt in the working directory cannot leak in).
   */
  Cli.Result run(Path tmp, String... extra) {
    List<String> args = new ArrayList<>();
    args.addAll(List.of("--url", server.url(), "--token", "tok-test"));
    if (!Arrays.asList(extra).contains("--color")) args.addAll(List.of("--color", "never"));
    if (!Arrays.asList(extra).contains("--exclusions")) {
      args.addAll(List.of("--exclusions", emptyExclusions(tmp).toString()));
    }
    args.addAll(Arrays.asList(extra));
    return Cli.run(new SonarAuditCheck(), args.toArray(String[]::new));
  }

  static Path emptyExclusions(Path tmp) {
    try {
      Path f = tmp.resolve("no-exclusions.txt");
      if (!Files.exists(f)) Files.writeString(f, "");
      return f;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
