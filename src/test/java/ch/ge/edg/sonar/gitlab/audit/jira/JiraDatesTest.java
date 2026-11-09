package ch.ge.edg.sonar.gitlab.audit.jira;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JiraDatesTest {

  private static final ObjectMapper M = new ObjectMapper();

  @Test
  void jiraAndIsoDatesAndEpochNumbersRead() {
    assertThat(JiraApi.instant(M.getNodeFactory().textNode("2026-03-04T10:15:30.000+0100")))
        .isEqualTo(Instant.parse("2026-03-04T09:15:30Z"));
    assertThat(JiraApi.instant(M.getNodeFactory().textNode("2026-03-04T10:15:30Z")))
        .isEqualTo(Instant.parse("2026-03-04T10:15:30Z"));
    assertThat(JiraApi.instant(M.getNodeFactory().numberNode(1_000L)))
        .isEqualTo(Instant.ofEpochMilli(1_000));
  }

  @Test
  void absentNullOrUnreadableIsNull() {
    assertThat(JiraApi.instant(null)).isNull();
    assertThat(JiraApi.instant(MissingNode.getInstance())).isNull();
    assertThat(JiraApi.instant(M.getNodeFactory().nullNode())).isNull();
    assertThat(JiraApi.instant(M.getNodeFactory().textNode("yesterday"))).isNull();
  }
}
