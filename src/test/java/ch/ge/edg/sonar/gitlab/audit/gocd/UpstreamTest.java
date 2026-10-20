package ch.ge.edg.sonar.gitlab.audit.gocd;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UpstreamTest {

  @Test
  void parses_pipeline_and_counter_of_a_pipeline_revision() {
    Upstream u = Upstream.parse("build/12/compile/1");
    assertThat(u).isEqualTo(new Upstream("build", 12));
    assertThat(String.valueOf(u)).isEqualTo("build/12");
    assertThat(Upstream.parse("build/ 7")).isEqualTo(new Upstream("build", 7));
  }

  @Test
  void anything_else_is_null() {
    assertThat(Upstream.parse(null)).isNull();
    assertThat(Upstream.parse("7fd1a60b")).isNull();
    assertThat(Upstream.parse("/12/s/1")).isNull();
    assertThat(Upstream.parse("build/x/s/1")).isNull();
    assertThat(Upstream.parse("build/0/s/1")).isNull();
  }
}
