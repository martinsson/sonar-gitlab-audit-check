package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

/** La conclusion d'un contrôle sur une story : le verdict, la preuve relevée et le conseil. */
record Finding(Verdict verdict, String evidence, String comment) {

  static Finding respecte(String evidence, String comment) {
    return new Finding(Verdict.RESPECTE, evidence, comment);
  }

  static Finding partiel(String evidence, String comment) {
    return new Finding(Verdict.PARTIEL, evidence, comment);
  }

  static Finding nonRespecte(String evidence, String comment) {
    return new Finding(Verdict.NON_RESPECTE, evidence, comment);
  }

  static Finding sansObjet(String evidence, String comment) {
    return new Finding(Verdict.SANS_OBJET, evidence, comment);
  }

  /** Les points qui lisent la description n'ont rien à dire d'une story qui n'en a pas. */
  static Finding sansDescription(String comment) {
    return sansObjet("Pas de description", comment);
  }

  /** Les points 7 à 15 de la checklist n'ont pas encore de contrôle automatique. */
  static Finding notImplemented(int point) {
    return sansObjet(
        "Point " + point + " non implémenté", "Inspection non implémentée pour ce point");
  }
}
