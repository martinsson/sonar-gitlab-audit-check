package ch.ge.edg.sonar.gitlab.audit.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Key normalisation, repository links and the namespace-mapping key helpers. */
class ProjectMapperKeysTest {

  @Test
  void normalise_lowercases_and_folds_every_separator_into_one_dash() {
    assertThat(ProjectMapper.normalise("Team-A/Alpha_Service")).isEqualTo("team-a-alpha-service");
    assertThat(ProjectMapper.normalise("grp.alpha:svc")).isEqualTo("grp-alpha-svc");
    assertThat(ProjectMapper.normalise("--x__y--")).isEqualTo("x-y");
    assertThat(ProjectMapper.normalise("Élan")).isEqualTo("lan");
    assertThat(ProjectMapper.normalise("")).isEmpty();
  }

  @Test
  void linked_paths_read_https_ssh_and_tree_urls_ignoring_the_host() {
    String links =
        "gitlab https://git.example.test/team-a/alpha.git"
            + " | github git@git.example.test:Team-B/Beta"
            + " | other https://git.example.test/team-a/alpha/-/tree/main";
    assertThat(ProjectMapper.linkedPaths(links)).containsExactly("team-a/alpha", "team-b/beta");
  }

  @Test
  void linked_paths_strip_query_fragment_trailing_slash_and_dot_git() {
    assertThat(ProjectMapper.linkedPaths("https://h/a/b?x=1#y")).containsExactly("a/b");
    assertThat(ProjectMapper.linkedPaths("https://h/A/B/")).containsExactly("a/b");
    assertThat(ProjectMapper.linkedPaths("x https://h/a/b.git/")).containsExactly("a/b");
    assertThat(ProjectMapper.linkedPaths("ftp://h/g/sub/p")).containsExactly("g/sub/p");
  }

  @Test
  void linked_paths_drop_what_is_not_a_group_path() {
    assertThat(ProjectMapper.linkedPaths(null)).isEmpty();
    assertThat(ProjectMapper.linkedPaths("  ")).isEmpty();
    assertThat(ProjectMapper.linkedPaths("site https://h/alpha")).isEmpty();
    assertThat(ProjectMapper.linkedPaths("x y://z")).isEmpty();
    assertThat(ProjectMapper.linkedPaths("https://h")).isEmpty();
  }

  @Test
  void linked_paths_split_only_on_spaced_pipes() {
    assertThat(ProjectMapper.linkedPaths("https://h/a/b|https://h/c/d")).isEmpty();
    assertThat(ProjectMapper.linkedPaths("https://h/a/b | https://h/c/d"))
        .containsExactly("a/b", "c/d");
  }

  @Test
  void strip_ticket_number_removes_a_leading_number_and_dash() {
    assertThat(ProjectMapper.stripTicketNumber(" 12-ab ")).isEqualTo("ab");
    assertThat(ProjectMapper.stripTicketNumber("12-ab-34")).isEqualTo("ab-34");
    assertThat(ProjectMapper.stripTicketNumber("12-")).isEmpty();
    assertThat(ProjectMapper.stripTicketNumber("-ab")).isEqualTo("-ab");
    assertThat(ProjectMapper.stripTicketNumber("a1-b")).isEqualTo("a1-b");
    assertThat(ProjectMapper.stripTicketNumber("12")).isEqualTo("12");
  }

  @Test
  void map_path_to_sonar_key_drops_the_namespace_and_ticket_numbers() {
    assertThat(ProjectMapper.mapPathToSonarKey(null)).isEmpty();
    assertThat(ProjectMapper.mapPathToSonarKey(" ")).isEmpty();
    assertThat(ProjectMapper.mapPathToSonarKey("Alpha_X")).isEqualTo("alpha-x");
    assertThat(ProjectMapper.mapPathToSonarKey("Team-A/123-Alpha")).isEqualTo("alpha");
    assertThat(ProjectMapper.mapPathToSonarKey("team-a/sub/456-beta_svc"))
        .isEqualTo("sub-beta-svc");
    assertThat(ProjectMapper.mapPathToSonarKey("team-a/12-/x")).isEqualTo("x");
    assertThat(ProjectMapper.mapPathToSonarKey("team-a/12-/34-")).isEmpty();
    assertThat(ProjectMapper.mapPathToSonarKey("team-a/12-")).isEmpty();
    assertThat(ProjectMapper.mapPathToSonarKey("team-a/-x-/y")).isEqualTo("x-y");
  }

