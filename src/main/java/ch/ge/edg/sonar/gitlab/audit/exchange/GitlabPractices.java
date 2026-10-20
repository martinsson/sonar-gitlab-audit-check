package ch.ge.edg.sonar.gitlab.audit.exchange;

import java.util.List;

/** pratiques.csv de GitlabActivityAudit (--deep), relu par le croisement. */
public final class GitlabPractices {

  public static final String PATH = "path";
  public static final String ID = "id";
  public static final String COMMITS_WINDOW = "commits_window";
  public static final String LINES_CHANGED = "lignes_modifiees";
  public static final String SONAR_KEY = "cle_sonar";
  public static final String POM_KEY = "cle_pom";
  public static final String POM_NAME = "nom_pom";

  /** Les colonnes que le croisement reprend, préfixées gl_ dans son fichier. */
  public static final List<String> CROSSED =
      List.of(
          PATH,
          "bucket",
          COMMITS_WINDOW,
          "authors_window",
          "mr_fusionnees",
          "auto_merge",
          "part_approuvee",
          "branche_protegee",
          "taux_succes",
          "rouge_non_resolu",
          "ci_sonar",
          "ci_securite",
          SONAR_KEY,
          "source_cle_sonar",
          POM_KEY,
          POM_NAME,
          ID,
          "fenetre_j",
          LINES_CHANGED,
          "commits_lignes",
          "lignes_tronquees");

  private GitlabPractices() {}

  /** Ce que le croisement exige de pratiques.csv. */
  public static String[] required() {
    return new String[] {PATH, COMMITS_WINDOW};
  }
}
