package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Point 6 : les AC sont testables et évitent les termes vagues. */
final class AcFormCheck implements Check {

  private static final Pattern AC_LINE =
      Pattern.compile("(AC\\s*[.:\\s]?\\d+[.)]?\\s*.+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern VAGUE =
      Pattern.compile(
          "\\b(?:bien|rapidement|facilement|correctement|appropriément|good|fast|easy)\\b",
          Pattern.DOTALL);
  private static final Pattern TESTABLE =
      Pattern.compile(
          "\\b(?:si|lorsque|quand|then|alors|vérifier|validate|test|should|must)\\b",
          Pattern.DOTALL);

  @Override
  public Finding inspect(Story story) {
    if (story.description().isEmpty()) {
      return Finding.sansDescription("Non applicable");
    }
    List<String> criteria = criteria(story.description());
    if (criteria.isEmpty()) {
      return Finding.partiel("Aucun AC trouvé", "Pas de critères d'acceptation à évaluer");
    }
    String evidence = criteria.size() + " AC analysés";
    boolean vague = criteria.stream().anyMatch(ac -> VAGUE.matcher(ac).find());
    boolean testable = criteria.stream().anyMatch(ac -> TESTABLE.matcher(ac).find());
    if (testable && !vague) {
      return Finding.respecte(evidence, "AC bien rédigés et testables");
    }
    return Finding.partiel(evidence, shortcomings(testable, vague));
  }

  /** Le texte de chaque AC, en minuscules, de son numéro à la fin de sa ligne. */
  private static List<String> criteria(String description) {
    List<String> criteria = new ArrayList<>();
    Matcher matcher = AC_LINE.matcher(description);
    while (matcher.find()) {
      criteria.add(matcher.group(1).toLowerCase(Locale.ROOT));
    }
    return criteria;
  }

  private static String shortcomings(boolean testable, boolean vague) {
    String comment = "AC ";
    if (testable) comment += "testables ";
    if (vague) comment += "mais termes vagues détectés ";
    if (!testable) comment += "pourraient être plus testables";
    return comment.trim();
  }
}
