package ch.ge.edg.sonar.gitlab.audit.testing;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Request;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * A GitLab instance on a loopback socket, for the tests of the commands that read it. It answers
 * the API routes the commands call, from the data the test registers: projects by listing or by
 * path, commits with GitLab's paging, merge requests, notes, files, failures. Nothing is checked
 * beyond the path and the query the test looks at; every other request is a 404.
 *
 * <p>The data is plain JSON: a value registered here is serialised with GitLab's mapper, so a
 * {@code Map}, a Jackson node or a record all work. Builders of the GitLab objects belong to the
 * command under test, next to the test that reads them.
 */
public final class GitlabFake implements AutoCloseable {

  /** What {@link #normalize} writes in place of the instance address. */
  public static final String URL_MARK = "http://gitlab.test";

  /** The page size GitLab uses by default, and the one the commands ask for. */
  public static final int PAGE_SIZE = 100;

  private final FakeHttpServer server = new FakeHttpServer();

  private volatile Function<Request, Response> currentUser =
      r -> Response.json("{\"username\":\"alice\",\"name\":\"Alice A\"}");

  public GitlabFake() {
    server.on("/api/v4/user", r -> currentUser.apply(r));
  }

  /** The HTTP server behind this instance: its URL and every request it received. */
  public FakeHttpServer server() {
    return server;
  }

  /** The base URL of the instance, e.g. {@code http://127.0.0.1:54321}. */
  public String url() {
    return server.url();
  }

  @Override
  public void close() {
    server.close();
  }

  /** Serialises a value as GitLab would write it. */
  public static String toJson(Object value) {
    try {
      return Gitlab.WRITER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  // ----------------------------------------------------------------------
  // Routes: any path, any answer
  // ----------------------------------------------------------------------

  /** Answers the exact path. */
  public GitlabFake route(String path, Function<Request, Response> handler) {
    server.on(path, handler);
    return this;
  }

  /** Answers every path that starts with {@code prefix}, after the exact routes. */
  public GitlabFake prefix(String prefix, Function<Request, Response> handler) {
    server.onPrefix(prefix, handler);
    return this;
  }

  /** Answers the exact path with a 200 whose body is {@code body}, as written. */
  public GitlabFake json(String path, String body) {
    server.json(path, body);
    return this;
  }

  /** Answers the exact path with a 200 whose body is {@code body}, serialised. */
  public GitlabFake value(String path, Object body) {
    return json(path, toJson(body));
  }

  /** Answers the exact path with a failure: a status and its body, as written. */
  public GitlabFake fail(String path, int status, String body) {
    server.on(path, r -> Response.status(status, body));
    return this;
  }

  /** Replaces the answer to {@code /api/v4/user}, the token's own account. */
  public GitlabFake currentUser(Function<Request, Response> handler) {
    currentUser = handler;
    return this;
  }

  /** The instance metadata: its version, and whether it is the enterprise edition. */
  public GitlabFake enterprise(boolean enterprise) {
    return json("/api/v4/metadata", "{\"version\":\"17.0.0\",\"enterprise\":" + enterprise + "}");
  }

  // ----------------------------------------------------------------------
  // Projects, commits, files
  // ----------------------------------------------------------------------

  /** The project list of a group, one page. */
  public GitlabFake group(String path, Object... projects) {
    return serve("/api/v4/groups/" + Gitlab.enc(path) + "/projects", List.of(projects), false);
  }

  /** The project list of the whole instance, one page. */
  public GitlabFake instance(Object... projects) {
    return serve("/api/v4/projects", List.of(projects), false);
  }

  /** Each project answers its own path, as {@code projects/:path} does. */
  public GitlabFake byPath(Object... projects) {
    for (Object p : projects) {
      String body = toJson(p);
      json("/api/v4/projects/" + Gitlab.enc(pathOf(body)), body);
    }
    return this;
  }

  /** The path_with_namespace of a project given as JSON. */
  private static String pathOf(String project) {
    try {
      return Gitlab.READER.readTree(project).path("path_with_namespace").asText();
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The commits of a project, paged, with X-Total on every page. */
  public GitlabFake commits(long id, List<?> commits) {
    return serve("/api/v4/projects/" + id + "/repository/commits", commits, true);
  }

  /** The same, without X-Total: a client that needs the count then pages through them. */
  public GitlabFake commitsWithoutTotal(long id, List<?> commits) {
    return serve("/api/v4/projects/" + id + "/repository/commits", commits, false);
  }

  /**
   * Any list, paged the way GitLab pages: {@code per_page} and {@code page}, X-Next-Page. A smaller
   * {@code per_page} is honoured, a larger one is cut down to {@code maxPerPage}, as GitLab cuts it
   * to its own maximum.
   */
  public GitlabFake pages(String path, List<?> items, int maxPerPage) {
    server.on(path, r -> page(r, items, maxPerPage, false));
    return this;
  }

  /** The file tree of a project's default branch: one entry per name, at the top level. */
  public GitlabFake tree(long id, String... names) {
    List<Object> entries = new ArrayList<>();
    for (String n : names) entries.add(Map.of("name", n));
    return value("/api/v4/projects/" + id + "/repository/tree", entries);
  }

  /** The raw content of one file of a project. */
  public GitlabFake raw(long id, String file, String body) {
    return route(
        "/api/v4/projects/" + id + "/repository/files/" + Gitlab.enc(file) + "/raw",
        r -> Response.status(200, body));
  }

  private GitlabFake serve(String path, List<?> items, boolean total) {
    server.on(path, r -> page(r, items, PAGE_SIZE, total));
    return this;
  }

  /** The page of {@code items} the request asks for; X-Next-Page while more follows. */
  static Response page(Request r, List<?> items, int maxPerPage, boolean total) {
    int size =
        r.param("per_page") == null
            ? maxPerPage
            : Math.min(maxPerPage, Integer.parseInt(r.param("per_page")));
    int number = r.param("page") == null ? 1 : Integer.parseInt(r.param("page"));
    int from = Math.min(items.size(), (number - 1) * size);
    int to = Math.min(items.size(), from + size);
    Response answer = Response.json(toJson(items.subList(from, to)));
    if (to < items.size()) answer = answer.withHeader("X-Next-Page", String.valueOf(number + 1));
    return total ? answer.withHeader("X-Total", String.valueOf(items.size())) : answer;
  }

  /** Output with the instance address, and the temp directory when given, replaced by marks. */
  public String normalize(String text, @Nullable Path tmp) {
    String s = text.replace(server.url(), URL_MARK);
    if (tmp != null) s = Native.marked(s, tmp, "<TMP>");
    return s;
  }
}
