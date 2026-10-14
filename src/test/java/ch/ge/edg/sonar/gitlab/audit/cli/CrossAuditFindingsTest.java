package ch.ge.edg.sonar.gitlab.audit.cli;

import static ch.ge.edg.sonar.gitlab.audit.cli.CrossAuditFixtures.GITLAB_HEADER;
import static ch.ge.edg.sonar.gitlab.audit.cli.CrossAuditFixtures.SONAR_HEADER;
import static ch.ge.edg.sonar.gitlab.audit.cli.CrossAuditFixtures.csv;
import static ch.ge.edg.sonar.gitlab.audit.cli.CrossAuditFixtures.read;
import static ch.ge.edg.sonar.gitlab.audit.cli.CrossAuditFixtures.run;
import static ch.ge.edg.sonar.gitlab.audit.cli.CrossAuditFixtures.scrub;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every finding at its thresholds (20 commits, 0 MR, coverage 20, 5 analyses, 500 lines), with
 * several examples each so that the sort order shows. Every GitLab project carries the Sonar key in
 * cle_sonar, so the join is exact and more than half the projects are paired; no inventory.
 */
class CrossAuditFindingsTest {

  @TempDir Path dir;

  private Path gitlab() throws IOException {
    return csv(
        dir.resolve("pratiques.csv"),
        GITLAB_HEADER,
        "grp/g01,g01,1,,20,0,false,k01,,,,,,",
        "grp/g02,g02,2,,19,0,false,k02,,,,,,",
        "grp/g03,g03,3,,60,1,false,k03,,,,,,",
        "grp/g04,g04,4,,25,0,false,k04,,,,,,",
        "grp/g05,g05,5,,30,0,false,k05,,,,,,",
        "grp/g06,g06,6,,0,0,false,k06,,,,,,",
        "grp/g07,g07,7,,0,0,false,k07,,,,,,",
        "grp/g08,g08,8,,,0,false,k08,,,,,,",
        "grp/g09,g09,9,,0,0,false,k09,,,,,,",
        "grp/g10,g10,10,,5,0,false,k10,,,,,,",
        "grp/g11,g11,11,,5,0,false,k11,,,,,,",
        "grp/g12,g12,12,,5,0,false,k12,,,,,,",
        "grp/g13,g13,13,,5,0,false,k13,,,,,,",
        "grp/g14,g14,14,,5,0,false,k14,,,30,500,3,false",
        "grp/g15,g15,15,,5,0,false,k15,,,30,1000,0,false",
        "grp/g16,g16,16,,5,0,false,k16,,,30,499,10,false",
        "grp/g17,g17,17,,5,0,false,k17,,,30,1000,10,false",
        "grp/g18,g18,18,,5,0,false,k18,,,30,1000,10,false",
        "grp/g19,g19,19,,5,0,false,k19,,,30,0,0,false",
        "grp/g20,g20,20,,5,0,false,k20,,,,1000,10,false",
        "other/u1,u1,31,,20,0,true,,,,,,,",
        "other/u2,u2,32,,19,0,false,,,,,,,",
        "other/u3,u3,33,,100,0,false,,,,,,,",
        "other/u4,u4,34,,,0,TRUE,nowhere,,,,,,");
  }

  private Path sonar() throws IOException {
    return csv(
        dir.resolve("projets.csv"),
        SONAR_HEADER,
        "k01,K01,2026-01-01,100,19.9,0,0,1,,,,,",
        "k02,K02,2026-01-01,100,5,0,0,9,,,,,",
        "k03,K03,2026-01-01,100,5,0,0,9,,,,,",
        "k04,K04,2026-01-01,100,20,0,0,4,,,,,",
        "k05,K05,2026-01-01,100,,0,0,0,,,,,",
        "k06,K06,2026-01-01,100,,,50,,,,,,",
        "k07,K07,2026-01-01,100,,,500,,,,,,",
        "k08,K08,2026-01-01,100,,,999,,,,,,",
        "k09,K09,2026-01-01,100,,,0,,,,,,",
        "k10,K10,2026-01-01,100,0,3,,,,,,,",
        "k11,K11,2026-01-01,100,0,0,,,,,,,",
        "k12,K12,2026-01-01,100,,5,,,,,,,",
        "k13,K13,2026-01-01,100,0,1,,,,,,,",
        "k14,K14,2026-01-01,100,,,,,,,,5,3",
        "k15,K15,2026-01-01,100,,,,,,,,9,10",
        "k16,K16,2026-01-01,100,,,,,,,,9,10",
        "k17,K17,2026-01-01,100,,,,,,,,4,10",
        "k18,K18,2026-01-01,100,,,,,,,,9,-2",
        "k19,K19,2026-01-01,100,,,,,,,,9,10",
        "k20,K20,2026-01-01,100,,,,,,,,9,10",
        "s-lonely,Lonely,,,,,,,,,,,");
  }

  @Test
  void each_finding_keeps_exactly_the_projects_past_its_thresholds_in_order() throws IOException {
    Path out = dir.resolve("croisement.csv");
    Cli.Result r =
        run(
            "--sonar", sonar().toString(),
            "--gitlab", gitlab().toString(),
            "--out", out.toString(),
            "--color", "never");

    assertThat(r.exitCode()).isZero();
    assertThat(r.err()).isEmpty();
    expectSelfie(scrub(r.out(), dir)).toMatchDisk("stdout");
    expectSelfie(read(out)).toMatchDisk("croisement");
    assertThat(dir.resolve("sonar-gitlab.csv")).doesNotExist();
  }
}
