package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Mise en forme des nombres et des textes du rapport, sans rien imprimer. */
final class Format {

  private Format() {}

  static String bar(double percent) {
    int n = (int) Math.round(percent / 5);
    return "#".repeat(Math.max(0, Math.min(20, n)));
  }

  static String dowBar(int[] byDow) {
    String[] labels = {"L", "M", "M", "J", "V", "S", "D"};
    int max = Arrays.stream(byDow).max().orElse(1);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 7; i++) {
      int h = max == 0 ? 0 : (int) Math.round(5.0 * byDow[i] / max);
      sb.append(labels[i]).append(":").append("▁▂▃▅▆█".charAt(Math.min(5, h))).append(' ');
    }
    return sb.toString().trim();
  }

  /** Interpolation linéaire sur une liste déjà triée. */
  static double percentile(List<Long> sorted, double p) {
    if (sorted.isEmpty()) return 0;
    if (sorted.size() == 1) return sorted.get(0);
    double rank = (p / 100.0) * (sorted.size() - 1);
    int lo = (int) Math.floor(rank);
    int hi = (int) Math.ceil(rank);
    return sorted.get(lo) + (rank - lo) * (sorted.get(hi) - sorted.get(lo));
  }

  static String hours(double h) {
    if (h * 60 < 1) return "< 1 min";
    if (h < 1) return Text.format("%.0f min", h * 60);
    if (h < 48) return Text.format("%.1f h", h);
    return Text.format("%.1f j", h / 24);
  }

  static String humanBytes(@Nullable Long bytes) {
    if (bytes == null) return "n/a";
    double v = bytes;
    String[] units = {"o", "Ko", "Mo", "Go"};
    int i = 0;
    while (v >= 1024 && i < units.length - 1) {
      v /= 1024;
      i++;
    }
    return Text.format("%.1f %s", v, units[i]);
  }

  /** Rapport entre lignes touchées et solde net : au-delà de 3, on refait plus qu'on n'ajoute. */
  static String rework(long added, long deleted) {
    long touched = added + deleted;
    long net = Math.abs(added - deleted);
    if (net == 0) return Text.format("%d touchées, solde nul", touched);
    return Text.format("%d / %d = %.1f", touched, net, (double) touched / net);
  }

  static String label(Map<String, String> labels, String identity) {
    return Text.truncate(labels.getOrDefault(identity, identity), 32);
  }

  static int orZero(@Nullable Integer i) {
    return i == null ? 0 : i;
  }

  static @Nullable String str(@Nullable Object o) {
    return o == null ? null : String.valueOf(o);
  }
}
