package ch.ge.edg.sonar.gitlab.audit.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class GitlabResponseTest {

  record Item(String name, int size) {}

  private static Gitlab.Response resp(int status, @Nullable String body) {
    return new Gitlab.Response(status, body, Map.of());
  }

  @Test
  void unreachableOnlyForStatusZero() {
    assertThat(resp(0, "x").unreachable()).isTrue();
    assertThat(resp(200, "x").unreachable()).isFalse();
    assertThat(resp(-1, "x").unreachable()).isFalse();
  }

  @Test
  void okIsTheTwoHundredRange() {
    assertThat(resp(199, "").ok()).isFalse();
    assertThat(resp(200, "").ok()).isTrue();
    assertThat(resp(299, "").ok()).isTrue();
    assertThat(resp(300, "").ok()).isFalse();
  }

  @Test
  void jsonParsesTheBodyOrGivesANullNode() {
    assertThat(resp(200, "{\"a\":1}").json().path("a").asInt()).isEqualTo(1);
    assertThat(resp(200, null).json().isNull()).isTrue();
    assertThat(resp(200, "{broken").json().isNull()).isTrue();
    assertThat(resp(200, "").json().isMissingNode()).isTrue();
  }

  @Test
  void asReadsATypedValueOnlyFromAGoodResponse() {
    assertThat(resp(200, "{\"name\":\"alpha\",\"size\":3,\"extra\":true}").as(Item.class))
        .isEqualTo(new Item("alpha", 3));
    assertThat(resp(404, "{\"name\":\"alpha\"}").as(Item.class)).isNull();
    assertThat(resp(200, "  ").as(Item.class)).isNull();
    assertThat(resp(200, null).as(Item.class)).isNull();
    assertThat(resp(200, "[1,").as(Item.class)).isNull();
  }

  @Test
  void asListReadsATypedListOnlyFromAGoodResponse() {
    assertThat(resp(200, "[{\"name\":\"a\",\"size\":1},{\"name\":\"b\"}]").asList(Item.class))
        .hasValue(List.of(new Item("a", 1), new Item("b", 0)));
    assertThat(resp(500, "[]").asList(Item.class)).isEmpty();
    assertThat(resp(200, "").asList(Item.class)).isEmpty();
    assertThat(resp(200, null).asList(Item.class)).isEmpty();
    assertThat(resp(200, "{\"name\":\"a\"}").asList(Item.class)).isEmpty();
    assertThat(resp(299, "[]").asList(Item.class)).hasValue(List.of());
    assertThat(resp(200, "null").asList(Item.class)).isEmpty();
  }

  @Test
  void intHeaderIsLookedUpInLowerCaseAndTrimmed() {
    Gitlab.Response r =
        new Gitlab.Response(
            200, "", Map.of("x-total", " 42 ", "x-blank", " ", "x-bad", "4x", "X-Upper", "7"));
    assertThat(r.intHeader("X-Total")).isEqualTo(42);
    assertThat(r.intHeader("x-total")).isEqualTo(42);
    assertThat(r.intHeader("x-blank")).isNull();
    assertThat(r.intHeader("x-bad")).isNull();
    assertThat(r.intHeader("x-missing")).isNull();
    assertThat(r.intHeader("X-Upper")).as("stored keys are expected lower-case").isNull();
  }

  @Test
  void errorMessageFor429IgnoresTheBody() {
    assertThat(resp(429, "{\"message\":\"slow down\"}").errorMessage())
        .isEqualTo("limite de débit atteinte (429)");
  }

  @Test
  void errorMessageWithoutBodyIsEmpty() {
    assertThat(resp(500, null).errorMessage()).isEmpty();
  }

  @Test
  void errorMessagePrefersMessageThenErrorThenDescription() {
    assertThat(resp(401, "{\"message\":\"m\",\"error\":\"e\"}").errorMessage()).isEqualTo("m");
    assertThat(resp(401, "{\"error\":\"e\",\"error_description\":\"d\"}").errorMessage())
        .isEqualTo("e");
    assertThat(resp(401, "{\"error_description\":\"d\"}").errorMessage()).isEqualTo("d");
  }

  @Test
  void errorMessageSkipsNonTextualOrNullFields() {
    assertThat(resp(400, "{\"message\":{\"name\":[\"taken\"]},\"error\":\"e\"}").errorMessage())
        .isEqualTo("e");
    assertThat(resp(400, "{\"message\":null}").errorMessage()).isEqualTo("{\"message\":null}");
  }

  @Test
  void errorMessageFallsBackToTheRawBodyOnOneLine() {
    assertThat(resp(502, "Bad\ngateway").errorMessage()).isEqualTo("Bad gateway");
  }

  @Test
  void errorMessageIsTruncatedToNinetyCharacters() {
    String longText = "x".repeat(95);
    assertThat(resp(500, longText).errorMessage()).hasSize(90);
    assertThat(resp(500, "{\"message\":\"" + longText + "\"}").errorMessage())
        .isEqualTo("x".repeat(90));
    assertThat(resp(500, "y".repeat(90)).errorMessage()).isEqualTo("y".repeat(90));
  }

  @Test
  void pageIsAPlainRecord() {
    Gitlab.Page<String> p = new Gitlab.Page<>(List.of("a"), true, "err");
    assertThat(p.items()).containsExactly("a");
    assertThat(p.truncated()).isTrue();
    assertThat(p.error()).isEqualTo("err");
  }
}
