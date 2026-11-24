package ch.ge.edg.sonar.gitlab.audit.cli.activity.model;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.contributors.Contributors;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ApiTest {

  @Test
  void parentIdsAreReadFromTheCommit() throws Exception {
    JsonNode merge = json("{\"parent_ids\":[\"a\",\"b\"]}");
    assertThat(Api.parentIds(merge)).containsExactly("a", "b");
    assertThat(Contributors.isMerge(Api.parentIds(merge))).isTrue();
  }

  @Test
  void aCommitWithoutParentsIsNotAMerge() throws Exception {
    assertThat(Contributors.isMerge(Api.parentIds(json("{\"parent_ids\":[\"a\"]}")))).isFalse();
    assertThat(Api.parentIds(json("{\"parent_ids\":[]}"))).isEmpty();
    assertThat(Api.parentIds(json("{}"))).isEmpty();
  }

  @Test
  void textReadsAFieldAsTextOrEmpty() throws Exception {
    var n = Gitlab.READER.readTree("{\"a\":\"x\",\"b\":3,\"c\":null}");
    assertThat(Api.text(n, "a")).isEqualTo("x");
    assertThat(Api.text(n, "b")).isEqualTo("3");
    assertThat(Api.text(n, "c")).isEmpty();
    assertThat(Api.text(n, "missing")).isEmpty();
    assertThat(Api.text(null, "a")).isEmpty();
  }

  private static JsonNode json(String s) throws Exception {
    return new ObjectMapper().readTree(s);
  }
}
