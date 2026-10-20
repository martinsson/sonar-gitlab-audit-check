package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * One test per rule of the selection (step 4): strata, namespace cap, solo authors, the random
 * witness, the remainder and the budget. Every project is eligible (--floor 1), so only the
 * selection decides.
 */
class SelectionRulesTest extends FunnelRulesTestBase {

  private static final String[] BASE = {"--graphql=false", "--validation-sample=0", "--floor", "1"};

  private Cli.Result select(String... extra) {
    return run(args(extra));
  }

  /**
   * Three projects per stratum, each in its own namespace, two authors each. In each stratum the
   * first is the busiest: A 30/20/10, B 29/19/9, C 28/18/8, D 27/17/7 commits.
   */
  private void fourStrata() {
    long[] history = {50, 500, 5_000, 50_000};
    String[] letter = {"a", "b", "c", "d"};
    for (int s = 0; s < 4; s++) {
      for (int k = 0; k < 3; k++) {
        long id = 1 + s * 3L + k;
        add(id, "ns" + id + "/" + letter[s] + (k + 1), history[s], 30 - s - 10 * k, 2);
      }
    }
  }

  private List<String> withReason(String reason) {
    return pathsWhere("motif_selection", reason).toList();
  }

  private List<String> withReasonStarting(String prefix) {
    return rows().values().stream()
        .filter(r -> r.str("motif_selection").startsWith(prefix))
        .map(r -> r.str("path"))
        .toList();
  }

  @Test
  void strataAreCutOnHistoricalCommitsAtOneHundredOneThousandAndTenThousand() {
    add(1, "n1/p99", 99, 5, 2);
    add(2, "n2/p100", 100, 5, 2);
    add(3, "n3/p999", 999, 5, 2);
    add(4, "n4/p1000", 1_000, 5, 2);
    add(5, "n5/p9999", 9_999, 5, 2);
    add(6, "n6/p10000", 10_000, 5, 2);

    select();

    assertThat(row("n1/p99").str("bucket")).isEqualTo("A(<100)");
    assertThat(row("n2/p100").str("bucket")).isEqualTo("B(<1k)");
    assertThat(row("n3/p999").str("bucket")).isEqualTo("B(<1k)");
    assertThat(row("n4/p1000").str("bucket")).isEqualTo("C(<10k)");
    assertThat(row("n5/p9999").str("bucket")).isEqualTo("C(<10k)");
    assertThat(row("n6/p10000").str("bucket")).isEqualTo("D(>10k)");
  }

  @Test
  void withoutACommitCountTheStratumIsEstimatedFromRepoSizeAtOneCommitPerTwentyKilobytes() {
    add(1, "n1/sized", 0, 5, 2).putObject("statistics").put("repository_size", 2_000_000);
    add(2, "n2/blind", 0, 5, 2).remove("statistics");

    select();

    assertThat(row("n1/sized").str("bucket")).isEqualTo("B(<1k)");
    assertThat(row("n2/blind").str("bucket")).isEqualTo("A(<100)");
  }

  @Test
  void strataHeadsTakeSeventyPercentOfTheBudgetRoundRobinBusiestFirst() {
    fourStrata();

    select("--top", "10");

    // 7 heads: the busiest of each stratum, then the second of A, B and C.
    assertThat(withReasonStarting("tête de strate "))
        .containsExactlyInAnyOrder(
            "ns1/a1", "ns2/a2", "ns4/b1", "ns5/b2", "ns7/c1", "ns8/c2", "ns10/d1");
    assertThat(row("ns1/a1").str("motif_selection")).isEqualTo("tête de strate A(<100)");
    assertThat(row("ns10/d1").str("motif_selection")).isEqualTo("tête de strate D(>10k)");
  }

  @Test
  void strataHeadsSkipANamespaceOnceItHoldsItsCapOfThree() {
    for (int i = 0; i < 6; i++) add(1 + i, "big/p" + i, 50, 30 - i, 2);
    for (int i = 0; i < 4; i++) add(11 + i, "small" + i + "/q", 50, 10 - i, 2);

    Cli.Result r = select("--top", "10");

    assertThat(withReasonStarting("tête de strate "))
        .containsExactlyInAnyOrder(
            "big/p0", "big/p1", "big/p2", "small0/q", "small1/q", "small2/q", "small3/q");
    assertThat(r.out()).contains("(plafond 3 par namespace)");
  }

  @Test
  void theNamespaceCapGrowsToFifteenPercentOfTheBudget() {
    add(1, "n/p", 50, 5, 2);

    Cli.Result r = select("--top", "40");

    assertThat(r.out()).contains("(plafond 6 par namespace)");
  }

  @Test
  void theBusiestSoloAuthorProjectsLeftByTheStrataGetTenPercentOfTheBudget() {
    fourStrata();
    add(13, "ns13/solo-busy", 50, 6, 1);
    add(14, "ns14/solo-quiet", 50, 5, 1);

    select("--top", "10");

    assertThat(withReason("mono-auteur, forte activité")).containsExactly("ns13/solo-busy");
  }

  @Test
  void theWitnessIsDrawnFromWhatTheOtherTranchesLeftAndTheSameSeedDrawsTheSameOne() {
    fourStrata();
    select("--top", "10", "--seed", "7");
    List<String> first = withReason("témoin aléatoire");

    runTo(tmp.resolve("again.csv"), args("--top", "10", "--seed", "7"));
    Map<String, Csv.Row> again = rows(tmp.resolve("again.csv"));

    assertThat(first).hasSize(1);
    assertThat(withReasonStarting("tête de strate ")).doesNotContainAnyElementsOf(first);
    assertThat(
            again.values().stream()
                .filter(r -> r.str("motif_selection").equals("témoin aléatoire"))
                .map(r -> r.str("path")))
        .containsExactlyElementsOf(first);
  }

  @Test
  void aBudgetTheTranchesLeaveUnspentGoesToTheNextBusiestProjects() {
    fourStrata();

    select("--top", "10");

    List<String> remainder = withReason("reliquat de budget");
    assertThat(remainder).hasSize(2);
    int leastRemainder = remainder.stream().mapToInt(p -> commits(row(p))).min().orElseThrow();
    assertThat(pathsWhere("selectionne", "false").map(p -> commits(row(p))))
        .allSatisfy(n -> assertThat(n).isLessThan(leastRemainder));
  }

  @Test
  void withEnoughEligibleProjectsExactlyTheBudgetIsSelected() {
    fourStrata();

    Cli.Result r = select("--top", "10");

    assertThat(selectedPaths()).hasSize(10);
    assertThat(r.out()).contains("  Sélectionnés                    : 10\n");
  }

  @Test
  void aBudgetLargerThanTheEligibleSetSelectsEveryEligibleProject() {
    fourStrata();

    select("--top", "40");

    assertThat(selectedPaths()).hasSize(12);
  }

  private static int commits(Csv.Row row) {
    return Integer.parseInt(row.str("commits_window"));
  }

  private static String[] args(String... extra) {
    String[] all = new String[BASE.length + extra.length];
    System.arraycopy(BASE, 0, all, 0, BASE.length);
    System.arraycopy(extra, 0, all, BASE.length, extra.length);
    return all;
  }
}
