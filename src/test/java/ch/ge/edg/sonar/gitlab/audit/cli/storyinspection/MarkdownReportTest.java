package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

import static ch.ge.edg.sonar.gitlab.audit.cli.storyinspection.StoryFixtures.GOOD_DESCRIPTION;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownReportTest {

  private static final List<ChecklistPoint> POINTS =
      List.of(ChecklistPoint.TITRAGE, ChecklistPoint.TYPE, ChecklistPoint.DESCRIPTION);

  private static StoryResult result(String key, String summary, String description) {
    Story story = new Story(key, summary, description, "Open", "Alice", "Story");
    return StoryResult.of(story, POINTS);
  }

  private static String report() {
    return MarkdownReport.render(
        List.of(
            result("DEMO-1", "Déposer une demande en ligne", GOOD_DESCRIPTION.strip()),
            result("DEMO-2", "", "")),
        POINTS);
  }

  @Test
  void theHeaderNamesTheProjectAndThePointsChecked() {
    assertThat(report())
        .startsWith("# Rapport d'Inspection - Projet DEMO\n\n")
        .contains("## Points de contrôle vérifiés: 1, 2, 3\n\n---\n\n## Story : DEMO-1 - ");
  }

  @Test
  void aConformStoryHasFullScoreAndNoAction() {
    String section = report().split("## Story : DEMO-2", 2)[0];
    assertThat(section)
        .contains(
            "| 1 | 1. Respect du titrage | ✅ | Titre: 'Déposer une demande en ligne' | "
                + "Titre présent et de longueur raisonnable |\n")
        .contains("- **Points respectés :** 3/3\n")
        .contains("- **Score de qualité :** 100%\n")
        .contains("### Actions recommandées\nAucune action requise - Story conforme\n");
  }

  @Test
  void aFlawedStoryListsItsActions() {
    String section = report().split("## Story : DEMO-2", 2)[1].split("## Rapport global", 2)[0];
    assertThat(section)
        .contains("- **Points respectés :** 1/3\n")
        .contains("- **Points non respectés :** 2\n")
        .contains("- **Score de qualité :** 33%\n")
        .contains("- [1. Respect du titrage] La story n'a pas de titre\n")
        .contains("- [3. Respect de la description] La story n'a pas de description\n");
  }

  @Test
  void theProjectSummaryCountsStoriesAndRanksThePointsAtRisk() {
    String summary = report().split("## Rapport global", 2)[1];
    assertThat(summary)
        .contains("- **Total de stories inspectées :** 2\n")
        .contains("- **Stories 100% conformes :** 1 (50%)\n")
        .contains("- **Stories avec non-conformités critiques :** 1 (50%)\n")
        .contains("- **Stories avec non-conformités mineures :** 0 (0%)\n")
        .contains("| Point de contrôle | ✅ Respecté | ⚠️ Partiel | ❌ Non respecté | ⏭️ N/A |\n")
        .contains("| 1. Respect du titrage | 1 | 0 | 1 | 0 |\n")
        .contains("| 7. Respect du modèle INVEST | 0 | 0 | 0 | 0 |\n")
        .contains("### Points de vigilance majeurs\n1. 1. Respect du titrage: 1 problèmes")
        .contains("\n3. 3. Respect de la description: 1 problèmes (1 critiques, 0 mineurs)\n")
        .endsWith("5. Utiliser des templates pour la description et les AC\n");
  }

  @Test
  void conformityClassifiesEachStoryOnce() {
    Conformity conformity =
        Conformity.of(
            List.of(
                result("DEMO-1", "Déposer une demande en ligne", GOOD_DESCRIPTION.strip()),
                result("DEMO-2", "", ""),
                result("DEMO-3", "Court", GOOD_DESCRIPTION.strip())));
    assertThat(conformity).isEqualTo(new Conformity(3, 1, 1, 1));
    assertThat(conformity.percent(1)).isEqualTo(33);
  }

  @Test
  void onlyThePointsOneToSixCountInTheConformity() {
    StoryResult seven =
        StoryResult.of(
            new Story("DEMO-1", "", "", "Open", "Alice", "Story"), List.of(ChecklistPoint.INVEST));
    assertThat(seven.criticalCount()).isZero();
    assertThat(seven.minorCount()).isZero();
  }
}
