package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import ch.ge.edg.sonar.gitlab.audit.dora.JiraKeys;
import ch.ge.edg.sonar.gitlab.audit.dora.Times;
import ch.ge.edg.sonar.gitlab.audit.gocd.GocdApi;
import ch.ge.edg.sonar.gitlab.audit.gocd.Materials;
import ch.ge.edg.sonar.gitlab.audit.gocd.Upstream;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Les commits qu'un run a embarqués.
 *
 * <p>Directs : ses matériaux git marqués « changed ». Par l'amont : si le run précédent du même
 * pipeline avait pris build/9 et celui-ci build/12, les commits des builds 10, 11 et 12, et ainsi
 * de suite à chaque niveau (prod ← recette ← build ← git). Un run sans prédécesseur connu ne compte
 * que le run amont qu'il a pris. Un rebuild forcé sans nouveau commit n'embarque rien : GoCD ne
 * marque alors aucun matériau changé.
 *
 * <p>Chaque commit ne compte qu'une fois par run, par sa révision, avec le premier chemin trouvé.
 * Les runs amont qui ne sont pas déjà en cache coûtent un appel : {@code maxUpstream} borne ces
 * appels pour un même run, et la plage lue à chaque niveau.
 */
public final class DeployedCommits {

  /** prod ← preprod ← recette ← intégration ← build ← git, et un peu de marge. */
  static final int MAX_DEPTH = 6;

  private final GocdApi gocd;
  private final int maxUpstream;

  public DeployedCommits(GocdApi gocd, int maxUpstream) {
    this.gocd = gocd;
    this.maxUpstream = maxUpstream;
  }

  public List<Commit> of(String pipeline, JsonNode run) {
    Walk walk = new Walk();
    walk.collect(pipeline, run, "direct", 0);
    return new ArrayList<>(walk.found.values());
  }

  /** Le compteur du pipeline amont qu'un run a pris, -1 si inconnu. */
  static int upstreamCounter(Optional<JsonNode> run, String upstream) {
    if (run.isEmpty()) return -1;
    for (JsonNode mr : run.get().path("build_cause").path("material_revisions")) {
      for (JsonNode mod : mr.path("modifications")) {
        Upstream up = Upstream.parse(mod.path("revision").asText());
        if (up != null && up.pipeline().equals(upstream)) return up.counter();
      }
    }
    return -1;
  }

  /**
   * Le premier compteur amont à lire : celui qui suit le run amont du run précédent, s'il est plus
   * ancien ; sinon le run amont lui-même. Jamais plus de {@code maxUpstream} runs en arrière.
   */
  int firstCounter(int previous, int to) {
    int from = previous > 0 && previous < to ? previous + 1 : to;
    return Math.max(from, to - maxUpstream + 1);
  }

  /** Un parcours, pour un run : ses commits trouvés et ce qui reste d'appels permis. */
  private final class Walk {

    final Map<String, Commit> found = new LinkedHashMap<>();
    int budget = maxUpstream;

    void collect(String pipeline, JsonNode run, String via, int depth) {
      if (depth > MAX_DEPTH) return;
      int counter = run.path("counter").asInt();
      for (JsonNode mr : run.path("build_cause").path("material_revisions")) {
        if (!mr.path("changed").asBoolean(false)) continue;
        JsonNode material = mr.path("material");
        if (Materials.isGit(material)) git(mr, via);
        else if (Materials.isPipeline(material)) upstream(pipeline, counter, mr, depth);
      }
    }

    private void git(JsonNode mr, String via) {
      String repo = Materials.gitUrl(mr.path("material"));
      for (JsonNode mod : mr.path("modifications")) {
        String revision = mod.path("revision").asText();
        found.putIfAbsent(
            revision,
            new Commit(
                via,
                repo,
                revision,
                Times.parse(mod.path("modified_time")),
                JiraKeys.in(mod.path("comment").asText(""))));
      }
    }

    private void upstream(String pipeline, int counter, JsonNode mr, int depth) {
      for (JsonNode mod : mr.path("modifications")) {
        Upstream to = Upstream.parse(mod.path("revision").asText());
        if (to == null) continue;
        int previous = upstreamCounter(fetch(pipeline, counter - 1), to.pipeline());
        for (int c = firstCounter(previous, to.counter()); c <= to.counter(); c++) {
          String via = to.pipeline() + "/" + c;
          fetch(to.pipeline(), c).ifPresent(up -> collect(to.pipeline(), up, via, depth + 1));
        }
      }
    }

    /** Un run déjà lu ne coûte rien ; les autres consomment le budget du run. */
    private Optional<JsonNode> fetch(String pipeline, int counter) {
      if (gocd.cached(pipeline, counter)) return gocd.instance(pipeline, counter);
      if (counter <= 0 || budget <= 0) return Optional.empty();
      budget--;
      return gocd.instance(pipeline, counter);
    }
  }
}
