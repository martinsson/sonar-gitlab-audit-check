package ch.ge.edg.sonar.gitlab.audit.dora;

import static ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures.git;
import static ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures.run;
import static ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures.upstream;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.gocd.GocdApi;
import ch.ge.edg.sonar.gitlab.audit.gocd.Run;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The range rule, on hand-made runs shaped like the GoCD 25.3 captures: prod/1 took recette/3,
 * prod/2 took recette/5, so prod/2 embarks what recette/4 and recette/5 brought, and so on down to
 * git.
 */
class DeployedCommitsTest {

  private static final String URL = GocdFixtures.HELLO;

  private static List<Run> prodRuns(GocdApi api) {
    return api.history("prod", r -> false, 10).runs();
  }

  private static List<String> vias(List<DeployedCommit> commits) {
    return commits.stream().map(c -> c.revision() + "@" + c.via()).toList();
  }

  @Test
  void aDeployEmbarksEveryUpstreamRunSinceThePreviousDeployDownToGit() throws IOException {
    try (FakeHttpServer gocd = new FakeHttpServer()) {
      GocdFixtures.chain(gocd);
      GocdApi api = GocdFixtures.api(gocd);
      List<Run> prod = prodRuns(api);
      DeployedCommits commits = new DeployedCommits(api, 30);

      List<DeployedCommit> second = commits.of("prod", prod.get(0));
      assertThat(vias(second)).containsExactly("c7@build/7", "c9@build/9");
      assertThat(second.get(0).repo()).isEqualTo(URL);
      assertThat(second.get(0).at()).isEqualTo(Instant.ofEpochMilli(1700000000000L));
      assertThat(second.get(0).keys()).containsExactly("ABC-7");
      assertThat(second.get(1).keys()).containsExactly("XYZ-9", "ABC-7");
      // recette/2 is unknown: prod/1 counts recette/3 and its own build only.
      assertThat(vias(commits.of("prod", prod.get(1)))).containsExactly("c6@build/6");
      assertThat(gocd.requests("/go/api/pipelines/build/8")).hasSize(1);
      // An unknown run is asked once per Accept version (latest, then v1), and only once.
      assertThat(gocd.requests("/go/api/pipelines/recette/2")).hasSize(2);
    }
  }

  @Test
  void theBudgetBoundsTheUpstreamCallsOfOneRunAndCachedRunsAreFree() throws IOException {
    try (FakeHttpServer gocd = new FakeHttpServer()) {
      GocdFixtures.chain(gocd);
      GocdApi api = GocdFixtures.api(gocd);
      List<Run> prod = prodRuns(api);
      // recette/4, recette/3, build/7: the budget is spent before recette/5.
      assertThat(vias(new DeployedCommits(api, 3).of("prod", prod.get(0))))
          .containsExactly("c7@build/7");
      assertThat(gocd.requests("/go/api/pipelines/recette/5")).isEmpty();
      // A new run has a new budget, and what is cached costs nothing.
      assertThat(vias(new DeployedCommits(api, 3).of("prod", prod.get(0))))
          .containsExactly("c7@build/7", "c9@build/9");
      assertThat(gocd.requests("/go/api/pipelines/recette/4")).hasSize(1);
    }
  }

  @Test
  void aBudgetOfZeroReadsNothingUpstream() throws IOException {
    try (FakeHttpServer gocd = new FakeHttpServer()) {
      GocdFixtures.chain(gocd);
      GocdApi api = GocdFixtures.api(gocd);
      assertThat(new DeployedCommits(api, 0).of("prod", prodRuns(api).get(0))).isEmpty();
      assertThat(gocd.requests()).hasSize(1);
    }
  }

  @Test
  void aMissingUpstreamRunEmbarksNothing() throws IOException {
    try (FakeHttpServer gocd = new FakeHttpServer()) {
      GocdApi api = GocdFixtures.api(gocd);
      Run run = GocdFixtures.runOf(run("prod", 1, 1, upstream(true, "recette", 99)));
      assertThat(new DeployedCommits(api, 30).of("prod", run)).isEmpty();
      assertThat(gocd.requests("/go/api/pipelines/recette/99")).hasSize(2);
    }
  }

