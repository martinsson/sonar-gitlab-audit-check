package ch.ge.edg.sonar.gitlab.audit.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QueryTest {

  @Test
  void paramsDropNullValuesAndAnOddTail() {
    assertThat(Query.params("a", "1", "b", null, "c", "3", "dangling"))
        .containsExactly(Map.entry("a", "1"), Map.entry("c", "3"));
    assertThat(Query.params()).isEmpty();
  }

  @Test
  void noParamsGiveNoQuestionMark() {
    assertThat(Query.string(Map.of())).isEmpty();
    Map<String, String> onlyNulls = new LinkedHashMap<>();
    onlyNulls.put("a", null);
    assertThat(Query.string(onlyNulls)).isEmpty();
  }

  @Test
  void keysAndValuesAreEncodedInOrder() {
    Map<String, String> p = new LinkedHashMap<>();
    p.put("metric Keys", "a,b");
    p.put("q", "é&=");
    assertThat(Query.string(p)).isEqualTo("?metric+Keys=a%2Cb&q=%C3%A9%26%3D");
  }

  @Test
  void aPathSegmentEncodesSpacesAsPercentTwenty() {
    assertThat(Query.enc("a/b c")).isEqualTo("a%2Fb+c");
    assertThat(Query.segment("a b/c")).isEqualTo("a%20b%2Fc");
  }
}
