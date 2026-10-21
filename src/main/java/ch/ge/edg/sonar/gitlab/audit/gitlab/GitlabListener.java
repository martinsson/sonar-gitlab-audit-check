package ch.ge.edg.sonar.gitlab.audit.gitlab;

/**
 * Ce que le client a à dire en chemin. Il n'imprime rien : la commande décide où et comment le
 * montrer.
 */
public interface GitlabListener {

  /**
   * Une lecture paginée s'est arrêtée sur une page en erreur ; ce qui a été lu est gardé.
   *
   * @param status « HTTP 403 », ou « injoignable »
   */
  void pagingInterrupted(String page, String status);

  /** La capture d'une réponse n'a pas pu s'écrire ({@code --dump-dir}). */
  void captureNotWritten(String reason);
}
