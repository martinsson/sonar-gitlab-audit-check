package ch.ge.edg.sonar.gitlab.audit.exchange;

import java.util.ArrayList;
import java.util.List;

/**
 * inventaire.csv de SonarAuditCheck : les colonnes qu'il écrit, lues par le classement, le
 * croisement et l'appariement. Un nom change ici ou nulle part.
 */
public final class SonarInventory {

  public static final String KEY = "key";
  public static final String NAME = "name";
  public static final String ANALYSIS_DATE = "analysisDate";
  public static final String DAYS_SINCE_ANALYSIS = "days_since_analysis";

  public static final String NCLOC = "ncloc";
  public static final String COVERAGE = "coverage";
  public static final String NEW_COVERAGE = "new_coverage";
  public static final String TESTS = "tests";
  public static final String SQALE_INDEX = "sqale_index";
  public static final String SQALE_DEBT_RATIO = "sqale_debt_ratio";
  public static final String NEW_LINES = "new_lines";
  public static final String NEW_VIOLATIONS = "new_violations";
  public static final String NEW_DUPLICATED_LINES_DENSITY = "new_duplicated_lines_density";
  public static final String ALERT_STATUS = "alert_status";

  public static final String ALM = "alm";
  public static final String ALM_REPOSITORY = "alm_repository";
  public static final String LIENS = "liens";

  public static final List<String> IDENTITY =
      List.of(KEY, NAME, ANALYSIS_DATE, DAYS_SINCE_ANALYSIS);

  public static final List<String> BRANCH =
      List.of("branch", "is_main_branch", "main_branch_analysisDate");

  public static final List<String> ATTACHMENT = List.of(ALM, ALM_REPOSITORY, LIENS);

  public static final List<String> TREND =
      List.of(
          "tendance_analyses",
          "tendance_jours",
          "violations_pente_mois",
          "violations_kloc_pente_pct_mois",
          "dette_ratio_pente_pct_mois");

  /**
   * Les mesures demandées à SonarQube, et — c'est le même objet — les colonnes du CSV. Il y avait
   * ici deux listes à tenir d'accord, une pour l'appel et une pour l'écriture : {@code sqale_index}
   * figurait dans la première et pas dans la seconde, donc il était demandé à chaque projet du parc
   * puis jeté. Une mesure absente du CSV se lit comme une mesure que SonarQube ne donne pas, ce qui
   * est faux et coûte cher : c'est la dette en valeur absolue, celle sur laquelle
   * GITLAB_ANALYSIS.md raisonne.
   *
   * <p>L'ordre est celui du CSV. Quatre groupes :
   *
   * <p>1. taille et forme — de quoi stratifier, et lire tout le reste ; 2. tests et couverture —
   * voir la note ci-dessous ; 3. dette et défauts — les comptes derrière les notes A–E ; 4. code
   * neuf — ce qui se crée, plutôt que ce qui est porté.
   *
   * <p>**Pourquoi autant autour de la couverture.** {@code coverage} seule ne distingue pas les
   * deux situations qui appellent des actions opposées : une équipe qui n'a pas de tests, et une
   * équipe qui en a mais dont le rapport de couverture n'arrive pas jusqu'à SonarQube. {@code
   * tests} et {@code lines_to_cover} les séparent — 0 % avec 400 tests est un problème de
   * tuyauterie CI, 0 % avec 0 test est un problème d'ingénierie — et {@code uncovered_lines} donne
   * enfin un volume à l'effort, là où un pourcentage sur un projet de 200 lignes et un sur un
   * monolithe se ressemblent.
   *
   * <p>**Pourquoi les comptes en plus des notes.** {@code *_rating} est ordinal : un 4 ne vaut pas
   * deux 2, et la moyenne d'une colonne de notes n'a pas de sens. {@code bugs}, {@code
   * vulnerabilities}, {@code code_smells} se somment, se comparent, se rapportent à {@code ncloc}.
   */
  public static final List<String> METRICS =
      List.of(
          // 1. Taille et forme
          "ncloc",
          "files",
          "complexity",
          "cognitive_complexity",
          "comment_lines_density",
          "duplicated_lines_density",
          "new_duplicated_lines_density",
          // 2. Tests et couverture
          "coverage",
          "line_coverage",
          "branch_coverage",
          "new_coverage",
          "lines_to_cover",
          "uncovered_lines",
          "tests",
          "test_failures",
          "test_errors",
          "skipped_tests",
          "test_success_density",
          // 3. Dette et défauts
          "sqale_index",
          "sqale_debt_ratio",
          "sqale_rating",
          "bugs",
          "reliability_rating",
          "vulnerabilities",
          "security_rating",
          "security_hotspots",
          "security_hotspots_reviewed",
          "code_smells",
          "violations",
          // 4. Code neuf
          "new_lines",
          "new_violations",
          "new_bugs",
          "new_vulnerabilities",
          "new_code_smells",
          "alert_status");

  private SonarInventory() {}

  /** Ce que tout lecteur de l'inventaire exige : sans elles, ce n'est pas un inventaire Sonar. */
  public static String[] required() {
    return new String[] {KEY, NCLOC, ANALYSIS_DATE};
  }

  /** L'en-tête complet, dans l'ordre d'écriture. */
  public static List<String> header() {
    List<String> header = new ArrayList<>(IDENTITY);
    header.addAll(METRICS);
    header.addAll(BRANCH);
    header.addAll(ATTACHMENT);
    header.addAll(TREND);
    return header;
  }
}
