package ch.ge.edg.sonar.gitlab.audit.cli.jiraextract;

import static ch.ge.edg.sonar.gitlab.audit.testing.JiraFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import org.junit.jupiter.api.Test;

class FieldValueTest {

  @Test
  void absent_and_null_are_empty() {
    assertThat(FieldValue.render(null)).isEmpty();
    assertThat(FieldValue.render(MissingNode.getInstance())).isEmpty();
    assertThat(FieldValue.render(NullNode.getInstance())).isEmpty();
  }

  @Test
  void scalars_are_their_text() {
    assertThat(FieldValue.render(json("3.5"))).isEqualTo("3.5");
    assertThat(FieldValue.render(json("true"))).isEqualTo("true");
    assertThat(FieldValue.render(json("\"texte\""))).isEqualTo("texte");
  }

  @Test
  void a_list_joins_its_values_with_a_pipe() {
    assertThat(FieldValue.render(json("[{\"name\":\"api\"},\"x\",null]"))).isEqualTo("api|x|");
    assertThat(FieldValue.render(json("[]"))).isEmpty();
  }

  @Test
  void an_object_shows_its_label_in_a_fixed_order() {
    assertThat(FieldValue.render(json("{\"name\":\"n\",\"value\":\"v\"}"))).isEqualTo("v");
    assertThat(FieldValue.render(json("{\"key\":\"k\",\"name\":\"n\"}"))).isEqualTo("n");
    assertThat(FieldValue.render(json("{\"displayName\":\"d\",\"key\":\"k\"}"))).isEqualTo("k");
    assertThat(FieldValue.render(json("{\"displayName\":\"Dev Un\",\"active\":true}")))
        .isEqualTo("Dev Un");
  }

  @Test
  void a_cascading_select_shows_parent_and_child() {
    assertThat(FieldValue.render(json("{\"value\":\"Prod\",\"child\":{\"value\":\"Site A\"}}")))
        .isEqualTo("Prod / Site A");
  }

  @Test
  void an_object_without_label_is_its_json() {
    assertThat(FieldValue.render(json("{\"id\":1,\"x\":\"y\"}")))
        .isEqualTo("{\"id\":1,\"x\":\"y\"}");
  }

  @Test
  void a_jira_server_sprint_is_reduced_to_its_name() {
    String sprint =
        "com.atlassian.greenhopper.service.sprint.Sprint@1f[id=3,rapidViewId=1,state=ACTIVE,"
            + "name=Sprint 3,goal=,startDate=2026-01-05]";
    assertThat(FieldValue.render(json("[\"" + sprint + "\"]"))).isEqualTo("Sprint 3");
    assertThat(FieldValue.render(json("\"com.atlassian.greenhopper.x[name=Last]\"")))
        .isEqualTo("Last");
  }

  @Test
  void text_mentioning_name_is_left_alone_unless_it_is_a_sprint() {
    assertThat(FieldValue.render(json("\"name=foo,bar\""))).isEqualTo("name=foo,bar");
    assertThat(FieldValue.render(json("\"com.atlassian.greenhopper sans nom\"")))
        .isEqualTo("com.atlassian.greenhopper sans nom");
  }

  @Test
  void long_text_is_cut_at_300_characters() {
    String exact = "a".repeat(FieldValue.MAX_TEXT);
    assertThat(FieldValue.render(json("\"" + exact + "\""))).isEqualTo(exact);
    assertThat(FieldValue.render(json("\"" + exact + "bc\""))).isEqualTo(exact + "…");
  }
}
