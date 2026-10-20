package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.params;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.parse;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.text;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.iso;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.median;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Les pipelines de la branche par défaut : volume, taux de succès, temps de retour au vert. */
final class Pipelines {

  private final Gitlab gl;
  private final Funnel funnel;

  Pipelines(Gitlab gl, Funnel funnel) {
    this.gl = gl;
    this.funnel = funnel;
  }

  Practices.Builds measure(GitlabProject p) {
    Gitlab.Response pl =
        gl.get(
            "projects/" + p.id() + "/pipelines",
            params(
                "ref",
                p.defaultBranch(),
                "updated_after",
                iso(funnel.windowStart()),
                "per_page",
                "100"));
    JsonNode arr = pl.json();
    if (pl.status() != 200 || !arr.isArray()) return Practices.Builds.none();
    int n = arr.size();
    int ok = 0;
    for (JsonNode x : arr) if ("success".equals(text(x, "status"))) ok++;
    return new Practices.Builds(n, n == 0 ? null : (double) ok / n, recovery(arr));
  }

  private record Run(OffsetDateTime at, boolean red, boolean green) {}

  /**
   * Combien de temps l'équipe laisse la branche par défaut cassée.
   *
   * <p>Sans environnement déclaré, aucune métrique DORA n'est calculable : il n'existe pas
   * d'événement de mise en production à dater. Ceci n'en est pas une et ne doit pas s'y substituer
   * sous un nom voisin — c'est le temps de retour au vert de la CI, qui se mesure sur les seuls
   * horodatages déjà présents dans la liste ci-dessus, sans un appel de plus.
   *
   * <p>Une série d'échecs consécutifs est UN incident, pas cinq : compter chaque pipeline rouge
   * gonflerait le nombre d'incidents et raccourcirait la médiane, exactement à l'envers de ce qu'on
   * cherche à voir. Les états ni verts ni rouges — annulé, ignoré, en cours, manuel — ne cassent ni
   * ne réparent : ils sont sautés plutôt que lus comme une fin de panne.
   */
  static Practices.@Nullable Recovery recovery(JsonNode arr) {
    List<Run> runs = runs(arr);
    if (runs.isEmpty()) return null;

    List<Double> hours = new ArrayList<>();
    int incidents = 0;
    OffsetDateTime brokenSince = null;
    for (Run run : runs) {
      if (run.red() && brokenSince == null) {
        brokenSince = run.at();
        incidents++;
      } else if (run.green() && brokenSince != null) {
        hours.add(ChronoUnit.MINUTES.between(brokenSince, run.at()) / 60.0);
        brokenSince = null;
      }
    }
    // Toujours rouge à la fin de la fenêtre : l'incident est réel, sa durée
    // ne l'est pas encore. On le compte sans lui inventer un retour au vert.
    return new Practices.Recovery(incidents, brokenSince != null ? 1 : 0, median(hours));
  }

  /** Les seuls pipelines verts ou rouges, datés, dans l'ordre chronologique. */
  private static List<Run> runs(JsonNode arr) {
    List<Run> runs = new ArrayList<>();
    for (JsonNode x : arr) {
      OffsetDateTime at = parse(text(x, "created_at"));
      String st = text(x, "status");
      boolean red = "failed".equals(st);
      boolean green = "success".equals(st);
      if (at != null && (red || green)) runs.add(new Run(at, red, green));
    }
    runs.sort(Comparator.comparing(Run::at));
    return runs;
  }
}
