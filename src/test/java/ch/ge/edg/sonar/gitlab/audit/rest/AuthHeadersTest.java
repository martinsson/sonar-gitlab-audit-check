package ch.ge.edg.sonar.gitlab.audit.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthHeadersTest {

  @Test
  void bearer_basic_and_cookie() {
    assertThat(AuthHeaders.bearer(" abc\n")).containsEntry("Authorization", "Bearer abc");
    assertThat(AuthHeaders.basic("jdoe", "pw"))
        .containsEntry("Authorization", "Basic amRvZTpwdw==");
    assertThat(AuthHeaders.cookie(" xyz ")).containsEntry("Cookie", "JSESSIONID=xyz");
    assertThat(AuthHeaders.cookie("JSESSIONID=xyz; other=1"))
        .containsEntry("Cookie", "JSESSIONID=xyz; other=1");
  }
}
