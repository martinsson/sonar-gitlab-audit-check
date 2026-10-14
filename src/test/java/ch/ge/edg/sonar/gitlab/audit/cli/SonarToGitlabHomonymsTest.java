package ch.ge.edg.sonar.gitlab.audit.cli;

import static ch.ge.edg.sonar.gitlab.audit.cli.SonarToGitlabFixtures.checks;
import static ch.ge.edg.sonar.gitlab.audit.cli.SonarToGitlabFixtures.describe;
import static ch.ge.edg.sonar.gitlab.audit.cli.SonarToGitlabFixtures.inventory;
import static ch.ge.edg.sonar.gitlab.audit.cli.SonarToGitlabFixtures.map;
import static ch.ge.edg.sonar.gitlab.audit.cli.SonarToGitlabFixtures.sonar;
import static com.diffplug.selfie.Selfie.expectSelfie;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Several repositories behind one derived method: names, then activity, then nothing. */
class SonarToGitlabHomonymsTest {

  @TempDir Path dir;

  @Test
  void names_decide_when_one_copy_resembles_clearly_more() throws IOException {
    Csv.Table inv =
        inventory(
            dir,
            "payments/billing-core/dup,dup,1,false,0,2026-01-01,false,false,",
            "zzz/dup,dup,2,false,0,2026-01-01,false,false,");
    Csv.Table sonar = sonar(dir, "dup,Payments Billing Core Dup,,,,");
    expectSelfie(describe(map(sonar, inv, List.of())))
        .toBe(
            "dup -> payments/billing-core/dup | nom court de la clé = dernier segment / nom GitLab (2 homonymes, départagés par ressemblance des noms) | suggestion | 1.00 | payments/billing-core/dup (1.00), zzz/dup (0.51) | ");
  }

  @Test
  void activity_decides_when_names_do_not_original_before_fork() throws IOException {
    Csv.Table inv =
        inventory(
            dir,
            "bob/dup,dup,1,false,0,2026-01-01,true,false,",
            "team-a/dup,dup,2,false,0,2025-01-01,false,false,",
            "carol/dup,dup,3,false,0,2026-01-01,false,true,");
    Csv.Table sonar = sonar(dir, "dup,Dup,,,,");
    expectSelfie(describe(map(sonar, inv, List.of())))
        .toBe(
            "dup -> team-a/dup | nom court de la clé = dernier segment / nom GitLab (3 homonymes, départagés par activité GitLab) | suggestion | 0.72 | bob/dup (0.72), team-a/dup (0.72), carol/dup (0.72) | ");
  }

  @Test
  void activity_decides_by_commits_then_last_activity() throws IOException {
    Csv.Table inv =
        inventory(
            dir,
            "team-a/dup,dup,1,false,3,2026-01-01,false,false,",
            "team-b/dup,dup,2,false,7,2025-01-01,false,false,",
            "team-c/same,same,3,false,,2025-01-01,false,false,",
            "team-d/same,same,4,false,,2026-03-01,false,false,");
    Csv.Table sonar = sonar(dir, "dup,Dup,,,,", "same,Same,,,,");
    expectSelfie(describe(map(sonar, inv, List.of())))
        .toBe(
"""
dup -> team-b/dup | nom court de la clé = dernier segment / nom GitLab (2 homonymes, départagés par activité GitLab) | suggestion | 0.92 | team-a/dup (0.92), team-b/dup (0.92) |\s
same ->  | aucune | none | - | team-c/same (0.92), team-d/same (0.92) | homonymes non départagés (nom court de la clé = dernier segment / nom GitLab)""");
  }

  @Test
  void equally_active_originals_are_split_by_the_most_recent_activity() throws IOException {
    Csv.Table inv =
        inventory(
            dir,
            "team-a/dup,dup,1,false,5,2026-01-01,false,false,",
            "team-b/dup,dup,2,false,5,2026-02-01,false,false,",
            "team-c/dup,dup,3,false,5,2025-12-01,false,false,");
    Csv.Table sonar = sonar(dir, "dup,Dup,,,,");
    expectSelfie(describe(map(sonar, inv, List.of())))
        .toBe(
            "dup -> team-b/dup | nom court de la clé = dernier segment / nom GitLab (3 homonymes, départagés par activité GitLab) | suggestion | 0.85 | team-a/dup (0.85), team-b/dup (0.85), team-c/dup (0.85) | ");
  }

