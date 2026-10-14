package ch.ge.edg.sonar.gitlab.audit.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.console.ConsoleOut;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Request;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GitlabPagingTest {

  record Item(int n) {}

  private final FakeHttpServer server = new FakeHttpServer();
  private Gitlab gl;

  @BeforeEach
  void start() throws Exception {
    ConsoleOut.colorMode("never");
    gl = new Gitlab(server.url(), "t", 5, false, null);
  }

  /** A client made inside the capture: it prints on the streams in place when it is built. */
  private Gitlab printing() {
    try {
      return new Gitlab(server.url(), "t", 5, false, null);
    } catch (IOException | GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  @AfterEach
  void stop() {
    server.close();
  }

  /** Pages of {@code size} items numbered from 1, the last one with an empty X-Next-Page. */
  private static Function<Request, Response> pages(int count, int size) {
    return r -> {
      int page = Integer.parseInt(r.param("page"));
      String body =
          IntStream.range(0, size)
              .mapToObj(i -> "{\"n\":" + ((page - 1) * size + i + 1) + "}")
              .collect(Collectors.joining(",", "[", "]"));
      return Response.json(body).withHeader("X-Next-Page", page < count ? "" + (page + 1) : "");
    };
  }

  private static List<Integer> numbers(List<JsonNode> nodes) {
    return nodes.stream().map(n -> n.path("n").asInt()).toList();
  }

  @Test
  void pagedFollowsXNextPageAndKeepsTheParameters() {
    server.on("/api/v4/items", pages(3, 2));
    List<JsonNode> all = gl.paged("items", Map.of("state", "open"));
    assertThat(numbers(all)).containsExactly(1, 2, 3, 4, 5, 6);
    List<Request> reqs = server.requests("/api/v4/items");
    assertThat(reqs).extracting(r -> r.param("page")).containsExactly("1", "2", "3");
    assertThat(reqs).allSatisfy(r -> assertThat(r.param("per_page")).isEqualTo("100"));
    assertThat(reqs).allSatisfy(r -> assertThat(r.param("state")).isEqualTo("open"));
  }

  @Test
  void pagedStopsWhenTheNextPageHeaderIsMissing() {
    server.json("/api/v4/items", "[{\"n\":1}]");
    assertThat(numbers(gl.paged("items", Map.of()))).containsExactly(1);
    assertThat(server.requests()).hasSize(1);
  }

  @Test
  void pagedReportsAnHttpErrorAndKeepsWhatItHas() {
    server.on(
        "/api/v4/items",
        r ->
            "1".equals(r.param("page"))
                ? Response.json("[{\"n\":1}]").withHeader("X-Next-Page", "2")
                : Response.status(500, "{}"));
    List<JsonNode> got = new ArrayList<>();
    GitlabStreams.Printed printed =
        GitlabStreams.capture(() -> got.addAll(printing().paged("items", Map.of())));
    assertThat(numbers(got)).containsExactly(1);
    assertThat(printed.out()).isEqualTo("    Pagination interrompue page 2 : HTTP 500\n");
  }

  @Test
  void pagedTreatsAnyNon200AsAnError() {
    server.on("/api/v4/items", r -> Response.status(201, "[{\"n\":1}]"));
    GitlabStreams.Printed printed =
        GitlabStreams.capture(() -> assertThat(printing().paged("items", Map.of())).isEmpty());
    assertThat(printed.out()).contains("page 1 : HTTP 201");
  }

  @Test
  void pagedStopsSilentlyOnANonArray() {
    server.on("/api/v4/items", r -> Response.json("{\"n\":1}").withHeader("X-Next-Page", "2"));
    GitlabStreams.Printed printed =
        GitlabStreams.capture(() -> assertThat(printing().paged("items", Map.of())).isEmpty());
    assertThat(printed.out()).isEmpty();
    assertThat(server.requests()).hasSize(1);
  }

  @Test
  void pagedGivesUpAfterFiveHundredPages() {
    server.on(
        "/api/v4/items",
        r ->
            Response.json("[{\"n\":1}]")
                .withHeader("X-Next-Page", "" + (Integer.parseInt(r.param("page")) + 1)));
    assertThat(gl.paged("items", Map.of())).hasSize(500);
    assertThat(server.requests()).hasSize(500);
  }

  @Test
  void typedPagedReadsEverythingUnderTheCap() {
    server.on("/api/v4/items", pages(2, 3));
    Gitlab.Page<Item> p = gl.paged("items", Map.of("a", "b"), Item.class, 10);
    assertThat(p.items()).extracting(Item::n).containsExactly(1, 2, 3, 4, 5, 6);
    assertThat(p.truncated()).isFalse();
    assertThat(p.error()).isNull();
    assertThat(server.requests()).allSatisfy(r -> assertThat(r.param("a")).isEqualTo("b"));
  }

  @Test
  void typedPagedCutsAtTheCapAndSaysSo() {
    server.on("/api/v4/items", pages(5, 3));
    Gitlab.Page<Item> p = gl.paged("items", Map.of(), Item.class, 4);
    assertThat(p.items()).extracting(Item::n).containsExactly(1, 2, 3, 4);
    assertThat(p.truncated()).isTrue();
    assertThat(server.requests()).hasSize(2);
  }

  @Test
  void typedPagedExactlyAtTheCapIsCompleteWithoutANextPage() {
    server.on("/api/v4/items", pages(2, 2));
    Gitlab.Page<Item> p = gl.paged("items", Map.of(), Item.class, 4);
    assertThat(p.items()).hasSize(4);
    assertThat(p.truncated()).isFalse();
  }

  @Test
  void typedPagedExactlyAtTheCapWithANextPageIsTruncated() {
    server.on("/api/v4/items", pages(3, 2));
    Gitlab.Page<Item> p = gl.paged("items", Map.of(), Item.class, 4);
    assertThat(p.items()).hasSize(4);
    assertThat(p.truncated()).isTrue();
    assertThat(server.requests()).hasSize(2);
  }

  @Test
  void typedPagedWithAZeroCapAsksNothing() {
    Gitlab.Page<Item> p = gl.paged("items", Map.of(), Item.class, 0);
    assertThat(p.items()).isEmpty();
    assertThat(p.truncated()).isTrue();
    assertThat(server.requests()).isEmpty();
  }

  @Test
  void typedPagedReportsAnHttpErrorWithWhatItHas() {
    server.on(
        "/api/v4/items",
        r ->
            "1".equals(r.param("page"))
                ? Response.json("[{\"n\":1}]").withHeader("X-Next-Page", "2")
                : Response.status(403, "{\"message\":\"403 Forbidden\"}"));
    Gitlab.Page<Item> p = gl.paged("items", Map.of(), Item.class, 10);
    assertThat(p.items()).extracting(Item::n).containsExactly(1);
    assertThat(p.truncated()).isFalse();
    assertThat(p.error()).isEqualTo("HTTP 403 — 403 Forbidden");
  }

  @Test
  void typedPagedReportsAnUnreadablePage() {
    server.json("/api/v4/items", "{\"n\":1}");
    Gitlab.Page<Item> p = gl.paged("items", Map.of(), Item.class, 10);
    assertThat(p.items()).isEmpty();
    assertThat(p.truncated()).isFalse();
    assertThat(p.error()).isEqualTo("réponse illisible");
  }
}
