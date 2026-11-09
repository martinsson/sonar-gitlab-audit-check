package ch.ge.edg.sonar.gitlab.audit.gitlab;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.rest.Capture;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class GitlabConventionsTest {

  @Test
  void parseKeepsTheOffset() {
    OffsetDateTime t = requireNonNull(Gitlab.parse("2026-08-18T09:12:33.000+02:00"));
    assertThat(t).isNotNull();
    assertThat(t.getOffset()).isEqualTo(ZoneOffset.ofHours(2));
    assertThat(t.getHour()).isEqualTo(9);
    assertThat(requireNonNull(Gitlab.parse("2026-08-18T09:12:33Z")).getOffset())
        .isEqualTo(ZoneOffset.UTC);
  }

  @Test
  void parseGivesNullOnBlankOrGarbage() {
    assertThat(Gitlab.parse(null)).isNull();
    assertThat(Gitlab.parse(" ")).isNull();
    assertThat(Gitlab.parse("2026-08-18")).isNull();
    assertThat(Gitlab.parse("yesterday")).isNull();
  }

  @Test
  void encIsFormUrlEncoding() {
    assertThat(Gitlab.enc("team-a/alpha service")).isEqualTo("team-a%2Falpha+service");
    assertThat(Gitlab.enc("é")).isEqualTo("%C3%A9");
  }

  @Test
  void mapperIgnoresUnknownProperties() throws Exception {
    record One(int a) {}
    assertThat(Gitlab.READER.readValue("{\"a\":1,\"b\":2}", One.class)).isEqualTo(new One(1));
  }

  @Test
  void aLongRequestIsCutInTheDumpNameNeverTheExtension() {
    String request = "api/v4/projects/1/repository/commits?since=" + "9".repeat(200);
    String name = Capture.name(request);
    assertThat(name)
        .startsWith("v4_projects_1_repository_commits-")
        .endsWith(".json")
        .hasSizeLessThan(80);
    assertThat(Capture.name("api/v4/user")).matches("v4_user-[0-9a-f]{8}\\.json");
  }
}
