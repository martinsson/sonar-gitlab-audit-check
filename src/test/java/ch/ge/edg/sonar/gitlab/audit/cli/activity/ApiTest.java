package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.contributors.Contributors;
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

  private static JsonNode json(String s) throws Exception {
    return new ObjectMapper().readTree(s);
  }
}
