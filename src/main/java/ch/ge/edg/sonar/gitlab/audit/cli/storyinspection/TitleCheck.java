package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

/** Point 1 : le titre existe et est assez descriptif. */
final class TitleCheck implements Check {

  private static final int MIN_LENGTH = 10;

  @Override
  public Finding inspect(Story story) {
    String title = story.summary();
    if (title.isEmpty()) {
      return Finding.nonRespecte("Titre vide", "La story n'a pas de titre");
    }
    if (title.length() < MIN_LENGTH) {
      return Finding.partiel(
          "Titre trop court: '" + title + "'", "Le titre devrait être plus descriptif");
    }
    return Finding.respecte("Titre: '" + title + "'", "Titre présent et de longueur raisonnable");
  }
}
