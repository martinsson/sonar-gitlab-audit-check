package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Request;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * A synthetic GitLab for GitlabActivityAudit: JSON builders for projects and commits, and the
 * routes the audit reads. Every name and date is invented. Dates in 2099 sit inside any activity
 * window, dates in 2000 outside the default one, so nothing depends on today's date.
 */
final class GitlabAuditFake implements AutoCloseable {

  static final String FUTURE = "2099-01-01T10:00:00Z";
  static final String PAST = "2000-01-01T10:00:00Z";
  static final String URL_MARK = "http://gitlab.test";

  final FakeHttpServer server = new FakeHttpServer();

  private volatile Function<Request, Response> user =
      r -> Response.json("{\"username\":\"alice\",\"name\":\"Alice A\"}");

  GitlabAuditFake() {
    server.on("/api/v4/user", r -> user.apply(r));
  }

  /** Replaces the answer to api/v4/user. */
  GitlabAuditFake user(Function<Request, Response> handler) {
    user = handler;
    return this;
  }

  @Override
  public void close() {
    server.close();
  }

  GitlabAuditFake enterprise(boolean enterprise) {
    server.json("/api/v4/metadata", "{\"version\":\"17.0.0\",\"enterprise\":" + enterprise + "}");
    return this;
  }

  /** The one-page project list of a group. */
  GitlabAuditFake group(String path, ObjectNode... projects) {
    server.json("/api/v4/groups/" + Gitlab.enc(path) + "/projects", array(projects));
    return this;
  }

  /** The one-page project list of the whole instance. */
  GitlabAuditFake instance(ObjectNode... projects) {
    server.json("/api/v4/projects", array(projects));
    return this;
  }

  /** Each project readable by its path, as --projects reads them. */
  GitlabAuditFake byPath(ObjectNode... projects) {
    for (ObjectNode p : projects) {
      server.json(
          "/api/v4/projects/" + Gitlab.enc(p.path("path_with_namespace").asText()), p.toString());
    }
    return this;
  }

  /** The commits of a project, 100 per page, with X-Total on the per_page=1 count. */
  GitlabAuditFake commits(long id, List<ObjectNode> commits) {
    server.on("/api/v4/projects/" + id + "/repository/commits", commitPages(commits, true));
    return this;
  }

  GitlabAuditFake commits(long id, ObjectNode... commits) {
    return commits(id, Arrays.asList(commits));
  }

  /** Same, without X-Total on the count: the audit then asks for a full page. */
  GitlabAuditFake commitsWithoutTotal(long id, ObjectNode... commits) {
    server.on(
        "/api/v4/projects/" + id + "/repository/commits",
        commitPages(Arrays.asList(commits), false));
    return this;
  }

  GitlabAuditFake route(String path, Function<Request, Response> handler) {
    server.on(path, handler);
    return this;
  }

  GitlabAuditFake prefix(String prefix, Function<Request, Response> handler) {
    server.onPrefix(prefix, handler);
    return this;
  }

  GitlabAuditFake json(String path, String body) {
    server.json(path, body);
    return this;
  }

  GitlabAuditFake raw(long id, String file, String body) {
    server.on(
        "/api/v4/projects/" + id + "/repository/files/" + Gitlab.enc(file) + "/raw",
        r -> Response.status(200, body));
    return this;
  }

  GitlabAuditFake tree(long id, String... names) {
    List<ObjectNode> entries = new ArrayList<>();
    for (String n : names) entries.add(Gitlab.MAPPER.createObjectNode().put("name", n));
    server.json(
        "/api/v4/projects/" + id + "/repository/tree", array(entries.toArray(ObjectNode[]::new)));
    return this;
  }

  private static Function<Request, Response> commitPages(List<ObjectNode> all, boolean total) {
    return r -> {
      if ("1".equals(r.param("per_page"))) {
        Response first = Response.json(array(all.stream().limit(1).toArray(ObjectNode[]::new)));
        return total ? first.withHeader("X-Total", String.valueOf(all.size())) : first;
      }
      int page = r.param("page") == null ? 1 : Integer.parseInt(r.param("page"));
      int from = Math.min(all.size(), (page - 1) * 100);
      int to = Math.min(all.size(), from + 100);
      return Response.json(array(all.subList(from, to).toArray(ObjectNode[]::new)));
    };
  }

  /** Runs the audit against this server, colours off. */
  Cli.Result run(String... args) {
    List<String> all =
        new ArrayList<>(
            List.of("--url", server.url(), "--token", "glpat-test", "--color", "never"));
    all.addAll(Arrays.asList(args));
    return Cli.run(new GitlabActivityAudit(), all.toArray(String[]::new));
  }

  /** Output with the random port and the temp directory replaced by fixed marks. */
  String normalize(String text, Path tmp) {
    String s = text.replace(server.url(), URL_MARK);
    if (tmp != null) s = s.replace(tmp.toAbsolutePath().toString(), "<TMP>");
    return s;
  }

  // ----------------------------------------------------------------------
  // JSON builders
  // ----------------------------------------------------------------------

  static String array(ObjectNode... nodes) {
    ArrayNode a = Gitlab.MAPPER.createArrayNode();
    for (ObjectNode n : nodes) a.add(n);
    return a.toString();
  }

  /** An active, unarchived project in the namespace of its path, 50 commits in its history. */
  static ObjectNode project(long id, String path) {
    ObjectNode p = Gitlab.MAPPER.createObjectNode();
    p.put("id", id);
    p.put("path_with_namespace", path);
    p.put("name", path.substring(path.lastIndexOf('/') + 1).toUpperCase(java.util.Locale.ROOT));
    p.putObject("namespace")
        .put("full_path", path.substring(0, Math.max(0, path.lastIndexOf('/'))));
    p.put("default_branch", "main");
    p.put("visibility", "private");
    p.put("created_at", "2020-01-01T00:00:00Z");
    p.put("last_activity_at", FUTURE);
    p.put("archived", false);
    p.putObject("statistics").put("commit_count", 50).put("repository_size", 1000);
    return p;
  }

  /** A single-parent commit on {@code date}. */
  static ObjectNode commit(String sha, String author, String date) {
    ObjectNode c = Gitlab.MAPPER.createObjectNode();
    c.put("id", sha);
    c.put("author_email", author + "@example.org");
    c.put("author_name", author);
    c.put("title", "change " + sha);
    c.put("committed_date", date);
    c.putArray("parent_ids").add("p-" + sha);
    return c;
  }

  static ObjectNode merge(ObjectNode commit) {
    commit.putArray("parent_ids").add("p1").add("p2");
    return commit;
  }

  static ObjectNode stats(ObjectNode commit, int additions, int deletions) {
    commit.putObject("stats").put("additions", additions).put("deletions", deletions);
    return commit;
  }

  /** {@code n} commits by {@code author}, one per day from 2099-01-01. */
  static List<ObjectNode> daily(String prefix, String author, int n) {
    List<ObjectNode> out = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      out.add(
          commit(
              prefix + i, author, java.time.OffsetDateTime.parse(FUTURE).minusDays(i).toString()));
    }
    return out;
  }
}
