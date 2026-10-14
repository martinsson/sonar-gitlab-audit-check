package ch.ge.edg.sonar.gitlab.audit.cli;

import static ch.ge.edg.sonar.gitlab.audit.cli.SonarToGitlabFixtures.describe;
import static ch.ge.edg.sonar.gitlab.audit.cli.SonarToGitlabFixtures.inventory;
import static ch.ge.edg.sonar.gitlab.audit.cli.SonarToGitlabFixtures.map;
import static ch.ge.edg.sonar.gitlab.audit.cli.SonarToGitlabFixtures.sonar;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The derived methods of SonarToGitlab, one at a time, each designating a single repository. */
class SonarToGitlabDerivedTest {

  @TempDir Path dir;

  private Csv.Table inv() throws IOException {
    return inventory(
        dir,
        "team-h/import-me,import-me,1,false,5,2026-01-01,false,false,",
        "team-h/uuid-repo,uuid-repo,2,false,5,2026-01-01,false,false,",
        "team-h/plain,plain,3,false,5,2026-01-01,false,false,",
        "dept/sub/repo-x,repo-x,4,false,5,2026-01-01,false,false,",
        "dept/sub2/123-ticket,123-ticket,5,false,5,2026-01-01,false,false,",
        "dept/sub3/repo-y,repo-y,6,false,5,2026-01-01,false,false,",
        "dept/sub4/repo-z,repo-z,7,false,5,2026-01-01,false,false,",
        "dept/sub5/xyz,Fancy Name,8,false,5,2026-01-01,false,false,",
        "dept/sub6/named,named,9,false,5,2026-01-01,false,false,",
        "dept/sub7/full,full,10,false,5,2026-01-01,false,false,");
  }

  @Test
  void each_derived_method_in_turn() throws IOException {
    Csv.Table sonar =
        sonar(
            dir,
            "team-h/import-me_AYabcdefghijklmnopqr,Import,,,,",
            "team-h/uuid-repo_0123abcd-0123-4567-89ab-0123456789ab,Uuid,,,,",
            "team-h:plain,P,,,,",
            "sub_repo-x,Rx,,,,",
            "sub2-ticket,Tk,,,,",
            "org.example:repo-y,Ry,,,,",
            "77-repo-z,Rz,,,,",
            "fancy-name,F,,,,",
            "unrelated-key,(named) Something,,,,",
            "other-key,dept/sub7/full,,,,");
    SonarToGitlab.Result r = map(sonar, inv(), List.of());
    expectSelfie(describe(r))
        .toBe(
"""
team-h/import-me_AYabcdefghijklmnopqr -> team-h/import-me | clé d'import GitLab (chemin_id) = chemin GitLab | derived | 0.86 |  |\s
team-h/uuid-repo_0123abcd-0123-4567-89ab-0123456789ab -> team-h/uuid-repo | clé d'import GitLab (chemin_id) = chemin GitLab | derived | 0.64 |  |\s
team-h:plain -> team-h/plain | clé normalisée = chemin GitLab | derived | 0.96 |  |\s
sub_repo-x -> dept/sub/repo-x | clé normalisée = fin du chemin GitLab | derived | 0.51 |  |\s
sub2-ticket -> dept/sub2/123-ticket | clé normalisée = fin du chemin GitLab | derived | 0.66 |  |\s
org.example:repo-y -> dept/sub3/repo-y | nom court de la clé = dernier segment / nom GitLab | derived | 0.25 |  |\s
77-repo-z -> dept/sub4/repo-z | nom court de la clé = dernier segment / nom GitLab | derived | 0.40 |  |\s
fancy-name -> dept/sub5/xyz | nom court de la clé = dernier segment / nom GitLab | derived | 0.00 |  |\s
unrelated-key -> dept/sub6/named | nom Sonar = dernier segment / nom GitLab | derived | 0.43 |  |\s
other-key -> dept/sub7/full | nom Sonar = dernier segment / nom GitLab | derived | 0.62 |  |\s""");
    assertThat(r.checks()).isEmpty();
  }

  @Test
  void an_import_key_needs_a_twenty_character_id_or_a_uuid() throws IOException {
    Csv.Table sonar =
        sonar(
            dir,
            "team-h/import-me_AYabcdefghijklmnopq,Short,,,,",
            "team-h/import-me_0123abcd-0123-4567-89ab-0123456789aZ,BadUuid,,,,");
    expectSelfie(describe(map(sonar, inv(), List.of())))
        .toBe(
"""
team-h/import-me_AYabcdefghijklmnopq -> team-h/import-me | noms ressemblants (TF-IDF) | suggestion | 0.64 | team-h/plain (0.10) |\s
team-h/import-me_0123abcd-0123-4567-89ab-0123456789aZ ->  | aucune | none | - | team-h/import-me (0.52) | déjà proposé à team-h/import-me_AYabcdefghijklmnopq""");
  }

  @Test
  void a_rejected_pair_is_proposed_by_no_method() throws IOException {
    Csv.Table sonar = sonar(dir, "team-h:plain,plain,,,,");
    SonarToGitlab.Result r =
        SonarToGitlab.map(
            sonar,
            inv(),
            List.of(),
            new SonarToGitlab.Rejections(Set.of("team-h:plain\nteam-h/plain"), Set.of()),
            new SonarToGitlab.NameThresholds(0.5, 0.1));
    expectSelfie(describe(r))
        .toBe(
            "team-h:plain ->  | aucune | none | - | team-h/uuid-repo (0.10), team-h/import-me (0.08) | sous le seuil");
  }

  @Test
  void blank_ids_names_and_keys_index_nothing() throws IOException {
    Csv.Table inv =
        inventory(
            dir,
            "team-h/no-id,,,false,5,2026-01-01,false,false,",
            "team-h/other,other,7,false,5,2026-01-01,false,false,");
    Csv.Table sonar = sonar(dir, "---,,gitlab,,,", "no-id,,,,,");
    expectSelfie(describe(map(sonar, inv, List.of())))
        .toBe(
"""
--- ->  | aucune | none | - |  | aucun candidat
no-id -> team-h/no-id | nom court de la clé = dernier segment / nom GitLab | derived | 0.94 |  |\s""");
  }

  @Test
  void the_joined_paths_are_those_of_sure_pairs_only() throws IOException {
    Csv.Table sonar = sonar(dir, "team-h:plain,P,,,,", "nothing-alike,Zzz,,,,");
    SonarToGitlab.Result r = map(sonar, inv(), List.of());
    assertThat(r.joinedPaths()).isEqualTo(Set.of("team-h/plain"));
    assertThat(r.pairs().get(1).joined()).isFalse();
    assertThat(r.pairs().get(1).path()).isEmpty();
  }
}
