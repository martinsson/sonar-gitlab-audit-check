package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

/** L'étiquette colorée d'une ligne de statut : ce qu'a répondu un endpoint. */
enum Verdict {
  OK(Report.GREEN, "OK      "),
  DENIED(Report.RED, "REFUSE  "),
  MISSING(Report.YELLOW, "ABSENT  "),
  EMPTY(Report.YELLOW, "VIDE    "),
  ERROR(Report.RED, "ERREUR  ");

  final String color;
  final String tag;

  Verdict(String color, String tag) {
    this.color = color;
    this.tag = tag;
  }
}
