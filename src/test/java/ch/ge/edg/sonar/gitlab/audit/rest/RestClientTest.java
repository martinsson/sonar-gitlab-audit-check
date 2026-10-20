package ch.ge.edg.sonar.gitlab.audit.rest;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RestClientTest {

  @TempDir Path dir;

  private final List<Long> waits = new ArrayList<>();
  private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
  private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

  private RestClient client(String base, Map<String, String> headers) throws IOException {
    return new RestClient(
        RestClient.Settings.of(base, headers, false),
        Capture.none(),
        pause -> waits.add(pause.toSeconds()));
  }

  @Test
  void sends_the_fixed_headers_and_the_extra_ones_and_counts_calls() throws IOException {
    try (FakeHttpServer gocd = new FakeHttpServer()) {
      gocd.json("/go/api/version", "{\"version\":\"25.3.0\"}");
      RestClient c = client(gocd.url() + "//", AuthHeaders.bearer(" tok "));
      RestResponse r = c.get("/go/api/version", Map.of("Accept", "application/vnd.go.cd+json"));
      assertThat(r.ok()).isTrue();
      assertThat(r.json().path("version").asText()).isEqualTo("25.3.0");
      assertThat(r.header("Content-Type")).isEqualTo("application/json");
      FakeHttpServer.Request sent = gocd.requests().get(0);
      assertThat(sent.header("authorization")).isEqualTo("Bearer tok");
      assertThat(sent.header("accept")).isEqualTo("application/vnd.go.cd+json");
      assertThat(c.calls()).isEqualTo(1);
      assertThat(c.base()).isEqualTo(gocd.url());
    }
  }

  @Test
  void a_full_url_is_used_as_is_for_pagination_links() throws IOException {
    try (FakeHttpServer gocd = new FakeHttpServer()) {
      gocd.json("/next", "[]");
      RestClient c = client("http://unused.invalid", Map.of());
      assertThat(c.get(gocd.url() + "/next").ok()).isTrue();
    }
  }

  @Test
  void a_malformed_url_is_an_unreachable_response_not_a_crash() throws IOException {
    RestResponse r = client("http://bad host", Map.of()).get("/x");
    assertThat(r.status()).isZero();
    assertThat(r.error()).startsWith("IllegalArgumentException");
  }

  @Test
  void dump_dir_keeps_each_raw_answer_under_a_name_stable_across_runs() throws IOException {
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.json("/rest/api/2/serverInfo", "{\"version\":\"11.3.10\"}");
      Path dumps = dir.resolve("dumps");
      RestClient c =
          new RestClient(
              new RestClient.Settings(
                  jira.url(), Map.of(), 5, false, false, RestClient.Retries.DEFAULT),
              new Capture(
                  dumps, null, reason -> err.println("  (capture non écrite : " + reason + ")")),
              pause -> waits.add(pause.toSeconds()));
      c.get("/rest/api/2/serverInfo");
      String name = Capture.name("rest/api/2/serverInfo");
      assertThat(name).startsWith("rest_api_2_serverInfo-").endsWith(".json");
      assertThat(Files.readString(dumps.resolve(name))).contains("11.3.10");
      assertThat(dumps.resolve("index.txt")).hasContent(name + "  rest/api/2/serverInfo");
    }
  }

  @Test
  void a_dump_that_cannot_be_written_is_reported_not_fatal() throws IOException {
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.json("/x", "{}");
      Path dumps = dir.resolve("gone");
      RestClient c =
          new RestClient(
              new RestClient.Settings(
                  jira.url(), Map.of(), 5, false, false, RestClient.Retries.DEFAULT),
              new Capture(
                  dumps, null, reason -> err.println("  (capture non écrite : " + reason + ")")),
              pause -> waits.add(pause.toSeconds()));
      Files.delete(dumps);
      assertThat(c.get("/x").ok()).isTrue();
      assertThat(errBytes.toString(StandardCharsets.UTF_8)).contains("capture non écrite");
    }
  }

  @Test
  void insecure_builds_a_client_that_trusts_any_certificate() throws IOException {
    RestClient c =
        new RestClient(
            RestClient.Settings.of("https://self-signed.invalid", Map.of(), true),
            Capture.none(),
            pause -> {});
    assertThat(c.base()).isEqualTo("https://self-signed.invalid");
  }
}
