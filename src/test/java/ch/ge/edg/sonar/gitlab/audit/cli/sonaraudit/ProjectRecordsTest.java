package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Component;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.CurrentUser;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.QualityGateResponse;
import org.junit.jupiter.api.Test;

/** Accessors of the project, user and quality-gate records, missing parts included. */
class ProjectRecordsTest {

  @Test
  void componentOnBranchKeepsTheMainDate() {
    Component c = new Component("k", "n", "TRK", "d-main", "leak", null, null, null);
    assertThat(c.onOtherBranch()).isFalse();
    Component b = c.onBranch("dev", false, "d-dev");
    assertThat(b)
        .isEqualTo(new Component("k", "n", "TRK", "d-dev", "leak", "dev", false, "d-main"));
    assertThat(b.onOtherBranch()).isTrue();
    assertThat(c.onBranch("main", true, "d").onOtherBranch()).isFalse();
    assertThat(new Component("k", null, null, null, null, "dev", null, null).onOtherBranch())
        .isFalse();
  }

  @Test
  void recordAccessorsWithMissingParts() {
    assertThat(new Attachments("illisible (HTTP 403)", "", "").refused()).isTrue();
    assertThat(new Attachments("gitlab", "", "").refused()).isFalse();
    assertThat(new CurrentUser("u", null, null, null).globalPermissions()).isEmpty();
    assertThat(
            new CurrentUser("u", null, new CurrentUser.Permissions(null), null).globalPermissions())
        .isEmpty();
    assertThat(new QualityGateResponse(null).gateName()).isEmpty();
    assertThat(
            new QualityGateResponse(new QualityGateResponse.QualityGate("1", null, true))
                .gateName())
        .isEmpty();
  }
}
