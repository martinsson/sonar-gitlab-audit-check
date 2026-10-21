package ch.ge.edg.sonar.gitlab.audit.jira;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JiraApiTest {

  private static JiraApi api(FakeHttpServer jira) throws IOException {
    try {
      return JiraApi.connect(jira.url(), "t", false, pause -> {});
    } catch (Stop e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void searchSendsJqlPagingAndExtraParams() throws IOException {
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.json("/rest/api/2/search", "{\"total\":3,\"issues\":[]}");
      JiraApi api = api(jira);
      assertThat(api.search("project = \"A B\"", 100, 50, "fields=*all").ok()).isTrue();
      FakeHttpServer.Request r = jira.requests().get(0);
      assertThat(r.param("jql")).isEqualTo("project = \"A B\"");
      assertThat(r.param("startAt")).isEqualTo("100");
      assertThat(r.param("maxResults")).isEqualTo("50");
      assertThat(r.param("fields")).isEqualTo("*all");
      assertThat(r.target()).doesNotContain("+");
      api.search("x", 0, 1, "");
      assertThat(jira.requests().get(1).target()).endsWith("maxResults=1");
    }
  }

  @Test
  void searchFallsBackToLatestWhenV2IsGone() throws IOException {
    for (int gone : new int[] {404, 410}) {
      try (FakeHttpServer jira = new FakeHttpServer()) {
        jira.on("/rest/api/2/search", r -> Response.status(gone, "{}"));
        jira.json("/rest/api/latest/search", "{\"total\":7}");
        assertThat(api(jira).count("created >= -30d")).isEqualTo(7);
      }
    }
  }

  @Test
  void countIsMinusOneWhenTheSearchFailsAndOtherErrorsDoNotFallBack() throws IOException {
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.on("/rest/api/2/search", r -> Response.status(400, "{}"));
      assertThat(api(jira).count("bad jql")).isEqualTo(-1);
      assertThat(jira.requests("/rest/api/latest/search")).isEmpty();
    }
  }

  @Test
  void statusCategoriesByIdAndEmptyWhenUnreadable() throws IOException {
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.json(
          "/rest/api/2/status",
          "[{\"id\":\"1\",\"statusCategory\":{\"key\":\"new\"}},{\"id\":\"3\"}]");
      assertThat(api(jira).statusCategories())
          .containsExactly(Map.entry("1", "new"), Map.entry("3", ""));
    }
    try (FakeHttpServer jira = new FakeHttpServer()) {
      assertThat(api(jira).statusCategories()).isEmpty();
    }
  }

  @Test
  void statusCategoriesParseAStatusListAlreadyRead() throws IOException {
    JsonNode statuses =
        new ObjectMapper()
            .readTree(
                "[{\"id\":\"6\",\"statusCategory\":{\"key\":\"done\"}},"
                    + "{\"id\":\"4\",\"statusCategory\":{}}]");
    assertThat(JiraApi.statusCategories(statuses))
        .containsOnly(Map.entry("6", "done"), Map.entry("4", ""));
  }

  @Test
  void theOtherEndpointsHitTheDocumentedPaths() throws IOException {
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.onPrefix("/rest/api/2/", r -> Response.json("{}"));
      JiraApi api = api(jira);
      api.serverInfo();
      api.myself();
      api.projects();
      api.project("AB C");
      api.versions("ABC");
      api.fields();
      api.issue("ABC-1", "expand=changelog");
      api.issue("ABC-2", "");
      assertThat(jira.requests().stream().map(FakeHttpServer.Request::target))
          .containsExactly(
              "/rest/api/2/serverInfo",
              "/rest/api/2/myself",
              "/rest/api/2/project",
              "/rest/api/2/project/AB%20C",
              "/rest/api/2/project/ABC/versions",
              "/rest/api/2/field",
              "/rest/api/2/issue/ABC-1?expand=changelog",
              "/rest/api/2/issue/ABC-2");
      assertThat(api.calls()).isEqualTo(8);
    }
  }
}
