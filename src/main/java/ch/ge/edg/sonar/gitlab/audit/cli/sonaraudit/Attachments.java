package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

/** Ce que l'inventaire écrit de la liaison et des liens d'un projet. */
record Attachments(String alm, String repository, String links) {
  static final String REFUSED = "illisible";

  boolean refused() {
    return alm.startsWith(REFUSED);
  }
}
