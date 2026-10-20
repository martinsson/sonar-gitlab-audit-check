package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.GitlabAuditFake.commit;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * One test per rule of the funnel up to the floor, and of the deep pass that follows the selection.
 * Selection itself is in {@link SelectionRulesTest}.
 */
class FunnelRulesTest extends FunnelRulesTestBase {

  private static final String NO_SAMPLE = "--validation-sample=0";
  private static final String REST = "--graphql=false";

  private static String lastCommit(String date) {
    return "{\"repository\":{\"rootRef\":\"main\",\"tree\":{\"lastCommit\":"
        + "{\"sha\":\"head\",\"committedDate\":\""
        + date
        + "\"}}}}";
  }

  @Nested
  class ExclusionRules {

    @Test
    void eachHardExclusionIsRecordedWithItsReasonAndNeverCounted() throws IOException {
      add(1, "t/archived", 50, 10, 2).put("archived", true);
      add(2, "t/empty", 50, 10, 2).put("empty_repo", true);
      add(3, "t/mirror", 50, 10, 2).put("mirror", true);
      add(4, "t/nobranch", 50, 10, 2).put("default_branch", "");
      add(5, "t/listed", 50, 10, 2);
      add(6, "t/kept", 50, 10, 2);
      Path list = write("exclusions.txt", "t/listed\n");

      run(REST, NO_SAMPLE, "--exclusions", list.toString());

      assertThat(row("t/archived").str("exclu")).isEqualTo("archivé");
      assertThat(row("t/empty").str("exclu")).isEqualTo("dépôt vide");
      assertThat(row("t/mirror").str("exclu")).isEqualTo("miroir");
      assertThat(row("t/nobranch").str("exclu")).isEqualTo("sans branche par défaut");
      assertThat(row("t/listed").str("exclu")).isEqualTo("liste d'exclusion");
      assertThat(row("t/kept").str("exclu")).isEmpty();
      for (long id = 1; id <= 5; id++) assertThat(countedByRest(id)).as("project %d", id).isFalse();
      assertThat(rows()).hasSize(6);
    }

    @Test
    void theExclusionListWinsOverEveryOtherReason() {
      add(1, "t/listed-archived-mirror", 50).put("archived", true).put("mirror", true);
      add(2, "t/archived-mirror", 50).put("archived", true).put("mirror", true);
      Path list = write("exclusions.txt", "t/listed-archived-mirror\n");

      run(REST, NO_SAMPLE, "--exclusions", list.toString());

      assertThat(row("t/listed-archived-mirror").str("exclu")).isEqualTo("liste d'exclusion");
      assertThat(row("t/archived-mirror").str("exclu")).isEqualTo("archivé");
    }

    @Test
    void includeArchivedKeepsArchivedProjectsInTheFunnel() {
      add(1, "t/archived", 50, 10, 2).put("archived", true);

      run(REST, NO_SAMPLE, "--include-archived");

      assertThat(row("t/archived").str("exclu")).isEmpty();
      assertThat(countedByRest(1)).isTrue();
    }

    @Test
    void aForkWithNoActivityPastItsFirstDayIsAnUndivergedCopy() {
      ObjectNode copy = add(1, "t/copy", 50, 10, 2);
      copy.put("created_at", NOW.minusDays(10).toString());
      copy.put("last_activity_at", NOW.minusDays(10).plusHours(23).toString());
      copy.putObject("forked_from_project").put("path_with_namespace", "up/stream");
      ObjectNode diverged = add(2, "t/diverged", 50, 10, 2);
      diverged.put("created_at", NOW.minusDays(10).toString());
      diverged.put("last_activity_at", NOW.minusDays(9).toString());
      diverged.putObject("forked_from_project").put("path_with_namespace", "up/stream");

      run(REST, NO_SAMPLE);

      assertThat(row("t/copy").str("exclu")).isEqualTo("fork non divergé");
      assertThat(row("t/diverged").str("exclu")).isEmpty();
      assertThat(row("t/diverged").str("fork")).isEqualTo("true");
    }
  }

  @Nested
  class GateRules {

    @Test
    void aProjectLastActiveExactlyAtTheWindowStartIsKept() {
      add(1, "t/edge", 50, 10, 2).put("last_activity_at", WINDOW_START.toString());

      run(REST, NO_SAMPLE);

      assertThat(row("t/edge").str("exclu")).isEmpty();
      assertThat(countedByRest(1)).isTrue();
    }

