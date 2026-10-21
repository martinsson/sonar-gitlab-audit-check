package ch.ge.edg.sonar.gitlab.audit.rest;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

/**
 * Arrêt propre d'une commande, sur ce qu'on ne sait pas contourner : URL ou jeton absent, jeton
 * refusé, un appel sans lequel rien n'a de sens. L'outil s'arrête en le disant, sans trace de pile.
 */
public final class Stop extends Exception {

  @Serial private static final long serialVersionUID = 1L;

  public Stop(String message) {
    super(message);
  }

  /** La valeur d'une option qui retombe sur une variable d'environnement. */
  public static String require(@Nullable String value, String env) throws Stop {
    if (value == null || value.isBlank()) throw new Stop(env + " manquant");
    return value.trim();
  }

  /**
   * La réponse telle quelle, sauf un 401 : un jeton refusé l'est pour toutes les requêtes
   * suivantes, continuer ne lirait plus rien.
   *
   * @param base l'instance appelée, pour le message
   */
  public static RestResponse unlessRefused(RestResponse r, String base) throws Stop {
    if (r.refused()) throw new Stop(base + " : jeton refusé (401)");
    return r;
  }
}
