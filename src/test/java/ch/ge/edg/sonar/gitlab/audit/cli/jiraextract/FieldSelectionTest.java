package ch.ge.edg.sonar.gitlab.audit.cli.jiraextract;

import static ch.ge.edg.sonar.gitlab.audit.testing.JiraFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FieldSelectionTest {

  private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
  private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

  private String err() {
    return errBytes.toString(StandardCharsets.UTF_8);
  }

  private static String field(String id, String name, boolean custom, String schemaCustom) {
    return "{\"id\":\"%s\",\"name\":\"%s\",\"custom\":%s,\"schema\":{\"custom\":\"%s\"}}"
        .formatted(id, name, custom, schemaCustom);
  }

  private FieldSelection of(List<String> wanted, String... fields) {
    return FieldSelection.of(json("[" + String.join(",", fields) + "]"), wanted, err);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Environnement",
        "Plateforme",
        "Target platform",
        "Sévérité",
        "Severite",
        "Severity",
        "Gravité",
        "Impact métier",
        "Incident lié",
        "Cause racine",
        "Origine",
        "Détecté par",
        "Detection"
      })
  void custom_fields_named_like_platform_or_severity_are_taken(String name) {
    FieldSelection s = of(List.of(), field("cf_1", name, true, "x:select"));
    assertThat(s.headers()).containsExactly(name + " [cf_1]");
  }

  @Test
  void system_fields_and_other_names_are_not_taken() {
    FieldSelection s =
        of(
            List.of(),
            field("environment", "Environment", false, ""),
            field("cf_2", "Story Points", true, "x:float"));
    assertThat(s.headers()).isEmpty();
    s.announce(err);
    assertThat(err()).isEmpty();
  }

  @Test
  void wanted_fields_by_id_or_by_name_whatever_the_case_unknown_ones_are_ignored() {
    FieldSelection s =
        of(
            List.of("sprint", "cf_3", "nope", "cf_1"),
            field("cf_1", "Sévérité", true, ""),
            field("cf_3", "Story Points", true, ""),
            field("cf_4", "Sprint", true, ""),
            field("cf_5", "SPRINT", true, ""));
    assertThat(s.headers())
        .containsExactly("Sévérité [cf_1]", "Sprint [cf_4]", "Story Points [cf_3]");
    assertThat(err()).isEqualTo("Champ inconnu, ignoré : nope\n");
    s.announce(err);
    assertThat(err()).endsWith("Champs en plus : Sévérité, Sprint, Story Points\n");
  }

  @Test
  void an_id_wins_over_a_name() {
    FieldSelection s =
        of(List.of("cf_9"), field("cf_1", "cf_9", true, ""), field("cf_9", "Autre", true, ""));
    assertThat(s.headers()).containsExactly("Autre [cf_9]");
  }

  @Test
  void cells_render_the_selected_fields_in_header_order() {
    FieldSelection s =
        of(List.of("cf_2"), field("cf_1", "Sévérité", true, ""), field("cf_2", "Points", true, ""));
    assertThat(s.cells(json("{\"cf_2\":5,\"cf_1\":{\"value\":\"Haute\"}}")))
        .containsExactly("Haute", "5");
    assertThat(s.cells(json("{}"))).containsExactly("", "");
  }

  @Test
  void the_epic_link_is_found_by_its_schema_whatever_its_id() {
    FieldSelection s =
        of(
            List.of(),
            field("cf_7", "Lien d'épopée", true, "com.pyxis.greenhopper.jira:gh-epic-link"),
            field("cf_8", "Epic Name", true, "com.pyxis.greenhopper.jira:gh-epic-label"));
    assertThat(s.epic(json("{\"cf_7\":\"ABC-9\",\"cf_8\":\"Nom\"}"))).isEqualTo("ABC-9");
    assertThat(s.headers()).isEmpty();
  }

  @Test
  void no_epic_link_field_means_an_empty_epic() {
    FieldSelection s = of(List.of(), field("cf_8", "Epic Name", true, "x:gh-epic-label"));
    assertThat(s.epic(json("{\"cf_8\":\"Nom\"}"))).isEmpty();
  }
}
