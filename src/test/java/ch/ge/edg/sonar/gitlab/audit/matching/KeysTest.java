package ch.ge.edg.sonar.gitlab.audit.matching;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.text.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Key normalisation, repository links and the namespace-mapping key helpers. */
class KeysTest {

  @ParameterizedTest
  @CsvSource({
    "Team-A/Alpha_Service,team-a-alpha-service",
    "grp.alpha:svc,grp-alpha-svc",
    "--x__y--,x-y",
    "Élan,lan",
    "'',''"
  })
  void normaliseLowercasesAndFoldsEverySeparatorIntoOneDash(String raw, String expected) {
    assertThat(Keys.normalise(raw)).isEqualTo(expected);
  }

  @Test
  void linkedPathsReadHttpsSshAndTreeUrlsIgnoringTheHost() {
    String links =
        "gitlab https://git.example.test/team-a/alpha.git"
            + " | github git@git.example.test:Team-B/Beta"
            + " | other https://git.example.test/team-a/alpha/-/tree/main";
    assertThat(Keys.linkedPaths(links)).containsExactly("team-a/alpha", "team-b/beta");
  }

  @ParameterizedTest
  @CsvSource({
    "https://h/a/b?x=1#y,a/b",
    "https://h/A/B/,a/b",
    "x https://h/a/b.git/,a/b",
    "ftp://h/g/sub/p,g/sub/p"
  })
  void linkedPathsStripQueryFragmentTrailingSlashAndDotGit(String links, String expected) {
    assertThat(Keys.linkedPaths(links)).containsExactly(expected);
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "null",
      value = {"null", "'  '", "site https://h/alpha", "x y://z", "https://h"})
  void linkedPathsDropWhatIsNotAGroupPath(String links) {
    assertThat(Keys.linkedPaths(links)).isEmpty();
  }

  @Test
  void linkedPathsSplitOnlyOnSpacedPipes() {
    assertThat(Keys.linkedPaths("https://h/a/b|https://h/c/d")).isEmpty();
    assertThat(Keys.linkedPaths("https://h/a/b | https://h/c/d")).containsExactly("a/b", "c/d");
  }

  @ParameterizedTest
  @CsvSource({"' 12-ab ',ab", "12-ab-34,ab-34", "'12-',''", "-ab,-ab", "a1-b,a1-b", "12,12"})
  void stripTicketNumberRemovesALeadingNumberAndDash(String raw, String expected) {
    assertThat(Keys.stripTicketNumber(raw)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "null",
      value = {
        "null,''",
        "' ',''",
        "Alpha_X,alpha-x",
        "Team-A/123-Alpha,alpha",
        "team-a/sub/456-beta_svc,sub-beta-svc",
        "team-a/12-/x,x",
        "team-a/12-/34-,''",
        "team-a/12-,''",
        "team-a/-x-/y,x-y"
      })
  void mapPathToSonarKeyDropsTheNamespaceAndTicketNumbers(String path, String expected) {
    assertThat(Keys.mapPathToSonarKey(path)).isEqualTo(expected);
  }

  @Test
  void mappedGitlabPathsGiveUpToThreeVariants() {
    assertThat(NamespaceMapping.empty().mappedGitlabPaths(null)).isEmpty();
    assertThat(NamespaceMapping.empty().mappedGitlabPaths("")).isEmpty();
    assertThat(NamespaceMapping.empty().mappedGitlabPaths("Alpha")).containsExactly("alpha");
    assertThat(NamespaceMapping.empty().mappedGitlabPaths("Team-A/123-alpha"))
        .containsExactly("team-a-alpha", "alpha");
    assertThat(NamespaceMapping.empty().mappedGitlabPaths("team-a/sub/9-beta"))
        .containsExactly("team-a-sub-beta", "team-a-beta", "sub-beta");
    assertThat(NamespaceMapping.empty().mappedGitlabPaths("team-a/12-")).containsExactly("team-a");
    assertThat(NamespaceMapping.empty().mappedGitlabPaths("/x")).containsExactly("x", "x");
  }

  @Test
  void mappedSonarKeyAddsTheShortNameWhenItDiffers() {
    assertThat(Keys.mappedSonarKey("grp.alpha:Alpha_Svc"))
        .containsExactly("grp-alpha-alpha-svc", "alpha-svc");
    assertThat(Keys.mappedSonarKey("123-beta")).containsExactly("123-beta", "beta");
    assertThat(Keys.mappedSonarKey("gamma")).containsExactly("gamma");
    assertThat(Keys.mappedSonarKey("Gamma")).containsExactly("gamma");
    assertThat(Keys.mappedSonarKey("x:")).containsExactly("x");
    assertThat(Keys.mappedSonarKey("")).containsExactly("");
    assertThat(Keys.mappedSonarKey(" ")).containsExactly("");
  }

  @Test
  void mappedSonarKeyOfNullIsEmpty() {
    assertThat(Keys.mappedSonarKey(null)).isEmpty();
  }

  @Test
  void extractShortNameTakesWhatFollowsTheColonOrTheLastSeparator() {
    assertThat(Keys.extractShortName("a:b:c")).isEqualTo("b:c");
    assertThat(Keys.extractShortName("a: 9-c ")).isEqualTo("c");
    assertThat(Keys.extractShortName("a_b-c")).isEqualTo("c");
    assertThat(Keys.extractShortName("a-b_c")).isEqualTo("c");
    assertThat(Keys.extractShortName("12-x")).isEqualTo("x");
    assertThat(Keys.extractShortName(" x ")).isEqualTo("x");
    assertThat(Keys.extractShortName("x-")).isEmpty();
  }

  @Test
  void prefixIsThePartBeforeTheLastColonOrTheFirstSeparator() {
    assertThat(Keys.prefix("grp.alpha:svc")).isEqualTo("grp.alpha:");
    assertThat(Keys.prefix("a:b:c")).isEqualTo("a:b:");
    assertThat(Keys.prefix("dsi_paie_front")).isEqualTo("dsi_");
    assertThat(Keys.prefix("a.b/c")).isEqualTo("a.");
    assertThat(Keys.prefix("x/y")).isEqualTo("x/");
    assertThat(Keys.prefix("x-y")).isEqualTo("x-");
    assertThat(Keys.prefix(":x")).isEqualTo("(sans séparateur)");
    assertThat(Keys.prefix("abc")).isEqualTo("(sans séparateur)");
    assertThat(Keys.prefix("_abc")).isEqualTo("(sans séparateur)");
  }

  @Test
  void formattingHelpers() {
    assertThat(Text.decimal(0.5)).isEqualTo("0.50");
    assertThat(Text.decimal(0.125)).isEqualTo("0.13");
    assertThat(Text.pct(1, 3)).isEqualTo("33 %");
    assertThat(Text.pct(0, 0)).isEqualTo("—");
    assertThat(Text.pct(0, 5)).isEqualTo("0 %");
    assertThat(Text.pct(0.5)).isEqualTo(" 50 %");
    assertThat(Text.pct(1.0)).isEqualTo("100 %");
  }
}
