package ch.ge.edg.sonar.gitlab.audit.matching;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.exchange.Mapping;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * One test per rule of the replay: what the file decided, what the computation decided, and what
 * {@code --reapparier} changes. Inputs are tiny fictional tables built in code; snapshots of the
 * same rules live with the commands.
 */
class ReplayRulesTest {

  private static final Spec SPEC =
      new Spec(
          "nom",
          "code",
          List.of(
              KeyMethod.parse("ref:nom=code", Kind.EXACT),
              KeyMethod.parse("chemin:nom=code", Kind.DERIVED)),
          null,
          0.8,
          0.1);

  private static final String[] MAPPING_HEADER = {
    Mapping.left("nom"), Mapping.right("code"), Mapping.METHOD, Mapping.CONFIDENCE
  };

  private static Csv.Table table(String[] header, String[]... rows) {
    Map<String, Integer> index = new LinkedHashMap<>();
    for (int i = 0; i < header.length; i++) index.put(header[i], i);
    List<Csv.Row> out = new ArrayList<>();
    for (String[] r : rows) out.add(new Csv.Row(index, r));
    return new Csv.Table(index, out);
  }

  /** A one-column table: the values, one row each. */
  private static Csv.Table column(String name, String... values) {
    String[][] rows = new String[values.length][];
    for (int i = 0; i < values.length; i++) rows[i] = new String[] {values[i]};
    return table(new String[] {name}, rows);
  }

  private static String[] cells(String... values) {
    return values;
  }

  /** A mapping as an earlier run wrote it: left, right, method, confidence. */
  private static Csv.Table mapping(String[]... rows) {
    return table(MAPPING_HEADER, rows);
  }

  private static Replay.Outcome replay(Csv.Table left, Csv.Table right, Csv.Table previous) {
    return replay(left, right, previous, false);
  }

  private static Replay.Outcome replay(
      Csv.Table left, Csv.Table right, Csv.Table previous, boolean recomputeAll) {
    return Replay.run(Mapper.map(SPEC, left, right), previous, recomputeAll);
  }

  private static Match match(Replay.Outcome o, String left) {
    return o.result().matches().stream()
        .filter(m -> m.left().str("nom").equals(left))
        .findFirst()
        .orElseThrow();
  }

  @Nested
  class ManualRules {

    @Test
    void aManualPairWinsEvenAgainstTheComputation() {
      Replay.Outcome o =
          replay(
              column("nom", "alpha"),
              column("code", "alpha", "beta"),
              mapping(cells("alpha", "beta", "", "manuel")));

      Match m = match(o, "alpha");
      assertThat(m.rightKey(SPEC)).isEqualTo("beta");
      assertThat(m.label()).isEqualTo(Replay.MANUAL_LABEL);
      assertThat(m.confidence()).isEqualTo(Confidence.MANUEL);
      assertThat(m.joined()).isTrue();
      assertThat(o.stats().manual()).isEqualTo(1);
      assertThat(o.stats().kept()).isZero();
    }

    @Test
    void aManualPairToAKeyThatNoLongerExistsIsReportedAndNotHeld() {
      Replay.Outcome o =
          replay(
              column("nom", "alpha"),
              column("code", "alpha"),
              mapping(cells("alpha", "gone", "", "manuel")));

      Match m = match(o, "alpha");
      assertThat(m.rightKey(SPEC)).isEqualTo("alpha");
      assertThat(m.label()).isEqualTo("ref");
      assertThat(o.stats().stale()).containsExactly("alpha → gone (manuel)");
    }
  }

  @Nested
  class RejectedRules {

    @Test
    void aRejectedRowStaysUnpairedEvenWhenTheComputationFindsAKey() {
      Replay.Outcome o =
          replay(
              column("nom", "alpha"),
              column("code", "alpha"),
              mapping(cells("alpha", "alpha", "", "rejete")));

      Match m = match(o, "alpha");
      assertThat(m.right()).isNull();
      assertThat(m.joined()).isFalse();
      assertThat(m.label()).isEqualTo(Replay.REJECTED_LABEL);
      assertThat(m.confidence()).isEqualTo(Confidence.REJETE);
      assertThat(m.writtenKey(SPEC)).isEqualTo("alpha");
      assertThat(o.stats().rejected()).isEqualTo(1);
      assertThat(o.stats().kept()).isZero();
    }
  }

  @Nested
  class ExactAndDerivedRules {

    @Test
    void anExactPairIsKeptWhileItsKeyExists() {
      Replay.Outcome o =
          replay(
              column("nom", "alpha"),
              column("code", "alpha", "beta"),
              mapping(cells("alpha", "beta", "ref", "exact")));

      Match m = match(o, "alpha");
      assertThat(m.rightKey(SPEC)).isEqualTo("beta");
      assertThat(m.method()).isEqualTo("ref");
      assertThat(m.confidence()).isEqualTo(Confidence.EXACT);
      assertThat(o.stats().kept()).isEqualTo(1);
      assertThat(o.stats().recomputed()).isZero();
      assertThat(o.stats().stale()).isEmpty();
    }

