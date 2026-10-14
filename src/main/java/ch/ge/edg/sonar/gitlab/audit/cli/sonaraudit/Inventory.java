package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.BOLD;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.c;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Component;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.SearchHistory;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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

  void run() throws IOException {
    PrintStream out = session.out();
    Presentation.title(out, "3. Inventaire des projets visibles");

    Projects visible = new Projects(session);
    List<Component> projects = visible.fetchAll();
    out.println("  Projets visibles avec ce token : " + c(String.valueOf(projects.size()), BOLD));

    visible.reportScopeGap(projects.size());
    projects = visible.exclude(projects, output.exclusionsFile);
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
    PrintStream out = session.out();
    InventoryCsv writer = new InventoryCsv(output.comma);
    writer.write(csv, park);
    out.println();
    out.println("  CSV écrit : " + c(csv.toString(), BOLD));
    out.println(c(Csv.openingHint(csv, output.comma), DIM));
    if (!histories.isEmpty()) {
      Path history = InventoryCsv.historyPath(csv);
      writer.writeHistory(history, histories);
      out.println("  Historique écrit : " + c(history.toString(), BOLD));
    }
  }
}
