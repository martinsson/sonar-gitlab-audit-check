package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/** Point 4 : les critères d'acceptation (AC) sont numérotés, sans trou. */
final class AcNumberingCheck implements Check {

  private static final Pattern AC_NUMBER =
      Pattern.compile("AC\\s*[.:\\s]?(\\d+)", Pattern.CASE_INSENSITIVE);

  @Override
  public Finding inspect(Story story) {
    if (story.description().isEmpty()) {
      return Finding.sansDescription("Non applicable - pas de description");
    }
    List<Integer> numbers = numbers(story.description());
    if (numbers.isEmpty()) {
      return Finding.partiel("Aucun AC trouvé", "Pas de critères d'acceptation numérotés");
    }
    int count = numbers.size();
    if (count == 1) {
      return Finding.respecte(count + " AC trouvé(s)", "Numérotation OK");
    }
    if (sequential(numbers)) {
      return Finding.respecte(count + " AC numérotés séquentiellement", "Numérotation correcte");
    }
    return Finding.partiel(
        count + " AC mais numérotation non séquentielle", "Vérifier la numérotation des AC");
  }

  /** Les numéros des AC, dans l'ordre où le texte les cite. */
  static List<Integer> numbers(String description) {
    List<Integer> numbers = new ArrayList<>();
    Matcher matcher = AC_NUMBER.matcher(description);
    while (matcher.find()) {
      numbers.add(Integer.parseInt(matcher.group(1)));
    }
    return numbers;
  }

  /** Une fois triés, chaque numéro suit le précédent de un : ni trou ni doublon. */
  static boolean sequential(List<Integer> numbers) {
    List<Integer> sorted = new ArrayList<>(numbers);
    Collections.sort(sorted);
    return IntStream.range(1, sorted.size()).allMatch(i -> sorted.get(i) == sorted.get(i - 1) + 1);
  }
}
