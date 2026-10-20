package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.params;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.CellFormat.iso;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * DORA ne vaut que ce que valent les environnements déclarés : un projet qui déploie tous les jours
 * depuis un pipeline sans environnement « production » affiche une fréquence nulle. « Aucun
 * environnement » est un constat de disponibilité de la donnée, pas un mauvais score.
 */
final class Delivery {

  private final Gitlab gitlab;
  private final boolean enterprise;
  private final Funnel funnel;

  /** {@code enterprise} : DORA ne se lit que sur ce plan. */
  Delivery(Gitlab gitlab, boolean enterprise, Funnel funnel) {
    this.gitlab = gitlab;
    this.enterprise = enterprise;
    this.funnel = funnel;
  }

  Practices.Deployment measure(GitlabProject p) {
    Gitlab.Response env =
        gitlab.get("projects/" + p.id() + "/environments", Map.of("per_page", "1"));
    Integer environments = null;
    if (env.status() == 200) {
      Integer t = env.intHeader("x-total");
      environments = t != null ? t : env.json().size();
    }
    if (!enterprise || (environments != null && environments == 0)) {
      return new Practices.Deployment(environments, null, false);
    }
    return dora(p, environments);
  }

  /** La fréquence de déploiement sur la fenêtre, ou le constat qu'elle n'est pas disponible. */
  private Practices.Deployment dora(GitlabProject p, @Nullable Integer environments) {
    Gitlab.Response df =
        gitlab.get(
            "projects/" + p.id() + "/dora/metrics",
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
      return new Practices.Deployment(environments, sum, false);
    }
    return new Practices.Deployment(environments, null, df.status() == 403 || df.status() == 404);
  }
}
