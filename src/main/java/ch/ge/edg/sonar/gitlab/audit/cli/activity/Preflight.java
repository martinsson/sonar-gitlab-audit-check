package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.text;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Screen.RED;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Optional;

/** Préambule : connectivité, identité, licence. */
final class Preflight {

  private final Gitlab gitlab;
  private final Screen screen;

  Preflight(Gitlab gitlab, Screen screen) {
    this.gitlab = gitlab;
    this.screen = screen;
  }

  /**
   * Ce que l'instance permet de mesurer.
   *
   * @param enterprise le plan Enterprise, sans lequel ni DORA ni les approbations ne se lisent
   */
  record Instance(boolean enterprise) {}

  /** Vide quand l'instance est injoignable ou le jeton refusé : rien d'autre n'a de sens. */
  Optional<Instance> run() {
    screen.title("0. Connectivité, identité, licence");

    Gitlab.Response me = gitlab.get("user", Map.of());
    if (me.unreachable()) {
      screen.line("api/v4/user", ProbeVerdict.ERROR, me.errorMessage());
      screen.say("\n  Instance injoignable. Vérifie l'URL, le proxy, le TLS.", RED);
      return Optional.empty();
    }
    if (me.status() != 200) {
      screen.line("api/v4/user", ProbeVerdict.DENIED, "HTTP " + me.status() + " — jeton refusé");
      screen.say("\n  Le jeton n'est pas accepté. La portée read_api suffit,", RED);
      screen.say("  mais un jeton expiré ou révoqué donne le même 401.", RED);
      return Optional.empty();
    }
    JsonNode u = me.json();
    screen.line(
        "api/v4/user", ProbeVerdict.OK, "%s (%s)".formatted(text(u, "username"), text(u, "name")));
    return Optional.of(new Instance(enterprise()));
  }

  /**
   * Le plan conditionne la moitié des signaux de pratique : approbations, DORA, règles de push. Le
   * découvrir après coup, c'est réécrire l'analyse.
   */
  private boolean enterprise() {
    Gitlab.Response meta = gitlab.get("metadata", Map.of());
    if (meta.status() == 200) {
      JsonNode m = meta.json();
      boolean enterprise = m.path("enterprise").asBoolean(false);
      screen.line(
          "api/v4/metadata",
          enterprise ? ProbeVerdict.OK : ProbeVerdict.EMPTY,
          "GitLab %s · %s"
              .formatted(
                  text(m, "version"),
                  enterprise ? "Enterprise" : "Community — DORA et approbations indisponibles"));
      return enterprise;
    }
    screen.line(
        "api/v4/metadata",
        ProbeVerdict.MISSING,
        "HTTP " + meta.status() + " — plan inconnu, on suppose Community");
    return false;
  }
}
