package ch.ge.edg.sonar.gitlab.audit.sonar;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Request;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import java.net.ServerSocket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The transport: URL building, headers, organization, retries, unreachable instance. */
class SonarClientTest {

  private final FakeHttpServer server = new FakeHttpServer();

  @AfterEach
  void stop() {
    server.close();
  }

  private Sonar client(@Nullable String organization) throws Exception {
    return new Sonar(new SonarConnection(server.url() + "//", "tok-1", organization, 5, false));
  }

  @Test
  void getSendsBearerTokenAcceptAndQueryOnACleanUrl() throws Exception {
    server.json("/api/system/status", "{\"status\":\"UP\"}");
    Sonar sq = client(null);
    Sonar.Response r = sq.get("//api/system/status", Map.of("a", "x y"));

    assertThat(r).isEqualTo(new Sonar.Response(200, "{\"status\":\"UP\"}"));
    Request req = server.requests("/api/system/status").getFirst();
    assertThat(req.method()).isEqualTo("GET");
    assertThat(req.target()).isEqualTo("/api/system/status?a=x+y");
    assertThat(req.header("Authorization")).isEqualTo("Bearer tok-1");
    assertThat(req.header("Accept")).isEqualTo("application/json");
    assertThat(sq.base).isEqualTo(server.url());
    assertThat(sq.calls()).isEqualTo(1);
    assertThat(sq.replaying()).isFalse();
  }

  @Test
  void baseIsEmptyWhenUrlIsNull() throws Exception {
    assertThat(new Sonar(new SonarConnection(null, "t", null, 1, false)).base).isEmpty();
  }

  @Test
  void nonOkStatusIsReturnedWithItsBody() throws Exception {
    server.on("/api/x", r -> Response.status(403, "{\"errors\":[{\"msg\":\"nope\"}]}"));
    Sonar.Response r = client(null).get("api/x", Map.of());
    assertThat(r.status()).isEqualTo(403);
    assertThat(r.errorMessage()).isEqualTo("nope");
    assertThat(server.requests("/api/x").getFirst().target()).isEqualTo("/api/x");
  }

  @Test
  void organizationIsAddedOnlyToEndpointsThatNeedIt() throws Exception {
    server.onPrefix("/api/", r -> Response.json("{}"));
    Sonar sq = client("org-a");
    for (String p :
        List.of(
            "api/components/search_projects",
            "api/qualityprofiles/search",
            "api/projects/search",
            "api/issues/search",
            "api/qualitygates/get_by_project",
            "api/measures/search")) {
      sq.get(p, Map.of());
    }
    List<String> orgs = server.requests().stream().map(r -> r.param("organization")).toList();
    assertThat(orgs).containsExactly("org-a", "org-a", "org-a", "org-a", "org-a", null);
  }

  @Test
  void explicitOrganizationParameterWins() throws Exception {
    server.onPrefix("/api/", r -> Response.json("{}"));
    client("org-a").get("api/issues/search", Map.of("organization", "org-b"));
    assertThat(server.requests().getFirst().param("organization")).isEqualTo("org-b");
  }

  @Test
  void blankOrganizationIsIgnored() throws Exception {
    server.onPrefix("/api/", r -> Response.json("{}"));
    client("  ").get("api/issues/search", Map.of());
    assertThat(server.requests().getFirst().target()).isEqualTo("/api/issues/search");
  }

  @Test
  void nullParameterValuesAreDropped() throws Exception {
    server.onPrefix("/api/", r -> Response.json("{}"));
    Map<String, @Nullable String> params = new LinkedHashMap<>();
    params.put("a", null);
    params.put("b", "2");
    client(null).get("api/x", params);
    assertThat(server.requests().getFirst().target()).isEqualTo("/api/x?b=2");
  }

  @Test
  void throttledRequestsAreRetriedThreeTimesThenReturned() throws Exception {
    server.on("/api/t", r -> Response.status(429, "slow").withHeader("Retry-After", " 0 "));
    Sonar sq = client(null);
    Sonar.Response r = sq.get("api/t", Map.of());
    assertThat(r).isEqualTo(new Sonar.Response(429, "slow"));
    assertThat(sq.calls()).isEqualTo(4);
    assertThat(sq.throttled()).isEqualTo(3);
    assertThat(sq.retried()).isZero();
  }

  @Test
  void throttledThenOkReturnsTheOkResponse() throws Exception {
    AtomicInteger n = new AtomicInteger();
    server.on(
        "/api/t",
        r ->
            n.getAndIncrement() == 0
                ? Response.status(429, "").withHeader("Retry-After", "0")
                : Response.json("{\"ok\":1}"));
    Sonar sq = client(null);
    assertThat(sq.get("api/t", Map.of()).body()).isEqualTo("{\"ok\":1}");
    assertThat(sq.calls()).isEqualTo(2);
    assertThat(sq.throttled()).isEqualTo(1);
  }

