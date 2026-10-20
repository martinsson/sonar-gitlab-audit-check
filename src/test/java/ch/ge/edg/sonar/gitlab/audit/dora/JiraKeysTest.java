package ch.ge.edg.sonar.gitlab.audit.dora;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JiraKeysTest {

  @Test
  void keys_between_separators_in_order_without_duplicates() {
    assertThat(JiraKeys.in("ABC-12 fix (XYZ-3), merge ABC-12; see [AB_C-9]"))
        .containsExactly("ABC-12", "XYZ-3", "AB_C-9");
  }

  @Test
  void look_alikes_are_rejected_or_left_for_jira_to_refuse() {
    assertThat(JiraKeys.in("abc-12 A-1 ABC-0 ABC-012 XABC-1x feature/ABC-7-b ABC-12345678"))
        .containsExactly("ABC-7");
    assertThat(JiraKeys.in("x-ABC-5")).isEmpty();
    assertThat(JiraKeys.in("ABC-5-")).containsExactly("ABC-5");
    assertThat(JiraKeys.in("UTF-8")).containsExactly("UTF-8");
    assertThat(JiraKeys.in("ABCDEFGHIJKLMNOP-1")).isEmpty();
    assertThat(JiraKeys.in("ABCDEFGHIJKLMNO-1")).containsExactly("ABCDEFGHIJKLMNO-1");
    assertThat(JiraKeys.in(null)).isEmpty();
  }

  @Test
  void prefix_is_the_project() {
    assertThat(JiraKeys.prefix("AB-CD-12")).isEqualTo("AB-CD");
    assertThat(JiraKeys.prefix("NOKEY")).isEqualTo("NOKEY");
  }
}
