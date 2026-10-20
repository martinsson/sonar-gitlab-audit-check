package ch.ge.edg.sonar.gitlab.audit.cli.jiraextract;

import static ch.ge.edg.sonar.gitlab.audit.testing.JiraFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import org.junit.jupiter.api.Test;

class FieldValueTest {

  @Test
  void absentAndNullAreEmpty() {
    assertThat(FieldValue.render(null)).isEmpty();
    assertThat(FieldValue.render(MissingNode.getInstance())).isEmpty();
    assertThat(FieldValue.render(NullNode.getInstance())).isEmpty();
  }

  @Test
  void scalarsAreTheirText() {
    assertThat(FieldValue.render(json("3.5"))).isEqualTo("3.5");
    assertThat(FieldValue.render(json("true"))).isEqualTo("true");
    assertThat(FieldValue.render(json("\"texte\""))).isEqualTo("texte");
  }

  @Test
  void aListJoinsItsValuesWithAPipe() {
    assertThat(FieldValue.render(json("[{\"name\":\"api\"},\"x\",null]"))).isEqualTo("api|x|");
    assertThat(FieldValue.render(json("[]"))).isEmpty();
  }

  @Test
  void anObjectShowsItsLabelInAFixedOrder() {
    assertThat(FieldValue.render(json("{\"name\":\"n\",\"value\":\"v\"}"))).isEqualTo("v");
    assertThat(FieldValue.render(json("{\"key\":\"k\",\"name\":\"n\"}"))).isEqualTo("n");
    assertThat(FieldValue.render(json("{\"displayName\":\"d\",\"key\":\"k\"}"))).isEqualTo("k");
    assertThat(FieldValue.render(json("{\"displayName\":\"Dev Un\",\"active\":true}")))
        .isEqualTo("Dev Un");
  }

  @Test
  void aCascadingSelectShowsParentAndChild() {
    assertThat(FieldValue.render(json("{\"value\":\"Prod\",\"child\":{\"value\":\"Site A\"}}")))
        .isEqualTo("Prod / Site A");
  }

  @Test
  void anObjectWithoutLabelIsItsJson() {
    assertThat(FieldValue.render(json("{\"id\":1,\"x\":\"y\"}")))
        .isEqualTo("{\"id\":1,\"x\":\"y\"}");
  }

  @Test
  void aJiraServerSprintIsReducedToItsName() {
    String sprint =
        "com.atlassian.greenhopper.service.sprint.Sprint@1f[id=3,rapidViewId=1,state=ACTIVE,"
            + "name=Sprint 3,goal=,startDate=2026-01-05]";
    assertThat(FieldValue.render(json("[\"" + sprint + "\"]"))).isEqualTo("Sprint 3");
    assertThat(FieldValue.render(json("\"com.atlassian.greenhopper.x[name=Last]\"")))
        .isEqualTo("Last");
  }

  @Test
  void textMentioningNameIsLeftAloneUnlessItIsASprint() {
    assertThat(FieldValue.render(json("\"name=foo,bar\""))).isEqualTo("name=foo,bar");
    assertThat(FieldValue.render(json("\"com.atlassian.greenhopper sans nom\"")))
        .isEqualTo("com.atlassian.greenhopper sans nom");
  }

  @Test
  void longTextIsCutAt300Characters() {
    String exact = "a".repeat(FieldValue.MAX_TEXT);
    assertThat(FieldValue.render(json("\"" + exact + "\""))).isEqualTo(exact);
    assertThat(FieldValue.render(json("\"" + exact + "bc\""))).isEqualTo(exact + "…");
  }
}
