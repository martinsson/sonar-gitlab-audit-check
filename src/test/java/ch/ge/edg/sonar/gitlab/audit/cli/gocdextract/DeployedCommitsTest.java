package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import static ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures.git;
import static ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures.run;
import static ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures.upstream;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.dora.JiraKeys;
import ch.ge.edg.sonar.gitlab.audit.gocd.GocdApi;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The range rule, on hand-made runs shaped like the GoCD 25.3 captures: prod/1 took recette/3,
 * prod/2 took recette/5, so prod/2 embarks what recette/4 and recette/5 brought, and so on down to
 * git.
 */
class DeployedCommitsTest {

  private static final String URL = GocdFixtures.HELLO;

  private static List<JsonNode> prodRuns(GocdApi api) {
    return api.history("prod", r -> false, 10).runs();
  }

  private static List<String> vias(List<Commit> commits) {
    return commits.stream().map(c -> c.revision() + "@" + c.via()).toList();
  }

  @Test
  void a_deploy_embarks_every_upstream_run_since_the_previous_deploy_down_to_git()
      throws IOException {
    try (FakeHttpServer gocd = new FakeHttpServer()) {
      GocdFixtures.chain(gocd);
      GocdApi api = GocdFixtures.api(gocd);
      List<JsonNode> prod = prodRuns(api);
      DeployedCommits commits = new DeployedCommits(api, 30);

      List<Commit> second = commits.of("prod", prod.get(0));
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
  void the_budget_bounds_the_upstream_calls_of_one_run_and_cached_runs_are_free()
      throws IOException {
    try (FakeHttpServer gocd = new FakeHttpServer()) {
      GocdFixtures.chain(gocd);
      GocdApi api = GocdFixtures.api(gocd);
      List<JsonNode> prod = prodRuns(api);
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
  void a_budget_of_zero_reads_nothing_upstream() throws IOException {
    try (FakeHttpServer gocd = new FakeHttpServer()) {
      GocdFixtures.chain(gocd);
      GocdApi api = GocdFixtures.api(gocd);
      assertThat(new DeployedCommits(api, 0).of("prod", prodRuns(api).get(0))).isEmpty();
      assertThat(gocd.requests()).hasSize(1);
    }
  }

  @Test
  void a_missing_upstream_run_embarks_nothing() throws IOException {
    try (FakeHttpServer gocd = new FakeHttpServer()) {
      GocdApi api = GocdFixtures.api(gocd);
      JsonNode run = GocdFixtures.node(run("prod", 1, 1, upstream(true, "recette", 99)));
      assertThat(new DeployedCommits(api, 30).of("prod", run)).isEmpty();
      assertThat(gocd.requests("/go/api/pipelines/recette/99")).hasSize(2);
    }
  }

  @Test
  void a_forced_rebuild_without_new_commit_and_unknown_materials_embark_nothing()
      throws IOException {
    try (FakeHttpServer gocd = new FakeHttpServer()) {
      GocdApi api = GocdFixtures.api(gocd);
      String svn =
          "{\"changed\":true,\"material\":{\"type\":\"Svn\"},\"modifications\":[{\"revision\":\"r1\"}]}";
      String odd =
          "{\"changed\":true,\"material\":{\"type\":\"Pipeline\"},"
              + "\"modifications\":[{\"revision\":\"nonsense\"},{\"revision\":\"up/x/s/1\"}]}";
      JsonNode run =
          GocdFixtures.node(
              run("build", 2, 1, git(false, URL, "c1", ""), svn, odd, upstream(false, "a", 1)));
      assertThat(new DeployedCommits(api, 30).of("build", run)).isEmpty();
      assertThat(gocd.requests()).isEmpty();
    }
  }

  @Test
  void the_walk_stops_six_levels_up() throws IOException {
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
      JsonNode p0 = GocdFixtures.node(run("p0", 1, 1, upstream(true, "p1", 1)));
      assertThat(vias(new DeployedCommits(GocdFixtures.api(gocd), 30).of("p0", p0)))
          .containsExactly("level6@p6/1");

      JsonNode p1 = GocdFixtures.node(run("q", 1, 1, upstream(true, "p2", 1)));
      // From one level lower, p7 sits at depth 6: read.
      assertThat(vias(new DeployedCommits(GocdFixtures.api(gocd), 30).of("q", p1)))
          .containsExactly("level6@p6/1", "level7@p7/1");
    }
  }

  @Test
  void the_range_starts_after_the_previous_upstream_run_and_is_bounded_by_the_budget()
      throws IOException {
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
  void the_upstream_counter_of_a_run_is_the_first_valid_revision_of_that_pipeline() {
    JsonNode run =
        GocdFixtures.node(
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

  @Test
  void a_commit_row_writes_its_date_in_the_given_zone_and_its_keys_joined() {
    Commit c = new Commit("build/7", URL, "c7", Instant.parse("2026-01-02T03:04:05Z"), Set.of());
    assertThat(c.cells("prod", 2, java.time.ZoneId.of("Europe/Zurich")))
        .containsExactly("prod", "2", "build/7", URL, "c7", "2026-01-02 04:04:05", "");
    Commit k = new Commit("direct", URL, "c8", null, JiraKeys.in("BB-1 AA-2"));
    assertThat(k.cells("p", 1, java.time.ZoneOffset.UTC)[5]).isEmpty();
    assertThat(k.cells("p", 1, java.time.ZoneOffset.UTC)[6]).isEqualTo("BB-1|AA-2");
  }
}
