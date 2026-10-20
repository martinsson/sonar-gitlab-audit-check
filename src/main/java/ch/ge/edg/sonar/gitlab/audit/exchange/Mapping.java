package ch.ge.edg.sonar.gitlab.audit.exchange;

/**
 * Le fichier d'appariement : ProjectMapper l'écrit, un humain y corrige des lignes, et le mapper
 * comme CrossAudit le relisent.
 */
public final class Mapping {

  public static final String METHOD = "methode";
  public static final String CONFIDENCE = "confiance";

  private static final String LEFT_PREFIX = "gauche_";
  private static final String RIGHT_PREFIX = "droite_";

  private Mapping() {}

  /** La colonne qui porte la clé du côté gauche. */
  public static String left(String leftKey) {
    return LEFT_PREFIX + leftKey;
  }

  public static String right(String rightKey) {
    return RIGHT_PREFIX + rightKey;
  }
}