  @Test
  void mapped_gitlab_paths_give_up_to_three_variants() {
    assertThat(ProjectMapper.mappedGitlabPaths(null)).isEmpty();
    assertThat(ProjectMapper.mappedGitlabPaths("")).isEmpty();
    assertThat(ProjectMapper.mappedGitlabPaths("Alpha")).containsExactly("alpha");
    assertThat(ProjectMapper.mappedGitlabPaths("Team-A/123-alpha"))
        .containsExactly("team-a-alpha", "alpha");
    assertThat(ProjectMapper.mappedGitlabPaths("team-a/sub/9-beta"))
        .containsExactly("team-a-sub-beta", "team-a-beta", "sub-beta");
    assertThat(ProjectMapper.mappedGitlabPaths("team-a/12-")).containsExactly("team-a");
    assertThat(ProjectMapper.mappedGitlabPaths("/x")).containsExactly("x", "x");
  }

  @Test
  void mapped_sonar_key_adds_the_short_name_when_it_differs() {
    assertThat(ProjectMapper.mappedSonarKey("grp.alpha:Alpha_Svc"))
        .containsExactly("grp-alpha-alpha-svc", "alpha-svc");
    assertThat(ProjectMapper.mappedSonarKey("123-beta")).containsExactly("123-beta", "beta");
    assertThat(ProjectMapper.mappedSonarKey("gamma")).containsExactly("gamma");
    assertThat(ProjectMapper.mappedSonarKey("Gamma")).containsExactly("gamma");
    assertThat(ProjectMapper.mappedSonarKey("x:")).containsExactly("x");
    assertThat(ProjectMapper.mappedSonarKey("")).containsExactly("");
    assertThat(ProjectMapper.mappedSonarKey(" ")).containsExactly("");
  }

  @Test
  void mapped_sonar_key_of_null_throws_before_its_null_check() {
    assertThatThrownBy(() -> ProjectMapper.mappedSonarKey(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void extract_short_name_takes_what_follows_the_colon_or_the_last_separator() {
    assertThat(ProjectMapper.extractShortName("a:b:c")).isEqualTo("b:c");
    assertThat(ProjectMapper.extractShortName("a: 9-c ")).isEqualTo("c");
    assertThat(ProjectMapper.extractShortName("a_b-c")).isEqualTo("c");
    assertThat(ProjectMapper.extractShortName("a-b_c")).isEqualTo("c");
    assertThat(ProjectMapper.extractShortName("12-x")).isEqualTo("x");
    assertThat(ProjectMapper.extractShortName(" x ")).isEqualTo("x");
    assertThat(ProjectMapper.extractShortName("x-")).isEmpty();
  }

  @Test
  void prefix_is_the_part_before_the_last_colon_or_the_first_separator() {
    assertThat(ProjectMapper.prefix("grp.alpha:svc")).isEqualTo("grp.alpha:");
    assertThat(ProjectMapper.prefix("a:b:c")).isEqualTo("a:b:");
    assertThat(ProjectMapper.prefix("dsi_paie_front")).isEqualTo("dsi_");
    assertThat(ProjectMapper.prefix("a.b/c")).isEqualTo("a.");
    assertThat(ProjectMapper.prefix("x/y")).isEqualTo("x/");
    assertThat(ProjectMapper.prefix("x-y")).isEqualTo("x-");
    assertThat(ProjectMapper.prefix(":x")).isEqualTo("(sans séparateur)");
    assertThat(ProjectMapper.prefix("abc")).isEqualTo("(sans séparateur)");
    assertThat(ProjectMapper.prefix("_abc")).isEqualTo("(sans séparateur)");
  }

  @Test
  void formatting_helpers() {
    assertThat(ProjectMapper.score(0.5)).isEqualTo("0.50");
    assertThat(ProjectMapper.score(0.125)).isEqualTo("0.13");
    assertThat(ProjectMapper.pct(1, 3)).isEqualTo("33 %");
    assertThat(ProjectMapper.pct(0, 0)).isEqualTo("—");
    assertThat(ProjectMapper.pct(0, 5)).isEqualTo("0 %");
    assertThat(ProjectMapper.pct(0.5)).isEqualTo(" 50 %");
    assertThat(ProjectMapper.pct(1.0)).isEqualTo("100 %");
  }
}
