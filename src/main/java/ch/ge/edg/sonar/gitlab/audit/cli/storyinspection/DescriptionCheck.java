package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

import java.util.regex.Pattern;

/** Point 3 : la description existe, a une taille suffisante, un contexte et des exigences. */
final class DescriptionCheck implements Check {

  private static final int MIN_LENGTH = 50;
  private static final Pattern CONTEXT =
      Pattern.compile("(?i).*(?:en tant que|user|utilisateur|contexte|objectif).*");
  private static final Pattern REQUIREMENTS =
      Pattern.compile("(?i).*(?:doit|faut|require|need|should|will|va pouvoir|va devoir).*");

  @Override
  public Finding inspect(Story story) {
    String description = story.description();
    if (description.isEmpty()) {
      return Finding.nonRespecte("Description absente", "La story n'a pas de description");
    }
    if (description.length() < MIN_LENGTH) {
      return Finding.partiel(
          "Description courte (" + description.length() + " caractères)",
          "La description semble insuffisante");
    }
    String evidence = "Description: " + description.length() + " caractères";
    boolean hasContext = CONTEXT.matcher(description).matches();
    boolean hasRequirements = REQUIREMENTS.matcher(description).matches();
    if (hasContext && hasRequirements) {
      return Finding.respecte(evidence, "Description complète avec contexte et exigences");
    }
    return Finding.partiel(evidence, missing(hasContext, hasRequirements));
  }

  private static String missing(boolean hasContext, boolean hasRequirements) {
    String comment = "Description présente";
    if (!hasContext) comment += " - Ajouter le contexte métier";
    if (!hasRequirements) comment += " - Clarifier les exigences";
    return comment;
  }
}
