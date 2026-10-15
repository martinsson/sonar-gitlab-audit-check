package ch.ge.edg.sonar.gitlab.audit.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

class FakeHttpServerTest {

  private static HttpResponse<String> get(String url) throws Exception {
    HttpRequest req =
        HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer t").build();
    return HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void serves_routes_records_requests_and_404s_the_rest() throws Exception {
    try (FakeHttpServer server = new FakeHttpServer()) {
      server.json("/api/x", "{\"a\":1}");
      server.onPrefix("/api/p/", r -> FakeHttpServer.Response.json("\"" + r.param("q") + "\""));

      assertThat(get(server.url() + "/api/x?k=v").body()).isEqualTo("{\"a\":1}");
      assertThat(get(server.url() + "/api/p/z?q=a%20b").body()).isEqualTo("\"a b\"");
      assertThat(get(server.url() + "/nope").statusCode()).isEqualTo(404);

      assertThat(server.requests()).hasSize(3);
      FakeHttpServer.Request first = server.requests("/api/x").getFirst();
      assertThat(first.param("k")).isEqualTo("v");
      assertThat(first.header("Authorization")).isEqualTo("Bearer t");
    }
  }
}
