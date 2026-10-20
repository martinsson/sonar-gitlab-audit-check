package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.GREEN;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.RED;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.YELLOW;

import ch.ge.edg.sonar.gitlab.audit.sonar.Sonar;

/** Ce que l'audit dit d'un appel : OK, REFUSE, ABSENT, VIDE ou ERREUR. */
enum CallVerdict {
  OK(GREEN, "OK      "),
  DENIED(RED, "REFUSE  "),
  MISSING(YELLOW, "ABSENT  "),
  EMPTY(YELLOW, "VIDE    "),
  ERROR(RED, "ERREUR  ");
  final String color;
  final String tag;

  CallVerdict(String color, String tag) {
    this.color = color;
    this.tag = tag;
  }

  /**
   * Le verdict est une affaire de présentation, pas de transport : le client rapporte un statut,
   * l'audit décide de ce qu'il en dit.
   */
  static CallVerdict of(Sonar.Response r) {
    return switch (r.status()) {
      case 200 -> OK;
      case 401, 403 -> DENIED;
      case 404 -> MISSING;
      default -> ERROR;
    };
  }
}
