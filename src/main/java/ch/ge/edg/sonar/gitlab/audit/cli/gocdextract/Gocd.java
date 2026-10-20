package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import ch.ge.edg.sonar.gitlab.audit.gocd.GocdApi;
import ch.ge.edg.sonar.gitlab.audit.rest.AuthHeaders;
import ch.ge.edg.sonar.gitlab.audit.rest.RestClient;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import java.io.IOException;
import java.io.PrintStream;
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
   * @param err où dire qu'une capture n'a pas pu s'écrire
   */
  public static Gocd connect(
      @Nullable String url, @Nullable String token, boolean insecure, PrintStream err)
      throws Stop, IOException {
    String base = Stop.require(url, "GOCD_URL").replaceAll("/+$", "").replaceFirst("/go$", "");
    String pat = Stop.require(token, "GOCD_TOKEN");
    return new Gocd(
        new GocdApi(
            new RestClient(RestClient.Settings.of(base, AuthHeaders.bearer(pat), insecure), err)));
  }

  GocdApi api() {
    return api;
  }

  public int calls() {
    return api.http().calls();
  }

  /** Un échec, quel qu'il soit, arrête : pour les appels sans lesquels rien n'a de sens. */
  RestResponse must(RestResponse r, String path) throws Stop {
    Stop.unlessRefused(r, api.http());
    if (!r.ok()) throw new Stop(path + " : " + r.why());
    return r;
  }

  /** Un 401 arrête l'outil ; toute autre réponse revient telle quelle. */
  RestResponse checked(RestResponse r) throws Stop {
    return Stop.unlessRefused(r, api.http());
  }
}
