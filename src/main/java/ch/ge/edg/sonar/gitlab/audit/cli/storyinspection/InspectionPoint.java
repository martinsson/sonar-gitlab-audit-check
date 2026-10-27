package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

/** Le résultat d'un point de contrôle (numéro 1 à 15) sur une story. */
record InspectionPoint(int point, Verdict verdict, String evidence, String comment) {}
