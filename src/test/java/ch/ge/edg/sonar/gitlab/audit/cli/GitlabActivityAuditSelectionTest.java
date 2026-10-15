package ch.ge.edg.sonar.gitlab.audit.cli;

import static ch.ge.edg.sonar.gitlab.audit.cli.GitlabAuditFake.commit;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitlabAuditFake.project;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.SuspectedBug;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class GitlabActivityAuditSelectionTest extends GitlabActivityAuditTestBase {

  private final List<ObjectNode> projects = new ArrayList<>();

  /** A project with {@code history} commits in total and {@code n} in the window. */
  private ObjectNode add(long id, String path, @Nullable Long history, int n, int authors) {
    ObjectNode p = project(id, path);
    if (history == null) p.remove("statistics");
    else ((ObjectNode) p.get("statistics")).put("commit_count", history);
    projects.add(p);
    List<ObjectNode> commits = new ArrayList<>();
    OffsetDateTime day = OffsetDateTime.parse(GitlabAuditFake.FUTURE);
    for (int i = 0; i < n; i++) {
      commits.add(
          commit(id + "-" + i, "dev" + id + "-" + (i % authors), day.minusDays(i).toString()));
    }
    fake.commits(id, commits);
    return p;
  }

  private void park() {
    add(1, "team-a/a1", 50L, 30, 2);
    add(2, "team-a/a2", 500L, 28, 2);
    add(3, "team-a/a3", 5000L, 26, 2);
    add(4, "team-a/a4", 50000L, 24, 2);
    add(5, "team-a/a5", 99L, 22, 1);
    add(6, "team-b/b1", 100L, 20, 2);
    ObjectNode b2 = add(7, "team-b/b2", null, 18, 2);
    b2.putObject("statistics").put("repository_size", 2_000_000);
    add(8, "team-b/b3", null, 16, 1);
    add(9, "team-c/c1", 999L, 14, 2);
    add(10, "team-c/c2", 1000L, 12, 2);
    add(11, "team-c/c3", 9999L, 10, 1);
    add(12, "team-c/c4", 10000L, 8, 2);
    add(13, "team-d/d1", 10L, 6, 2);
    add(14, "team-d/d2", 10L, 6, 2);
    add(15, "team-d/d3", 10L, 6, 2);
  }

  private Cli.Result run(String... extra) {
    fake.enterprise(false).group("team", projects.toArray(ObjectNode[]::new));
    List<String> args =
        new ArrayList<>(List.of("--group", "team", "--out-dir", tmp.toString(), "--graphql=false"));
    args.addAll(List.of(extra));
    return fake.run(args.toArray(String[]::new));
  }

  @Test
  void quotasMixStrataNamespacesSoloAuthorsAndAWitness() {
    park();
    Cli.Result r = run("--top", "10");
    assertOk(r);
    expectSelfie(out(r)).toMatchDisk("stdout");
    expectSelfie(file(tmp.resolve("inventaire.csv"))).toMatchDisk("csv");
  }

  @Test
  void anotherSeedDrawsAnotherWitness() {
    park();
    Cli.Result r = run("--top", "10", "--seed", "1");
    assertOk(r);
    expectSelfie(file(tmp.resolve("inventaire.csv"))).toMatchDisk();
  }

  @Test
  void aLargeBudgetTakesEveryoneAndTheRestGoesBackToTheRanking() {
    park();
    Cli.Result r = run("--top", "40");
    assertOk(r);
    expectSelfie(out(r)).toMatchDisk("stdout");
    expectSelfie(file(tmp.resolve("inventaire.csv"))).toMatchDisk("csv");
  }

  @Test
  @SuspectedBug("--top 1 should select one project, not one per tranche (three)")
  void aBudgetOfOneStillFillsEveryTrancheAndSelectsThree() {
    park();
    Cli.Result r = run("--top", "1", "--floor", "0");
    assertOk(r);
    assertThat(r.out())
        .contains("  Budget                          : 1\n")
        .contains("  Sélectionnés                    : 3\n")
        .contains("  Namespaces représentés          : ")
        .contains("(plafond 3 par namespace)");
  }

  @Test
  void theRemainderRespectsTheNamespaceCap() {
    for (int i = 0; i < 8; i++) add(1 + i, "team-a/p" + i, 50L, 10 + i, 2);
    Cli.Result r = run("--top", "20", "--validation-sample", "0");
    assertOk(r);
    String inv = file(tmp.resolve("inventaire.csv"));
    assertThat(r.out())
        .contains("  Sélectionnés                    : 6\n")
        .contains("    tête de strate                   : 3\n")
        .contains("    témoin aléatoire                 : 3\n")
        .contains("(plafond 3 par namespace)");
    assertThat(inv).doesNotContain("reliquat").doesNotContain("mono-auteur");
  }

  @Test
  @SuspectedBug("the single-author and witness slices should respect the per-namespace cap")
  void soloAuthorsAndTheWitnessGoPastTheNamespaceCap() {
    for (int i = 0; i < 10; i++) add(1 + i, "team-a/p" + i, 50L, 10 + i, i % 2 == 0 ? 1 : 2);
    Cli.Result r = run("--top", "20", "--validation-sample", "0");
    assertOk(r);
    assertThat(r.out())
        .contains("  Sélectionnés                    : 8\n")
        .contains("    tête de strate                   : 3\n")
        .contains("    mono-auteur, forte activité      : 2\n")
        .contains("    témoin aléatoire                 : 3\n")
        .contains("  Namespaces représentés          : 1 (plafond 3 par namespace)");
  }

  @Test
  @SuspectedBug("the single-author and witness slices should stay within the --top budget")
  void soloAuthorsAndTheWitnessGoPastABudgetOfTwo() {
    for (int i = 0; i < 6; i++) add(1 + i, "team-" + i + "/p", 50L, 10 + i, i % 2 == 0 ? 1 : 2);
    Cli.Result r = run("--top", "2", "--validation-sample", "0");
    assertOk(r);
    assertThat(r.out())
        .contains("  Budget                          : 2\n")
        .contains("  Sélectionnés                    : 3\n")
        .contains("    mono-auteur, forte activité      : 1\n")
        .contains("    témoin aléatoire                 : 1\n");
  }

  @Test
  void aZeroWindowGivesNoWeeklyRate() {
    add(1, "team-a/alpha", 50L, 6, 1);
    Path csv = tmp.resolve("inventaire.csv");
    Cli.Result r = run("--since", "0", "--floor", "0");
    assertOk(r);
    assertThat(file(csv)).contains(";\"0.00\";\"false\";");
  }
}
