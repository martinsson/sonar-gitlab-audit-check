package ch.ge.edg.sonar.gitlab.audit.cli;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The pure helpers of {@link Vues}: the named signals, and the small utilities around them. */
class VuesSignalsTest {

  /** A row from alternating column/value pairs. */
  static Csv.Row row(String... kv) {
    Map<String, Integer> index = new HashMap<>();
    String[] cells = new String[kv.length / 2];
    for (int i = 0; i < kv.length; i += 2) {
      index.put(kv[i], i / 2);
      cells[i / 2] = kv[i + 1];
    }
    return new Csv.Row(index, cells);
  }

  @Test
  void an_empty_row_raises_no_signal() {
    assertThat(Vues.signals(row())).isEmpty();
  }

  @Test
  void density_slope_counts_strictly_above_two() {
    assertThat(Vues.signals(row("sq_violations_kloc_pente_pct_mois", "2"))).isEmpty();
    assertThat(Vues.signals(row("sq_violations_kloc_pente_pct_mois", "2.01")))
        .containsExactly("densité en hausse");
  }

  @Test
  void issues_per_modified_kloc_counts_strictly_above_zero() {
    assertThat(Vues.signals(row("issues_par_kloc_modifie", "0"))).isEmpty();
    assertThat(Vues.signals(row("issues_par_kloc_modifie", "0.1")))
        .containsExactly("modifs ajoutent des issues");
  }

  @Test
  void new_code_density_needs_200_lines_and_more_than_10_per_kloc() {
    assertThat(Vues.signals(row("sq_new_lines", "200", "sq_new_violations", "2"))).isEmpty();
    assertThat(Vues.signals(row("sq_new_lines", "200", "sq_new_violations", "3")))
        .containsExactly("code neuf > 10 issues/kLOC");
    assertThat(Vues.signals(row("sq_new_lines", "199", "sq_new_violations", "100"))).isEmpty();
    assertThat(Vues.signals(row("sq_new_lines", "500"))).isEmpty();
    assertThat(Vues.signals(row("sq_new_violations", "500"))).isEmpty();
  }

  @Test
  void new_coverage_counts_strictly_below_fifty() {
    assertThat(Vues.signals(row("sq_new_coverage", "50"))).isEmpty();
    assertThat(Vues.signals(row("sq_new_coverage", "49.9")))
        .containsExactly("code neuf < 50 % couvert");
  }

  @Test
  void no_review_needs_twenty_commits_and_no_merged_mr() {
    assertThat(Vues.signals(row("gl_commits_window", "20", "gl_mr_fusionnees", "0")))
        .containsExactly("aucune revue");
    assertThat(Vues.signals(row("gl_commits_window", "19", "gl_mr_fusionnees", "0"))).isEmpty();
    assertThat(Vues.signals(row("gl_commits_window", "20"))).containsExactly("aucune revue");
    assertThat(Vues.signals(row("gl_commits_window", "20", "gl_mr_fusionnees", "1"))).isEmpty();
  }

  @Test
  void auto_merge_needs_five_merged_and_more_than_half() {
    assertThat(Vues.signals(row("gl_mr_fusionnees", "5", "gl_auto_merge", "3")))
        .containsExactly("auto-merge > 50 %");
    assertThat(Vues.signals(row("gl_mr_fusionnees", "4", "gl_auto_merge", "4"))).isEmpty();
    assertThat(Vues.signals(row("gl_mr_fusionnees", "10", "gl_auto_merge", "5"))).isEmpty();
    assertThat(Vues.signals(row("gl_mr_fusionnees", "10"))).isEmpty();
  }

  @Test
  void no_review_wins_over_auto_merge() {
    assertThat(
            Vues.signals(
                row("gl_commits_window", "30", "gl_mr_fusionnees", "0", "gl_auto_merge", "9")))
        .containsExactly("aucune revue");
  }

  @Test
  void appsec_is_only_reported_when_read_as_false() {
    assertThat(Vues.signals(row("gl_ci_securite", "FALSE"))).containsExactly("sans appsec");
    assertThat(Vues.signals(row("gl_ci_securite", "true"))).isEmpty();
    assertThat(Vues.signals(row("gl_ci_securite", ""))).isEmpty();
  }

  @Test
  void quality_gate_is_red_only_on_exact_error() {
    assertThat(Vues.signals(row("sq_alert_status", "ERROR"))).containsExactly("quality gate rouge");
    assertThat(Vues.signals(row("sq_alert_status", "error"))).isEmpty();
    assertThat(Vues.signals(row("sq_alert_status", "OK"))).isEmpty();
  }

  @Test
  void stale_analysis_needs_twenty_commits_and_more_than_thirty_days() {
    assertThat(
            Vues.signals(
                row(
                    "gl_commits_window",
                    "20",
                    "gl_mr_fusionnees",
                    "1",
                    "sq_days_since_analysis",
                    "31")))
        .containsExactly("analyse périmée");
    assertThat(
            Vues.signals(
                row(
                    "gl_commits_window",
                    "20",
                    "gl_mr_fusionnees",
                    "1",
                    "sq_days_since_analysis",
                    "30")))
        .isEmpty();
    assertThat(Vues.signals(row("gl_commits_window", "19", "sq_days_since_analysis", "99")))
        .isEmpty();
    assertThat(Vues.signals(row("gl_commits_window", "20", "gl_mr_fusionnees", "1"))).isEmpty();
  }

  @Test
  void every_signal_at_once_comes_in_a_fixed_order() {
    Csv.Row r =
        row(
            "sq_violations_kloc_pente_pct_mois", "5",
            "issues_par_kloc_modifie", "1",
            "sq_new_lines", "1000",
            "sq_new_violations", "50",
            "sq_new_coverage", "10",
            "gl_commits_window", "40",
            "gl_mr_fusionnees", "0",
            "gl_ci_securite", "false",
            "sq_alert_status", "ERROR",
            "sq_days_since_analysis", "60");
    assertThat(Vues.signals(r))
        .containsExactly(
            "densité en hausse",
            "modifs ajoutent des issues",
            "code neuf > 10 issues/kLOC",
            "code neuf < 50 % couvert",
            "aucune revue",
            "sans appsec",
            "quality gate rouge",
            "analyse périmée");
  }

  @Test
  void sibling_inserts_the_suffix_before_the_extension() {
    Path dir = Path.of("some", "dir");
    assertThat(Vues.sibling(dir.resolve("croisement.csv"), "derive"))
        .isEqualTo(dir.resolve("croisement-derive.csv"));
    assertThat(Vues.sibling(dir.resolve("a.b.txt"), "x")).isEqualTo(dir.resolve("a.b-x.txt"));
    assertThat(Vues.sibling(dir.resolve("noext"), "x")).isEqualTo(dir.resolve("noext-x.csv"));
    assertThat(Vues.sibling(dir.resolve(".hidden"), "x")).isEqualTo(dir.resolve(".hidden-x.csv"));
  }

  @Test
  void small_utilities() {
    assertThat(Vues.concat(List.of(1, 2), List.of(2, 3))).containsExactly(1, 2, 2, 3);
    assertThat(Vues.nz(row("a", "4.5"), "a")).isEqualTo(4.5);
    assertThat(Vues.nz(row("a", ""), "a")).isZero();
    assertThat(Vues.nz(row(), "a")).isZero();
    assertThat(Vues.orMin(null)).isEqualTo(-Double.MAX_VALUE);
    assertThat(Vues.orMin(3.0)).isEqualTo(3.0);
  }
}
