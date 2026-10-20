package ch.ge.edg.sonar.gitlab.audit.gocd;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.rest.RestClient;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GocdApiTest {

  private static final PrintStream QUIET =
      new PrintStream(new ByteArrayOutputStream(), true, java.nio.charset.StandardCharsets.UTF_8);

  private static GocdApi api(FakeHttpServer gocd) throws IOException {
    return new GocdApi(
        new RestClient(RestClient.Settings.of(gocd.url(), Map.of(), false), QUIET, pause -> {}));
  }

  private static String run(int counter, long scheduled) {
    return "{\"counter\":%d,\"scheduled_date\":%d}".formatted(counter, scheduled);
  }

  @Test
  void accept_falls_back_to_named_versions_on_404_or_406_and_remembers_what_worked()
      throws IOException {
    try (FakeHttpServer gocd = new FakeHttpServer()) {
      gocd.on(
          "/go/api/dashboard",
          r ->
              switch (String.valueOf(r.header("accept"))) {
                case "application/vnd.go.cd+json" -> Response.status(406, "{}");
                case "application/vnd.go.cd.v4+json" -> Response.status(404, "{}");
                default -> Response.json("{\"_embedded\":{}}");
              });
      GocdApi api = api(gocd);
      assertThat(api.dashboard().ok()).isTrue();
      assertThat(api.acceptSeen()).containsEntry("dashboard", "application/vnd.go.cd.v3+json");
      assertThat(gocd.requests()).hasSize(3);
    }
  }

  @Test
  void an_api_that_fails_everywhere_is_not_remembered_and_403_stops_the_fallback()
      throws IOException {
    try (FakeHttpServer gocd = new FakeHttpServer()) {
      gocd.on("/go/api/admin/templates", r -> Response.status(403, "{}"));
      GocdApi api = api(gocd);
      assertThat(api.api("/go/api/admin/templates", "v7", "v6").status()).isEqualTo(403);
      assertThat(gocd.requests()).hasSize(1);
      assertThat(api.api("/go/api/version", "v1").status()).isEqualTo(404);
      assertThat(api.acceptSeen()).isEmpty();
    }
  }

  @Test
  void api_names_group_admin_endpoints_and_drop_ids_and_queries() {
    assertThat(GocdApi.apiName("/go/api/admin/pipelines/p")).isEqualTo("admin/pipelines");
    assertThat(GocdApi.apiName("/go/api/admin")).isEqualTo("admin");
    assertThat(GocdApi.apiName("/go/api/pipelines/p/history?after=3")).isEqualTo("pipelines");
    assertThat(GocdApi.apiName("http://h/go/api/stages/p/1/s/1")).isEqualTo("stages");
  }

  @Test
  void history_follows_next_links_and_stops_at_the_first_refused_run() throws IOException {
    try (FakeHttpServer gocd = new FakeHttpServer()) {
      String next = gocd.url() + "/go/api/pipelines/a%20b/history?after=2";
      gocd.on(
          "/go/api/pipelines/a%20b/history",
          r ->
              r.param("after") == null
                  ? Response.json(
                      "{\"_links\":{\"next\":{\"href\":\"%s\"}},\"pipelines\":[%s,%s]}"
                          .formatted(next, run(4, 400), run(3, 300)))
                  : Response.json(
                      "{\"_links\":{\"next\":{\"href\":\"%s\"}},\"pipelines\":[%s,%s]}"
                          .formatted(next, run(2, 200), run(1, 100))));
      GocdApi api = api(gocd);
      GocdApi.History h = api.history("a b", r -> r.path("scheduled_date").asLong() < 150, 10);
      assertThat(h.runs()).extracting(r -> r.path("counter").asInt()).containsExactly(4, 3, 2);
      assertThat(h.pages()).isEqualTo(2);
      assertThat(h.complete()).isFalse();
      assertThat(h.first().ok()).isTrue();
      assertThat(api.cached("a b", 2)).isTrue();
      assertThat(api.cached("a b", 1)).isFalse();
    }
  }

  @Test
  void history_is_complete_when_gocd_gives_no_next_link_or_an_empty_page() throws IOException {
    try (FakeHttpServer gocd = new FakeHttpServer()) {
      gocd.json("/go/api/pipelines/p/history", "{\"pipelines\":[%s]}".formatted(run(1, 1)));
      gocd.json(
          "/go/api/pipelines/q/history",
          "{\"_links\":{\"next\":{\"href\":\"/x\"}},\"pipelines\":[]}");
      GocdApi api = api(gocd);
      GocdApi.History p = api.history("p", r -> false, 10);
      assertThat(p.complete()).isTrue();
      assertThat(p.runs()).hasSize(1);
      GocdApi.History q = api.history("q", r -> false, 10);
      assertThat(q.complete()).isTrue();
      assertThat(q.runs()).isEmpty();
    }
  }

  @Test
  void history_stops_at_max_pages_and_reports_the_first_failure() throws IOException {
    try (FakeHttpServer gocd = new FakeHttpServer()) {
      gocd.json(
          "/go/api/pipelines/p/history",
          "{\"_links\":{\"next\":{\"href\":\"/go/api/pipelines/p/history\"}},\"pipelines\":[%s]}"
              .formatted(run(1, 1)));
      GocdApi api = api(gocd);
      GocdApi.History h = api.history("p", r -> false, 3);
      assertThat(h.pages()).isEqualTo(3);
      assertThat(h.complete()).isFalse();
      GocdApi.History missing = api.history("absent", r -> false, 3);
      assertThat(missing.first().status()).isEqualTo(404);
      assertThat(missing.pages()).isZero();
      GocdApi.History none = api.history("p", r -> false, 0);
      assertThat(none.first().status()).isZero();
    }
  }

  @Test
  void instances_are_read_once_including_the_missing_ones() throws IOException {
    try (FakeHttpServer gocd = new FakeHttpServer()) {
      gocd.json("/go/api/pipelines/build/12", run(12, 1));
      GocdApi api = api(gocd);
      JsonNode run = api.instance("build", 12).orElseThrow();
      assertThat(run.path("counter").asInt()).isEqualTo(12);
      api.instance("build", 12);
      assertThat(api.instance("build", 13)).isEmpty();
      assertThat(api.instance("build", 13)).isEmpty();
      assertThat(gocd.requests("/go/api/pipelines/build/12")).hasSize(1);
      assertThat(gocd.requests("/go/api/pipelines/build/13")).hasSize(2);
      assertThat(api.http().calls()).isEqualTo(3);
    }
  }

  @Test
  void enc_escapes_spaces_as_percent_20() {
    assertThat(GocdApi.enc("a b/c")).isEqualTo("a%20b%2Fc");
    RestResponse ignored = RestResponse.unreachable("x");
    assertThat(ignored.ok()).isFalse();
  }
}
