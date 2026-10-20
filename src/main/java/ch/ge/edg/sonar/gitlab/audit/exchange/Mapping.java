package ch.ge.edg.sonar.gitlab.audit.exchange;

/**
 * Le fichier d'appariement : ProjectMapper l'écrit, un humain y corrige des lignes, et le mapper
 * comme CrossAudit le relisent.
 */
public final class Mapping {

  public static final String METHOD = Columns.named("methode");
  public static final String CONFIDENCE = Columns.named("confiance");

  private static final String LEFT_PREFIX = Columns.named("gauche_");
  private static final String RIGHT_PREFIX = Columns.named("droite_");

  private Mapping() {}

  /** La colonne qui porte la clé du côté gauche. */
  public static String left(String leftKey) {
    return LEFT_PREFIX + leftKey;
  }

  public static String right(String rightKey) {
    return RIGHT_PREFIX + rightKey;
  }
}
