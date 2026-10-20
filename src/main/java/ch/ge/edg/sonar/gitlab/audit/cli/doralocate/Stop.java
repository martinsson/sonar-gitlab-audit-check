package ch.ge.edg.sonar.gitlab.audit.cli.doralocate;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

/** Arrêt propre : URL ou jeton absent, jeton refusé, projet GitLab introuvable. */
public final class Stop extends Exception {

  @Serial private static final long serialVersionUID = 1L;

  public Stop(String message) {
    super(message);
  }

  /** La valeur d'une option qui retombe sur une variable d'environnement. */
  static String require(@Nullable String value, String env) throws Stop {
    if (value == null || value.isBlank()) throw new Stop(env + " manquant");
    return value.trim();
  }
}
