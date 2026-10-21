package ch.ge.edg.sonar.gitlab.audit.sonar;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.rest.Capture;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Request;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The transport: URL building, headers, organization, retries, unreachable instance. */
class SonarClientTest {

  /** What the clients of this test asked to wait, in order. */
  private final List<Duration> pauses = new ArrayList<>();

  private final FakeHttpServer server = new FakeHttpServer();

  @AfterEach
  void stop() {
    server.close();
  }

  private Sonar client(@Nullable String organization) throws Exception {
    return new Sonar(
        new SonarConnection(server.url() + "//", "tok-1", organization, 5, false),
        Capture.none(),
        pauses::add);
  }

  @Test
  void getSendsBearerTokenAcceptAndQueryOnACleanUrl() throws Exception {
    server.json("/api/system/status", "{\"status\":\"UP\"}");
    Sonar sq = client(null);
    RestResponse r = sq.get("//api/system/status", Map.of("a", "x y"));

    assertAnswered(r, 200, "{\"status\":\"UP\"}");
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
    assertThat(
            new Sonar(new SonarConnection(null, "t", null, 1, false), Capture.none(), pause -> {})
                .base)
        .isEmpty();
  }

  @Test
  void nonOkStatusIsReturnedWithItsBody() throws Exception {
    server.on("/api/x", r -> Response.status(403, "{\"errors\":[{\"msg\":\"nope\"}]}"));
    RestResponse r = client(null).get("api/x", Map.of());
    assertThat(r.is(403)).isTrue();
    assertThat(r.message()).isEqualTo("nope");
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
    RestResponse r = sq.get("api/t", Map.of());
    assertAnswered(r, 429, "slow");
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
    assertThat(client(null).get("api/t", Map.of()).ok()).isTrue();
    assertThat(pauses).containsExactly(Duration.ofSeconds(1));
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
    assertThat(sq.get("api/t", Map.of()).ok()).isTrue();
    assertThat(pauses).containsExactly(Duration.ofSeconds(5));
    assertThat(sq.throttled()).isEqualTo(1);
  }

  @Test
  void gatewayErrorsAreRetriedTwiceThenReturned() throws Exception {
    AtomicInteger n = new AtomicInteger();
    int[] codes = {503, 504, 502};
    server.on("/api/g", r -> Response.status(codes[n.getAndIncrement()], "gw"));
    Sonar sq = client(null);
    RestResponse r = sq.get("api/g", Map.of());
    assertThat(pauses).as("back-off").containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
    assertAnswered(r, 502, "gw");
    assertThat(sq.calls()).isEqualTo(3);
    assertThat(sq.retried()).isEqualTo(2);
    assertThat(sq.throttled()).isZero();
  }

  @Test
  void serverErrorOtherThanGatewayIsNotRetried() throws Exception {
    server.on("/api/e", r -> Response.status(500, "boom"));
    Sonar sq = client(null);
    assertThat(sq.get("api/e", Map.of()).is(500)).isTrue();
    assertThat(sq.calls()).isEqualTo(1);
    assertThat(sq.retried()).isZero();
  }

  @Test
  void unreachableInstanceGivesStatusZeroAndTheExceptionName() throws Exception {
    int port;
    try (ServerSocket s = new ServerSocket(0)) {
      port = s.getLocalPort();
    }
    Sonar sq =
        new Sonar(
            new SonarConnection("http://127.0.0.1:" + port, "t", null, 2, false),
            Capture.none(),
            pause -> {});
    RestResponse r = sq.get("api/system/status", Map.of());
    assertThat(r.unreachable()).isTrue();
    // "ConnectException: Connection refused" on macOS and Linux; the class name always leads.
    assertThat(r.why()).startsWith("ConnectException: ");
    assertThat(sq.calls()).isEqualTo(1);
  }

  @Test
  void malformedUrlIsReportedAsUnreachable() throws Exception {
    Sonar sq =
        new Sonar(
            new SonarConnection("not a url", "t", null, 2, false), Capture.none(), pause -> {});
    RestResponse r = sq.get("api/x", Map.of());
    assertThat(r.unreachable()).isTrue();
    assertThat(r.why()).startsWith("IllegalArgumentException: ");
  }

  @Test
  void urlWithoutSchemeIsReportedWithTheExceptionMessage() throws Exception {
    Sonar sq = new Sonar(new SonarConnection("", "t", null, 2, false), Capture.none(), pause -> {});
    assertThat(sq.get("api/x", Map.of()))
        .isEqualTo(RestResponse.unreachable("IllegalArgumentException: URI with undefined scheme"));
  }

  @Test
  void interruptedThreadGetsAnInterruptedResponse() throws Exception {
    server.json("/api/x", "{}");
    Sonar sq = client(null);
    Thread.currentThread().interrupt();
    try {
      RestResponse r = sq.get("api/x", Map.of());
      assertThat(r).isEqualTo(RestResponse.unreachable("interrompu"));
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void insecureClientStillTalksPlainHttp() throws Exception {
    server.json("/api/x", "{\"v\":1}");
    System.clearProperty("jdk.internal.httpclient.disableHostnameVerification");
    new Sonar(new SonarConnection(server.url(), "t", null, 5, false), Capture.none(), pause -> {});
    assertThat(System.getProperty("jdk.internal.httpclient.disableHostnameVerification")).isNull();
    Sonar sq =
        new Sonar(
            new SonarConnection(server.url(), "t", null, 5, true), Capture.none(), pause -> {});
    assertThat(sq.get("api/x", Map.of()).body()).isEqualTo("{\"v\":1}");
    assertThat(System.getProperty("jdk.internal.httpclient.disableHostnameVerification"))
        .isEqualTo("true");
  }

  private static void assertAnswered(RestResponse r, int status, String body) {
    assertThat(r.is(status)).as("status").isTrue();
    assertThat(r.body()).isEqualTo(body);
  }
}
