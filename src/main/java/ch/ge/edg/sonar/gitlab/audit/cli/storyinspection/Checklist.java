package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

import java.util.List;

/**
 * Le contrôle de chaque point de la checklist. Les points 7 à 15 n'en ont pas encore : ils sont
 * rendus « sans objet », avec la mention qu'ils ne sont pas implémentés.
 */
final class Checklist {

  /** Les contrôles des points 1 à 6, dans l'ordre des points. */
  private static final List<Check> CHECKS =
      List.of(
          new TitleCheck(),
          new TypeCheck(),
          new DescriptionCheck(),
          new AcNumberingCheck(),
          new ContextAcCheck(),
          new AcFormCheck());

  private Checklist() {}

  static InspectionPoint inspect(ChecklistPoint point, Story story) {
    int index = point.number() - 1;
    Finding finding =
        index < CHECKS.size()
            ? CHECKS.get(index).inspect(story)
            : Finding.notImplemented(point.number());
    return new InspectionPoint(
        point.number(), finding.verdict(), finding.evidence(), finding.comment());
  }
}