  @Test
  void retryAfterIsHonouredInSeconds() throws Exception {
    AtomicInteger n = new AtomicInteger();
    server.on(
        "/api/t",
        r ->
            n.getAndIncrement() == 0
                ? Response.status(429, "").withHeader("Retry-After", "1")
                : Response.json("{}"));
    long start = System.nanoTime();
    assertThat(client(null).get("api/t", Map.of()).ok()).isTrue();
    assertThat((System.nanoTime() - start) / 1_000_000).isBetween(950L, 4000L);
  }

  @Test
  void unreadableRetryAfterWaitsFiveSeconds() throws Exception {
    AtomicInteger n = new AtomicInteger();
    server.on(
        "/api/t",
        r ->
            n.getAndIncrement() == 0
                ? Response.status(429, "").withHeader("Retry-After", "soon")
                : Response.json("{}"));
    Sonar sq = client(null);
    long start = System.nanoTime();
    assertThat(sq.get("api/t", Map.of()).ok()).isTrue();
    assertThat((System.nanoTime() - start) / 1_000_000).isBetween(4900L, 9000L);
    assertThat(sq.throttled()).isEqualTo(1);
  }

  @Test
  void gatewayErrorsAreRetriedTwiceThenReturned() throws Exception {
    AtomicInteger n = new AtomicInteger();
    int[] codes = {503, 504, 502};
    server.on("/api/g", r -> Response.status(codes[n.getAndIncrement()], "gw"));
    Sonar sq = client(null);
    long start = System.nanoTime();
    Sonar.Response r = sq.get("api/g", Map.of());
    // Back-off of 1 s then 2 s.
    assertThat((System.nanoTime() - start) / 1_000_000).isBetween(2900L, 6000L);
    assertThat(r).isEqualTo(new Sonar.Response(502, "gw"));
    assertThat(sq.calls()).isEqualTo(3);
    assertThat(sq.retried()).isEqualTo(2);
    assertThat(sq.throttled()).isZero();
  }

  @Test
  void serverErrorOtherThanGatewayIsNotRetried() throws Exception {
    server.on("/api/e", r -> Response.status(500, "boom"));
    Sonar sq = client(null);
    assertThat(sq.get("api/e", Map.of()).status()).isEqualTo(500);
    assertThat(sq.calls()).isEqualTo(1);
    assertThat(sq.retried()).isZero();
  }

  @Test
  void unreachableInstanceGivesStatusZeroAndTheExceptionName() throws Exception {
    int port;
    try (ServerSocket s = new ServerSocket(0)) {
      port = s.getLocalPort();
    }
    Sonar sq = new Sonar(new SonarConnection("http://127.0.0.1:" + port, "t", null, 2, false));
    Sonar.Response r = sq.get("api/system/status", Map.of());
    assertThat(r.unreachable()).isTrue();
    // "ConnectException: Connection refused" on macOS and Linux; the class name always leads.
    assertThat(r.body()).startsWith("ConnectException: ");
    assertThat(sq.calls()).isEqualTo(1);
  }

  @Test
  void malformedUrlIsReportedAsUnreachable() throws Exception {
    Sonar sq = new Sonar(new SonarConnection("not a url", "t", null, 2, false));
    Sonar.Response r = sq.get("api/x", Map.of());
    assertThat(r.status()).isZero();
    assertThat(r.body()).startsWith("IllegalArgumentException: ");
  }

  @Test
  void urlWithoutSchemeIsReportedWithTheExceptionMessage() throws Exception {
    Sonar sq = new Sonar(new SonarConnection("", "t", null, 2, false));
    assertThat(sq.get("api/x", Map.of()))
        .isEqualTo(new Sonar.Response(0, "IllegalArgumentException: URI with undefined scheme"));
  }

  @Test
  void interruptedThreadGetsAnInterruptedResponse() throws Exception {
    server.json("/api/x", "{}");
    Sonar sq = client(null);
    Thread.currentThread().interrupt();
    try {
      Sonar.Response r = sq.get("api/x", Map.of());
      assertThat(r).isEqualTo(new Sonar.Response(0, "interrompu"));
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void insecureClientStillTalksPlainHttp() throws Exception {
    server.json("/api/x", "{\"v\":1}");
    System.clearProperty("jdk.internal.httpclient.disableHostnameVerification");
    new Sonar(new SonarConnection(server.url(), "t", null, 5, false));
    assertThat(System.getProperty("jdk.internal.httpclient.disableHostnameVerification")).isNull();
    Sonar sq = new Sonar(new SonarConnection(server.url(), "t", null, 5, true));
    assertThat(sq.get("api/x", Map.of()).body()).isEqualTo("{\"v\":1}");
    assertThat(System.getProperty("jdk.internal.httpclient.disableHostnameVerification"))
        .isEqualTo("true");
  }
}
