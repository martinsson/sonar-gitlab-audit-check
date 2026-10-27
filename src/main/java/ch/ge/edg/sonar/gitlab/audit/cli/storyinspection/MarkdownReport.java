package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

import java.util.List;
import java.util.stream.Collectors;

/** Le rapport Markdown d'une inspection : une section par story, puis le bilan du projet. */
final class MarkdownReport {

  private MarkdownReport() {}

  /** Le rapport de {@code results}, qui ne peut pas être vide : le projet vient de leurs clés. */
  static String render(List<StoryResult> results, List<ChecklistPoint> points) {
    String project = results.get(0).project();
    String checked =
        points.stream().map(p -> String.valueOf(p.number())).collect(Collectors.joining(", "));
    return "# Rapport d'Inspection - Projet "
        + project
        + "\n\n## Points de contrôle vérifiés: "
        + checked
        + "\n\n---\n\n"
        + results.stream().map(r -> new StoryReport(r).render()).collect(Collectors.joining())
        + new ProjectReport(results).render();
  }
}
