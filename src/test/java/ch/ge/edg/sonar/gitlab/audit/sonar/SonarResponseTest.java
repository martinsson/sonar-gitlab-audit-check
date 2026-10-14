package ch.ge.edg.sonar.gitlab.audit.sonar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SonarResponseTest {

  record Named(String name) {}

  @Test
  void statusZeroIsUnreachableAndNotOk() {
    Sonar.Response r = new Sonar.Response(0, "x");
    assertThat(r.unreachable()).isTrue();
    assertThat(r.ok()).isFalse();
  }

  @Test
  void status200IsOkAndReachable() {
    Sonar.Response r = new Sonar.Response(200, "{}");
    assertThat(r.ok()).isTrue();
    assertThat(r.unreachable()).isFalse();
  }

  @Test
  void otherStatusesAreNeitherOkNorUnreachable() {
    assertThat(new Sonar.Response(201, "").ok()).isFalse();
    assertThat(new Sonar.Response(201, "").unreachable()).isFalse();
    assertThat(new Sonar.Response(404, "").ok()).isFalse();
  }

  @Test
  void asParsesTheBodyOn200IgnoringUnknownFields() {
    Named n = new Sonar.Response(200, "{\"name\":\"alpha\",\"extra\":1}").as(Named.class);
    assertThat(n).isEqualTo(new Named("alpha"));
  }

  @Test
  void asIsNullWhenStatusIsNot200() {
    assertThat(new Sonar.Response(201, "{\"name\":\"alpha\"}").as(Named.class)).isNull();
    assertThat(new Sonar.Response(404, "{\"name\":\"alpha\"}").as(Named.class)).isNull();
  }

  @Test
  void asIsNullWithoutBodyOrWithBadJson() {
    assertThat(new Sonar.Response(200, null).as(Named.class)).isNull();
    assertThat(new Sonar.Response(200, "not json").as(Named.class)).isNull();
  }

  @Test
  void errorMessageIsEmptyWithoutBody() {
    assertThat(new Sonar.Response(500, null).errorMessage()).isEmpty();
  }

  @Test
  void errorMessageJoinsSonarErrorMessagesSkippingNulls() {
    String body = "{\"errors\":[{\"msg\":\"first\"},{\"other\":1},{\"msg\":\"second\"}]}";
    assertThat(new Sonar.Response(400, body).errorMessage()).isEqualTo("first; second");
  }

  @Test
  void errorMessageIsEmptyWhenNoErrorCarriesAMessage() {
    assertThat(new Sonar.Response(400, "{\"errors\":[{\"other\":1}]}").errorMessage()).isEmpty();
  }

  @Test
  void errorMessageTruncatesJoinedMessagesTo90() {
    String msg = "m".repeat(120);
    String body = "{\"errors\":[{\"msg\":\"" + msg + "\"}]}";
    assertThat(new Sonar.Response(400, body).errorMessage()).isEqualTo("m".repeat(90));
  }

  @Test
  void errorMessageFallsBackToRawBodyWithNewlinesFlattened() {
    assertThat(new Sonar.Response(502, "Bad\ngateway").errorMessage()).isEqualTo("Bad gateway");
  }

  @Test
  void errorMessageFallsBackToBodyWhenErrorsEmptyOrAbsent() {
    assertThat(new Sonar.Response(400, "{\"errors\":[]}").errorMessage())
        .isEqualTo("{\"errors\":[]}");
    assertThat(new Sonar.Response(400, "{\"a\":1}").errorMessage()).isEqualTo("{\"a\":1}");
  }

  @Test
  void errorMessageTruncatesRawBodyTo90AndKeepsExactly90() {
    assertThat(new Sonar.Response(500, "b".repeat(200)).errorMessage()).hasSize(90);
    assertThat(new Sonar.Response(500, "c".repeat(90)).errorMessage()).isEqualTo("c".repeat(90));
  }

  @Test
  void errorRecordsAreDeserializable() throws Exception {
    Sonar.ErrorResponse e =
        Sonar.MAPPER.readValue(
            "{\"errors\":[{\"msg\":\"m\",\"x\":2}],\"y\":3}", Sonar.ErrorResponse.class);
    assertThat(e.errors()).containsExactly(new Sonar.ErrorMessage("m"));
    assertThat(List.of(Sonar.DEFAULT_CONCURRENCY)).containsExactly(8);
  }
}
