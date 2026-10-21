package ch.ge.edg.sonar.gitlab.audit.rest;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse.Answered;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RestResponseTest {

  record Item(String name, int size) {}

  private static RestResponse of(int status, String body) {
    return new Answered(status, body, Map.of());
  }

  @Test
  void aLoginPageAnswered200IsNotASuccess() {
    RestResponse page = new Answered(200, "<p>login</p>", Map.of("content-type", "text/html"));
    assertThat(page.ok()).isFalse();
    assertThat(page.tag()).isEqualTo("HTML");
    assertThat(page.why()).contains("HTML");
    assertThat(of(200, "  <!DOCTYPE html>").html()).isTrue();
    assertThat(of(200, "<HTML>").html()).isTrue();
    assertThat(of(200, "<?xml version=\"1.0\"?><Projects/>").html()).isFalse();
    assertThat(RestResponse.unreachable("x").html()).isFalse();
  }

  @Test
  void okIsATwoHundredThatIsNotHtml() {
    assertThat(of(200, "{}").ok()).isTrue();
    assertThat(of(204, "").ok()).isTrue();
    assertThat(of(299, "").ok()).isTrue();
    assertThat(of(300, "").ok()).isFalse();
    assertThat(of(199, "").ok()).isFalse();
    assertThat(RestResponse.unreachable("x").ok()).isFalse();
  }

  @Test
  void anExactStatusIsAskedByName() {
    assertThat(of(200, "").is(200)).isTrue();
    assertThat(of(201, "").is(200)).isFalse();
    assertThat(of(401, "").refused()).isTrue();
    assertThat(of(403, "").refused()).isFalse();
    assertThat(RestResponse.unreachable("x").is(200)).isFalse();
    assertThat(RestResponse.unreachable("x").refused()).isFalse();
  }

  @Test
  void noAnswerIsItsOwnStateWithItsReasonApartFromTheBody() {
    RestResponse r = RestResponse.unreachable("ConnectException");
    assertThat(r.unreachable()).isTrue();
    assertThat(of(200, "").unreachable()).isFalse();
    assertThat(r.body()).isEmpty();
    assertThat(r.header("x-total")).isEmpty();
    assertThat(r.json()).isEmpty();
    assertThat(r.statusLine()).isEqualTo("injoignable");
    assertThat(of(404, "").statusLine()).isEqualTo("HTTP 404");
  }

  @Test
  void tagsSayWhatHappenedInOneWord() {
    assertThat(of(200, "{}").tag()).isEqualTo("OK");
    assertThat(of(403, "").tag()).isEqualTo("403");
    assertThat(of(199, "").tag()).isEqualTo("199");
    assertThat(RestResponse.unreachable("ConnectException").tag()).isEqualTo("ERR");
  }

  @Test
  void reasonsNeverQuoteTheBody() {
    assertThat(RestResponse.unreachable("ConnectException").why()).isEqualTo("ConnectException");
    assertThat(of(401, "secret").why()).isEqualTo("non authentifié");
    assertThat(of(403, "").why()).isEqualTo("authentifié mais pas autorisé");
    assertThat(of(404, "").why()).startsWith("introuvable");
    assertThat(of(406, "").why()).contains("Accept");
    assertThat(of(500, "stack").why()).isEqualTo("HTTP 500");
    assertThat(of(299, "").why()).isEqualTo("HTTP 299");
    RestResponse redirect =
        new Answered(302, "", Map.of("location", "https://gocd.example/go/auth/login?x=1"));
    assertThat(redirect.why()).isEqualTo("redirection vers /go/auth/login");
    assertThat(of(399, "").why()).isEqualTo("redirection vers ");
    assertThat(of(400, "").why()).isEqualTo("HTTP 400");
  }

  @Test
  void aBodyThatIsNotJsonIsVisiblyUnreadable() {
    assertThat(of(200, "not json").json()).isEmpty();
    assertThat(of(200, "").json()).isEmpty();
    assertThat(of(200, "{broken").tree().isMissingNode()).isTrue();
    assertThat(of(200, "[1]").json()).hasValueSatisfying(j -> assertThat(j.size()).isEqualTo(1));
    assertThat(of(404, "{\"a\":1}").tree().path("a").asInt()).isEqualTo(1);
  }

  @Test
  void asReadsATypedValueOnlyFromASuccess() {
    assertThat(of(200, "{\"name\":\"alpha\",\"size\":3,\"extra\":true}").as(Item.class))
        .hasValue(new Item("alpha", 3));
    assertThat(of(404, "{\"name\":\"alpha\"}").as(Item.class)).isEmpty();
    assertThat(of(200, "  ").as(Item.class)).isEmpty();
    assertThat(of(200, "[1,").as(Item.class)).isEmpty();
    assertThat(RestResponse.unreachable("x").as(Item.class)).isEmpty();
  }

  @Test
  void asListReadsATypedListOnlyFromASuccess() {
    assertThat(of(200, "[{\"name\":\"a\",\"size\":1},{\"name\":\"b\"}]").asList(Item.class))
        .hasValue(List.of(new Item("a", 1), new Item("b", 0)));
    assertThat(of(500, "[]").asList(Item.class)).isEmpty();
    assertThat(of(200, "").asList(Item.class)).isEmpty();
    assertThat(of(200, "{\"name\":\"a\"}").asList(Item.class)).isEmpty();
    assertThat(of(299, "[]").asList(Item.class)).hasValue(List.of());
    assertThat(of(200, "null").asList(Item.class)).isEmpty();
  }

  @Test
  void intHeaderIsLookedUpInLowerCaseAndTrimmed() {
    RestResponse r =
        new Answered(
            200, "", Map.of("x-total", " 42 ", "x-blank", " ", "x-bad", "4x", "X-Upper", "7"));
    assertThat(r.intHeader("X-Total")).hasValue(42);
    assertThat(r.intHeader("x-blank")).isEmpty();
    assertThat(r.intHeader("x-bad")).isEmpty();
    assertThat(r.intHeader("x-missing")).isEmpty();
    assertThat(r.intHeader("X-Upper")).as("stored keys are expected lower-case").isEmpty();
  }

  @Test
  void messageOfNoAnswerIsItsReasonOnOneLine() {
    assertThat(RestResponse.unreachable("Connect\nrefused").message()).isEqualTo("Connect refused");
    assertThat(RestResponse.unreachable("r".repeat(95)).message()).hasSize(90);
  }

  @Test
  void messageFor429IgnoresTheBody() {
    assertThat(of(429, "{\"message\":\"slow down\"}").message())
        .isEqualTo("limite de débit atteinte (429)");
  }

  @Test
  void messageJoinsSonarErrorMessagesSkippingThoseWithout() {
    String body = "{\"errors\":[{\"msg\":\"first\"},{\"other\":1},{\"msg\":\"second\"}]}";
    assertThat(of(400, body).message()).isEqualTo("first; second");
    assertThat(of(400, "{\"errors\":[{\"msg\":\"" + "m".repeat(120) + "\"}]}").message())
        .isEqualTo("m".repeat(90));
    assertThat(of(400, "{\"errors\":[{\"other\":1}]}").message())
        .isEqualTo("{\"errors\":[{\"other\":1}]}");
    assertThat(of(400, "{\"errors\":[]}").message()).isEqualTo("{\"errors\":[]}");
  }

  @Test
  void messagePrefersGitlabMessageThenErrorThenDescription() {
    assertThat(of(401, "{\"message\":\"m\",\"error\":\"e\"}").message()).isEqualTo("m");
    assertThat(of(401, "{\"error\":\"e\",\"error_description\":\"d\"}").message()).isEqualTo("e");
    assertThat(of(401, "{\"error_description\":\"d\"}").message()).isEqualTo("d");
    assertThat(of(400, "{\"message\":{\"name\":[\"taken\"]},\"error\":\"e\"}").message())
        .isEqualTo("e");
    assertThat(of(400, "{\"message\":null}").message()).isEqualTo("{\"message\":null}");
  }

  @Test
  void messageFallsBackToTheRawBodyOnOneLineTruncatedToNinety() {
    assertThat(of(502, "Bad\ngateway").message()).isEqualTo("Bad gateway");
    assertThat(of(500, "").message()).isEmpty();
    assertThat(of(500, "x".repeat(95)).message()).hasSize(90);
    assertThat(of(500, "{\"message\":\"" + "x".repeat(95) + "\"}").message())
        .isEqualTo("x".repeat(90));
    assertThat(of(500, "y".repeat(90)).message()).isEqualTo("y".repeat(90));
  }
}