    @Test
    void aProjectLastActiveOneSecondBeforeTheWindowIsDroppedAsInactive() {
      add(1, "t/stale", 50, 10, 2).put("last_activity_at", WINDOW_START.minusSeconds(1).toString());

      run(REST, NO_SAMPLE);

      assertThat(row("t/stale").str("exclu")).isEqualTo("inactif > 90j");
      assertThat(row("t/stale").str("commits_window")).isEmpty();
      assertThat(countedByRest(1)).isFalse();
    }

    @Test
    void aProjectWithNoLastActivityIsDroppedAsInactive() {
      add(1, "t/never", 50, 10, 2).remove("last_activity_at");

      run(REST, NO_SAMPLE);

      assertThat(row("t/never").str("exclu")).isEqualTo("inactif > 90j");
    }

    @Test
    void sinceWidensTheWindowTheGateCutsOn() {
      add(1, "t/older", 50, 10, 2).put("last_activity_at", NOW.minusDays(100).toString());

      run(REST, NO_SAMPLE, "--since", "120");

      assertThat(row("t/older").str("exclu")).isEmpty();
    }

    @Test
    void theGateDoesNotRelabelAProjectAlreadyExcludedByTheInventory() {
      add(1, "t/old-mirror", 50)
          .put("mirror", true)
          .put("last_activity_at", "2000-01-01T00:00:00Z");

      run(REST, NO_SAMPLE);

      assertThat(row("t/old-mirror").str("exclu")).isEqualTo("miroir");
    }

    @Test
    void anInactiveProjectFoundWithCommitsIsAMeasuredLeakButStaysExcluded() {
      add(1, "t/leaky", 50, 3, 1).put("last_activity_at", "2000-01-01T00:00:00Z");
      add(2, "t/quiet", 50).put("last_activity_at", "2000-01-01T00:00:00Z");
      fake.commits(2, List.of());

      Cli.Result r = run(REST, "--validation-sample", "5");

      Csv.Row leaky = row("t/leaky");
      assertThat(leaky.str("fuite_filtre")).isEqualTo("true");
      assertThat(leaky.str("commits_window")).isEqualTo("3");
      assertThat(leaky.str("exclu")).isEqualTo("inactif > 90j");
      assertThat(leaky.str("selectionne")).isEqualTo("false");
      assertThat(row("t/quiet").str("fuite_filtre")).isEqualTo("false");
      assertThat(r.out()).contains("Échantillon de contrôle sous la coupe : 2 projets, 1 faux");
    }

    @Test
    void theControlSampleDrawsOnlyFromProjectsTheGateDropped() {
      add(1, "t/old-archived", 50, 3, 1)
          .put("archived", true)
          .put("last_activity_at", "2000-01-01T00:00:00Z");

      run(REST, "--validation-sample", "5");

      assertThat(countedByRest(1)).isFalse();
      assertThat(row("t/old-archived").str("fuite_filtre")).isEqualTo("false");
    }

    @Test
    void aValidationSampleOfZeroLooksAtNoDroppedProject() {
      add(1, "t/leaky", 50, 3, 1).put("last_activity_at", "2000-01-01T00:00:00Z");

      run(REST, NO_SAMPLE);

      assertThat(countedByRest(1)).isFalse();
      assertThat(row("t/leaky").str("fuite_filtre")).isEqualTo("false");
    }
  }

  @Nested
  class CutRules {

    @Test
    void withoutActiveWithinEveryFreshProjectIsCounted() {
      add(1, "t/month-ago", 50, 10, 2).put("last_activity_at", NOW.minusDays(60).toString());

      run(REST, NO_SAMPLE);

      assertThat(countedByRest(1)).isTrue();
    }

    @Test
    void aProjectLastActiveExactlyAtTheCutIsCounted() {
      add(1, "t/edge", 50, 10, 2).put("last_activity_at", NOW.minusDays(30).toString());

      run(REST, NO_SAMPLE, "--active-within", "30");

      assertThat(countedByRest(1)).isTrue();
      assertThat(row("t/edge").str("commits_window")).isEqualTo("10");
    }

    @Test
    void aProjectLastActiveBeforeTheCutIsSetAsideUncountedNotZero() {
      add(1, "t/quiet", 50, 10, 2)
          .put("last_activity_at", NOW.minusDays(30).minusSeconds(1).toString());

      run(REST, NO_SAMPLE, "--active-within", "30");

      Csv.Row quiet = row("t/quiet");
      assertThat(quiet.str("exclu")).isEqualTo("hors coupe d'activité (dernière activité > 30j)");
      assertThat(quiet.str("commits_window")).isEmpty();
      assertThat(countedByRest(1)).isFalse();
    }

