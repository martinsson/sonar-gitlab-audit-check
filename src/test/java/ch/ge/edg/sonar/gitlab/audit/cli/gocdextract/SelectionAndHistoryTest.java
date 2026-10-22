package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import static ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures.history;
import static ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures.run;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.ge.edg.sonar.gitlab.audit.gocd.Run;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SelectionAndHistoryTest {

  private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
  private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

  private String err() {
    return errBytes.toString(StandardCharsets.UTF_8);
  }

  private Gocd gocd(FakeHttpServer server) {
    return new Gocd(GocdFixtures.api(server));
  }

  @Test
  void namedPipelinesComeFirstThenEveryPipelineOfTheNamedGroups() throws Exception {
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
  void anUnreadableDashboardStopsAndARefusedTokenSaysSo() throws IOException {
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
  void historyStopsAtTheFirstRunOlderThanTheWindowWithoutAWord() throws Exception {
    try (FakeHttpServer server = new FakeHttpServer()) {
      server.json(
          "/go/api/pipelines/p/history",
          history(run("p", 3, 3000), run("p", 2, 2000), run("p", 1, 1000)));
      List<Run> runs = RunHistory.since(gocd(server), "p", Instant.ofEpochMilli(2000), err);
      assertThat(runs).extracting(Run::counter).containsExactly(3, 2);
      assertThat(err()).isEmpty();
      List<Run> all = RunHistory.since(gocd(server), "p", Instant.ofEpochMilli(0), err);
      assertThat(all).hasSize(3);
      assertThat(err()).isEmpty();
    }
  }

  @Test
  void aRunWithoutDateIsKept() throws Exception {
    try (FakeHttpServer server = new FakeHttpServer()) {
      server.json("/go/api/pipelines/p/history", "{\"pipelines\":[{\"counter\":1}]}");
      assertThat(RunHistory.since(gocd(server), "p", Instant.ofEpochMilli(5), err)).hasSize(1);
    }
  }

  @Test
  void anUnreadableHistoryIsReportedAndExtractionGoesOn() throws Exception {
    try (FakeHttpServer server = new FakeHttpServer()) {
      server.on("/go/api/pipelines/p/history", r -> Response.status(403, "{}"));
      assertThat(RunHistory.since(gocd(server), "p", Instant.EPOCH, err)).isEmpty();
      assertThat(err()).contains("p : historique illisible (authentifié mais pas autorisé)");
    }
  }

  @Test
  void aHistoryCutAfterSomePagesIsReported() throws Exception {
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
  void aRefusedTokenOnAHistoryStops() throws IOException {
    try (FakeHttpServer server = new FakeHttpServer()) {
      server.on("/go/api/pipelines/p/history", r -> Response.status(401, "{}"));
      assertThatThrownBy(() -> RunHistory.since(gocd(server), "p", Instant.EPOCH, err))
          .isInstanceOf(Stop.class);
    }
  }

  @Test
  void aMissingUrlOrTokenStopsBeforeAnyCall() {
    assertThatThrownBy(() -> Gocd.connect(null, "t", false, pause -> {}))
        .isInstanceOf(Stop.class)
        .hasMessage("GOCD_URL manquant");
    assertThatThrownBy(() -> Gocd.connect("http://h", " ", false, pause -> {}))
        .isInstanceOf(Stop.class)
        .hasMessage("GOCD_TOKEN manquant");
  }

  @Test
  void theUrlMayEndWithGoAndTheTokenGoesAsBearer() throws Exception {
    try (FakeHttpServer server = GocdFixtures.serve(new FakeHttpServer())) {
      Gocd gocd = Gocd.connect(" " + server.url() + "/go// ", " pat ", false, pause -> {});
      Selection.resolve(gocd, List.of(), List.of("hello"), err);
      assertThat(server.requests().get(0).target()).isEqualTo("/go/api/dashboard");
      assertThat(server.requests().get(0).header("Authorization")).isEqualTo("Bearer pat");
      assertThat(gocd.calls()).isEqualTo(1);
    }
  }
}
