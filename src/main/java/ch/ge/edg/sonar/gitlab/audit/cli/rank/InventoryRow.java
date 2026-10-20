package ch.ge.edg.sonar.gitlab.audit.cli.rank;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Une ligne d'inventaire : la lecture générique de {@link Csv.Row}, plus ce que ce classement-ci
 * sait de ces colonnes. Les valeurs restent des String là-dessous — vide n'est pas zéro.
 */
record InventoryRow(Csv.Row cells) {

  /**
   * La lecture est celle de {@link Csv} : le séparateur s'y renifle, le BOM s'y retire, et un
   * aller-retour par Excel y survit. Ce fichier en avait sa propre copie, à quelques détails près —
   * deux lecteurs pour un seul format qui ne divergent que le jour où l'un des deux est corrigé.
   */
  public static List<InventoryRow> read(Path path) throws IOException {
    Csv.Table t = Csv.read(path);
    t.require(path, "SonarAuditCheck --csv", "key", "ncloc", "analysisDate");
    return t.rows().stream().map(InventoryRow::new).toList();
  }

  public String str(String column) {
    return cells.str(column);
  }

  /** {@code null} si la métrique est absente — jamais 0. */
  public @Nullable Double num(String column) {
    return cells.num(column);
  }

  public String key() {
    return str("key");
  }

  public String name() {
    return str("name");
  }

  public double ncloc() {
    Double n = num("ncloc");
    return n == null ? 0 : n;
  }

  /** {@code null} = jamais analysé. */
  public @Nullable Integer daysSinceAnalysis() {
    Double d = num("days_since_analysis");
    return (d == null || str("analysisDate").isEmpty()) ? null : (int) (double) d;
  }

  /** Sans nom, un projet se désigne par sa clé. */
  public String label() {
    String name = name();
    return name.isEmpty() ? key() : name;
  }

  /** Pas de code neuf sur la fenêtre : rien à mesurer en vitesse. */
  boolean noNewCode() {
    Double newLines = num("new_lines");
    return newLines == null || newLines <= 0;
  }
}
