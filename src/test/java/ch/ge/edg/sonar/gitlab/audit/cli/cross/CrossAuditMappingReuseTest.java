package ch.ge.edg.sonar.gitlab.audit.cli.cross;

import static ch.ge.edg.sonar.gitlab.audit.cli.cross.CrossAuditFixtures.csv;
import static ch.ge.edg.sonar.gitlab.audit.cli.cross.CrossAuditFixtures.read;
import static ch.ge.edg.sonar.gitlab.audit.cli.cross.CrossAuditFixtures.scrub;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.SuspectedBug;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The appariement.csv kept between runs: hand-made decisions (manuel, rejete) survive, pairs to a
 * vanished key are recomputed, rows for projects that left the selection are kept, the previous
 * version goes to .bak, and rejections also steer the pass over the whole of GitLab.
 */
class CrossAuditMappingReuseTest {

  @TempDir Path dir;

  private Path sonar;
  private Path gitlab;
  private Path mapping;

  @BeforeEach
  void inputs() throws IOException {
    sonar = CrossAuditFixtures.sonar(dir);
    gitlab = CrossAuditFixtures.gitlab(dir);
    CrossAuditFixtures.inventory(dir);
    mapping =
        csv(
            dir.resolve("appariement.csv"),
            "gauche_path,droite_key,methode,confiance",
            "team-b/delta-portal,delta-portal-app,,manuel",
            "team-a/alpha-service,alpha-key,clé lue dans la CI,rejete",
            "team-g/legacy,,,rejete",
            "team-f/mu-tool,,,rejected",
            "gone/project,ghost-key,clé lue dans la CI,exact",
            "team-c/zeta-web,zeta-renamed,nom du pom = nom Sonar,derived",
            "team-d/eta-one,vanished-key,,manuel",
            "team-a/beta-lib,org.example:beta-lib,clé Maven du pom,exact",
            "team-d/theta-two,theta-guess,noms ressemblants (TF-IDF),suggestion");
  }

  private Cli.Result run(String... extra) {
    String[] base = {
      "--sonar", sonar.toString(),
      "--gitlab", gitlab.toString(),
      "--out", dir.resolve("croisement.csv").toString(),
      "--color", "never"
    };
    String[] args = new String[base.length + extra.length];
    System.arraycopy(base, 0, args, 0, base.length);
    System.arraycopy(extra, 0, args, base.length, extra.length);
    return CrossAuditFixtures.run(args);
  }

  @Test
  void hand_made_decisions_are_replayed_and_the_old_file_kept_as_bak() throws IOException {
    String before = read(mapping);
    Cli.Result r = run();

    assertThat(r.exitCode()).isZero();
    assertThat(r.err()).isEmpty();
    assertThat(read(dir.resolve("appariement.csv.bak"))).isEqualTo(before);
    expectSelfie(scrub(r.out(), dir)).toMatchDisk("stdout");
    expectSelfie(read(mapping)).toMatchDisk("appariement");
    expectSelfie(read(dir.resolve("croisement.csv"))).toMatchDisk("croisement");
    expectSelfie(read(dir.resolve("sonar-gitlab.csv"))).toMatchDisk("sonarGitlab");
  }

  @Test
  void a_second_run_keeps_the_rejection_by_path_only() throws IOException {
    run();
    String afterFirst = read(mapping);
    Cli.Result r = run();

    assertThat(r.exitCode()).isZero();
    assertThat(read(dir.resolve("appariement.csv.bak"))).isEqualTo(afterFirst);
    expectSelfie(scrub(r.out(), dir)).toMatchDisk("stdout");
    expectSelfie(read(mapping)).toMatchDisk("appariement");
    expectSelfie(read(dir.resolve("sonar-gitlab.csv"))).toMatchDisk("sonarGitlab");
  }

  @Test
  @SuspectedBug("The rewritten rejected row loses its droite_key, so the pair comes back next run")
  void a_rejected_pair_comes_back_in_sonar_gitlab_on_the_second_run() throws IOException {
    String rejectedPair = "\"alpha-key\";\"Alpha Service\";\"team-a/alpha-service\";";
    run();
    assertThat(read(mapping)).contains("\"team-a/alpha-service\";\"\";\"rejeté à la main\"");
    assertThat(read(dir.resolve("sonar-gitlab.csv"))).doesNotContain(rejectedPair);

    run();

    assertThat(read(dir.resolve("sonar-gitlab.csv"))).contains(rejectedPair);
  }

  @Test
  void reapparier_recomputes_exact_and_derived_but_keeps_manual_and_rejected() throws IOException {
    Cli.Result r = run("--reapparier");

    assertThat(r.exitCode()).isZero();
    expectSelfie(scrub(r.out(), dir)).toMatchDisk("stdout");
    expectSelfie(read(mapping)).toMatchDisk("appariement");
  }

  @Test
  void an_appariement_without_its_columns_is_refused() throws IOException {
    csv(mapping, "gauche_path,confiance", "team-a/alpha-service,exact");
    Cli.Result r = run();
    assertThat(r.exitCode()).isEqualTo(1);
    assertThat(r.err()).contains("colonne « droite_key » absente de " + mapping);
  }
}
