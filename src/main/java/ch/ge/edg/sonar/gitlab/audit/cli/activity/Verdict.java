package ch.ge.edg.sonar.gitlab.audit.cli.activity;

/** Le verdict d'une sonde du préambule, avec sa couleur. */
enum Verdict {
  OK(Console.GREEN, "OK"),
  DENIED(Console.RED, "REFUSÉ"),
  MISSING(Console.YELLOW, "ABSENT"),
  EMPTY(Console.YELLOW, "VIDE"),
  ERROR(Console.RED, "ERREUR");
  final String color;
  final String tag;

  Verdict(String color, String tag) {
    this.color = color;
    this.tag = tag;
  }
}
