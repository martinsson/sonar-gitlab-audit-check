package ch.ge.edg.sonar.gitlab.audit.cli.doralocate;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GocdIndexTest {

  static JsonNode json(String s) {
    try {
      return GocdIndex.JSON.readTree(s);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static GocdIndex.Pipeline p(List<String> git, List<String> upstream) {
    return new GocdIndex.Pipeline("g", git, upstream, "");
  }

  @Test
  void theIndexFileRoundTripsAndOmitsAnUnknownLastRun() {
    Map<String, GocdIndex.Pipeline> pipes = new LinkedHashMap<>();
    pipes.put("b", new GocdIndex.Pipeline("g", List.of("git@h:o/r.git"), List.of(), "2026-01-01"));
    pipes.put("a", p(List.of(), List.of("b")));
    GocdIndex index = new GocdIndex("hier", pipes);
    String file = index.toJson().toString();
    assertThat(file)
        .isEqualTo(
            "{\"built_at\":\"hier\",\"pipelines\":{"
                + "\"b\":{\"group\":\"g\",\"git\":[\"git@h:o/r.git\"],\"upstream\":[],"
                + "\"last_run\":\"2026-01-01\"},"
                + "\"a\":{\"group\":\"g\",\"git\":[],\"upstream\":[\"b\"]}}}");
    GocdIndex back = GocdIndex.fromJson(json(file));
    assertThat(back).isEqualTo(index);
    assertThat(back.pipelines().keySet()).containsExactly("b", "a");
    assertThat(back.unknown()).isZero();
    assertThat(back.pipeline("nope").group()).isEmpty();
  }

  @Test
  void aPipelineWithoutMaterialIsUnknown() {
    assertThat(p(List.of(), List.of()).unknown()).isTrue();
    assertThat(p(List.of("u"), List.of()).unknown()).isFalse();
    assertThat(p(List.of(), List.of("u")).unknown()).isFalse();
    assertThat(GocdIndex.fromJson(json("{}")).pipelines()).isEmpty();
  }

  @Test
  void downstreamPipelinesAreFoundLevelByLevelEvenThroughACycle() {
    Map<String, GocdIndex.Pipeline> pipes = new LinkedHashMap<>();
    pipes.put("prod", p(List.of(), List.of("rec")));
    pipes.put("rec", p(List.of(), List.of("build", "prod")));
    pipes.put("build", p(List.of("ssh://git@h:22/Octo/Repo.git"), List.of()));
    pipes.put("other", p(List.of("https://h/octo/repo-two.git"), List.of("other")));
    pipes.put("side", p(List.of(), List.of("build")));
    Map<String, Integer> depth = RepoPipelines.of(new GocdIndex("", pipes), "octo/repo");
    assertThat(depth)
        .containsExactly(
            Map.entry("build", 0), Map.entry("rec", 1), Map.entry("side", 1), Map.entry("prod", 2));
    assertThat(RepoPipelines.of(new GocdIndex("", pipes), "nobody/none")).isEmpty();
  }
}