  @Test
  void two_dead_originals_are_not_split_and_no_names_are_tried_after() throws IOException {
    Csv.Table inv =
        inventory(
            dir,
            "team-a/dup,dup,1,false,0,2026-01-01,false,false,",
            "team-b/dup,dup,2,false,0,2025-01-01,false,false,");
    Csv.Table sonar = sonar(dir, "dup,Dup,,,,");
    expectSelfie(describe(map(sonar, inv, List.of())))
        .toBe(
            "dup ->  | aucune | none | - | team-a/dup (0.84), team-b/dup (0.84) | homonymes non départagés (nom court de la clé = dernier segment / nom GitLab)");
  }

  @Test
  void a_pick_on_a_rejected_path_leaves_the_project_unpaired() throws IOException {
    Csv.Table inv =
        inventory(
            dir,
            "team-a/dup,dup,1,false,9,2026-01-01,false,false,",
            "team-b/dup,dup,2,false,0,2025-01-01,false,false,");
    Csv.Table sonar = sonar(dir, "dup,Dup,,,,");
    SonarToGitlab.Result r =
        SonarToGitlab.map(sonar, inv, List.of(), Set.of(), Set.of("team-a/dup"), 0.5, 0.1);
    expectSelfie(describe(r))
        .toBe(
            "dup ->  | aucune | none | - | team-a/dup (0.84), team-b/dup (0.84) | homonymes non départagés (nom court de la clé = dernier segment / nom GitLab)");
  }

  @Test
  void more_than_five_homonyms_list_only_the_first_five() throws IOException {
    Csv.Table inv =
        inventory(
            dir,
            "a1/dup,dup,1,false,6,2026-01-01,false,false,",
            "a2/dup,dup,2,false,5,2026-01-01,false,false,",
            "a3/dup,dup,3,false,4,2026-01-01,false,false,",
            "a4/dup,dup,4,false,3,2026-01-01,false,false,",
            "a5/dup,dup,5,false,2,2026-01-01,false,false,",
            "a6/dup,dup,6,false,1,2026-01-01,false,false,");
    Csv.Table sonar = sonar(dir, "dup,Dup,,,,");
    expectSelfie(describe(map(sonar, inv, List.of())))
        .toBe(
            "dup -> a1/dup | nom court de la clé = dernier segment / nom GitLab (6 homonymes, départagés par activité GitLab) | suggestion | 1.00 | a1/dup (1.00), a2/dup (1.00), a3/dup (1.00), a4/dup (1.00), a5/dup (1.00) | ");
  }

  @Test
  void derived_methods_are_replayed_on_exact_liaison_pairs() throws IOException {
    Csv.Table inv =
        inventory(
            dir,
            "team-a/alpha,alpha,1,false,5,2026-01-01,false,false,",
            "team-a/beta,beta,2,false,5,2026-01-01,false,false,",
            "team-b/dup,dup,3,false,5,2026-01-01,false,false,",
            "team-c/dup,dup,4,false,5,2026-01-01,false,false,",
            "team-d/nothing,nothing,5,false,5,2026-01-01,false,false,");
    Csv.Table sonar =
        sonar(
            dir,
            "alpha,Alpha,gitlab,1,,",
            "beta,Beta,gitlab,1,,",
            "dup,Dup,gitlab,4,,",
            "zz-none,Zz None,gitlab,5,,");
    SonarToGitlab.Result r = map(sonar, inv, List.of());
    expectSelfie(describe(r))
        .toBe(
"""
alpha -> team-a/alpha | liaison DevOps Sonar → GitLab | exact | - |  |\s
beta -> team-a/alpha | liaison DevOps Sonar → GitLab | exact | - |  |\s
dup -> team-c/dup | liaison DevOps Sonar → GitLab | exact | - |  |\s
zz-none -> team-d/nothing | liaison DevOps Sonar → GitLab | exact | - |  |\s""");
    expectSelfie(checks(r))
        .toBe(
"""
nom court de la clé = dernier segment / nom GitLab = true
nom court de la clé = dernier segment / nom GitLab = false
nom court de la clé = dernier segment / nom GitLab (homonymes) = false""");
  }
}
