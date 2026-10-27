package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

/** Point 2 : le ticket est bien de type Story. */
final class TypeCheck implements Check {

  @Override
  public Finding inspect(Story story) {
    String evidence = "Type: " + story.issueType();
    if ("Story".equals(story.issueType())) {
      return Finding.respecte(evidence, "Type correct pour une User Story");
    }
    return Finding.partiel(evidence, "Le type ne correspond pas à une User Story standard");
  }
}
