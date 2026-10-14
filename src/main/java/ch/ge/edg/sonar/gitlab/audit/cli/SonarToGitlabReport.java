package ch.ge.edg.sonar.gitlab.audit.cli;

import ch.ge.edg.sonar.gitlab.audit.cli.SonarToGitlab.Check;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarToGitlab.Pair;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarToGitlab.Result;
import ch.ge.edg.sonar.gitlab.audit.console.ConsoleOut;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/** Ce que la passe de {@link SonarToGitlab} a donné, en quelques lignes. */
final class SonarToGitlabReport {

  private static final String DIM = "\033[2m";

  private final PrintStream out;

  SonarToGitlabReport(PrintStream out) {
    this.out = out;
  }

  void print(Result r, Path file) {
    int n = r.pairs().size();
    long joined = r.pairs().stream().filter(Pair::joined).count();
    out.printf(
        "  Projets Sonar : %d, dont %d appariés sûrement (%s)%n",
        n, joined, ProjectMapper.pct(joined, n));
    confidences(r);
    reasons(r);
    checks(r);
    out.printf("  Sonar × tout GitLab : %d lignes → %s%n", n, file.toAbsolutePath());
  }

  /** Le compte par confiance, et les paires sûres hors de la sélection GitLab. */
  private void confidences(Result r) {
    Map<String, Integer> byConf = new TreeMap<>();
    int outside = 0, outsideActive = 0;
    for (Pair p : r.pairs()) {
      byConf.merge(p.confidence().code(), 1, Integer::sum);
      Csv.Row g = p.gitlab();
      if (g != null && p.joined() && !g.flag("selectionne")) {
        outside++;
        if (GitlabInventoryIndex.commits(g) >= 10) outsideActive++;
      }
    }
    byConf.forEach((c, k) -> out.printf("    %-10s %d%n", c, k));
    out.printf(
        "  Appariés à un dépôt hors de la sélection GitLab : %d, dont %d actifs "
            + "(10 commits ou plus)%n",
        outside, outsideActive);
  }

  private void reasons(Result r) {
    Map<String, Integer> reasons = new TreeMap<>();
    for (Pair p : r.pairs()) {
      if (p.gitlab() == null)
        reasons.merge(
            p.reason()
                .replaceFirst(" \\(.*", "")
                .replaceFirst("^déjà proposé à .*", "déjà proposé à un autre"),
            1,
            Integer::sum);
    }
    if (!reasons.isEmpty()) {
      out.println("  Sans dépôt GitLab, par raison :");
      reasons.forEach((why, k) -> out.printf("    %-36s %d%n", why, k));
    }
  }

  private void checks(Result r) {
    if (r.checks().isEmpty()) return;
    long ok = r.checks().stream().filter(Check::agrees).count();
    out.printf(
        "  Méthodes dérivées rejouées sur les paires exactes : %d sur %d d'accord%n",
        ok, r.checks().size());
    Map<String, int[]> perMethod = new TreeMap<>();
    for (Check c : r.checks()) {
      int[] a = perMethod.computeIfAbsent(c.method(), x -> new int[2]);
      a[c.agrees() ? 0 : 1]++;
    }
    perMethod.forEach(
        (m, a) ->
            out.println(
                ConsoleOut.color("    %s : %d d'accord, %d non".formatted(m, a[0], a[1]), DIM)));
  }
}
