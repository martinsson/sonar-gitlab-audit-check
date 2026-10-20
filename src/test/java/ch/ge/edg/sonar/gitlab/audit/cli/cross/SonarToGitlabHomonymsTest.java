package ch.ge.edg.sonar.gitlab.audit.cli.cross;

import static ch.ge.edg.sonar.gitlab.audit.cli.cross.SonarToGitlabFixtures.checks;
import static ch.ge.edg.sonar.gitlab.audit.cli.cross.SonarToGitlabFixtures.describe;
import static ch.ge.edg.sonar.gitlab.audit.cli.cross.SonarToGitlabFixtures.inventory;
import static ch.ge.edg.sonar.gitlab.audit.cli.cross.SonarToGitlabFixtures.map;
import static ch.ge.edg.sonar.gitlab.audit.cli.cross.SonarToGitlabFixtures.sonar;
import static com.diffplug.selfie.Selfie.expectSelfie;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.matching.Guards;
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
  void namesDecideWhenOneCopyResemblesClearlyMore() throws IOException {
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
  void activityDecidesWhenNamesDoNotOriginalBeforeFork() throws IOException {
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
  void activityDecidesByCommitsThenLastActivity() throws IOException {
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
  void equallyActiveOriginalsAreSplitByTheMostRecentActivity() throws IOException {
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
  // Intended (DerivedMethods.tieBreak): two dead copies are not told apart, a person decides.
  void twoDeadOriginalsAreLeftToAPerson() throws IOException {
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
  void aPickOnARejectedPathLeavesTheProjectUnpaired() throws IOException {
    Csv.Table inv =
        inventory(
            dir,
            "team-a/dup,dup,1,false,9,2026-01-01,false,false,",
            "team-b/dup,dup,2,false,0,2025-01-01,false,false,");
    Csv.Table sonar = sonar(dir, "dup,Dup,,,,");
    SonarToGitlab.Result r =
        SonarToGitlab.map(
            sonar,
            inv,
            List.of(),
            new SonarToGitlab.Rejections(Set.of(), Set.of("team-a/dup")),
            new Guards(0.5, 0.1));
    expectSelfie(describe(r))
        .toBe(
            "dup ->  | aucune | none | - | team-a/dup (0.84), team-b/dup (0.84) | homonymes non départagés (nom court de la clé = dernier segment / nom GitLab)");
  }

  @Test
  void moreThanFiveHomonymsListOnlyTheFirstFive() throws IOException {
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
  void derivedMethodsAreReplayedOnExactLiaisonPairs() throws IOException {
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
