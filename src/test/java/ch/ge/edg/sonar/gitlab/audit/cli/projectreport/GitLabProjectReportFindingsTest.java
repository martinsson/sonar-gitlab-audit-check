package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.GitLabProjectReportFakeGitlab.commit;
import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.GitLabProjectReportFakeGitlab.mr;
import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.GitLabProjectReportFakeGitlab.note;
import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.GitLabProjectReportFakeGitlab.user;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Projects built to trip the findings of section 5, each one at a time or all together. */
class GitLabProjectReportFindingsTest extends GitLabProjectReportHarness {

  static final String SAT = "2024-03-09T11:00:00+02:00";
  static final String SUN = "2024-03-10T22:00:00+02:00";
  static final String MON = "2024-03-04T10:00:00+02:00";

  /** A team that left: one main author, everyone gone for months, bots still busy. */
  void abandonedProject() {
    Map<String, Object> p = fx.project(ID, PATH, "main", true);
    project(p);
    List<Map<String, Object>> c = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      c.add(
          commit(
              "Alice",
              "alice@example.com",
              i % 2 == 0 ? SAT : SUN,
              fx.ago(200 + i, 0),
              new int[] {i == 0 ? 2500 : 10, 0}));
    }
    c.add(commit("Bob", "bob@example.com", MON, fx.ago(250, 0), new int[] {1, 1}));
    c.add(commit("Carol", "carol@example.com", MON, fx.ago(260, 0), new int[] {1, 1}));
    for (int i = 0; i < 11; i++) {
      c.add(commit("dependabot", "dependabot@example.com", MON, fx.ago(1 + i, 0), null));
    }
    commits(c);
    contributors(0);
    mrs(List.of());
  }

  @Test
  void an_abandoned_project_trips_the_process_findings() {
    abandonedProject();

    Cli.Result r = run("--days", "365");

    assertThat(r.exitCode()).isZero();
    expectSelfie(normalized(r.out())).toMatchDisk();
  }

  @Test
  void findings_are_colored_when_asked() {
    abandonedProject();

    Cli.Result r = run("--days", "365", "--color", "always");

    assertThat(r.out())
        .contains("\033[33m   ARCHIVÉ\033[0m")
        .contains("\033[33m  → Projet archivé : lecture historique seulement.\033[0m")
        .contains("\033[33m  Aucune MR créée sur la fenêtre.\033[0m")
        .contains("\033[33m1\033[0m");
  }

  /** MRs that exist but review nothing: self-merged, silent, squashed, slow. */
  List<Map<String, Object>> rubberStampMrs() {
    List<Map<String, Object>> out = new ArrayList<>();
    for (int i = 1; i <= 6; i++) {
      Map<String, Object> m = mr(i, "merged", "alice", fx.ago(100 + i, 0));
      m.put("merged_at", fx.ago(100 + i - (i == 6 ? 70 : 1), 0));
      m.put("merge_user", user("alice"));
      m.put("squash", true);
      out.add(m);
      notes(i, List.of(note("alice", fx.ago(100, 0), false)));
    }
    return out;
  }

  @Test
  void rubber_stamp_reviews_trip_the_review_findings() {
    project(fx.project(ID, PATH, "main", false));
    commits(
        List.of(
            commit("Alice", "alice@example.com", MON, fx.ago(1, 0), new int[] {1, 1}),
            commit("Bob", "bob@example.com", MON, fx.ago(2, 0), new int[] {1, 1})));
    contributors(2);
    mrs(rubberStampMrs());

    Cli.Result r = run();

    assertThat(r.exitCode()).isZero();
    expectSelfie(normalized(r.out())).toMatchDisk();
  }

  @Test
  void the_dormant_finding_needs_more_than_ninety_days_of_silence() {
    project(fx.project(ID, PATH, "main", false));
    commits(
        List.of(
            commit("Alice", "alice@example.com", MON, fx.ago(91, 0), new int[] {1, 1}),
            commit("Bob", "bob@example.com", MON, fx.ago(95, 0), new int[] {1, 1}),
            commit("Carol", "carol@example.com", MON, fx.ago(96, 0), new int[] {1, 1})));
    contributors(0);
    mrs(List.of(mr(1, "opened", "alice", fx.ago(1, 0))));

    Cli.Result r = run();

    assertThat(r.out())
        .contains("  → Dernier commit il y a 91 j : dépôt dormant.")
        .doesNotContain("le travail vit ailleurs")
        .contains("3 auteurs sur la fenêtre, 0 actif sur 90 j : l'équipe s'est vidée.")
        .doesNotContain("Une seule personne");
  }

  @Test
  void ninety_days_of_silence_is_not_yet_dormant() {
    project(fx.project(ID, PATH, "main", false));
    commits(
        List.of(
            commit("Alice", "alice@example.com", MON, fx.ago(90, 1), new int[] {1, 1}),
            commit("Bob", "bob@example.com", MON, fx.ago(90, 2), new int[] {1, 1})));
    contributors(0);
    mrs(List.of(mr(1, "opened", "alice", fx.ago(1, 0))));

    Cli.Result r = run();

    assertThat(r.out())
        .doesNotContain("dormant")
        .contains("Actifs sur 90 j                 : 0 sur 2");
  }
}
