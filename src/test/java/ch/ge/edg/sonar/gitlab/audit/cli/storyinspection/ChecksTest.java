package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

import static ch.ge.edg.sonar.gitlab.audit.cli.storyinspection.StoryFixtures.described;
import static ch.ge.edg.sonar.gitlab.audit.cli.storyinspection.StoryFixtures.story;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChecksTest {

  @Test
  void titleIsMissingShortOrFine() {
    TitleCheck check = new TitleCheck();
    assertThat(check.inspect(story("", "")).verdict()).isEqualTo(Verdict.NON_RESPECTE);
    Finding court = check.inspect(story("Court", ""));
    assertThat(court.verdict()).isEqualTo(Verdict.PARTIEL);
    assertThat(court.evidence()).isEqualTo("Titre trop court: 'Court'");
    assertThat(check.inspect(story("Déposer une demande", "")).verdict())
        .isEqualTo(Verdict.RESPECTE);
  }

  @Test
  void onlyAStoryTypeIsRight() {
    TypeCheck check = new TypeCheck();
    assertThat(check.inspect(story("Un titre assez long", "")).verdict())
        .isEqualTo(Verdict.RESPECTE);
    Story bug = new Story("DEMO-2", "Un titre assez long", "", "Open", "Alice", "Bug");
    Finding finding = check.inspect(bug);
    assertThat(finding.verdict()).isEqualTo(Verdict.PARTIEL);
    assertThat(finding.evidence()).isEqualTo("Type: Bug");
  }

  @Test
  void descriptionNeedsLengthContextAndRequirements() {
    DescriptionCheck check = new DescriptionCheck();
    assertThat(check.inspect(described("")).verdict()).isEqualTo(Verdict.NON_RESPECTE);
    Finding court = check.inspect(described("trop court"));
    assertThat(court.verdict()).isEqualTo(Verdict.PARTIEL);
    assertThat(court.evidence()).isEqualTo("Description courte (10 caractères)");
    assertThat(check.inspect(described(StoryFixtures.GOOD_DESCRIPTION.strip())).verdict())
        .isEqualTo(Verdict.RESPECTE);
  }

  @Test
  void aLongDescriptionOnOneLineSaysWhatIsMissing() {
    DescriptionCheck check = new DescriptionCheck();
    String neutral = "x".repeat(60);
    assertThat(check.inspect(described(neutral)).comment())
        .isEqualTo("Description présente - Ajouter le contexte métier - Clarifier les exigences");
    assertThat(check.inspect(described(neutral + " utilisateur")).comment())
        .isEqualTo("Description présente - Clarifier les exigences");
    assertThat(check.inspect(described(neutral + " il faut")).comment())
        .isEqualTo("Description présente - Ajouter le contexte métier");
  }

  @Test
  void pointsSevenToFifteenAreNotImplemented() {
    InspectionPoint invest = Checklist.inspect(ChecklistPoint.INVEST, described(""));
    assertThat(invest)
        .isEqualTo(
            new InspectionPoint(
                7,
                Verdict.SANS_OBJET,
                "Point 7 non implémenté",
                "Inspection non implémentée pour ce point"));
    assertThat(Checklist.inspect(ChecklistPoint.LIEN_RELEASE, described("")).point()).isEqualTo(15);
  }

  @Test
  void theSymbolsAreTheOnesTheReportsAlwaysUsed() {
    assertThat(Verdict.RESPECTE.symbol()).isEqualTo("✅");
    assertThat(Verdict.PARTIEL.symbol()).isEqualTo("⚠️");
    assertThat(Verdict.NON_RESPECTE.symbol()).isEqualTo("❌");
    assertThat(Verdict.SANS_OBJET.symbol()).isEqualTo("⏭️");
  }
}
