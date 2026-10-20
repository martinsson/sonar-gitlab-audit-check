package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.GitlabAuditFake.FUTURE;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.GitlabAuditFake.PAST;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.GitlabAuditFake.commit;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.GitlabAuditFake.daily;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.GitlabAuditFake.merge;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.GitlabAuditFake.project;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Request;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import ch.ge.edg.sonar.gitlab.audit.testing.SuspectedBug;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class GitlabActivityAuditActivityTest extends GitlabActivityAuditTestBase {

  private static String lastCommit(@Nullable String sha, String date) {
    return "{\"repository\":{\"rootRef\":\"main\",\"tree\":{\"lastCommit\":{\"sha\":"
        + (sha == null ? "null" : "\"" + sha + "\"")
        + ",\"committedDate\":\""
        + date
        + "\"}}}}";
  }

  private Path csv() {
    return tmp.resolve("inv.csv");
  }

  private Cli.Result runGroup(String... extra) {
    List<String> args = new ArrayList<>(List.of("--group", "team-a", "--csv", csv().toString()));
    args.addAll(List.of(extra));
    return fake.run(args.toArray(String[]::new));
  }

  @Test
  void graphqlDatesTheLastCommitAndSparesTheStaleProjects() {
    fake.enterprise(false)
        .group(
            "team-a",
            project(1, "team-a/alpha"),
            project(2, "team-a/beta"),
            project(3, "team-a/gamma"))
        .json(
            "/api/graphql",
            "{\"data\":{\"p0\":"
                + lastCommit("sha-alpha", "2099-02-01T00:00:00Z")
                + ",\"p1\":"
                + lastCommit(null, PAST)
                + ",\"p2\":{\"repository\":null}}}")
        .commits(1, daily("a", "alice", 6))
        .commits(3, daily("c", "carol", 8));

    Cli.Result r = runGroup();

    assertOk(r);
    expectSelfie(out(r)).toMatchDisk("stdout");
    expectSelfie(file(csv())).toMatchDisk("csv");
    assertThat(fake.server.requests("/api/v4/projects/2/repository/commits")).isEmpty();
    assertThat(fake.server.requests("/api/graphql")).hasSize(1);
  }

  @Test
  @SuspectedBug(
      "a project GraphQL dates before the window should get 0 merges, reverts and active days")
  void aProjectGraphqlDatesBeforeTheWindowGetsZeroCommitsButBlankMergesAndActiveDays()
      throws IOException {
    fake.enterprise(false)
        .group("team-a", project(1, "team-a/alpha"))
        .json("/api/graphql", "{\"data\":{\"p0\":" + lastCommit("sha-old", PAST) + "}}");

    assertOk(runGroup("--floor", "0"));

    Csv.Row alpha = Csv.read(csv()).rows().getFirst();
    assertThat(alpha.str("commits_window")).isEqualTo("0");
    assertThat(alpha.str("authors_window")).isEqualTo("0");
    assertThat(alpha.str("merge_commits")).isEmpty();
    assertThat(alpha.str("reverts")).isEmpty();
    assertThat(alpha.str("active_days")).isEmpty();
  }

  @Test
  void graphqlErrorsFallBackToRest() {
    fake.enterprise(false)
        .group("team-a", project(1, "team-a/alpha"))
        .json("/api/graphql", "{\"data\":{\"p0\":null},\"errors\":[{\"message\":\"no\"}]}")
        .commits(1, daily("a", "alice", 6));
    Cli.Result r = runGroup();
    assertThat(r.out()).contains("  GraphQL indisponible sur cette instance — repli REST.\n");
    assertThat(r.out()).doesNotContain("Dernier commit daté");
    assertThat(file(csv())).contains("\"2099-01-01T10:00Z\";\"6\"");
  }

  @Test
  void graphqlWithoutDataFallsBackToRest() {
    fake.enterprise(false)
        .group("team-a", project(1, "team-a/alpha"))
        .json("/api/graphql", "{\"other\":1}")
        .commits(1, daily("a", "alice", 6));
    assertThat(runGroup().out()).contains("GraphQL indisponible sur cette instance");
  }

  @Test
  void aFailingSecondGraphqlBatchKeepsTheFirst() {
    List<ObjectNode> projects = new ArrayList<>();
    StringBuilder data = new StringBuilder("{\"data\":{");
    for (int i = 0; i < 51; i++) {
      projects.add(project(100 + i, "team-a/p\"" + i));
      data.append(i == 0 ? "" : ",").append("\"p").append(i).append("\":");
      data.append(lastCommit("s" + i, PAST));
    }
    data.append("}}");
    AtomicInteger calls = new AtomicInteger();
    fake.enterprise(false)
        .group("team-a", projects.toArray(ObjectNode[]::new))
        .route(
            "/api/graphql",
            req ->
                calls.incrementAndGet() == 1
                    ? Response.json(data.toString())
                    : Response.status(500, "{}"));
    Cli.Result r = runGroup("--validation-sample", "0");
    assertOk(r);
    assertThat(r.out())
        .contains("  Dernier commit daté par GraphQL : 50 projets en 2 requêtes\n")
        .contains("    dont aucun commit dans la fenêtre : 50\n")
        .contains("  Comptés par pagination REST     : 1 projets\n")
        .contains("  Activité non mesurable          : 1\n");
    assertThat(fake.server.requests("/api/v4/projects/150/repository/commits")).hasSize(1);
    assertThat(fake.server.requests("/api/v4/projects/149/repository/commits")).isEmpty();
  }

  @Test
  void graphqlCanBeTurnedOff() {
    fake.enterprise(false)
        .group("team-a", project(1, "team-a/alpha"))
        .commits(1, daily("a", "alice", 6));
    Cli.Result r = runGroup("--graphql=false");
    assertOk(r);
    assertThat(fake.server.requests("/api/graphql")).isEmpty();
    assertThat(r.out()).doesNotContain("GraphQL indisponible");
    assertThat(r.out()).contains("  4 appels API (4 GET, 0 GraphQL, 0 429 réessayés).\n");
  }

  @Test
  void activeWithinSkipsQuietProjectsByTheirGraphqlDate() {
    fake.enterprise(false)
        .group("team-a", project(1, "team-a/alpha"), project(2, "team-a/quiet"))
        .json(
            "/api/graphql",
            "{\"data\":{\"p0\":"
                + lastCommit("s0", FUTURE)
                + ",\"p1\":"
                + lastCommit("s1", PAST)
                + "}}")
        .commits(1, daily("a", "alice", 6));
    Cli.Result r = runGroup("--since", "36500", "--active-within", "30");
    assertOk(r);
    expectSelfie(out(r)).toMatchDisk("stdout");
    expectSelfie(file(csv())).toMatchDisk("csv");
    assertThat(fake.server.requests("/api/v4/projects/2/repository/commits")).isEmpty();
  }

  @Test
  void activeWithinFallsBackOnLastActivityWithoutGraphql() {
    fake.enterprise(false)
        .group(
            "team-a",
            project(1, "team-a/alpha"),
            project(2, "team-a/quiet").put("last_activity_at", PAST))
        .commits(1, daily("a", "alice", 6));
    Cli.Result r = runGroup("--since", "36500", "--active-within", "30", "--graphql=false");
    assertOk(r);
    assertThat(r.out())
        .contains("  Coupe --active-within 30        : 1 projets écartés sur 2\n")
        .contains("    Gratuit — GraphQL indisponible, la coupe porte sur last_activity_at,\n")
        .contains("    qui majore l'activité : elle écarte donc moins, jamais à tort.\n")
        .contains("  1 projets laissés de côté par --active-within 30, non mesurés.\n");
    assertThat(file(csv())).contains("\"hors coupe d'activité (dernière activité > 30j)\"");
  }

  @Test
  void activeWithinThatLeavesNothingToCount() {
    fake.enterprise(false)
        .group("team-a", project(1, "team-a/quiet").put("last_activity_at", PAST));
    Cli.Result r = runGroup("--since", "36500", "--active-within", "1", "--graphql=false");
    assertOk(r);
    expectSelfie(out(r)).toMatchDisk();
  }

  @Test
  void theValidationSampleCountsCommitsUnderTheCut() {
    ObjectNode[] sleepy = new ObjectNode[4];
    for (int i = 0; i < 4; i++) {
      sleepy[i] = project(10 + i, "team-a/sleepy-" + i).put("last_activity_at", PAST);
    }
    fake.enterprise(false)
        .group("team-a", project(1, "team-a/alpha"), sleepy[0], sleepy[1], sleepy[2], sleepy[3])
        .commits(1, daily("a", "alice", 6))
        .commits(10, daily("x", "dave", 2))
        .commitsWithoutTotal(11, daily("y", "erin", 3).toArray(ObjectNode[]::new))
        .route("/api/v4/projects/12/repository/commits", req -> Response.status(403, "{}"));
    Cli.Result r = runGroup();
    assertOk(r);
    expectSelfie(out(r)).toMatchDisk("stdout");
    expectSelfie(file(csv())).toMatchDisk("csv");
    List<Request> counts = fake.server.requests("/api/v4/projects/10/repository/commits");
    assertThat(counts.getFirst().param("per_page")).isEqualTo("1");
    assertThat(counts.getFirst().param("ref_name")).isEqualTo("main");
    assertThat(counts.getFirst().param("since")).isNotBlank();
    assertThat(fake.server.requests("/api/v4/projects/11/repository/commits"))
        .extracting(q -> q.param("per_page"))
        .containsExactly("1", "100");
  }

  @Test
  void theValidationSampleIsDrawnWithTheSeedAndCanBeTurnedOff() {
    ObjectNode[] sleepy = new ObjectNode[5];
    for (int i = 0; i < 5; i++) {
      sleepy[i] = project(10 + i, "team-a/sleepy-" + i).put("last_activity_at", PAST);
    }
    fake.enterprise(false).group("team-a", sleepy);
    Cli.Result one = runGroup("--validation-sample", "2", "--seed", "7");
    assertThat(one.out())
        .contains("Échantillon de contrôle sous la coupe : 2 projets, 0 faux négatif");
    assertThat(fake.server.requests())
        .filteredOn(q -> q.path().endsWith("/repository/commits"))
        .extracting(Request::path)
        .containsExactlyInAnyOrder(
            "/api/v4/projects/13/repository/commits", "/api/v4/projects/14/repository/commits");
    Cli.Result off = runGroup("--validation-sample", "0");
    assertThat(off.out())
        .doesNotContain("Échantillon")
        .doesNotContain("Filtre de fraîcheur validé");
  }

  @Test
  void botsMergesRevertsAndIdentitiesAreSortedOut() {
    ObjectNode noEmail = commit("n1", "alice", "2099-01-03T10:00:00Z").put("author_email", "");
    noEmail.put("author_name", "ALICE");
    ObjectNode undated = commit("u1", "carol", FUTURE);
    undated.remove("committed_date");
    fake.enterprise(false)
        .group("team-a", project(1, "team-a/alpha"), project(2, "team-a/robots"))
        .commits(
            1,
            commit("h1", "alice", FUTURE),
            commit("h2", "alice", FUTURE).put("title", "Revert \"change h1\""),
            noEmail,
            merge(commit("m1", "bob", "2099-01-02T10:00:00Z")),
            commit("r1", "renovate", FUTURE),
            commit("r2", "bob", FUTURE).put("title", "  revert: drop it"),
            commit("r3", "bob", FUTURE).put("title", "fix the revert script"),
            commit("r4", "bob", FUTURE).put("title", "reverted things"),
            undated)
        .commits(2, commit("b1", "renovate", FUTURE), commit("b2", "dependabot", FUTURE));
    Cli.Result r = runGroup("--floor", "1");
    assertOk(r);
    expectSelfie(out(r)).toMatchDisk("stdout");
    expectSelfie(file(csv())).toMatchDisk("csv");
  }

  @Test
  void aCustomBotPatternReplacesTheDefault() {
    fake.enterprise(false)
        .group("team-a", project(1, "team-a/alpha"))
        .commits(1, commit("a1", "alice", FUTURE), commit("r1", "renovate", FUTURE));
    runGroup("--bot-pattern", "(?i)alice", "--floor", "0");
    assertThat(file(csv())).contains("\"2099-01-01T10:00Z\";\"1\";\"1\";\"1\"");
  }

  @Test
  void commitPagingStopsAtTheCapAndSaysItIsAFloor() {
    fake.enterprise(false)
        .group(
            "team-a",
            project(1, "team-a/capped"),
            project(2, "team-a/two-pages"),
            project(3, "team-a/exact"))
        .commits(1, daily("a", "alice", 250))
        .commits(2, daily("b", "bob", 150))
        .commits(3, daily("c", "carol", 100));
    Cli.Result r = runGroup("--max-commit-pages", "2", "--graphql=false");
    assertOk(r);
    expectSelfie(out(r)).toMatchDisk("stdout");
    expectSelfie(file(csv())).toMatchDisk("csv");
    assertThat(fake.server.requests("/api/v4/projects/1/repository/commits"))
        .extracting(q -> q.param("page"))
        .containsExactly("1", "2");
    assertThat(fake.server.requests("/api/v4/projects/3/repository/commits"))
        .extracting(q -> q.param("page"))
        .containsExactly("1", "2");
  }

  @Test
  void unreadableCommitsAreUnmeasurableNotZero() {
    fake.enterprise(false)
        .group("team-a", project(1, "team-a/alpha"), project(2, "team-a/secret"))
        .commits(1, daily("a", "alice", 6))
        .route("/api/v4/projects/2/repository/commits", req -> Response.status(403, "{}"));
    Cli.Result r = runGroup();
    assertOk(r);
    assertThat(r.out())
        .contains("  Activité non mesurable          : 1\n")
        .contains("    403 ou erreur sur les commits. Écartés, pas classés à zéro :\n")
        .contains("    « je n'ai pas pu voir » n'est pas « il ne se passe rien ».\n");
    assertThat(file(csv())).contains("\"activité non mesurable (HTTP 403)\"");
  }

  @Test
  void aNonArrayCommitPageEndsTheCount() {
    fake.enterprise(false)
        .group("team-a", project(1, "team-a/alpha"))
        .json("/api/v4/projects/1/repository/commits", "{\"message\":\"odd\"}");
    runGroup("--floor", "0");
    assertThat(file(csv()))
        .contains("\"A(<100)\";\"\";\"0\";\"0\";\"0\";\"0\";\"0\";\"0\";\"0.00\";\"false\";\"\"");
  }

  @Test
  void anImposedFloorReplacesTheDerivedOne() {
    fake.enterprise(false)
        .group("team-a", project(1, "team-a/alpha"), project(2, "team-a/beta"))
        .commits(1, daily("a", "alice", 3))
        .commits(2, daily("b", "bob", 2));
    Cli.Result r = runGroup("--floor", "3");
    assertThat(r.out())
        .contains("  Plancher imposé (--floor)       : 3 commits\n")
        .contains("  Éligibles au tirage             : 1\n")
        .contains("  Cohorte faible activité         : 1\n");
  }

  @Test
  void theDerivedFloorRisesUntilTheBudgetFits() {
    ObjectNode[] ps = new ObjectNode[5];
    for (int i = 0; i < 5; i++) {
      ps[i] = project(1 + i, "team-a/p" + i);
      fake.commits(1 + i, daily("c" + i, "dev" + i, 6 + i));
    }
    fake.enterprise(false).group("team-a", ps);
    Cli.Result r = runGroup("--top", "2");
    assertThat(r.out())
        .contains("  Distribution des commits (déciles) : D1=10 D3=9 D5=8 D7=7 D9=6\n")
        .contains("  Plancher déduit des données     : 8 commits sur 90 jours\n")
        .contains("  Éligibles au tirage             : 3\n");
  }

  @Test
  void theDerivedFloorStopsAtSixty() {
    ObjectNode[] ps = new ObjectNode[3];
    for (int i = 0; i < 3; i++) {
      ps[i] = project(1 + i, "team-a/p" + i);
      fake.commits(1 + i, daily("c" + i, "dev" + i, 70));
    }
    fake.enterprise(false).group("team-a", ps);
    Cli.Result r = runGroup("--top", "1");
    assertThat(r.out()).contains("  Plancher déduit des données     : 60 commits sur 90 jours\n");
  }
}