  @Test
  void aForcedRebuildWithoutNewCommitAndUnknownMaterialsEmbarkNothing() throws IOException {
    try (FakeHttpServer gocd = new FakeHttpServer()) {
      GocdApi api = GocdFixtures.api(gocd);
      String svn =
          "{\"changed\":true,\"material\":{\"type\":\"Svn\"},\"modifications\":[{\"revision\":\"r1\"}]}";
      String odd =
          "{\"changed\":true,\"material\":{\"type\":\"Pipeline\"},"
              + "\"modifications\":[{\"revision\":\"nonsense\"},{\"revision\":\"up/x/s/1\"}]}";
      Run run =
          GocdFixtures.runOf(
              run("build", 2, 1, git(false, URL, "c1", ""), svn, odd, upstream(false, "a", 1)));
      assertThat(new DeployedCommits(api, 30).of("build", run)).isEmpty();
      assertThat(gocd.requests()).isEmpty();
    }
  }

  @Test
  void theWalkStopsSixLevelsUp() throws IOException {
    try (FakeHttpServer gocd = new FakeHttpServer()) {
      for (int i = 1; i <= 7; i++) {
        String next = upstream(true, "p" + (i + 1), 1);
        String[] materials =
            switch (i) {
              case 6 -> new String[] {git(true, URL, "level6", ""), next};
              case 7 -> new String[] {git(true, URL, "level7", "")};
              default -> new String[] {next};
            };
        gocd.json("/go/api/pipelines/p" + i + "/1", run("p" + i, 1, 1, materials));
      }
      Run p0 = GocdFixtures.runOf(run("p0", 1, 1, upstream(true, "p1", 1)));
      assertThat(vias(new DeployedCommits(GocdFixtures.api(gocd), 30).of("p0", p0)))
          .containsExactly("level6@p6/1");

      Run p1 = GocdFixtures.runOf(run("q", 1, 1, upstream(true, "p2", 1)));
      // From one level lower, p7 sits at depth 6: read.
      assertThat(vias(new DeployedCommits(GocdFixtures.api(gocd), 30).of("q", p1)))
          .containsExactly("level6@p6/1", "level7@p7/1");
    }
  }

  @Test
  void theRangeStartsAfterThePreviousUpstreamRunAndIsBoundedByTheBudget() throws IOException {
    try (FakeHttpServer gocd = new FakeHttpServer()) {
      DeployedCommits thirty = new DeployedCommits(GocdFixtures.api(gocd), 30);
      assertThat(thirty.firstCounter(3, 5)).isEqualTo(4);
      assertThat(thirty.firstCounter(4, 5)).isEqualTo(5);
      assertThat(thirty.firstCounter(5, 5)).isEqualTo(5);
      assertThat(thirty.firstCounter(7, 5)).isEqualTo(5);
      assertThat(thirty.firstCounter(0, 5)).isEqualTo(5);
      assertThat(thirty.firstCounter(-1, 5)).isEqualTo(5);
      assertThat(thirty.firstCounter(1, 40)).isEqualTo(11);
      DeployedCommits two = new DeployedCommits(GocdFixtures.api(gocd), 2);
      assertThat(two.firstCounter(1, 5)).isEqualTo(4);
      assertThat(two.firstCounter(3, 5)).isEqualTo(4);
    }
  }

  @Test
  void theUpstreamCounterOfARunIsTheFirstValidRevisionOfThatPipeline() {
    Run run =
        GocdFixtures.runOf(
            run(
                "prod",
                1,
                1,
                git(true, URL, "c1", ""),
                "{\"material\":{\"type\":\"Pipeline\"},\"modifications\":"
                    + "[{\"revision\":\"recette/x/s/1\"},{\"revision\":\"other/9/s/1\"},"
                    + "{\"revision\":\"recette/4/s/1\"},{\"revision\":\"recette/6/s/1\"}]}"));
    assertThat(DeployedCommits.upstreamCounter(Optional.of(run), "recette")).isEqualTo(4);
    assertThat(DeployedCommits.upstreamCounter(Optional.of(run), "build")).isEqualTo(-1);
    assertThat(DeployedCommits.upstreamCounter(Optional.empty(), "recette")).isEqualTo(-1);
  }
}
