package ch.ge.edg.sonar.gitlab.audit.testing;

import ch.ge.edg.sonar.gitlab.audit.gocd.GocdApi;
import ch.ge.edg.sonar.gitlab.audit.rest.RestClient;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Responses captured from a real GoCD 25.3.0 (synthetic data: octocat/Hello-World built by
 * hello-build, deployed by hello-deploy-recette then hello-deploy-prod, whose manual stage fails;
 * other-build builds another repository). A file is named after its request path and query, every
 * non-alphanumeric run replaced by « _ ». Also small hand-made runs shaped like the captures, for
 * the cases the capture does not show.
 */
public final class GocdFixtures {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final PrintStream QUIET =
      new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);

  private GocdFixtures() {}

  /** A GoCD client on a fake server, without authentication. */
  public static GocdApi api(FakeHttpServer server) {
    try {
      return new GocdApi(
          new RestClient(RestClient.Settings.of(server.url(), Map.of(), false), QUIET));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static JsonNode node(String json) {
    try {
      return JSON.readTree(json);
    } catch (IOException e) {
      throw new IllegalArgumentException(json, e);
    }
  }

  /** A capture, by file name without « .json ». */
  public static String fixture(String name) {
    String file = "/fixtures/gocd-25.3/" + name + ".json";
    try (InputStream in = GocdFixtures.class.getResourceAsStream(file)) {
      if (in == null) throw new IllegalArgumentException("no fixture " + file);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static String nameOf(String target) {
    return target.replaceFirst("^/", "").replaceAll("[^A-Za-z0-9]+", "_");
  }

  /** Serves every captured GoCD request; anything else gets a 404, as GoCD would. */
  public static FakeHttpServer serve(FakeHttpServer gocd) {
    return gocd.onPrefix(
        "/go/api/",
        r -> {
          String name = nameOf(r.target());
          return GocdFixtures.class.getResource("/fixtures/gocd-25.3/" + name + ".json") == null
              ? Response.status(404, "{\"message\":\"not found\"}")
              : Response.json(fixture(name));
        });
  }

  public static final String HELLO = "https://github.com/octocat/Hello-World.git";

  /**
   * prod/1 took recette/3, prod/2 recette/5; recette/3, 4, 5 took build/6, 7, 9; build/8 is a
   * forced rebuild without change, build/9 brings c9 and c7 again. recette/2 is unknown.
   */
  public static void chain(FakeHttpServer gocd) {
    gocd.json("/go/api/pipelines/build/6", run("build", 6, 1, git(true, HELLO, "c6", "ABC-6 fix")));
    gocd.json("/go/api/pipelines/build/7", run("build", 7, 2, git(true, HELLO, "c7", "ABC-7")));
    gocd.json("/go/api/pipelines/build/8", run("build", 8, 3, git(false, HELLO, "c7", "")));
    gocd.json(
        "/go/api/pipelines/build/9",
        run("build", 9, 4, git(true, HELLO, "c9", "XYZ-9 ABC-7"), git(true, HELLO, "c7", "again")));
    gocd.json("/go/api/pipelines/recette/3", run("recette", 3, 5, upstream(true, "build", 6)));
    gocd.json("/go/api/pipelines/recette/4", run("recette", 4, 6, upstream(true, "build", 7)));
    gocd.json("/go/api/pipelines/recette/5", run("recette", 5, 7, upstream(true, "build", 9)));
    gocd.json(
        "/go/api/pipelines/prod/history",
        history(
            run("prod", 2, 9, upstream(true, "recette", 5)),
            run("prod", 1, 8, upstream(true, "recette", 3))));
  }

  // ---------------------------------------------------------------- hand-made runs

  /** A run: counter, scheduled time (epoch ms), forced or not, its material revisions. */
  public static String run(String name, int counter, long scheduled, String... materials) {
    return """
        {"name":"%s","counter":%d,"label":"%d","scheduled_date":%d,
         "build_cause":{"trigger_forced":false,"material_revisions":[%s]},
         "stages":[{"result":"Passed","name":"s","counter":"1","scheduled":true,
                    "approval_type":"success","jobs":[{"scheduled_date":%d}]}]}
        """
        .formatted(name, counter, counter, scheduled, String.join(",", materials), scheduled);
  }

  /** A git material revision. */
  public static String git(boolean changed, String url, String revision, String comment) {
    return """
        {"changed":%s,"material":{"type":"Git","description":"URL: %s, Branch: main"},
         "modifications":[{"revision":"%s","modified_time":1700000000000,"comment":"%s"}]}
        """
        .formatted(changed, url, revision, comment);
  }

  /** A dependency on an upstream run, « up/7 ». */
  public static String upstream(boolean changed, String pipeline, int counter) {
    return """
        {"changed":%s,"material":{"type":"Pipeline","description":"%s [ s ]"},
         "modifications":[{"revision":"%s/%d/s/1"}]}
        """
        .formatted(changed, pipeline, pipeline, counter);
  }

  /** A history page holding these runs, most recent first, with no next page. */
  public static String history(String... runs) {
    return "{\"pipelines\":[" + String.join(",", runs) + "]}";
  }

  /** A dashboard: group, then its pipelines, then the next group… */
  public static String dashboard(String group, String... pipelines) {
    StringBuilder names = new StringBuilder();
    for (String p : pipelines) {
      names.append(names.isEmpty() ? "" : ",").append('"').append(p).append('"');
    }
    return """
        {"_embedded":{"pipeline_groups":[{"name":"%s","pipelines":[%s]}]}}
        """
        .formatted(group, names);
  }
}
