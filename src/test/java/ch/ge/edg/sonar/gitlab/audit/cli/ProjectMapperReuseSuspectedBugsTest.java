package ch.ge.edg.sonar.gitlab.audit.cli;

import static ch.ge.edg.sonar.gitlab.audit.cli.ProjectMapperFixtures.run;
import static ch.ge.edg.sonar.gitlab.audit.cli.ProjectMapperFixtures.write;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.SuspectedBug;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Replaying a mapping by hand: manual rows the replay handles in a way believed wrong. */
class ProjectMapperReuseSuspectedBugsTest {

  private static final String HEADER = "gauche_nom,droite_code,methode,confiance\n";

  @TempDir Path dir;

  /** Maps {@code left} to {@code right} by name, replaying {@code previous}; comma-separated. */
  private Cli.Result map(String left, String right, String previous) throws Exception {
    Path l = write(dir, "l.csv", left);
    Path r = write(dir, "r.csv", right);
    Path out = write(dir, "correspondances.csv", HEADER + previous);
    return run(
        "--left",
        l.toString(),
        "--right",
        r.toString(),
        "--out",
        out.toString(),
        "--comma",
        "--left-key",
        "nom",
        "--right-key",
        "code",
        "--exact",
        "ref:nom=code",
        "--names",
        "nom=code");
  }

  /** The rewritten mapping, without its header, each line cut to its first four columns. */
  private List<String> written() throws Exception {
    return Files.readAllLines(dir.resolve("correspondances.csv")).stream()
        .skip(1)
        .map(line -> String.join(",", List.of(line.split(",")).subList(0, 4)))
        .toList();
  }

  @Test
  @SuspectedBug("A manual row whose key vanished is counted in no total; it should count somewhere")
  void a_stale_manual_row_is_left_out_of_the_reuse_totals() throws Exception {
    Cli.Result r = map("nom\np0\np1\np2\n", "code\np0\np1\n", "p0,gone,,manuel\np1,p1,ref,exact\n");

    assertThat(r.exitCode()).isZero();
    // Three left rows, but the totals add up to two: p0 is in none of them.
    assertThat(r.out())
        .contains("1 gardées, 0 saisies à la main, 0 rejetées, 0 recalculées, 1 nouveaux projets");
  }

  @Test
  @SuspectedBug("A manual row whose key vanished is replaced by the fresh match; it should be kept")
  void a_stale_manual_row_is_rewritten_with_the_fresh_match() throws Exception {
    Cli.Result r = map("nom\np0\n", "code\np0\n", "p0,gone,,manuel\n");

    assertThat(r.exitCode()).isZero();
    assertThat(written()).containsExactly("\"p0\",\"p0\",\"ref\",\"exact\"");
  }

  @Test
  @SuspectedBug("A manual pair should withdraw the suggestion of its right row to another left row")
  void a_manual_pair_leaves_a_suggestion_of_the_same_right_row_in_place() throws Exception {
    Cli.Result r =
        map("nom\nzeta\nomega servic\n", "code\nomega service\n", "zeta,omega service,,manuel\n");

    assertThat(r.exitCode()).isZero();
    assertThat(written())
        .containsExactly(
            "\"zeta\",\"omega service\",\"saisi à la main\",\"manuel\"",
            "\"omega servic\",\"omega service\",\"noms ressemblants (TF-IDF)\",\"suggestion\"");
  }

  @Test
  @SuspectedBug("A stale manual row without a key prints a double space before (manuel)")
  void a_stale_manual_row_without_a_key_prints_two_spaces_before_manuel() throws Exception {
    Cli.Result r = map("nom\np0\n", "code\np0\n", "p0,,,manuel\n");

    assertThat(r.out()).contains("p0 →  (manuel)");
  }
}
