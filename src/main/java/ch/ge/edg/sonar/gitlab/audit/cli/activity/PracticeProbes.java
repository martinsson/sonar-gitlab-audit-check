package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.text;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/** Les sondes de la passe profonde, passées une à une sur un projet sélectionné. */
final class PracticeProbes {

  private final Gitlab gl;
  private final Funnel funnel;
  private final Reviews reviews;
  private final Delivery delivery;
  private final CiConfig ci;
  private final SonarProbe sonar;

  /**
   * @param instance ce que le plan de l'instance permet de lire
   * @param cache les clés Sonar déjà lues, par tête de branche
   * @param all tout le périmètre : la CI d'un projet en inclut d'autres par leur chemin
   */
  PracticeProbes(
      Gitlab gl,
      Preflight.Instance instance,
      Funnel funnel,
      SonarCache cache,
      List<GitlabProject> all) {
    this.gl = gl;
    this.funnel = funnel;
    this.reviews = new Reviews(gl, instance.enterprise(), funnel);
    this.delivery = new Delivery(gl, instance.enterprise(), funnel);
    this.ci = new CiConfig(gl, all);
    this.sonar = new SonarProbe(gl, cache, ci);
  }

  Practices measure(AuditedProject a) {
    GitlabProject p = a.project();
    return new Practices(
        protection(p),
        reviews.measure(p),
        new Pipelines(gl, funnel).measure(p),
        delivery.measure(p),
        sonar.measure(a),
        Churn.measure(gl, funnel, p));
  }

  /** Le statut qui a refusé ci/lint au jeton, 0 tant qu'il ne l'a pas été. */
  int lintRefusedStatus() {
    return ci.lintRefusedStatus();
  }

  /** Binaire, non ambigu, et cela explique le nombre de poussées directes. */
  private Practices.Protection protection(GitlabProject p) {
    Gitlab.Response br =
        gl.get("projects/" + p.id() + "/protected_branches", Map.of("per_page", "100"));
    Practices.Protection found = Practices.Protection.none();
    if (br.status() != 200) return found;
    for (JsonNode b : br.json()) {
      if (p.defaultBranch().equals(text(b, "name"))) {
        JsonNode lvl = b.path("push_access_levels");
        found =
            new Practices.Protection(
                true, lvl.size() > 0 && lvl.get(0).path("access_level").asInt(-1) == 0);
      }
    }
    return found;
  }
}