    @Test
    void theCutUsesTheGraphqlLastCommitDateWhenThereIsOne() {
      add(1, "t/busy-tickets", 50, 10, 2);
      fake.json(
          "/api/graphql", "{\"data\":{\"p0\":" + lastCommit(NOW.minusDays(40).toString()) + "}}");

      run(NO_SAMPLE, "--active-within", "30");

      assertThat(row("t/busy-tickets").str("exclu"))
          .isEqualTo("hors coupe d'activité (dernier commit > 30j)");
      assertThat(countedByRest(1)).isFalse();
    }

    @Test
    void aLastCommitDatedBeforeTheWindowCountsAsZeroWithoutARestCall() {
      add(1, "t/tickets-only", 50, 10, 2);
      fake.json(
          "/api/graphql",
          "{\"data\":{\"p0\":" + lastCommit(WINDOW_START.minusDays(1).toString()) + "}}");

      run(NO_SAMPLE);

      assertThat(row("t/tickets-only").str("commits_window")).isEqualTo("0");
      assertThat(countedByRest(1)).isFalse();
    }
  }

  @Nested
  class CountingRules {

    @Test
    void unreadableCommitsLeaveTheProjectUnmeasurableNotAtZero() {
      add(1, "t/forbidden", 50);
      fake.route("/api/v4/projects/1/repository/commits", req -> Response.status(403, "{}"));
      add(2, "t/visible", 50, 10, 2);

      Cli.Result r = run(REST, NO_SAMPLE);

      Csv.Row forbidden = row("t/forbidden");
      assertThat(forbidden.str("commits_window")).isEmpty();
      assertThat(forbidden.str("authors_window")).isEmpty();
      assertThat(forbidden.str("exclu")).isEqualTo("activité non mesurable (HTTP 403)");
      assertThat(forbidden.str("selectionne")).isEqualTo("false");
      assertThat(r.out()).contains("Activité non mesurable          : 1");
    }

    @Test
    void anUnmeasurableProjectStaysOutEvenWithAFloorOfZero() {
      add(1, "t/broken", 50);
      fake.route("/api/v4/projects/1/repository/commits", req -> Response.status(500, "{}"));

      run(REST, NO_SAMPLE, "--floor", "0");

      assertThat(row("t/broken").str("exclu")).isEqualTo("activité non mesurable (HTTP 500)");
      assertThat(row("t/broken").str("selectionne")).isEqualTo("false");
    }

    @Test
    void theCountStopsAtMaxCommitPagesAndSaysItIsTruncated() {
      add(1, "t/huge", 50, 150, 3);

      run(REST, NO_SAMPLE, "--max-commit-pages", "1");

      assertThat(row("t/huge").str("commits_window")).isEqualTo("100");
      assertThat(row("t/huge").str("tronque")).isEqualTo("true");
    }

    @Test
    void botCommitsAreCountedApartFromHumanOnes() {
      add(1, "t/mixed", 50);
      List<ObjectNode> commits = new java.util.ArrayList<>(window(1, 6, 2));
      commits.add(commit("r1", "renovate", NOW.toString()));
      commits.add(commit("r2", "renovate", NOW.toString()));
      fake.commits(1, commits);

      run(REST, NO_SAMPLE);

      assertThat(row("t/mixed").str("commits_window")).isEqualTo("6");
      assertThat(row("t/mixed").str("commits_bots")).isEqualTo("2");
      assertThat(row("t/mixed").str("authors_window")).isEqualTo("2");
    }
  }

  @Nested
  class FloorRules {

    @Test
    void anImposedFloorKeepsProjectsAtTheFloorAndDropsThoseBelow() {
      add(1, "t/at", 50, 8, 2);
      add(2, "t/below", 50, 7, 2);

      Cli.Result r = run(REST, NO_SAMPLE, "--floor", "8");

      assertThat(row("t/at").str("exclu")).isEmpty();
      assertThat(row("t/below").str("exclu")).isEqualTo("sous le plancher (7 commits)");
      assertThat(r.out()).contains("Plancher imposé (--floor)       : 8 commits");
    }

