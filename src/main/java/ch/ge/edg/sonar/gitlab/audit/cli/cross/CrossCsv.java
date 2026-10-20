package ch.ge.edg.sonar.gitlab.audit.cli.cross;

import static ch.ge.edg.sonar.gitlab.audit.cli.cross.CrossReport.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.cross.CrossReport.c;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.matching.DetailColumns;
import ch.ge.edg.sonar.gitlab.audit.matching.Result;
import com.opencsv.CSVWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** croisement.csv : une ligne par projet GitLab, appariée ou non, les deux côtés préfixés. */
final class CrossCsv {

  /**
   * Les colonnes des deux côtés, plus la méthode d'appariement. Le préfixe dit d'où vient chaque
   * valeur : sans lui, {@code coverage} et {@code commits} dans la même ligne laissent croire à une
   * mesure unique alors que ce sont deux systèmes, deux dates et deux définitions.
   */
  private static final List<String> GL_COLUMNS =
      List.of(
          "path",
          "bucket",
          "commits_window",
          "authors_window",
          "mr_fusionnees",
          "auto_merge",
          "part_approuvee",
          "branche_protegee",
          "taux_succes",
          "rouge_non_resolu",
          "ci_sonar",
          "ci_securite",
          "cle_sonar",
          "source_cle_sonar",
          "cle_pom",
          "nom_pom",
          "id",
          "fenetre_j",
          "lignes_modifiees",
          "commits_lignes",
          "lignes_tronquees");

  private static final List<String> SQ_COLUMNS =
      List.of(
          "key",
          "name",
          "analysisDate",
          "days_since_analysis",
          "ncloc",
          "coverage",
          "new_coverage",
          "tests",
          "test_failures",
          "lines_to_cover",
          "uncovered_lines",
          "sqale_index",
          "sqale_debt_ratio",
          "bugs",
          "vulnerabilities",
          "code_smells",
          "violations",
          "new_lines",
          "new_violations",
          "alert_status",
          "duplicated_lines_density",
          "new_duplicated_lines_density",
          "alm",
          "alm_repository",
          "liens",
          "tendance_analyses",
          "tendance_jours",
          "violations_pente_mois",
          "violations_kloc_pente_pct_mois",
          "dette_ratio_pente_pct_mois");

  /** Calculées ici, à partir des deux côtés : ni gl_ ni sq_. */
  private static final List<String> CROSS_COLUMNS =
      List.of("issues_par_kloc_modifie", "issues_par_commit");

  private CrossCsv() {}

  static void write(
      Result mapping, List<CrossPair> pairs, Path file, boolean comma, PrintStream out)
      throws IOException {
    Path dir = file.toAbsolutePath().getParent();
    if (dir != null) Files.createDirectories(dir);

    try (CSVWriter w = Csv.writer(file, comma)) {
      w.writeNext(header(mapping).toArray(String[]::new));
      for (CrossPair p : pairs) w.writeNext(row(mapping, p));
    }
    out.printf("%n  Croisement : %d lignes → %s%n", pairs.size(), file.toAbsolutePath());
    out.println(c(Csv.openingHint(file, comma), DIM));
    out.println(
        c("    Une ligne par projet GitLab, appariée ou non. " + "Filtrer sur confiance", DIM));
    out.println(
        c("    = suggestion donne la liste des rapprochements " + "qu'un humain doit", DIM));
    out.println(c("    confirmer ; ils ne comptent dans aucun des constats " + "ci-dessus.", DIM));
  }

  /**
   * « projet » en tête : la colonne qu'on fige dans le tableur pour savoir de quel projet on lit la
   * ligne en défilant vers la droite.
   */
  private static List<String> header(Result mapping) {
    List<String> header = new ArrayList<>(List.of("projet", "methode_jointure", "confiance"));
    header.addAll(DetailColumns.header(mapping));
    GL_COLUMNS.forEach(col -> header.add("gl_" + col));
    SQ_COLUMNS.forEach(col -> header.add("sq_" + col));
    header.addAll(CROSS_COLUMNS);
    return header;
  }

  private static String[] row(Result mapping, CrossPair p) {
    List<String> row =
        new ArrayList<>(List.of(p.gl().str("path"), p.m().label(), p.m().confidence().code()));
    row.addAll(DetailColumns.cells(mapping, p.m()));
    GL_COLUMNS.forEach(col -> row.add(p.gl().str(col)));
    SQ_COLUMNS.forEach(col -> row.add(p.sqStr(col)));
    row.add(dec(CrossPair.perKlocChanged(p)));
    row.add(dec(CrossPair.perCommit(p)));
    return row.toArray(String[]::new);
  }

  static String dec(@Nullable Double d) {
    return d == null ? "" : String.format(Locale.ROOT, "%.2f", d);
  }
}
