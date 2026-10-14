package ch.ge.edg.sonar.gitlab.audit.cli.rank;

/** Pourquoi un projet actif ne reçoit pas de score de vélocité. */
enum Exclusion {
  NONE(""),
  TOO_SMALL("trop petit"),
  NO_NEW_CODE("pas de code neuf sur la fenêtre"),
  TOO_FEW_SIGNALS("trop peu de signaux");

  final String label;

  Exclusion(String label) {
    this.label = label;
  }
}
