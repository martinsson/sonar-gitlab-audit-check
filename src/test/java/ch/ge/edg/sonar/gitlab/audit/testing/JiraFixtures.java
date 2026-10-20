package ch.ge.edg.sonar.gitlab.audit.testing;

import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * The hand-written Jira responses under fixtures/jira-handmade/extract, and a fake Jira that serves
 * them: project ABC with three issues over two search pages (ABC-2's changelog truncated by the
 * search), project XYZ without readable versions.
 */
public final class JiraFixtures {

  private static final ObjectMapper JSON = new ObjectMapper();

  private JiraFixtures() {}

  public static String read(String name) {
    String path = "/fixtures/jira-handmade/extract/" + name;
    try (InputStream in =
        Objects.requireNonNull(JiraFixtures.class.getResourceAsStream(path), path)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static JsonNode json(String text) {
    try {
      return JSON.readTree(text);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The whole fake: fields, statuses, both search pages, ABC-2 alone, ABC's versions. */
  public static FakeHttpServer jira() {
    return serve(new FakeHttpServer());
  }

  /**
   * Adds the fixture routes to {@code jira}. The first route registered for a path wins: register
   * an override (a failure, say) before calling this.
   */
  public static FakeHttpServer serve(FakeHttpServer jira) {
    jira.json("/rest/api/2/field", read("field.json"));
    jira.json("/rest/api/2/status", read("status.json"));
    jira.on(
        "/rest/api/2/search",
        r ->
            Response.json(
                read("2".equals(r.param("startAt")) ? "search-2.json" : "search-0.json")));
    jira.json("/rest/api/2/issue/ABC-2", read("issue-ABC-2.json"));
    jira.json("/rest/api/2/project/ABC/versions", read("versions-ABC.json"));
    return jira;
  }
}