    @Test
    void aDerivedPairIsKeptLikeAnExactOne() {
      Replay.Outcome o =
          replay(
              column("nom", "alpha"),
              column("code", "alpha", "beta"),
              mapping(cells("alpha", "beta", "chemin", "derived")));

      Match m = match(o, "alpha");
      assertThat(m.rightKey(SPEC)).isEqualTo("beta");
      assertThat(m.method()).isEqualTo("chemin");
      assertThat(m.confidence()).isEqualTo(Confidence.DERIVED);
      assertThat(o.stats().kept()).isEqualTo(1);
    }

    @Test
    void anExactPairWhoseKeyVanishedIsRecomputedAndReportedStale() {
      Replay.Outcome o =
          replay(
              column("nom", "alpha"),
              column("code", "alpha"),
              mapping(cells("alpha", "gone", "ref", "exact")));

      Match m = match(o, "alpha");
      assertThat(m.rightKey(SPEC)).isEqualTo("alpha");
      assertThat(m.confidence()).isEqualTo(Confidence.EXACT);
      assertThat(o.stats().recomputed()).isEqualTo(1);
      assertThat(o.stats().kept()).isZero();
      assertThat(o.stats().stale()).containsExactly("alpha → gone");
    }
  }

  @Nested
  class RecomputedRules {

    @Test
    void aSuggestionIsRecomputedNotKept() {
      Replay.Outcome o =
          replay(
              column("nom", "delta"),
              column("code", "beta", "delta"),
              mapping(cells("delta", "beta", "", "suggestion")));

      Match m = match(o, "delta");
      assertThat(m.rightKey(SPEC)).isEqualTo("delta");
      assertThat(m.confidence()).isEqualTo(Confidence.EXACT);
      assertThat(o.stats().recomputed()).isEqualTo(1);
      assertThat(o.stats().kept()).isZero();
    }

    @Test
    void aRowWithoutAMatchIsRecomputedAndFindsOneOnceAMethodCanReachIt() {
      Replay.Outcome o =
          replay(
              column("nom", "gamma"),
              column("code", "gamma"),
              mapping(cells("gamma", "", "", "none")));

      assertThat(match(o, "gamma").rightKey(SPEC)).isEqualTo("gamma");
      assertThat(o.stats().recomputed()).isEqualTo(1);
    }
  }

  @Nested
  class ReapparierRules {

    @Test
    void reapparierRecomputesExactAndDerivedRows() {
      Replay.Outcome o =
          replay(
              column("nom", "alpha", "beta"),
              column("code", "alpha", "beta", "gamma"),
              mapping(
                  cells("alpha", "gamma", "ref", "exact"),
                  cells("beta", "gamma", "chemin", "derived")),
              true);

      assertThat(match(o, "alpha").rightKey(SPEC)).isEqualTo("alpha");
      assertThat(match(o, "beta").rightKey(SPEC)).isEqualTo("beta");
      assertThat(o.stats().recomputed()).isEqualTo(2);
      assertThat(o.stats().kept()).isZero();
    }

    @Test
    void reapparierNeverRecomputesManualOrRejectedRows() {
      Replay.Outcome o =
          replay(
              column("nom", "alpha", "beta"),
              column("code", "alpha", "beta", "gamma"),
              mapping(cells("alpha", "gamma", "", "manuel"), cells("beta", "gamma", "", "rejete")),
              true);

      Match manual = match(o, "alpha");
      assertThat(manual.rightKey(SPEC)).isEqualTo("gamma");
      assertThat(manual.label()).isEqualTo(Replay.MANUAL_LABEL);
      Match rejected = match(o, "beta");
      assertThat(rejected.right()).isNull();
      assertThat(rejected.writtenKey(SPEC)).isEqualTo("gamma");
      assertThat(o.stats().manual()).isEqualTo(1);
      assertThat(o.stats().rejected()).isEqualTo(1);
      assertThat(o.stats().recomputed()).isZero();
    }
  }

  @Nested
  class NewAndOrphanRows {

    @Test
    void aNewLeftRowIsAddedAndCountedAsAdded() {
      Replay.Outcome o =
          replay(
              column("nom", "alpha", "delta"),
              column("code", "alpha", "delta"),
              mapping(cells("alpha", "alpha", "ref", "exact")));

      assertThat(match(o, "delta").rightKey(SPEC)).isEqualTo("delta");
      assertThat(o.stats().added()).isEqualTo(1);
      assertThat(o.stats().kept()).isEqualTo(1);
    }

    @Test
    void aPreviousRowWhoseLeftIsGoneIsKeptAsAnOrphan() {
      Replay.Outcome o =
          replay(
              column("nom", "alpha"),
              column("code", "alpha", "beta"),
              mapping(
                  cells("alpha", "alpha", "ref", "exact"), cells("gone", "beta", "ref", "exact")));

      assertThat(o.result().matches()).hasSize(1);
      assertThat(o.stats().orphans()).hasSize(1);
      assertThat(o.stats().orphans().getFirst()).containsExactly("gone", "beta", "ref", "exact");
    }
  }
}
