package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.io.PrintStream;
import java.nio.file.Path;

/** Ce que la commande dit en finissant : les quatre mesures, et où sont les fichiers. */
final class Summary {

  private Summary() {}

  static void print(Snapshot s, Path dir, PrintStream out) {
    Metrics m = Metrics.of(s);
    out.println("DORA " + s.project().path() + " sur " + m.days() + " jours");
    out.println(
        Text.format(
            "  Fréquence de déploiement      %s / sem. (%d déploiements réussis)",
            Show.tenths(m.perWeek()), m.successes().size()));
    out.println(
        Text.format(
            "  Délai de mise en production   %s (médiane sur %d)",
            Show.duration(m.leadTime()), m.leads().size()));
    if (m.incidentsRead()) {
      out.println(
          Text.format(
              "  Taux d'échec des changements  %s (%d sur %d)",
              Show.percent(m.changeFailureRate()), m.failedChanges(), m.runs().size()));
      out.println(
          Text.format(
              "  Temps de rétablissement       %s (médiane sur %d incident(s) résolu(s))",
              Show.duration(m.timeToRestore()), m.recoveries().size()));
    } else {
      out.println("  Taux d'échec des changements  — (pas de projet Jira)");
      out.println("  Temps de rétablissement       — (pas de projet Jira)");
    }
    out.println(
        "Écrit dans " + dir + " : " + SnapshotFile.NAME + ", dora.html, " + MiddlewareSql.NAME);
  }
}
