package ch.ge.edg.sonar.gitlab.audit.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class GitlabDatesTest {

  private static final ObjectMapper M = new ObjectMapper();

  @Test
  void isoDatesAndEpochNumbersRead() {
    assertThat(Gitlab.instant(M.getNodeFactory().textNode("2026-03-04T10:15:30.000+01:00")))
        .isEqualTo(Instant.parse("2026-03-04T09:15:30Z"));
    assertThat(Gitlab.instant(M.getNodeFactory().numberNode(1_000L)))
        .isEqualTo(Instant.ofEpochMilli(1_000));
  }

  @Test
  void absentNullOrUnreadableIsNull() {
    assertThat(Gitlab.instant(null)).isNull();
    assertThat(Gitlab.instant(MissingNode.getInstance())).isNull();
    assertThat(Gitlab.instant(M.getNodeFactory().nullNode())).isNull();
    assertThat(Gitlab.instant(M.getNodeFactory().textNode(""))).isNull();
    assertThat(Gitlab.instant(M.getNodeFactory().textNode("yesterday"))).isNull();
  }
}
