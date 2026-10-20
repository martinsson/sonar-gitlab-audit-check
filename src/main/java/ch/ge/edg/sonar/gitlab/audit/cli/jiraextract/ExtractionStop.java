package ch.ge.edg.sonar.gitlab.audit.cli.jiraextract;

/**
 * Une erreur qu'on ne sait pas contourner : jeton refusé, URL manquante, un appel sans lequel rien
 * n'a de sens. L'outil s'arrête en le disant, sans trace de pile.
 */
final class ExtractionStop extends Exception {

  private static final long serialVersionUID = 1L;

  public ExtractionStop(String message) {
    super(message);
  }
}
