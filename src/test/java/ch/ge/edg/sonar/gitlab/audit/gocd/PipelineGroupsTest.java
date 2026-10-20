package ch.ge.edg.sonar.gitlab.audit.gocd;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PipelineGroupsTest {

  @Test
  void theCapturedDashboardGivesEachPipelineItsGroupInDashboardOrder() throws IOException {
    try (InputStream in =
        getClass().getResourceAsStream("/fixtures/gocd-25.3/go_api_dashboard.json")) {
      Map<String, String> groupOf = PipelineGroups.of(new ObjectMapper().readTree(in));
      assertThat(groupOf)
          .containsExactly(
              Map.entry("other-build", "other"),
              Map.entry("hello-build", "hello"),
              Map.entry("hello-deploy-recette", "hello"),
              Map.entry("hello-deploy-prod", "hello"));
    }
  }

  @Test
  void aDashboardWithoutGroupsGivesNothing() throws IOException {
    assertThat(PipelineGroups.of(new ObjectMapper().readTree("{}"))).isEmpty();
  }
}
