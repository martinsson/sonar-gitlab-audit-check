package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import ch.ge.edg.sonar.gitlab.audit.rest.RestClient;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import java.io.Serial;
import org.jspecify.annotations.Nullable;

/**
 * Arrêt propre sur ce qu'on ne sait pas contourner : URL absente, jeton refusé, tableau illisible.
 */
final class Stop extends Exception {

  @Serial private static final long serialVersionUID = 1L;

  public Stop(String message) {
    super(message);
  }

  /** Un jeton refusé l'est pour toutes les requêtes suivantes : continuer ne lirait plus rien. */
  static RestResponse unlessRefused(RestResponse r, RestClient http) throws Stop {
    if (r.status() == 401) throw new Stop(http.base() + " : jeton refusé (401)");
    return r;
  }

  /** La valeur d'une option qui retombe sur une variable d'environnement. */
  static String require(@Nullable String value, String env) throws Stop {
    if (value == null || value.isBlank()) throw new Stop(env + " manquant");
    return value.trim();
  }
}
