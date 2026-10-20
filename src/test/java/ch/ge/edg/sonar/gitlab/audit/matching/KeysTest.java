package ch.ge.edg.sonar.gitlab.audit.matching;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.text.Text;
import org.junit.jupiter.api.Test;

/** Key normalisation, repository links and the namespace-mapping key helpers. */
class KeysTest {

  @Test
  void normaliseLowercasesAndFoldsEverySeparatorIntoOneDash() {
    assertThat(Keys.normalise("Team-A/Alpha_Service")).isEqualTo("team-a-alpha-service");
    assertThat(Keys.normalise("grp.alpha:svc")).isEqualTo("grp-alpha-svc");
    assertThat(Keys.normalise("--x__y--")).isEqualTo("x-y");
    assertThat(Keys.normalise("Élan")).isEqualTo("lan");
    assertThat(Keys.normalise("")).isEmpty();
  }

  @Test
  void linkedPathsReadHttpsSshAndTreeUrlsIgnoringTheHost() {
    String links =
        "gitlab https://git.example.test/team-a/alpha.git"
            + " | github git@git.example.test:Team-B/Beta"
            + " | other https://git.example.test/team-a/alpha/-/tree/main";
    assertThat(Keys.linkedPaths(links)).containsExactly("team-a/alpha", "team-b/beta");
  }

  @Test
  void linkedPathsStripQueryFragmentTrailingSlashAndDotGit() {
    assertThat(Keys.linkedPaths("https://h/a/b?x=1#y")).containsExactly("a/b");
    assertThat(Keys.linkedPaths("https://h/A/B/")).containsExactly("a/b");
    assertThat(Keys.linkedPaths("x https://h/a/b.git/")).containsExactly("a/b");
    assertThat(Keys.linkedPaths("ftp://h/g/sub/p")).containsExactly("g/sub/p");
  }

  @Test
  void linkedPathsDropWhatIsNotAGroupPath() {
    assertThat(Keys.linkedPaths(null)).isEmpty();
    assertThat(Keys.linkedPaths("  ")).isEmpty();
    assertThat(Keys.linkedPaths("site https://h/alpha")).isEmpty();
    assertThat(Keys.linkedPaths("x y://z")).isEmpty();
    assertThat(Keys.linkedPaths("https://h")).isEmpty();
  }

  @Test
  void linkedPathsSplitOnlyOnSpacedPipes() {
    assertThat(Keys.linkedPaths("https://h/a/b|https://h/c/d")).isEmpty();
    assertThat(Keys.linkedPaths("https://h/a/b | https://h/c/d")).containsExactly("a/b", "c/d");
  }

  @Test
  void stripTicketNumberRemovesALeadingNumberAndDash() {
    assertThat(Keys.stripTicketNumber(" 12-ab ")).isEqualTo("ab");
    assertThat(Keys.stripTicketNumber("12-ab-34")).isEqualTo("ab-34");
    assertThat(Keys.stripTicketNumber("12-")).isEmpty();
    assertThat(Keys.stripTicketNumber("-ab")).isEqualTo("-ab");
    assertThat(Keys.stripTicketNumber("a1-b")).isEqualTo("a1-b");
    assertThat(Keys.stripTicketNumber("12")).isEqualTo("12");
  }

  @Test
  void mapPathToSonarKeyDropsTheNamespaceAndTicketNumbers() {
    assertThat(Keys.mapPathToSonarKey(null)).isEmpty();
    assertThat(Keys.mapPathToSonarKey(" ")).isEmpty();
    assertThat(Keys.mapPathToSonarKey("Alpha_X")).isEqualTo("alpha-x");
    assertThat(Keys.mapPathToSonarKey("Team-A/123-Alpha")).isEqualTo("alpha");
    assertThat(Keys.mapPathToSonarKey("team-a/sub/456-beta_svc")).isEqualTo("sub-beta-svc");
    assertThat(Keys.mapPathToSonarKey("team-a/12-/x")).isEqualTo("x");
    assertThat(Keys.mapPathToSonarKey("team-a/12-/34-")).isEmpty();
    assertThat(Keys.mapPathToSonarKey("team-a/12-")).isEmpty();
    assertThat(Keys.mapPathToSonarKey("team-a/-x-/y")).isEqualTo("x-y");
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
