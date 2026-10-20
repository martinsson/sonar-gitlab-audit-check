package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import static ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures.history;
import static ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures.run;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.ge.edg.sonar.gitlab.audit.gocd.GocdApi;
import ch.ge.edg.sonar.gitlab.audit.rest.RestClient;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SelectionAndHistoryTest {

  private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
  private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

  private String err() {
    return errBytes.toString(StandardCharsets.UTF_8);
  }

  private Gocd gocd(FakeHttpServer server) throws IOException {
    return new Gocd(
        new GocdApi(
            new RestClient(
                RestClient.Settings.of(server.url(), Map.of(), false), err, pause -> {})));
  }

  @Test
  void named_pipelines_come_first_then_every_pipeline_of_the_named_groups() throws Exception {
    try (FakeHttpServer server = GocdFixtures.serve(new FakeHttpServer())) {
      Selection s =
          Selection.resolve(
              gocd(server), List.of("other-build", "ghost"), List.of("hello", "nobody"), err);
      assertThat(s.pipelines())
          .containsExactly(
              "other-build", "ghost", "hello-build", "hello-deploy-recette", "hello-deploy-prod");
      assertThat(s.group("hello-deploy-prod")).isEqualTo("hello");
      assertThat(s.group("ghost")).isEmpty();
      assertThat(err())
          .isEqualTo(
              "Groupe invisible ou inconnu : nobody%nPipeline invisible ou inconnu : ghost%n"
                  .formatted());
    }
  }

  @Test
  void an_unreadable_dashboard_stops_and_a_refused_token_says_so() throws IOException {
    try (FakeHttpServer server = new FakeHttpServer()) {
      server.on("/go/api/dashboard", r -> Response.status(500, "{}"));
      assertThatThrownBy(() -> Selection.resolve(gocd(server), List.of("a"), List.of(), err))
          .isInstanceOf(Stop.class)
          .hasMessage("/go/api/dashboard : HTTP 500");
    }
    try (FakeHttpServer server = new FakeHttpServer()) {
      server.on("/go/api/dashboard", r -> Response.status(401, "{}"));
      assertThatThrownBy(() -> Selection.resolve(gocd(server), List.of("a"), List.of(), err))
          .isInstanceOf(Stop.class)
          .hasMessage(server.url() + " : jeton refusé (401)");
    }
  }

  @Test
  void history_stops_at_the_first_run_older_than_the_window_without_a_word() throws Exception {
    try (FakeHttpServer server = new FakeHttpServer()) {
      server.json(
          "/go/api/pipelines/p/history",
          history(run("p", 3, 3000), run("p", 2, 2000), run("p", 1, 1000)));
      List<JsonNode> runs = RunHistory.since(gocd(server), "p", Instant.ofEpochMilli(2000), err);
      assertThat(runs).extracting(r -> r.path("counter").asInt()).containsExactly(3, 2);
      assertThat(err()).isEmpty();
      List<JsonNode> all = RunHistory.since(gocd(server), "p", Instant.ofEpochMilli(0), err);
      assertThat(all).hasSize(3);
      assertThat(err()).isEmpty();
    }
  }

  @Test
  void a_run_without_date_is_kept() throws Exception {
    try (FakeHttpServer server = new FakeHttpServer()) {
      server.json("/go/api/pipelines/p/history", "{\"pipelines\":[{\"counter\":1}]}");
      assertThat(RunHistory.since(gocd(server), "p", Instant.ofEpochMilli(5), err)).hasSize(1);
    }
  }

  @Test
  void an_unreadable_history_is_reported_and_extraction_goes_on() throws Exception {
    try (FakeHttpServer server = new FakeHttpServer()) {
      server.on("/go/api/pipelines/p/history", r -> Response.status(403, "{}"));
      assertThat(RunHistory.since(gocd(server), "p", Instant.EPOCH, err)).isEmpty();
      assertThat(err()).contains("p : historique illisible (authentifié mais pas autorisé)");
    }
  }

  @Test
  void a_history_cut_after_some_pages_is_reported() throws Exception {
    try (FakeHttpServer server = new FakeHttpServer()) {
      String next = server.url() + "/go/api/pipelines/p/history?after=1";
      server.on(
          "/go/api/pipelines/p/history",
          r ->
              r.param("after") == null
                  ? Response.json(
                      "{\"_links\":{\"next\":{\"href\":\"%s\"}},\"pipelines\":[%s]}"
                          .formatted(next, run("p", 2, 2000)))
                  : Response.status(500, "{}"));
      assertThat(RunHistory.since(gocd(server), "p", Instant.EPOCH, err)).hasSize(1);
      assertThat(err()).contains("p : historique interrompu après 1 page(s)");
    }
  }

  @Test
  void a_refused_token_on_a_history_stops() throws IOException {
    try (FakeHttpServer server = new FakeHttpServer()) {
      server.on("/go/api/pipelines/p/history", r -> Response.status(401, "{}"));
      assertThatThrownBy(() -> RunHistory.since(gocd(server), "p", Instant.EPOCH, err))
          .isInstanceOf(Stop.class);
    }
  }

  @Test
  void a_missing_url_or_token_stops_before_any_call() {
    assertThatThrownBy(() -> Gocd.connect(null, "t", false, err, pause -> {}))
        .isInstanceOf(Stop.class)
        .hasMessage("GOCD_URL manquant");
    assertThatThrownBy(() -> Gocd.connect("http://h", " ", false, err, pause -> {}))
        .isInstanceOf(Stop.class)
        .hasMessage("GOCD_TOKEN manquant");
  }

  @Test
  void the_url_may_end_with_go_and_the_token_goes_as_bearer() throws Exception {
    try (FakeHttpServer server = GocdFixtures.serve(new FakeHttpServer())) {
      Gocd gocd = Gocd.connect(" " + server.url() + "/go// ", " pat ", false, err, pause -> {});
      Selection.resolve(gocd, List.of(), List.of("hello"), err);
      assertThat(server.requests().get(0).target()).isEqualTo("/go/api/dashboard");
      assertThat(server.requests().get(0).header("Authorization")).isEqualTo("Bearer pat");
      assertThat(gocd.calls()).isEqualTo(1);
    }
  }
}
