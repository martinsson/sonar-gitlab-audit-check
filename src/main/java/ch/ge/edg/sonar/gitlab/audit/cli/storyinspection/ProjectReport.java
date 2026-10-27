package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Le bilan Markdown de toutes les stories du projet : conformité et répartition par point. */
final class ProjectReport {

  private static final int WATCH_LIMIT = 5;

  private static final String RECOMMENDATIONS =
      """
      1. Former les équipes aux conventions de titrage et description de stories
      2. Vérifier que chaque story contient au moins un AC de contexte
      3. Améliorer la qualité des AC en évitant les termes vagues
      4. Mettre en place une revue par les pairs avant le passage en 'Ready'
      5. Utiliser des templates pour la description et les AC
      """;

  private final List<StoryResult> results;
  private final Conformity conformity;
  private final Map<ChecklistPoint, Tally> byPoint = new EnumMap<>(ChecklistPoint.class);

  ProjectReport(List<StoryResult> results) {
    this.results = results;
    this.conformity = Conformity.of(results);
    for (ChecklistPoint point : ChecklistPoint.values()) {
      byPoint.put(
          point,
          Tally.of(
              results.stream()
                  .flatMap(r -> r.inspectionPoints().stream())
                  .filter(ip -> ip.point() == point.number())));
    }
  }

  String render() {
    return "## Rapport global d'inspection - Projet "
        + results.get(0).project()
        + "\n\n- **Total de stories inspectées :** "
        + conformity.total()
        + "\n"
        + share("Stories 100% conformes", conformity.conform())
        + share("Stories avec non-conformités critiques", conformity.critical())
        + share("Stories avec non-conformités mineures", conformity.minor())
        + "\n### Répartition par point de contrôle\n\n"
        + distribution()
        + "\n### Points de vigilance majeurs\n"
        + watchList()
        + "\n### Recommandations prioritaires\n"
        + RECOMMENDATIONS;
  }

  private String share(String label, long count) {
    return "- **" + label + " :** " + count + " (" + conformity.percent(count) + "%)\n";
  }

  private String distribution() {
    String header =
        "| Point de contrôle | "
            + Verdict.RESPECTE.symbol()
            + " Respecté | "
            + Verdict.PARTIEL.symbol()
            + " Partiel | "
            + Verdict.NON_RESPECTE.symbol()
            + " Non respecté | "
            + Verdict.SANS_OBJET.symbol()
            + " N/A |\n"
            + "|------------------|------------|-----------|----------------|--------|\n";
    return header
        + byPoint.entrySet().stream()
            .map(e -> distributionRow(e.getKey(), e.getValue()))
            .collect(Collectors.joining());
  }

  private static String distributionRow(ChecklistPoint point, Tally tally) {
    return "| "
        + point.label()
        + " | "
        + tally.of(Verdict.RESPECTE)
        + " | "
        + tally.of(Verdict.PARTIEL)
        + " | "
        + tally.of(Verdict.NON_RESPECTE)
        + " | "
        + tally.of(Verdict.SANS_OBJET)
        + " |\n";
  }

  /** Les cinq points qui posent le plus de problèmes, du pire au moins mauvais. */
  private String watchList() {
    return byPoint.entrySet().stream()
        .filter(e -> e.getValue().problems() > 0)
        .sorted((a, b) -> b.getValue().problems() - a.getValue().problems())
        .limit(WATCH_LIMIT)
        .map(e -> watchLine(e.getKey(), e.getValue()))
        .collect(Collectors.joining());
  }

  private static String watchLine(ChecklistPoint point, Tally tally) {
    return point.number()
        + ". "
        + point.label()
        + ": "
        + tally.problems()
        + " problèmes ("
        + tally.of(Verdict.NON_RESPECTE)
        + " critiques, "
        + tally.of(Verdict.PARTIEL)
        + " mineurs)\n";
  }
}
