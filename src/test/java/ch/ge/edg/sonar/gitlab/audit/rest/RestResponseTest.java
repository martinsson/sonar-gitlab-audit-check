package ch.ge.edg.sonar.gitlab.audit.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RestResponseTest {

  private static RestResponse of(int status, String body) {
    return new RestResponse(status, body, Map.of(), "");
  }

  @Test
  void aLoginPageAnswered200IsNotASuccess() {
    RestResponse page =
        new RestResponse(200, "<p>login</p>", Map.of("content-type", "text/html"), "");
    assertThat(page.ok()).isFalse();
    assertThat(page.tag()).isEqualTo("HTML");
    assertThat(page.why()).contains("HTML");
    assertThat(of(200, "  <!DOCTYPE html>").html()).isTrue();
    assertThat(of(200, "<HTML>").html()).isTrue();
    assertThat(of(200, "<?xml version=\"1.0\"?><Projects/>").html()).isFalse();
  }

  @Test
  void tagsSayWhatHappenedInOneWord() {
    assertThat(of(200, "{}").tag()).isEqualTo("OK");
    assertThat(of(204, "").ok()).isTrue();
    assertThat(of(299, "").ok()).isTrue();
    assertThat(of(300, "").ok()).isFalse();
    assertThat(of(199, "").ok()).isFalse();
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
        new RestResponse(302, "", Map.of("location", "https://gocd.example/go/auth/login?x=1"), "");
    assertThat(redirect.why()).isEqualTo("redirection vers /go/auth/login");
    assertThat(of(399, "").why()).isEqualTo("redirection vers ");
    assertThat(of(400, "").why()).isEqualTo("HTTP 400");
  }

  @Test
  void aBodyThatIsNotJsonReadsAsMissing() {
    assertThat(of(200, "not json").json().isMissingNode()).isTrue();
    assertThat(of(200, "").json().isMissingNode()).isTrue();
    assertThat(of(200, "[1]").json().size()).isEqualTo(1);
  }
}
