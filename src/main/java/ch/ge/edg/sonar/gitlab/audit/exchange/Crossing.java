package ch.ge.edg.sonar.gitlab.audit.exchange;

import java.util.ArrayList;
import java.util.List;

/**
 * croisement.csv de CrossAudit, relu par Vues : une ligne par projet GitLab, les colonnes de chaque
 * côté préfixées de leur origine.
 */
public final class Crossing {

  public static final String PROJECT = "projet";
  public static final String METHOD = "methode_jointure";
  public static final String CONFIDENCE = "confiance";
  public static final String ISSUES_PER_KLOC_CHANGED = "issues_par_kloc_modifie";
  public static final String ISSUES_PER_COMMIT = "issues_par_commit";

  private static final String GITLAB_PREFIX = "gl_";
  private static final String SONAR_PREFIX = "sq_";

  /** Les colonnes de l'inventaire Sonar que le croisement reprend, préfixées sq_. */
  public static final List<String> SONAR_SIDE =
      List.of(
          SonarInventory.KEY,
          SonarInventory.NAME,
          SonarInventory.ANALYSIS_DATE,
          SonarInventory.DAYS_SINCE_ANALYSIS,
          SonarInventory.NCLOC,
          SonarInventory.COVERAGE,
          SonarInventory.NEW_COVERAGE,
          SonarInventory.TESTS,
          "test_failures",
          "lines_to_cover",
          "uncovered_lines",
          SonarInventory.SQALE_INDEX,
          SonarInventory.SQALE_DEBT_RATIO,
          "bugs",
          "vulnerabilities",
          "code_smells",
          "violations",
          SonarInventory.NEW_LINES,
          SonarInventory.NEW_VIOLATIONS,
          SonarInventory.ALERT_STATUS,
          "duplicated_lines_density",
          SonarInventory.NEW_DUPLICATED_LINES_DENSITY,
          SonarInventory.ALM,
          SonarInventory.ALM_REPOSITORY,
          SonarInventory.LIENS,
          "tendance_analyses",
          "tendance_jours",
          "violations_pente_mois",
          "violations_kloc_pente_pct_mois",
          "dette_ratio_pente_pct_mois");

  private Crossing() {}

  public static String gitlab(String column) {
    return GITLAB_PREFIX + column;
  }

  public static String sonar(String column) {
    return SONAR_PREFIX + column;
  }

  /**
   * « projet » en tête : la colonne qu'on fige dans le tableur. Entre l'identité et les deux côtés,
   * le détail de l'appariement, que seul l'appariement sait écrire.
   */
  public static List<String> header(List<String> matchDetail) {
    List<String> header = new ArrayList<>(List.of(PROJECT, METHOD, CONFIDENCE));
    header.addAll(matchDetail);
    GitlabPractices.CROSSED.forEach(col -> header.add(gitlab(col)));
    SONAR_SIDE.forEach(col -> header.add(sonar(col)));
    header.add(ISSUES_PER_KLOC_CHANGED);
    header.add(ISSUES_PER_COMMIT);
    return header;
  }
}
