package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

/** Le chemin du projet tel que l'API le comprend, à partir de ce que l'utilisateur a collé. */
final class ProjectPath {

  private ProjectPath() {}

  /**
   * Le chemin est ce que l'utilisateur a sous les yeux : il colle en général une URL de navigateur,
   * parfois avec /-/tree/main au bout. On accepte les trois formes plutôt que d'exiger la seule que
   * l'API comprend.
   */
  public static String normalize(String raw) {
    String p = raw.trim();
    int scheme = p.indexOf("://");
    if (scheme >= 0) {
      int slash = p.indexOf('/', scheme + 3);
      p = (slash < 0) ? "" : p.substring(slash + 1);
    }
    int dash = p.indexOf("/-/"); // .../-/tree/main, .../-/issues
    if (dash >= 0) p = p.substring(0, dash);
    p = p.replaceAll("^/+", "").replaceAll("/+$", "");
    if (p.endsWith(".git")) p = p.substring(0, p.length() - 4);
    return p;
  }
}
