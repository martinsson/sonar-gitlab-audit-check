package ch.ge.edg.sonar.gitlab.audit.rest;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Quand RestClient recommence, combien de fois, et combien de temps il attend. */
class RestClientRetryTest {

  private final List<Long> waits = new ArrayList<>();

  private RestClient client(String base, Map<String, String> headers) throws IOException {
    return new RestClient(
        RestClient.Settings.of(base, headers, false),
        Capture.none(),
        pause -> waits.add(pause.toSeconds()));
  }

  @Test
  void a_429_waits_retry_after_then_tries_again() throws IOException {
    AtomicInteger seen = new AtomicInteger();
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.on(
          "/rest/api/2/myself",
          r ->
              seen.incrementAndGet() == 1
                  ? Response.status(429, "{}").withHeader("Retry-After", "7")
                  : Response.json("{}"));
      RestResponse r = client(jira.url(), Map.of()).get("/rest/api/2/myself");
      assertThat(r.ok()).isTrue();
      assertThat(waits).containsExactly(7L);
    }
  }

  @Test
  void the_wait_is_capped_and_a_bad_retry_after_means_five_seconds() {
    RestResponse huge = new RestResponse(429, "", Map.of("retry-after", "3600"), "");
    RestResponse junk = new RestResponse(429, "", Map.of("retry-after", "soon"), "");
    RestResponse negative = new RestResponse(429, "", Map.of("retry-after", "-3"), "");
    assertThat(RestClient.retryAfter(huge)).isEqualTo(3600);
    assertThat(RestClient.retryAfter(junk)).isEqualTo(5);
    assertThat(RestClient.retryAfter(negative)).isZero();
  }

  @Test
  void a_429_that_never_ends_stops_after_three_tries() throws IOException {
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.on("/x", r -> Response.status(429, "{}").withHeader("Retry-After", "100"));
      RestClient c = client(jira.url(), Map.of());
      assertThat(c.get("/x").status()).isEqualTo(429);
      assertThat(c.calls()).isEqualTo(3);
      assertThat(waits).containsExactly(60L, 60L);
    }
  }

  @Test
  void an_error_status_is_returned_without_retry() throws IOException {
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.on("/x", r -> Response.status(403, "{}"));
      RestClient c = client(jira.url(), Map.of());
      assertThat(c.get("/x").status()).isEqualTo(403);
      assertThat(c.calls()).isEqualTo(1);
    }
  }

  @Test
  void an_unreachable_host_is_retried_then_reported_with_the_exception_name() throws IOException {
    int port;
    try (ServerSocket s = new ServerSocket(0)) {
      port = s.getLocalPort();
    }
    RestClient c = client("http://127.0.0.1:" + port, Map.of());
    RestResponse r = c.get("/x");
    assertThat(r.status()).isZero();
    assertThat(r.error()).contains("ConnectException");
    assertThat(c.calls()).isEqualTo(3);
  }
}
