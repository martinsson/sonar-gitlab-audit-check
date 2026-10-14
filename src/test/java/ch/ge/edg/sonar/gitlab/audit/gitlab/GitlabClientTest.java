package ch.ge.edg.sonar.gitlab.audit.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Request;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
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
    return new Gitlab(server.url() + "//", token, 5, false, null);
  }

  private Gitlab dumpingTo(Path dir) {
    try {
      return new Gitlab(server.url(), "t", 5, false, dir);
    } catch (IOException | GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void getHitsTheV4ApiWithTheTokenAndJsonHeaders() throws Exception {
    server.json("/api/v4/user", "{\"username\":\"alice\"}");
    Gitlab gl = client("glpat-secret");
    assertThat(gl.base).isEqualTo(server.url());

    Gitlab.Response r = gl.get("///user", Map.of());

    assertThat(r.status()).isEqualTo(200);
    assertThat(r.json().path("username").asText()).isEqualTo("alice");
    Request req = server.requests("/api/v4/user").getFirst();
    assertThat(req.method()).isEqualTo("GET");
    assertThat(req.target()).isEqualTo("/api/v4/user");
    assertThat(req.header("PRIVATE-TOKEN")).isEqualTo("glpat-secret");
    assertThat(req.header("Accept")).isEqualTo("application/json");
    assertThat(req.header("Content-Type")).isEqualTo("application/json");
    assertThat(gl.calls()).isEqualTo(1);
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
    Gitlab.Response r = client("t").get("x", Map.of());
    assertThat(r.headers()).containsEntry("x-next-page", "3");
    assertThat(r.intHeader("X-Next-Page")).isEqualTo(3);
  }

  @Test
  void existsSendsAHeadAndIsTrueOnlyOn200() throws Exception {
    server.on("/api/v4/projects/1/repository/files/README.md", r -> Response.status(200, ""));
    server.on("/api/v4/projects/1/repository/files/other", r -> Response.status(204, ""));
    Gitlab gl = client("t");
    assertThat(gl.exists("projects/1/repository/files/README.md", Map.of("ref", "main"))).isTrue();
    assertThat(gl.exists("/projects/1/repository/files/other", Map.of())).isFalse();
    assertThat(gl.exists("projects/1/repository/files/missing", Map.of())).isFalse();
    Request head = server.requests().getFirst();
    assertThat(head.method()).isEqualTo("HEAD");
    assertThat(head.target()).isEqualTo("/api/v4/projects/1/repository/files/README.md?ref=main");
  }

  @Test
  void graphqlPostsToTheGraphqlEndpoint() throws Exception {
    server.json("/api/graphql", "{\"data\":{\"ok\":true}}");
    Gitlab.Response r = client("t").graphql("{ currentUser { username } }");
    assertThat(r.json().path("data").path("ok").asBoolean()).isTrue();
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
    Gitlab gl = client("t");
    Gitlab.Response r = gl.get("user", Map.of());
    assertThat(r.status()).isEqualTo(200);
    assertThat(gl.calls()).isEqualTo(2);
    assertThat(gl.throttled()).isEqualTo(1);
    assertThat(gl.forbidden()).isZero();
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
    Gitlab gl = client("t");
    long start = System.nanoTime();
    assertThat(gl.get("user", Map.of()).ok()).isTrue();
    assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(950);
  }

  @Test
  void a429IsRetriedThreeTimesThenReturned() throws Exception {
    server.on("/api/v4/user", r -> Response.status(429, "slow").withHeader("Retry-After", "0"));
    Gitlab gl = client("t");
    Gitlab.Response r = gl.get("user", Map.of());
    assertThat(r.status()).isEqualTo(429);
    assertThat(r.body()).isEqualTo("slow");
    assertThat(gl.calls()).isEqualTo(4);
    assertThat(gl.throttled()).isEqualTo(3);
    assertThat(server.requests("/api/v4/user")).hasSize(4);
  }

  @Test
  void a403IsCounted() throws Exception {
    server.on("/api/v4/user", r -> Response.status(403, "{\"message\":\"403 Forbidden\"}"));
    Gitlab gl = client("t");
    Gitlab.Response r = gl.get("user", Map.of());
    gl.get("user", Map.of());
    assertThat(r.status()).isEqualTo(403);
    assertThat(r.errorMessage()).isEqualTo("403 Forbidden");
    assertThat(gl.forbidden()).isEqualTo(2);
    assertThat(gl.throttled()).isZero();
  }

  @Test
  void anUnreachableInstanceGivesStatusZeroWithTheExceptionName() throws Exception {
    int port;
    try (ServerSocket s = new ServerSocket(0)) {
      port = s.getLocalPort();
    }
    Gitlab gl = new Gitlab("http://127.0.0.1:" + port, "t", 5, false, null);
    Gitlab.Response r = gl.get("user", Map.of());
    assertThat(r.unreachable()).isTrue();
    assertThat(r.body()).startsWith("ConnectException");
    assertThat(r.headers()).isEmpty();
    assertThat(gl.calls()).isEqualTo(1);
  }

  @Test
  void anExceptionWithAMessageKeepsBoth() throws Exception {
    Gitlab gl = new Gitlab("http://bad host", "t", 5, false, null);
    Gitlab.Response r = gl.get("user", Map.of());
    assertThat(r.status()).isZero();
    assertThat(r.body()).startsWith("IllegalArgumentException: ");
  }

  @Test
  void anInterruptedCallGivesStatusZeroAndKeepsTheFlag() throws Exception {
    server.json("/api/v4/user", "{}");
    Gitlab gl = client("t");
    Thread.currentThread().interrupt();
    Gitlab.Response r = gl.get("user", Map.of());
    boolean stillInterrupted = Thread.interrupted();
    assertThat(r.status()).isZero();
    assertThat(r.body()).isEqualTo("interrompu");
    assertThat(stillInterrupted).isTrue();
  }

  @Test
  void insecureModeStillTalksPlainHttp() throws Exception {
    String key = "jdk.internal.httpclient.disableHostnameVerification";
    String before = System.getProperty(key);
    try {
      server.json("/api/v4/user", "{}");
      Gitlab gl = new Gitlab(server.url(), "t", 5, true, null);
      assertThat(gl.get("user", Map.of()).ok()).isTrue();
      assertThat(System.getProperty(key)).isEqualTo("true");
    } finally {
      if (before == null) System.clearProperty(key);
      else System.setProperty(key, before);
    }
  }

  @Test
  void dumpsEveryResponseBodyNumberedByCall() throws Exception {
    server.json("/api/v4/projects/1/repository/commits", "[{\"id\":\"abc\"}]");
    server.on("/api/v4/user", r -> Response.status(401, "{\"message\":\"401\"}"));
    Path dir = tmp.resolve("nested/dump");
    Gitlab gl = new Gitlab(server.url(), "t", 5, false, dir);
    assertThat(dir).isDirectory();

    gl.get("projects/1/repository/commits", Map.of("ref_name", "main"));
    gl.get("user", Map.of());

    try (var files = Files.list(dir)) {
      assertThat(files.map(p -> p.getFileName().toString()).sorted().toList())
          .containsExactly(
              "0001-api_v4_projects_1_repository_commits_ref_name_main.json",
              "0002-api_v4_user.json");
    }
    assertThat(dir.resolve("0002-api_v4_user.json")).hasContent("{\"message\":\"401\"}");
  }

  @Test
  void dumpFileNamesAreCutAt120Characters() throws Exception {
    String path = "projects/" + "a".repeat(200);
    server.json("/api/v4/" + path, "{}");
    Gitlab gl = new Gitlab(server.url(), "t", 5, false, tmp);
    gl.get(path, Map.of());
    try (var files = Files.list(tmp)) {
      List<String> names = files.map(p -> p.getFileName().toString()).toList();
      assertThat(names)
          .containsExactly(("0001-api_v4_projects_" + "a".repeat(200)).substring(0, 120));
    }
  }

  @Test
  void aDumpThatCannotBeWrittenIsReportedNotFatal() throws Exception {
    server.json("/api/v4/user", "{\"id\":1}");
    Files.createDirectories(tmp.resolve("0001-api_v4_user.json"));
    GitlabStreams.Printed printed =
        GitlabStreams.capture(
            () -> {
              // built inside the capture: it prints on the streams in place when it is built
              Gitlab gl = dumpingTo(tmp);
              assertThat(gl.get("user", Map.of()).ok()).isTrue();
            });
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
    Gitlab gl = new Gitlab(server.url(), "t", 5, false, tmp);
    gl.get("user", Map.of());
    try (var files = Files.list(tmp)) {
      assertThat(files.map(p -> p.getFileName().toString()).toList())
          .containsExactly("0002-api_v4_user.json");
    }
  }
}