    @Test
    void theDerivedFloorNeverGoesBelowFiveCommits() {
      add(1, "t/five", 50, 5, 2);
      add(2, "t/four", 50, 4, 2);

      Cli.Result r = run(REST, NO_SAMPLE);

      assertThat(row("t/five").str("exclu")).isEmpty();
      assertThat(row("t/four").str("exclu")).isEqualTo("sous le plancher (4 commits)");
      assertThat(r.out()).contains("Plancher déduit des données     : 5 commits sur 90 jours");
    }

    @Test
    void theDerivedFloorRisesUntilAtMostOneAndAHalfBudgetsRemain() {
      // --top 2: at most 3 eligible. 10, 9 and 8 commits fit; 7 would make four.
      for (int i = 0; i < 5; i++) add(1 + i, "t/p" + (10 - i), 50, 10 - i, 2);

      Cli.Result r = run(REST, NO_SAMPLE, "--top", "2");

      assertThat(r.out()).contains("Plancher déduit des données     : 8 commits");
      assertThat(pathsWhere("exclu", "").toList()).containsExactly("t/p10", "t/p9", "t/p8");
      assertThat(row("t/p7").str("exclu")).isEqualTo("sous le plancher (7 commits)");
    }

    @Test
    void theDerivedFloorStopsRisingAtSixtyCommits() {
      add(1, "t/a", 50, 70, 2);
      add(2, "t/b", 50, 70, 2);
      add(3, "t/c", 50, 59, 2);

      Cli.Result r = run(REST, NO_SAMPLE, "--top", "1");

      assertThat(r.out()).contains("Plancher déduit des données     : 60 commits");
      assertThat(row("t/a").str("exclu")).isEmpty();
      assertThat(row("t/b").str("exclu")).isEmpty();
      assertThat(row("t/c").str("exclu")).isEqualTo("sous le plancher (59 commits)");
    }

    @Test
    void aProjectBelowTheFloorWithOnlyBotCommitsIsNamedAsRobotic() {
      add(1, "t/renovated", 50);
      fake.commits(
          1,
          commit("r1", "renovate", NOW.toString()),
          commit("r2", "renovate", NOW.toString()),
          commit("r3", "dependabot", NOW.toString()));

      run(REST, NO_SAMPLE);

      assertThat(row("t/renovated").str("exclu"))
          .isEqualTo("activité robotique seule (3 commits de bots)");
    }

    @Test
    void projectsBelowTheFloorGetNoStratumAndAreNotSelected() {
      add(1, "t/low", 50, 2, 2);

      Cli.Result r = run(REST, NO_SAMPLE);

      assertThat(row("t/low").str("bucket")).isEmpty();
      assertThat(row("t/low").str("selectionne")).isEqualTo("false");
      assertThat(r.out()).contains("Aucun projet au-dessus du plancher.");
    }
  }

  @Nested
  class DeepPassRules {

    private void selectedAndNot() {
      add(1, "t/selected", 50, 10, 2);
      add(2, "t/below", 50, 2, 2);
    }

    @Test
    void practicesAreMeasuredForSelectedProjectsOnly() throws IOException {
      selectedAndNot();

      Path pratiques = tmp.resolve("pratiques.csv");
      run(REST, NO_SAMPLE, "--deep", "--floor", "5", "--pratiques", pratiques.toString());

      List<String> measured = Csv.read(pratiques).rows().stream().map(r -> r.str("path")).toList();
      assertThat(measured).containsExactly("t/selected");
      assertThat(fake.server.requests("/api/v4/projects/1/protected_branches")).isNotEmpty();
      assertThat(fake.server.requests("/api/v4/projects/2/protected_branches")).isEmpty();
      assertThat(fake.server.requests("/api/v4/projects/2/merge_requests")).isEmpty();
    }

    @Test
    void withoutDeepNoPracticeIsMeasuredAndNoPracticeFileIsWritten() {
      selectedAndNot();

      run(REST, NO_SAMPLE, "--floor", "5");

      assertThat(row("t/selected").str("selectionne")).isEqualTo("true");
      assertThat(fake.server.requests("/api/v4/projects/1/protected_branches")).isEmpty();
      assertThat(tmp.resolve("pratiques.csv")).doesNotExist();
    }

    @Test
    void askingForThePracticeFileImpliesTheDeepPass() throws IOException {
      selectedAndNot();
      Path pratiques = tmp.resolve("out/p.csv");

      run(REST, NO_SAMPLE, "--floor", "5", "--pratiques", pratiques.toString());

      assertThat(Csv.read(pratiques).rows()).hasSize(1);
    }
  }
}
