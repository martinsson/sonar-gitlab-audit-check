package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

/** Des stories inventées, sans rapport avec un vrai projet. */
final class StoryFixtures {

  static final String GOOD_DESCRIPTION =
      "En tant que citoyen, je veux déposer une demande. Le système doit valider le dossier.\n";

  private StoryFixtures() {}

  static Story story(String summary, String description) {
    return new Story("DEMO-1", summary, description, "Open", "Alice", "Story");
  }

  static Story described(String description) {
    return story("Déposer une demande en ligne", description);
  }

  /** Un ticket Jira tel que la recherche le rend, avec ce qu'il faut pour les points 1 à 6. */
  static String issueJson(String key, String summary, String description) {
    return "{\"key\":\""
        + key
        + "\",\"fields\":{\"summary\":\""
        + summary
        + "\",\"description\":\""
        + description
        + "\",\"status\":{\"name\":\"Open\"},\"assignee\":{\"displayName\":\"Alice\"},"
        + "\"issuetype\":{\"name\":\"Story\"}}}";
  }

  static String searchJson(int total, String... issues) {
    return "{\"total\":" + total + ",\"issues\":[" + String.join(",", issues) + "]}";
  }
}
