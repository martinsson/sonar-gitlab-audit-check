package ch.ge.edg.sonar.gitlab.audit.cli.activity;

/** Le verdict d'une sonde du préambule, avec sa couleur. */
enum Verdict {
  OK(Screen.GREEN, "OK"),
  DENIED(Screen.RED, "REFUSÉ"),
  MISSING(Screen.YELLOW, "ABSENT"),
  EMPTY(Screen.YELLOW, "VIDE"),
  ERROR(Screen.RED, "ERREUR");
  final String color;
  final String tag;

  Verdict(String color, String tag) {
    this.color = color;
    this.tag = tag;
  }
}
