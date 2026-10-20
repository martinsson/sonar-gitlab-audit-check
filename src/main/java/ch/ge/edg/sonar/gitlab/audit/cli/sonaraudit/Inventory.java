package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.BOLD;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.DIM;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Component;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.SearchHistory;
import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** 3. Combien de projets je vois, combien m'échappent, et ce que chacun mesure. */
final class Inventory {

  private final Session session;
  private final Metrics metrics;
  private final OutputOptions output;
  private final ScanOptions scan;

  Inventory(Session session, Metrics metrics, OutputOptions output, ScanOptions scan) {
    this.session = session;
    this.metrics = metrics;
    this.output = output;
    this.scan = scan;
  }

  /**
   * @param exclusions la liste d'exclusion, ou null sans liste
   */
  void run(@Nullable Path exclusions) throws IOException {
    Console out = session.out();
    Presentation.title(out, "3. Inventaire des projets visibles");

    Projects visible = new Projects(session);
    List<Component> projects = visible.fetchAll();
    out.println(
        "  Projets visibles avec ce token : " + out.color(String.valueOf(projects.size()), BOLD));

    visible.reportScopeGap(projects.size());
    projects = visible.exclude(projects, exclusions);
    if (!scan.mainBranchOnly) projects = new Branches(session).resolveLatest(projects);
    visible.reportFreshness(projects, scan.staleDays);

    Map<String, Map<String, String>> measures = new Measures(session, metrics).fetch(projects);
    Measures.reportMissingCoverage(out, projects, measures);

    Map<String, Attachments> attachments =
        scan.noBindings ? Map.of() : new Bindings(session).fetch(projects);
    Trends trends = new Trends(session, scan.activityDays);
    Map<String, Trend> byKey = scan.noTrend ? Map.of() : trends.fetch(projects, metrics);

    Path csv = output.csv;
    if (csv != null) {
      write(csv, new InventoryCsv.Park(projects, measures, attachments, byKey), trends.histories());
    }
  }

  private void write(Path csv, InventoryCsv.Park park, Map<String, SearchHistory> histories)
      throws IOException {
    Console out = session.out();
    InventoryCsv writer = new InventoryCsv(output.comma.enabled(), session.clock());
    writer.write(csv, park);
    out.println();
    out.println("  CSV écrit : " + out.color(csv.toString(), BOLD));
    out.println(out.color(Csv.openingHint(csv, output.comma.enabled()), DIM));
    if (!histories.isEmpty()) {
      Path history = InventoryCsv.historyPath(csv);
      writer.writeHistory(history, histories);
      out.println("  Historique écrit : " + out.color(history.toString(), BOLD));
    }
  }
}
