package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import ch.ge.edg.sonar.gitlab.audit.gocd.GocdApi;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import ch.ge.edg.sonar.gitlab.audit.runtime.Sleeper;
import java.io.IOException;
import org.jspecify.annotations.Nullable;

/**
 * GoCD tel que l'extraction le lit : un PAT en Bearer, et un 401 qui arrête tout, où qu'il tombe.
 */
final class Gocd {

  private final GocdApi api;

  Gocd(GocdApi api) {
    this.api = api;
  }

  /**
   * Une URL donnée avec ou sans « /go » final : les chemins d'API le remettent.
   *
   * @param sleeper l'attente avant un nouvel essai
   */
  public static Gocd connect(
      @Nullable String url, @Nullable String token, boolean insecure, Sleeper sleeper)
      throws Stop, IOException {
    return new Gocd(GocdApi.connect(url, token, insecure, sleeper));
  }

  GocdApi api() {
    return api;
  }

  public int calls() {
    return api.calls();
  }

  /** Un échec, quel qu'il soit, arrête : pour les appels sans lesquels rien n'a de sens. */
  RestResponse must(RestResponse r, String path) throws Stop {
    api.unlessRefused(r);
    if (!r.ok()) throw new Stop(path + " : " + r.why());
    return r;
  }

  /** Un 401 arrête l'outil ; toute autre réponse revient telle quelle. */
  RestResponse checked(RestResponse r) throws Stop {
    return api.unlessRefused(r);
  }
}
