package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

import java.util.stream.Collectors;

/** La section Markdown d'une story : ses points, sa synthèse et les actions à mener. */
final class StoryReport {

  private final StoryResult result;
  private final Tally tally;

  StoryReport(StoryResult result) {
    this.result = result;
    this.tally = result.tally();
  }

  String render() {
    return "## Story : "
        + result.key()
        + " - "
        + result.summary()
        + "\n\n- **Statut actuel :** "
        + result.status()
        + "\n- **Assigné à :** "
        + result.assignee()
        + "\n\n### Résultats d'inspection\n\n"
        + "| # | Point de contrôle | Statut | Preuve | Commentaires |\n"
        + "|---|------------------|--------|--------|--------------|\n"
        + rows()
        + "\n### Synthèse\n"
        + synthesis()
        + "### Actions recommandées\n"
        + actions()
        + "\n---\n\n";
  }

  private String rows() {
    return result.inspectionPoints().stream().map(StoryReport::row).collect(Collectors.joining());
  }

  private static String row(InspectionPoint ip) {
    return "| "
        + ip.point()
        + " | "
        + ChecklistPoint.of(ip.point()).label()
        + " | "
        + ip.verdict().symbol()
        + " | "
        + ip.evidence()
        + " | "
        + ip.comment()
        + " |\n";
  }

  private String synthesis() {
    int size = result.inspectionPoints().size();
    int passed = tally.of(Verdict.RESPECTE);
    int checked = size - tally.of(Verdict.SANS_OBJET);
    int score = checked > 0 ? (int) Math.round((double) passed / checked * 100) : 0;
    return "- **Points respectés :** "
        + passed
        + "/"
        + size
        + "\n- **Points partiellement respectés :** "
        + tally.of(Verdict.PARTIEL)
        + "\n- **Points non respectés :** "
        + tally.of(Verdict.NON_RESPECTE)
        + "\n- **Points non applicables :** "
        + tally.of(Verdict.SANS_OBJET)
        + "\n- **Score de qualité :** "
        + score
        + "%\n\n";
  }

  private String actions() {
    if (tally.problems() == 0) {
      return "Aucune action requise - Story conforme\n";
    }
    return result.inspectionPoints().stream()
        .filter(ip -> ip.verdict() == Verdict.NON_RESPECTE || ip.verdict() == Verdict.PARTIEL)
        .map(ip -> "- [" + ChecklistPoint.of(ip.point()).label() + "] " + ip.comment() + "\n")
        .collect(Collectors.joining());
  }
}
