package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.text;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Screen.RED;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Optional;

/** Préambule : connectivité, identité, licence. */
final class Preflight {

  private final Gitlab gl;
  private final Screen con;

  Preflight(Gitlab gl, Screen con) {
    this.gl = gl;
    this.con = con;
  }

  /**
   * Ce que l'instance permet de mesurer.
   *
   * @param enterprise le plan Enterprise, sans lequel ni DORA ni les approbations ne se lisent
   */
  record Instance(boolean enterprise) {}

  /** Vide quand l'instance est injoignable ou le jeton refusé : rien d'autre n'a de sens. */
  Optional<Instance> run() {
    con.title("0. Connectivité, identité, licence");

    Gitlab.Response me = gl.get("user", Map.of());
    if (me.unreachable()) {
      con.line("api/v4/user", Verdict.ERROR, me.errorMessage());
      con.say("\n  Instance injoignable. Vérifie l'URL, le proxy, le TLS.", RED);
      return Optional.empty();
    }
    if (me.status() != 200) {
      con.line("api/v4/user", Verdict.DENIED, "HTTP " + me.status() + " — jeton refusé");
      con.say("\n  Le jeton n'est pas accepté. La portée read_api suffit,", RED);
      con.say("  mais un jeton expiré ou révoqué donne le même 401.", RED);
      return Optional.empty();
    }
    JsonNode u = me.json();
    con.line(
        "api/v4/user", Verdict.OK, Text.format("%s (%s)", text(u, "username"), text(u, "name")));
    return Optional.of(new Instance(enterprise()));
  }

  /**
   * Le plan conditionne la moitié des signaux de pratique : approbations, DORA, règles de push. Le
   * découvrir après coup, c'est réécrire l'analyse.
   */
  private boolean enterprise() {
    Gitlab.Response meta = gl.get("metadata", Map.of());
    if (meta.status() == 200) {
      JsonNode m = meta.json();
      boolean enterprise = m.path("enterprise").asBoolean(false);
      con.line(
          "api/v4/metadata",
          enterprise ? Verdict.OK : Verdict.EMPTY,
          Text.format(
              "GitLab %s · %s",
              text(m, "version"),
              enterprise ? "Enterprise" : "Community — DORA et approbations indisponibles"));
      return enterprise;
    }
    con.line(
        "api/v4/metadata",
        Verdict.MISSING,
        "HTTP " + meta.status() + " — plan inconnu, on suppose Community");
    return false;
  }
}
