package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

import com.fasterxml.jackson.databind.JsonNode;

/** Ce que les points de contrôle lisent d'un ticket Jira : ses champs utiles, jamais absents. */
record Story(
    String key,
    String summary,
    String description,
    String status,
    String assignee,
    String issueType) {

  static Story from(JsonNode issue) {
    JsonNode fields = issue.path("fields");
    return new Story(
        issue.path("key").asText(),
        fields.path("summary").asText(""),
        fields.path("description").asText(""),
        fields.path("status").path("name").asText(""),
        fields.path("assignee").path("displayName").asText("Unassigned"),
        fields.path("issuetype").path("name").asText(""));
  }
}
