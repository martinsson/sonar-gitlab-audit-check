package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

import java.util.regex.Pattern;

/** Point 5 : un AC décrit le contexte global (scénario, parcours utilisateur). */
final class ContextAcCheck implements Check {

  private static final Pattern CONTEXT_AC =
      Pattern.compile(
          "(?i).*(?:AC\\s*\\d+.*(?:contexte|scénario|utilisateur|utilisateur final|flux|workflow"
              + "|processus|journey)).*");

  @Override
  public Finding inspect(Story story) {
    if (story.description().isEmpty()) {
      return Finding.sansDescription("Non applicable");
    }
    if (CONTEXT_AC.matcher(story.description()).matches()) {
      return Finding.respecte("AC de contexte trouvé", "Un AC décrit le contexte global");
    }
    return Finding.nonRespecte(
        "Aucun AC de contexte détecté", "Ajouter un AC décrivant le scénario métier complet");
  }
}
