package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.text;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/** Les sondes de la passe profonde, passées une à une sur un projet sélectionné. */
final class PracticeProbes {

  private final Gitlab gl;
  private final Counters counters;
  private final Funnel funnel;
  private final SonarProbe sonar;

  /**
   * @param cache les clés Sonar déjà lues, par tête de branche
   * @param all tout le périmètre : la CI d'un projet en inclut d'autres par leur chemin
   */
  PracticeProbes(
      Gitlab gl, Counters counters, Funnel funnel, SonarCache cache, List<GitlabProject> all) {
    this.gl = gl;
    this.counters = counters;
    this.funnel = funnel;
    this.sonar = new SonarProbe(gl, counters, cache, new CiConfig(gl, counters, all));
  }

  Prat measure(AuditedProject a) {
    GitlabProject p = a.project();
    Prat r = new Prat();
    protection(p, r);
    new Reviews(gl, counters, funnel).measure(p, r);
    new Pipelines(gl, funnel).measure(p, r);
    new Delivery(gl, counters, funnel).measure(p, r);
    sonar.measure(a, r);
    Churn.measure(gl, funnel, p, r);
    return r;
  }

  /** Binaire, non ambigu, et cela explique le nombre de poussées directes. */
  private void protection(GitlabProject p, Prat r) {
    Gitlab.Response br =
        gl.get("projects/" + p.id() + "/protected_branches", Map.of("per_page", "100"));
    if (br.status() != 200) return;
    for (JsonNode b : br.json()) {
      if (p.defaultBranch().equals(text(b, "name"))) {
        r.defaultProtected = true;
        JsonNode lvl = b.path("push_access_levels");
        r.pushLocked = lvl.size() > 0 && lvl.get(0).path("access_level").asInt(-1) == 0;
      }
    }
  }
}
