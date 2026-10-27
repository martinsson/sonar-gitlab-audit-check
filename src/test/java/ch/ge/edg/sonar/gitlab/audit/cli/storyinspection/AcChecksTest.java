package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

import static ch.ge.edg.sonar.gitlab.audit.cli.storyinspection.StoryFixtures.described;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AcChecksTest {

  @Test
  void acNumberingIsReadInTheOrderCitedAndCheckedSorted() {
    AcNumberingCheck check = new AcNumberingCheck();
    assertThat(AcNumberingCheck.numbers("AC1 puis AC 2, ac:3 et AC.7")).containsExactly(1, 2, 3, 7);
    assertThat(AcNumberingCheck.sequential(List.of(3, 1, 2))).isTrue();
    assertThat(AcNumberingCheck.sequential(List.of(1, 1, 2))).isFalse();

    assertThat(check.inspect(described("")).verdict()).isEqualTo(Verdict.SANS_OBJET);
    assertThat(check.inspect(described("rien ici")).evidence()).isEqualTo("Aucun AC trouvé");
    assertThat(check.inspect(described("AC1 seul")).evidence()).isEqualTo("1 AC trouvé(s)");
    assertThat(check.inspect(described("AC2 AC1 AC3")).evidence())
        .isEqualTo("3 AC numérotés séquentiellement");
    Finding trou = check.inspect(described("AC1 AC3"));
    assertThat(trou.verdict()).isEqualTo(Verdict.PARTIEL);
    assertThat(trou.comment()).isEqualTo("Vérifier la numérotation des AC");
  }

  @Test
  void contextAcIsLookedForOnOneLine() {
    ContextAcCheck check = new ContextAcCheck();
    assertThat(check.inspect(described("")).verdict()).isEqualTo(Verdict.SANS_OBJET);
    assertThat(check.inspect(described("AC1 le scénario complet")).verdict())
        .isEqualTo(Verdict.RESPECTE);
    assertThat(check.inspect(described("AC1 un bouton bleu")).verdict())
        .isEqualTo(Verdict.NON_RESPECTE);
  }

  @Test
  void acFormWantsTestableWordsAndNoVagueOnes() {
    AcFormCheck check = new AcFormCheck();
    assertThat(check.inspect(described("")).verdict()).isEqualTo(Verdict.SANS_OBJET);
    assertThat(check.inspect(described("pas de critère")).evidence()).isEqualTo("Aucun AC trouvé");
    assertThat(check.inspect(described("AC1 si le dossier est complet alors il passe")))
        .isEqualTo(Finding.respecte("1 AC analysés", "AC bien rédigés et testables"));
    assertThat(check.inspect(described("AC1 si ok, il doit marcher bien")).comment())
        .isEqualTo("AC testables mais termes vagues détectés");
    assertThat(check.inspect(described("AC1 un bouton bleu")).comment())
        .isEqualTo("AC pourraient être plus testables");
    assertThat(check.inspect(described("AC1 ça marche facilement")).comment())
        .isEqualTo("AC mais termes vagues détectés pourraient être plus testables");
  }
}
