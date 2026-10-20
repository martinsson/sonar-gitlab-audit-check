package ch.ge.edg.sonar.gitlab.audit.matching;

/**
 * Les garde-fous de la ressemblance de noms : le score minimal d'un candidat, et l'écart minimal
 * avec le suivant. À plat dans le JSON du résumé.
 */
public record Guards(double threshold, double margin) {}
