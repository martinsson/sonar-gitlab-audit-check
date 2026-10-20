package ch.ge.edg.sonar.gitlab.audit.cli.cross;

import static ch.ge.edg.sonar.gitlab.audit.cli.cross.SonarToGitlabFixtures.checks;
import static ch.ge.edg.sonar.gitlab.audit.cli.cross.SonarToGitlabFixtures.describe;
import static ch.ge.edg.sonar.gitlab.audit.cli.cross.SonarToGitlabFixtures.inventory;
import static ch.ge.edg.sonar.gitlab.audit.cli.cross.SonarToGitlabFixtures.map;
import static ch.ge.edg.sonar.gitlab.audit.cli.cross.SonarToGitlabFixtures.match;
import static ch.ge.edg.sonar.gitlab.audit.cli.cross.SonarToGitlabFixtures.sonar;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.matching.Confidence;
import ch.ge.edg.sonar.gitlab.audit.matching.Guards;
import ch.ge.edg.sonar.gitlab.audit.matching.Match;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pairs taken over from the cross, the DevOps link, and the one-to-one name pass. */
class SonarToGitlabCrossAndNamesTest {

  @TempDir Path dir;

  private Csv.Table selection(String... rows) throws IOException {
    Path sub = Files.createDirectories(dir.resolve("sel"));
    return CrossAuditFixtures.table(sub, "pratiques.csv", "path,name,id", rows);
  }

  @Test
  void sure_pairs_of_the_cross_are_taken_over_and_others_ignored() throws IOException {
    Csv.Table inv =
        inventory(
            dir,
            "team-a/alpha,alpha,1,true,5,2026-01-01,false,false,",
            "team-b/delta,delta,4,true,5,2026-01-01,false,false,");
    Csv.Table sonar =
        sonar(dir, "alpha,Alpha,,,,", "beta,Beta,,,,", "gamma,Gamma,,,,", "delta,Delta,,,,");
    Csv.Table sel =
        selection(
            "team-a/alpha,alpha,1",
            "team-x/alpha-copy,alpha-copy,9",
            "sel/beta,beta,2",
            "sel/gamma,gamma,3",
            "sel/ghost,ghost,8",
            "team-b/delta,delta,4");
    List<Csv.Row> g = sel.rows();
    List<Csv.Row> s = sonar.rows();
    Csv.Row ghost = CrossAuditFixtures.table(dir, "ghost.csv", "key", "ghost").rows().get(0);
    List<Match> cross =
        List.of(
            match(g.get(0), s.get(0), Confidence.EXACT, "clé lue dans la CI"),
            match(g.get(1), s.get(0), Confidence.DERIVED, "clé normalisée = chemin GitLab"),
            match(g.get(2), s.get(1), Confidence.MANUEL, "saisi à la main"),
            match(g.get(3), s.get(2), Confidence.SUGGESTION, "noms ressemblants (TF-IDF)"),
            match(g.get(4), ghost, Confidence.EXACT, "clé lue dans la CI"),
            match(g.get(5), null, Confidence.NONE, "aucune"),
            match(g.get(5), s.get(3), Confidence.DERIVED, "nom du pom = nom Sonar"));
    SonarToGitlab.Result r = map(sonar, inv, cross);
    expectSelfie(describe(r))
        .toBe(
"""
alpha -> team-a/alpha | croisement : clé lue dans la CI | exact | - | clé partagée par team-a/alpha, team-x/alpha-copy |\s
beta -> sel/beta | croisement : saisi à la main | manuel | - |  |\s
gamma ->  | aucune | none | - |  | aucun candidat
delta -> team-b/delta | croisement : nom du pom = nom Sonar | derived | - |  |\s""");
    expectSelfie(checks(r)).toBe("nom court de la clé = dernier segment / nom GitLab = true");
    assertThat(r.pairs().get(1).gitlab()).isSameAs(g.get(2));
    assertThat(r.pairs().get(0).gitlab()).isSameAs(inv.rows().get(0));
    assertThat(r.joinedPaths())
        .containsExactlyInAnyOrder("team-a/alpha", "sel/beta", "team-b/delta");
  }

  @Test
  void the_devops_link_counts_only_for_gitlab_known_ids_and_unrejected_pairs() throws IOException {
    Csv.Table inv =
        inventory(
            dir,
            "team-a/one-repo,one-repo,1,false,5,2026-01-01,false,false,",
            "team-a/two-repo,two-repo,2,false,5,2026-01-01,false,false,");
    Csv.Table sonar =
        sonar(
            dir,
            "k-github,Qwerty,github,1,,",
            "k-unknown,Asdfgh,gitlab,99,,",
            "k-rejected,Zxcvbn,gitlab,2,,",
            "k-ok,Poiuyt,gitlab,1,,");
    SonarToGitlab.Result r =
        SonarToGitlab.map(
            sonar,
            inv,
            List.of(),
            new SonarToGitlab.Rejections(Set.of("k-rejected\nteam-a/two-repo"), Set.of()),
            new Guards(0.5, 0.1));
    expectSelfie(describe(r))
        .toBe(
"""
k-github ->  | aucune | none | - |  | aucun candidat
k-unknown ->  | aucune | none | - |  | aucun candidat
k-rejected ->  | aucune | none | - |  | aucun candidat
k-ok -> team-a/one-repo | liaison DevOps Sonar → GitLab | exact | - |  |\s""");
  }

