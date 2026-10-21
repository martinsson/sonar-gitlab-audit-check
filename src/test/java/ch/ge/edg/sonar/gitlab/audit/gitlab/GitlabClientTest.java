package ch.ge.edg.sonar.gitlab.audit.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.rest.Capture;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Request;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitlabClientTest {

  private final FakeHttpServer server = new FakeHttpServer();

  @TempDir Path tmp;

  @AfterEach
  void stop() {
    server.close();
  }

  private Gitlab client(@Nullable String token) throws Exception {
    return GitlabStreams.quiet(new Gitlab.Connection(server.url() + "//", token, 5, false, null));
  }

  @Test
  void getHitsTheV4ApiWithTheTokenAndJsonHeaders() throws Exception {
    server.json("/api/v4/user", "{\"username\":\"alice\"}");
    Gitlab gitlab = client("glpat-secret");
    assertThat(gitlab.base).isEqualTo(server.url());

    RestResponse r = gitlab.get("///user", Map.of());

    assertThat(r.is(200)).isTrue();
    assertThat(r.tree().path("username").asText()).isEqualTo("alice");
    Request req = server.requests("/api/v4/user").getFirst();
    assertThat(req.method()).isEqualTo("GET");
    assertThat(req.target()).isEqualTo("/api/v4/user");
    assertThat(req.header("PRIVATE-TOKEN")).isEqualTo("glpat-secret");
    assertThat(req.header("Accept")).isEqualTo("application/json");
    assertThat(req.header("Content-Type")).isEqualTo("application/json");
    assertThat(gitlab.calls()).isEqualTo(1);
  }

  @Test
  void noTokenHeaderWhenTheTokenIsBlankOrMissing() throws Exception {
    server.json("/api/v4/user", "{}");
    client("  ").get("user", Map.of());
    client(null).get("user", Map.of());
    assertThat(server.requests("/api/v4/user"))
        .hasSize(2)
        .allSatisfy(r -> assertThat(r.header("PRIVATE-TOKEN")).isNull());
  }

  @Test
  void queryParametersAreEncodedInOrderAndNullsDropped() throws Exception {
    server.json("/api/v4/projects", "[]");
    Map<String, String> params = new LinkedHashMap<>();
    params.put("search", "alpha service/é");
    params.put("skipped", null);
    params.put("page", "2");
    client("t").get("projects", params);
    assertThat(server.requests("/api/v4/projects").getFirst().target())
        .isEqualTo("/api/v4/projects?search=alpha+service%2F%C3%A9&page=2");
  }

  @Test
  void onlyNullParametersLeaveAnEmptyQuery() throws Exception {
    server.json("/api/v4/projects", "[]");
    Map<String, String> params = new LinkedHashMap<>();
    params.put("skipped", null);
    client("t").get("projects", params);
    assertThat(server.requests("/api/v4/projects").getFirst().target())
        .isEqualTo("/api/v4/projects");
  }

  @Test
  void responseHeadersAreLowerCasedWithTheirFirstValue() throws Exception {
    server.on("/api/v4/x", r -> Response.json("[]").withHeader("X-Next-Page", "3"));
    RestResponse r = client("t").get("x", Map.of());
    assertThat(r.header("x-next-page")).isEqualTo("3");
    assertThat(r.intHeader("X-Next-Page")).hasValue(3);
  }

  @Test
  void existsSendsAHeadAndIsTrueOnlyOn200() throws Exception {
    server.on("/api/v4/projects/1/repository/files/README.md", r -> Response.status(200, ""));
    server.on("/api/v4/projects/1/repository/files/other", r -> Response.status(204, ""));
    Gitlab gitlab = client("t");
    assertThat(gitlab.exists("projects/1/repository/files/README.md", Map.of("ref", "main")))
        .isTrue();
    assertThat(gitlab.exists("/projects/1/repository/files/other", Map.of())).isFalse();
    assertThat(gitlab.exists("projects/1/repository/files/missing", Map.of())).isFalse();
    Request head = server.requests().getFirst();
    assertThat(head.method()).isEqualTo("HEAD");
    assertThat(head.target()).isEqualTo("/api/v4/projects/1/repository/files/README.md?ref=main");
  }

  @Test
  void graphqlPostsToTheGraphqlEndpoint() throws Exception {
    server.json("/api/graphql", "{\"data\":{\"ok\":true}}");
    RestResponse r = client("t").graphql("{ currentUser { username } }");
    assertThat(r.tree().path("data").path("ok").asBoolean()).isTrue();
    Request req = server.requests("/api/graphql").getFirst();
    assertThat(req.method()).isEqualTo("POST");
    assertThat(req.header("Content-Type")).isEqualTo("application/json");
    assertThat(req.header("content-length")).isEqualTo("40");
  }

  @Test
  void a429IsRetriedAfterRetryAfterSeconds() throws Exception {
    AtomicInteger n = new AtomicInteger();
    server.on(
        "/api/v4/user",
        r ->
            n.incrementAndGet() == 1
                ? Response.status(429, "{}").withHeader("Retry-After", " 0 ")
                : Response.json("{\"id\":1}"));
    Gitlab gitlab = client("t");
    RestResponse r = gitlab.get("user", Map.of());
    assertThat(r.is(200)).isTrue();
    assertThat(gitlab.calls()).isEqualTo(2);
    assertThat(gitlab.throttled()).isEqualTo(1);
    assertThat(gitlab.forbidden()).isZero();
  }

  @Test
  void retryAfterIsWaitedInSeconds() throws Exception {
    AtomicInteger n = new AtomicInteger();
    server.on(
        "/api/v4/user",
        r ->
            n.incrementAndGet() == 1
                ? Response.status(429, "{}").withHeader("Retry-After", "1")
                : Response.json("{}"));
    List<Duration> pauses = new ArrayList<>();
    Gitlab gitlab =
        new Gitlab(
            new Gitlab.Connection(server.url(), "t", 5, false, null),
            new GitlabStreams(),
            pauses::add);
    assertThat(gitlab.get("user", Map.of()).ok()).isTrue();
    assertThat(pauses).containsExactly(Duration.ofSeconds(1));
  }

  @Test
  void a429IsRetriedThreeTimesThenReturned() throws Exception {
    server.on("/api/v4/user", r -> Response.status(429, "slow").withHeader("Retry-After", "0"));
    Gitlab gitlab = client("t");
    RestResponse r = gitlab.get("user", Map.of());
    assertThat(r.is(429)).isTrue();
    assertThat(r.body()).isEqualTo("slow");
    assertThat(gitlab.calls()).isEqualTo(4);
    assertThat(gitlab.throttled()).isEqualTo(3);
    assertThat(server.requests("/api/v4/user")).hasSize(4);
  }

  @Test
  void a403IsCounted() throws Exception {
    server.on("/api/v4/user", r -> Response.status(403, "{\"message\":\"403 Forbidden\"}"));
    Gitlab gitlab = client("t");
    RestResponse r = gitlab.get("user", Map.of());
    gitlab.get("user", Map.of());
    assertThat(r.is(403)).isTrue();
    assertThat(r.message()).isEqualTo("403 Forbidden");
    assertThat(gitlab.forbidden()).isEqualTo(2);
    assertThat(gitlab.throttled()).isZero();
  }

  @Test
  void anUnreachableInstanceGivesStatusZeroWithTheExceptionName() throws Exception {
    int port;
    try (ServerSocket s = new ServerSocket(0)) {
      port = s.getLocalPort();
    }
    Gitlab gitlab =
        GitlabStreams.quiet(new Gitlab.Connection("http://127.0.0.1:" + port, "t", 5, false, null));
    RestResponse r = gitlab.get("user", Map.of());
    assertThat(r.unreachable()).isTrue();
    assertThat(r.why()).startsWith("ConnectException");
    assertThat(r.header("x-next-page")).isEmpty();
    assertThat(gitlab.calls()).isEqualTo(1);
  }

  @Test
  void anExceptionWithAMessageKeepsBoth() throws Exception {
    Gitlab gitlab =
        GitlabStreams.quiet(new Gitlab.Connection("http://bad host", "t", 5, false, null));
    RestResponse r = gitlab.get("user", Map.of());
    assertThat(r.unreachable()).isTrue();
    assertThat(r.why()).startsWith("IllegalArgumentException: ");
  }

  @Test
  void anInterruptedCallGivesStatusZeroAndKeepsTheFlag() throws Exception {
    server.json("/api/v4/user", "{}");
    Gitlab gitlab = client("t");
    Thread.currentThread().interrupt();
    RestResponse r = gitlab.get("user", Map.of());
    boolean stillInterrupted = Thread.interrupted();
    assertThat(r.unreachable()).isTrue();
    assertThat(r.why()).isEqualTo("interrompu");
    assertThat(stillInterrupted).isTrue();
  }

  @Test
  void insecureModeStillTalksPlainHttp() throws Exception {
    String key = "jdk.internal.httpclient.disableHostnameVerification";
    String before = System.getProperty(key);
    try {
      server.json("/api/v4/user", "{}");
      Gitlab gitlab = GitlabStreams.quiet(new Gitlab.Connection(server.url(), "t", 5, true, null));
      assertThat(gitlab.get("user", Map.of()).ok()).isTrue();
      assertThat(System.getProperty(key)).isEqualTo("true");
    } finally {
      if (before == null) System.clearProperty(key);
      else System.setProperty(key, before);
    }
  }

  @Test
  void dumpsEveryResponseBodyUnderANameStableAcrossRuns() throws Exception {
    server.json("/api/v4/projects/1/repository/commits", "[{\"id\":\"abc\"}]");
    server.on("/api/v4/user", r -> Response.status(401, "{\"message\":\"401\"}"));
    Path dir = tmp.resolve("nested/dump");
    Gitlab gitlab = GitlabStreams.quiet(new Gitlab.Connection(server.url(), "t", 5, false, dir));
    assertThat(dir).isDirectory();

    gitlab.get("projects/1/repository/commits", Map.of("ref_name", "main"));
    gitlab.get("user", Map.of());

    String commits = Capture.name("api/v4/projects/1/repository/commits?ref_name=main");
    String user = Capture.name("api/v4/user");
    assertThat(dir.resolve(commits)).hasContent("[{\"id\":\"abc\"}]");
    assertThat(dir.resolve(user)).hasContent("{\"message\":\"401\"}");
    assertThat(dir.resolve(user + ".status")).hasContent("401");
    String nl = System.lineSeparator();
    assertThat(dir.resolve("index.txt"))
        .hasContent(
            commits
                + "  api/v4/projects/1/repository/commits?ref_name=main"
                + nl
                + user
                + "  api/v4/user");
  }

  @Test
  void onlyNullParametersLeaveNoQuestionMarkInTheDumpIndex() throws Exception {
    server.json("/api/v4/projects", "[]");
    Map<String, String> params = new LinkedHashMap<>();
    params.put("skipped", null);
    GitlabStreams.quiet(new Gitlab.Connection(server.url(), "t", 5, false, tmp))
        .get("projects", params);
    assertThat(tmp.resolve("index.txt"))
        .content()
        .endsWith("  api/v4/projects" + System.lineSeparator());
  }

  @Test
  void aLongRequestKeepsAShortDumpName() throws Exception {
    String path = "projects/" + "a".repeat(200);
    server.json("/api/v4/" + path, "{}");
    Gitlab gitlab = GitlabStreams.quiet(new Gitlab.Connection(server.url(), "t", 5, false, tmp));
    gitlab.get(path, Map.of());
    String name = Capture.name("api/v4/" + path);
    assertThat(name).hasSize(60 + 1 + 8 + 5);
    assertThat(tmp.resolve(name)).hasContent("{}");
  }

  @Test
  void aDumpThatCannotBeWrittenIsReportedNotFatal() throws Exception {
    server.json("/api/v4/user", "{\"id\":1}");
    Files.createDirectories(tmp.resolve(Capture.name("api/v4/user")));
    GitlabStreams printed = new GitlabStreams();
    Gitlab gitlab = printed.client(new Gitlab.Connection(server.url(), "t", 5, false, tmp));
    assertThat(gitlab.get("user", Map.of()).ok()).isTrue();
    assertThat(printed.err()).startsWith("  (capture non écrite : ").endsWith(")\n");
    assertThat(printed.out()).isEmpty();
  }

  @Test
  void a429IsDumpedOnlyOnceTheAnswerIsFinal() throws Exception {
    AtomicInteger n = new AtomicInteger();
    server.on(
        "/api/v4/user",
        r ->
            n.incrementAndGet() == 1
                ? Response.status(429, "{}").withHeader("Retry-After", "0")
                : Response.json("{\"id\":1}"));
    Gitlab gitlab = GitlabStreams.quiet(new Gitlab.Connection(server.url(), "t", 5, false, tmp));
    gitlab.get("user", Map.of());
    String name = Capture.name("api/v4/user");
    assertThat(tmp.resolve(name)).hasContent("{\"id\":1}");
    assertThat(tmp.resolve("index.txt")).hasContent(name + "  api/v4/user");
  }
}
