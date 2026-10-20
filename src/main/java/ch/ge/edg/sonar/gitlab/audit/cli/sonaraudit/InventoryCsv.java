package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.dec;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.orEmpty;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.parseDate;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Component;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.HistoryPoint;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.SearchHistory;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.exchange.SonarInventory;
import com.opencsv.CSVWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/** inventaire.csv, une ligne par projet, et inventaire-historique.csv à côté. */
final class InventoryCsv {

  /** Ce que l'inventaire a lu, projet par projet, indexé par clé. */
  record Park(
      List<Component> projects,
      Map<String, Map<String, String>> measures,
      Map<String, Attachments> attachments,
      Map<String, Trend> trends) {}

  private final boolean comma;
  private final Clock clock;

  /**
   * @param comma virgules sans BOM plutôt que la forme Excel
   * @param clock l'âge des analyses se compte depuis maintenant, dans le fuseau de l'horloge
   */
  InventoryCsv(boolean comma, Clock clock) {
    this.comma = comma;
    this.clock = clock;
  }

  void write(Path csv, Park park) throws IOException {
    List<String> header = SonarInventory.header();

    LocalDateTime now = LocalDateTime.now(clock);
    // Le même écrivain que les trois autres outils : un inventaire qui
    // s'ouvre de travers dans Excel passe pour un outil cassé, et personne
    // ne fait le détour par l'assistant d'import. Les outils qui relisent ce
    // fichier reniflent le séparateur, donc le défaut lisible ne leur coûte
    // rien ; --comma reste là pour qui veut l'autre forme.
    try (CSVWriter w = Csv.writer(csv, comma)) {
      w.writeNext(header.toArray(String[]::new));
      for (Component p : park.projects()) w.writeNext(row(p, park, now));
    }
  }

  /** inventaire.csv → inventaire-historique.csv, à côté. */
  static Path historyPath(Path csv) {
    String name = csv.getFileName().toString();
    int dot = name.lastIndexOf('.');
    String base = dot > 0 ? name.substring(0, dot) : name;
    return csv.resolveSibling(base + "-historique.csv");
  }

  /**
   * Une ligne par projet et par date d'analyse, les mesures de tendance en colonnes : de quoi
   * tracer la série que les pentes résument. Une cellule vide = mesure absente à cette date, pas
   * zéro.
   */
  void writeHistory(Path path, Map<String, SearchHistory> histories) throws IOException {
    List<String> header = new ArrayList<>(List.of("key", "date"));
    header.addAll(Trends.METRICS);
    try (CSVWriter w = Csv.writer(path, comma)) {
      w.writeNext(header.toArray(String[]::new));
      for (Map.Entry<String, SearchHistory> e : histories.entrySet()) {
        byDate(e.getValue())
            .forEach((date, values) -> w.writeNext(historyRow(e.getKey(), date, values)));
      }
    }
  }

  private static SortedMap<String, Map<String, String>> byDate(SearchHistory h) {
    SortedMap<String, Map<String, String>> byDate = new TreeMap<>();
    for (String metric : Trends.METRICS) {
      for (HistoryPoint pt : h.pointsFor(metric)) {
        byDate.computeIfAbsent(pt.date(), d -> new HashMap<>()).put(metric, pt.value());
      }
    }
    return byDate;
  }

  private static String[] historyRow(String key, String date, Map<String, String> values) {
    List<String> row = new ArrayList<>(List.of(key, date));
    Trends.METRICS.forEach(m -> row.add(orEmpty(values.get(m))));
    return row.toArray(String[]::new);
  }

  private String[] row(Component p, Park park, LocalDateTime now) {
    LocalDateTime d = parseDate(p.analysisDate(), clock.getZone());
    List<String> row =
        new ArrayList<>(
            List.of(
                orEmpty(p.key()),
                orEmpty(p.name()),
                orEmpty(p.analysisDate()),
                d == null ? "" : String.valueOf(ChronoUnit.DAYS.between(d, now))));
    Map<String, String> m = park.measures().getOrDefault(p.key(), Map.of());
    SonarInventory.METRICS.forEach(metric -> row.add(orEmpty(m.get(metric))));
    row.add(orEmpty(p.branch()));
    row.add(p.isMain() == null ? "" : String.valueOf(p.isMain()));
    row.add(orEmpty(p.mainBranchAnalysisDate()));
    attachmentCells(row, park.attachments().get(p.key()));
    trendCells(row, park.trends().get(p.key()));
    return row.toArray(String[]::new);
  }

  /**
   * Colonnes vides avec --no-bindings : non lues, ce que l'en-tête ne dit pas mais que la console a
   * dit.
   */
  private static void attachmentCells(List<String> row, @Nullable Attachments a) {
    row.add(a == null ? "" : a.alm());
    row.add(a == null ? "" : a.repository());
    row.add(a == null ? "" : a.links());
  }

  /**
   * Vides avec --no-trend, comme au-dessus. Vides aussi, colonne par colonne, quand la série ne
   * suffit pas : absent n'est pas « stable ».
   */
  private static void trendCells(List<String> row, @Nullable Trend t) {
    if (t == null) {
      row.addAll(List.of("", "", "", "", ""));
      return;
    }
    row.add(String.valueOf(t.points()));
    row.add(String.valueOf(t.spanDays()));
    row.add(dec(t.violationsPerMonth()));
    row.add(dec(t.densityPctPerMonth()));
    row.add(dec(t.debtRatioPctPerMonth()));
  }
}
