package ch.ge.edg.sonar.gitlab.audit.cli.jiraextract;

import static ch.ge.edg.sonar.gitlab.audit.cli.jiraextract.JiraFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.ge.edg.sonar.gitlab.audit.jira.JiraApi;
import ch.ge.edg.sonar.gitlab.audit.rest.RestClient;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IssuePagesTest {

  private static final PrintStream QUIET =
      new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);

  private static JiraSource source(FakeHttpServer jira) throws IOException {
    return new JiraSource(
        new JiraApi(new RestClient(RestClient.Settings.of(jira.url(), Map.of(), false), QUIET)));
  }

  @Test
  void the_default_query_quotes_each_project() {
    assertThat(IssuePages.defaultJql(List.of("ABC", "X Y"), 7))
        .isEqualTo("project in (\"ABC\",\"X Y\") AND updated >= -7d ORDER BY key ASC");
  }

  @Test
  void an_issue_whose_changelog_is_whole_or_absent_is_not_fetched_again() throws Exception {
    try (FakeHttpServer jira = new FakeHttpServer()) {
      IssuePages pages = new IssuePages(source(jira), QUIET);
      for (String issue :
          List.of(
              "{\"key\":\"A-1\"}",
              "{\"key\":\"A-1\",\"changelog\":{\"total\":2,\"histories\":[{},{}]}}",
              "{\"key\":\"A-1\",\"changelog\":{\"total\":0,\"histories\":[]}}")) {
        JsonNode node = json(issue);
        assertThat(pages.complete(node)).isSameAs(node);
      }
      assertThat(jira.requests()).isEmpty();
    }
  }

  @Test
  void a_truncated_changelog_is_read_again_from_the_issue_alone() throws Exception {
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.json("/rest/api/2/issue/A-1", "{\"key\":\"A-1\",\"full\":true}");
      IssuePages pages = new IssuePages(source(jira), QUIET);
      JsonNode truncated = json("{\"key\":\"A-1\",\"changelog\":{\"total\":3,\"histories\":[{}]}}");
      assertThat(pages.complete(truncated).path("full").asBoolean()).isTrue();
    }
  }

  @Test
  void pages_follow_start_at_until_the_total_and_report_progress() throws Exception {
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.on(
          "/rest/api/2/search",
          r ->
              FakeHttpServer.Response.json(
                  "0".equals(r.param("startAt"))
                      ? "{\"total\":3,\"issues\":[{\"key\":\"A-1\"},{\"key\":\"A-2\"}]}"
                      : "{\"total\":3,\"issues\":[{\"key\":\"A-3\"}]}"));
      ByteArrayOutputStream progress = new ByteArrayOutputStream();
      List<String> keys = new ArrayList<>();
      new IssuePages(source(jira), new PrintStream(progress, true, StandardCharsets.UTF_8))
          .each("q", i -> keys.add(i.path("key").asText()));
      assertThat(keys).containsExactly("A-1", "A-2", "A-3");
      assertThat(progress.toString(StandardCharsets.UTF_8))
          .isEqualTo("  2 / 3 tickets%n  3 / 3 tickets%n".formatted());
      assertThat(jira.requests("/rest/api/2/search")).hasSize(2);
    }
  }

  @Test
  void a_search_without_total_reads_one_page() throws Exception {
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.json("/rest/api/2/search", "{\"issues\":[{\"key\":\"A-1\"}]}");
      List<String> keys = new ArrayList<>();
      new IssuePages(source(jira), QUIET).each("q", i -> keys.add(i.path("key").asText()));
      assertThat(keys).containsExactly("A-1");
      assertThat(jira.requests()).hasSize(1);
    }
  }

  @Test
  void a_search_error_with_an_empty_body_has_no_trailing_space() throws Exception {
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.on("/rest/api/2/search", r -> FakeHttpServer.Response.status(500, ""));
      assertThatThrownBy(() -> source(jira).search("q", 0))
          .isInstanceOf(ExtractionStop.class)
          .hasMessage("search : HTTP 500");
    }
  }

  @Test
  void the_error_excerpt_is_flattened_then_cut_at_300() {
    assertThat(JiraSource.excerpt("a\n\n  b\tc")).isEqualTo("a b c");
    String body = "x ".repeat(200) + "\n".repeat(400);
    assertThat(JiraSource.excerpt(body)).hasSize(300).isEqualTo("x ".repeat(150));
    assertThat(JiraSource.excerpt("y".repeat(301))).isEqualTo("y".repeat(300));
  }

  @Test
  void a_required_value_is_trimmed() throws ExtractionStop {
    assertThat(JiraSource.require("  http://jira  ", "JIRA_URL")).isEqualTo("http://jira");
    assertThatThrownBy(() -> JiraSource.require(null, "JIRA_TOKEN"))
        .hasMessage("JIRA_TOKEN manquant");
  }

  @Test
  void a_cell_keeps_to_one_line() {
    assertThat(CountedCsv.flat(List.of("a\r\nb", "c\nd", "e"))).containsExactly("a  b", "c d", "e");
  }
}
