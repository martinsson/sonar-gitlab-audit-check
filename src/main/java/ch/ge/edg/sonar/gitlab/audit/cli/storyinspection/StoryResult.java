package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

import java.util.List;

/** Ce que la checklist conclut pour une story. */
record StoryResult(
    String key,
    String summary,
    String status,
    String assignee,
    List<InspectionPoint> inspectionPoints) {

  /** La story passée au crible des points demandés. */
  static StoryResult of(Story story, List<ChecklistPoint> points) {
    List<InspectionPoint> results = points.stream().map(p -> Checklist.inspect(p, story)).toList();
    return new StoryResult(story.key(), story.summary(), story.status(), story.assignee(), results);
  }

  /** La clé du projet, avant le tiret de la clé du ticket. */
  String project() {
    int dash = key.indexOf('-');
    return dash < 0 ? key : key.substring(0, dash);
  }

  Tally tally() {
    return Tally.of(inspectionPoints.stream());
  }

  /** Les points non respectés parmi ceux qui comptent. */
  int criticalCount() {
    return counted(Verdict.NON_RESPECTE);
  }

  /** Les points partiellement respectés parmi ceux qui comptent. */
  int minorCount() {
    return counted(Verdict.PARTIEL);
  }

  private int counted(Verdict verdict) {
    return (int)
        inspectionPoints.stream()
            .filter(ip -> ip.point() <= ChecklistPoint.LAST_COUNTED && ip.verdict() == verdict)
            .count();
  }
}
