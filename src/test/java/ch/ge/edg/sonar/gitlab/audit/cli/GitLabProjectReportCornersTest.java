package ch.ge.edg.sonar.gitlab.audit.cli;

import static ch.ge.edg.sonar.gitlab.audit.cli.GitLabProjectReportFakeGitlab.API;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitLabProjectReportFakeGitlab.commit;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitLabProjectReportFakeGitlab.mr;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitLabProjectReportFakeGitlab.note;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Corner cases of single computations, asserted line by line. */
class GitLabProjectReportCornersTest extends GitLabProjectReportHarness {

  static final String MON = "2024-03-04T10:00:00+02:00";

  @Test
  void commits_without_committed_dates_have_no_gaps_and_no_last_commit() {
    project(fx.project(ID, PATH, "main", false));
    commits(
        List.of(
            commit("Alice", "alice@example.com", MON, null, new int[] {1, 1}),
            commit("Alice", "alice@example.com", MON, "", new int[] {1, 1})));
    contributors(0);
    mrs(List.of());

    Cli.Result r = run("--max-commits", "1");

    assertThat(r.out())
        .contains("  Plafond --max-commits atteint : les chiffres portent sur les 1 plus récents.")
        .contains(
            "  Rythme                          : 0.0 commits / semaine   (sur les 180 j couverts)")
        .contains("  Jours avec au moins un commit   : 0 sur 180\n")
        .doesNotContain("Écart médian")
        .doesNotContain("dormant")
        .contains("  Actifs sur 90 j                 : 0 sur 1");
  }

  @Test
  void a_branch_idle_for_half_a_year_on_a_quiet_project_is_only_dormant() {
    Map<String, Object> p = fx.project(ID, PATH, "main", false);
    p.put("last_activity_at", fx.ago(100, 0));
    project(p);
    commits(List.of(commit("Alice", "alice@example.com", MON, fx.ago(200, 0), new int[] {1, 1})));
    contributors(0);
    mrs(List.of(mr(1, "opened", "alice", fx.ago(1, 0))));

    Cli.Result r = run("--days", "365");

    assertThat(r.out())
        .contains("  → Dernier commit il y a 200 j : dépôt dormant.")
        .doesNotContain("le travail vit ailleurs");
  }

  @Test
  void a_branch_idle_for_exactly_180_days_does_not_live_elsewhere() {
    project(fx.project(ID, PATH, "main", false));
    commits(List.of(commit("Alice", "alice@example.com", MON, fx.ago(180, 1), new int[] {1, 1})));
    contributors(0);
    mrs(List.of(mr(1, "opened", "alice", fx.ago(1, 0))));

    Cli.Result r = run("--days", "365");

    assertThat(r.out()).contains("dépôt dormant").doesNotContain("le travail vit ailleurs");
  }

  @Test
  void big_commits_above_a_tenth_are_a_finding() {
    project(fx.project(ID, PATH, "main", false));
    commits(
        List.of(
            commit("Alice", "alice@example.com", MON, fx.ago(1, 0), new int[] {2001, 0}),
            commit("Bob", "bob@example.com", MON, fx.ago(2, 0), new int[] {1000, 1000})));
    contributors(0);
    mrs(List.of(mr(1, "opened", "alice", fx.ago(1, 0))));

    Cli.Result r = run();

    assertThat(r.out())
        .contains("  Commits > 2000 lignes           : 1 (50 %)")
        .contains(
            "  → 50 % des commits dépassent 2000 lignes : imports, code généré ou gros lots.");
  }

  @Test
  void refused_contributors_and_nameless_bots_are_left_out() {
    project(fx.project(ID, PATH, "main", false));
    commits(
        List.of(
            commit("Alice", "alice@example.com", MON, fx.ago(1, 0), new int[] {1, 1}),
            commit("", "renovate@example.com", MON, fx.ago(2, 0), null),
            commit("Renovate", "renovate@example.com", MON, fx.ago(3, 0), null)));
    fail(API + ID + "/repository/contributors", 500, "{}");
    mrs(List.of(mr(1, "opened", "alice", fx.ago(1, 0))));

    Cli.Result r = run();

    assertThat(r.out())
        .contains("  Écartés comme bots              : Renovate\n")
        .doesNotContain("Contributeurs de tout temps");
  }

  @Test
  void merge_requests_without_dates_are_left_out_of_delays() {
    project(fx.project(ID, PATH, "main", false));
    commits(List.of(commit("Alice", "alice@example.com", MON, fx.ago(1, 0), new int[] {1, 1})));
    contributors(0);
    Map<String, Object> m1 = mr(1, "merged", "alice", null);
    m1.put("merged_at", fx.ago(1, 0));
    Map<String, Object> m2 = mr(2, "merged", "alice", null);
    mrs(List.of(m1, m2));
    notes(1, List.of(note("bob", fx.ago(1, 0), false)));
    notes(2, List.of());

    Cli.Result r = run("--max-mrs", "1");

    assertThat(r.out())
        .contains(
            "  MR créées                       : 1 (0.0 / semaine)   (sur les 180 j couverts)")
        .doesNotContain("Délai création → fusion")
        .contains("  Échantillon de revue            : 1 MR fusionnées")
        .contains("  Fusionnées sans relecture       : 0 (0 %)")
        .doesNotContain("Délai avant première relecture");
  }
}
