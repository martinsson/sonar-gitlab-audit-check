package ch.ge.edg.sonar.gitlab.audit.cli.rank;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import com.opencsv.CSVWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** classement.csv : les trois listes l'une sous l'autre, la colonne {@code liste} les sépare. */
final class RankCsv {

  private static final List<String> RAW_COLUMNS =
      List.of(
          "ncloc",
          "new_lines",
          "new_violations",
          "new_coverage",
          "coverage",
          "new_duplicated_lines_density",
          "sqale_debt_ratio",
          "sqale_rating",
          "reliability_rating",
          "security_rating",
          "alert_status",
          "analysisDate",
          "days_since_analysis");

  /** Au-delà, une liste annexe n'est plus une liste mais un export. */
  private static final int SIDE_LIST_KEEP = 30;

  private RankCsv() {}

  static void write(
      Path file, boolean comma, List<Scored> velocity, List<Scored> carrying, List<Scored> abandoned)
      throws IOException {
    List<String> header =
        new ArrayList<>(
            List.of(
                "liste",
                "rang",
                "retenu",
                "key",
                "name",
                "strate",
                "score",
                "signaux_presents",
                "exclusion"));
    for (Signal s : Signal.values()) header.add("pct_" + s.column);
    header.addAll(RAW_COLUMNS);

    try (CSVWriter csv = Csv.writer(file, comma)) {
      csv.writeNext(header.toArray(String[]::new));
      writeList(csv, "VELOCITE", velocity, true);
      writeList(csv, "DETTE_PORTEE", carrying, false);
      writeList(csv, "ABANDON_RECENT", abandoned, false);
      csv.flush();
    }
  }

  private static void writeList(CSVWriter csv, String list, List<Scored> rows, boolean velocity) {
    int rank = 0;
    for (Scored s : rows) {
      if (s.ranked()) rank++;
      csv.writeNext(cells(list, s, rank, velocity));
    }
  }

  private static String[] cells(String list, Scored s, int rank, boolean velocity) {
    boolean ranked = s.ranked();
    List<String> row = new ArrayList<>();
    row.add(list);
    row.add(ranked ? String.valueOf(rank) : "");
    row.add(retained(s, rank, velocity) ? "O" : "N");
    row.add(s.row.key());
    row.add(s.row.name());
    row.add(s.bucket.name());
    row.add(ranked ? "%.1f".formatted(s.score) : "");
    row.add(velocity ? String.valueOf(s.signalsPresent()) : "");
    row.add(s.exclusion.label);
    for (Signal sig : Signal.values()) {
      Double p = s.percentiles.get(sig);
      row.add(p == null ? "" : "%.0f".formatted(p));
    }
    for (String col : RAW_COLUMNS) row.add(s.row.str(col));
    return row.toArray(String[]::new);
  }

  /** La shortlist pour la vélocité ; les premiers de la liste pour une liste annexe. */
  private static boolean retained(Scored s, int rank, boolean velocity) {
    return velocity ? s.shortlisted : rank <= SIDE_LIST_KEEP;
  }
}
