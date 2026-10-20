package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.params;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.iso;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * DORA ne vaut que ce que valent les environnements déclarés : un projet qui déploie tous les jours
 * depuis un pipeline sans environnement « production » affiche une fréquence nulle. « Aucun
 * environnement » est un constat de disponibilité de la donnée, pas un mauvais score.
 */
final class Delivery {

  private final Gitlab gl;
  private final Counters counters;
  private final Funnel funnel;

  Delivery(Gitlab gl, Counters counters, Funnel funnel) {
    this.gl = gl;
    this.counters = counters;
    this.funnel = funnel;
  }

  void measure(Proj p, Prat r) {
    Gitlab.Response env = gl.get("projects/" + p.id + "/environments", Map.of("per_page", "1"));
    Integer environments = null;
    if (env.status() == 200) {
      Integer t = env.intHeader("x-total");
      environments = t != null ? t : env.json().size();
      r.environments = environments;
    }
    if (!counters.enterprise || (environments != null && environments == 0)) return;
    dora(p, r);
  }

  /** La fréquence de déploiement sur la fenêtre, ou le constat qu'elle n'est pas disponible. */
  private void dora(Proj p, Prat r) {
    Gitlab.Response df =
        gl.get(
            "projects/" + p.id + "/dora/metrics",
            params(
                "metric",
                "deployment_frequency",
                "start_date",
                iso(funnel.windowStart()).substring(0, 10),
                "interval",
                "monthly"));
    if (df.status() == 200 && df.json().isArray()) {
      double sum = 0;
      for (JsonNode d : df.json()) sum += d.path("value").asDouble(0);
      r.deployments = sum;
    } else if (df.status() == 403 || df.status() == 404) {
      r.doraUnavailable = true;
    }
  }
}
