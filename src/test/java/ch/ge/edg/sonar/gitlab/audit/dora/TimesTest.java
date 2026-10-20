package ch.ge.edg.sonar.gitlab.audit.dora;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TimesTest {

  private static final JsonNodeFactory F = new ObjectMapper().getNodeFactory();

  @Test
  void gocdEpochIsoAndJiraFormats() {
    assertThat(Times.parse(F.numberNode(1_000L))).isEqualTo(Instant.ofEpochMilli(1_000));
    assertThat(Times.parse(F.textNode("2026-03-04T10:15:30Z")))
        .isEqualTo(Instant.parse("2026-03-04T10:15:30Z"));
    assertThat(Times.parse("2026-03-04T10:15:30.000+0100"))
        .isEqualTo(Instant.parse("2026-03-04T09:15:30Z"));
  }

  @Test
  void absentOrUnreadableIsNull() {
    assertThat(Times.parse(F.nullNode())).isNull();
    assertThat(Times.parse(MissingNode.getInstance())).isNull();
    assertThat(Times.parse((com.fasterxml.jackson.databind.JsonNode) null)).isNull();
    assertThat(Times.parse(" ")).isNull();
    assertThat(Times.parse((String) null)).isNull();
    assertThat(Times.parse("yesterday")).isNull();
  }

  @Test
  void durationsUseADecimalCommaAndStayEmptyWhenUnknownOrNegative() {
    Instant a = Instant.parse("2026-01-01T00:00:00Z");
    assertThat(Times.hours(a, a.plusSeconds(90 * 60))).isEqualTo("1,5");
    assertThat(Times.days(a, a.plusSeconds(36 * 3600))).isEqualTo("1,5");
    assertThat(Times.hours(a, a)).isEqualTo("0,0");
    assertThat(Times.hours(a.plusSeconds(1), a)).isEmpty();
    assertThat(Times.days(null, a)).isEmpty();
    assertThat(Times.days(a, null)).isEmpty();
  }

  @Test
  void formatIsLocalTimeReadableByExcel() {
    Instant a = Instant.parse("2026-01-01T12:30:05Z");
    assertThat(Times.format(a, ZoneOffset.ofHours(1))).isEqualTo("2026-01-01 13:30:05");
    assertThat(Times.format(null, ZoneOffset.UTC)).isEmpty();
  }
}
