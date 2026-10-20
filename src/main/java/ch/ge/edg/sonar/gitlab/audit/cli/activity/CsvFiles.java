package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import com.opencsv.CSVWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/** Les deux CSV de l'audit, à lire ensemble : inventaire.csv et pratiques.csv. */
final class CsvFiles {

  static final List<String> INVENTORY =
      List.of(
          "id",
          "path",
          "name",
          "namespace",
          "default_branch",
          "visibility",
          "created_at",
          "last_activity_at",
          "archived",
          "fork",
          "mirror",
          "total_commits",
          "repo_size",
          "bucket",
          "last_commit",
          "commits_window",
          "commits_bots",
          "authors_window",
          "merge_commits",
          "reverts",
          "active_days",
          "commits_per_week",
          "tronque",
          "exclu",
          "fuite_filtre",
          "selectionne",
          "motif_selection");

  static final List<String> PRATIQUES =
      List.of(
          "path",
          "motif_selection",
          "bucket",
          "commits_window",
          "authors_window",
          "branche_protegee",
          "push_verrouille",
          "mr_fusionnees",
          "ttm_median_j",
          "auto_merge",
          "notes_par_mr",
          "echantillon_approbations",
          "part_approuvee",
          "auto_approbation",
          "pipelines",
          "taux_succes",
          "incidents_rouges",
          "retour_au_vert_h",
          "rouge_non_resolu",
          "environnements",
          "deploiements",
          "dora_indispo",
          "ci_sonar",
          "ci_securite",
          "cle_sonar",
          "source_cle_sonar",
          "fichiers",
          "id",
          "fenetre_j",
          "lignes_modifiees",
          "commits_lignes",
          "lignes_tronquees",
          "cle_pom",
          "nom_pom");

  private CsvFiles() {}

  /** Une ligne par projet, sous l'en-tête donné. */
  static void write(
      Path file, boolean comma, List<String> header, List<Proj> rows, Function<Proj, String[]> row)
      throws IOException {
    try (CSVWriter w = Csv.writer(file, comma)) {
      w.writeNext(header.toArray(String[]::new));
      for (Proj p : rows) w.writeNext(row.apply(p));
    }
  }
}
