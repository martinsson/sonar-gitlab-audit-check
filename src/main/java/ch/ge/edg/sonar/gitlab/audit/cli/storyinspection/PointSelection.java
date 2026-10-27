package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

/** L'option {@code --points} : des numéros et des intervalles, « 1,2,3 » ou « 1-15 ». */
final class PointSelection {

  private PointSelection() {}

  /** Les points demandés, triés par numéro. */
  static List<ChecklistPoint> parse(String option) throws Stop {
    List<Integer> numbers = new ArrayList<>();
    for (String part : option.split(",", -1)) {
      numbers.addAll(expand(part.trim()));
    }
    Collections.sort(numbers);
    return numbers.stream().map(ChecklistPoint::of).toList();
  }

  static List<Integer> numbers(List<ChecklistPoint> points) {
    return points.stream().map(ChecklistPoint::number).toList();
  }

  private static List<Integer> expand(String part) throws Stop {
    try {
      String[] bounds = part.split("-", -1);
      int start = Integer.parseInt(bounds[0].trim());
      int end = bounds.length > 1 ? Integer.parseInt(bounds[1].trim()) : start;
      int last = ChecklistPoint.values().length;
      if (start < 1 || end > last) {
        throw new Stop("--points : les points vont de 1 à " + last + " (« " + part + " »)");
      }
      return IntStream.rangeClosed(start, end).boxed().toList();
    } catch (NumberFormatException e) {
      throw new Stop("--points : « " + part + " » n'est pas un numéro ni un intervalle");
    }
  }
}