  @Test
  void the_name_pass_refuses_below_threshold_ambiguous_and_already_claimed() throws IOException {
    Csv.Table inv =
        inventory(
            dir,
            "team-n/orders-service,orders-service,1,false,5,2026-01-01,false,false,",
            "team-n/orders-service-v2,orders-service-v2,2,false,5,2026-01-01,false,false,",
            "team-p/payment-gateway,payment-gateway,3,false,5,2026-01-01,false,false,",
            "team-q/report-engine,report-engine,4,false,5,2026-01-01,false,false,",
            "team-r/taken-repo,taken-repo,5,false,5,2026-01-01,false,false,");
    Csv.Table sonar =
        sonar(
            dir,
            "orders-x,Orders Service Thing,,,,",
            "pay-a,Payment Gateway A,,,,",
            "pay-b,Payment Gateway Alpha,,,,",
            "rep-x,Report Engine Old Version Unused Words Here,,,,",
            "taken-repo,Taken,,,,",
            "tkn,Taken Repo Copy,,,,",
            "qqq,Nothing,,,,");
    expectSelfie(describe(map(sonar, inv, List.of())))
        .toBe(
"""
orders-x ->  | aucune | none | - | team-n/orders-service (0.79), team-n/orders-service-v2 (0.79) | ambigu
pay-a -> team-p/payment-gateway | noms ressemblants (TF-IDF) | suggestion | 0.75 |  |\s
pay-b ->  | aucune | none | - | team-p/payment-gateway (0.61) | déjà proposé à pay-a
rep-x ->  | aucune | none | - | team-q/report-engine (0.43) | sous le seuil
taken-repo -> team-r/taken-repo | nom court de la clé = dernier segment / nom GitLab | derived | 0.91 |  |\s
tkn ->  | aucune | none | - | team-q/report-engine (0.27) | sous le seuil
qqq ->  | aucune | none | - |  | aucun candidat""");
  }

  @Test
  void rejected_paths_and_pairs_are_kept_out_of_the_name_pass() throws IOException {
    Csv.Table inv =
        inventory(
            dir,
            "team-p/payment-gateway,payment-gateway,3,false,5,2026-01-01,false,false,",
            "team-q/report-engine,report-engine,4,false,5,2026-01-01,false,false,");
    Csv.Table sonar = sonar(dir, "pay-a,Payment Gateway A,,,,", "rep-a,Report Engine A,,,,");
    SonarToGitlab.Result r =
        SonarToGitlab.map(
            sonar,
            inv,
            List.of(),
            new SonarToGitlab.Rejections(
                Set.of("rep-a\nteam-q/report-engine"), Set.of("team-p/payment-gateway")),
            new Guards(0.5, 0.1));
    expectSelfie(describe(r))
        .toBe(
"""
pay-a ->  | aucune | none | - |  | aucun candidat
rep-a ->  | aucune | none | - |  | aucun candidat""");
  }

  @Test
  void threshold_and_margin_are_parameters() throws IOException {
    Csv.Table inv =
        inventory(
            dir,
            "team-n/orders-service,orders-service,1,false,5,2026-01-01,false,false,",
            "team-n/orders-service-v2,orders-service-v2,2,false,5,2026-01-01,false,false,");
    Csv.Table sonar = sonar(dir, "orders-x,Orders Service Thing,,,,");
    String loose =
        describe(
            SonarToGitlab.map(
                sonar,
                inv,
                List.of(),
                new SonarToGitlab.Rejections(Set.of(), Set.of()),
                new Guards(0.0, 0.0)));
    String strict =
        describe(
            SonarToGitlab.map(
                sonar,
                inv,
                List.of(),
                new SonarToGitlab.Rejections(Set.of(), Set.of()),
                new Guards(0.99, 0.0)));
    expectSelfie(loose + "\n" + strict)
        .toBe(
"""
orders-x -> team-n/orders-service | noms ressemblants (TF-IDF) | suggestion | 0.69 | team-n/orders-service-v2 (0.69) |\s
orders-x ->  | aucune | none | - | team-n/orders-service (0.69), team-n/orders-service-v2 (0.69) | sous le seuil""");
  }
}
