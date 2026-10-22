package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import ch.ge.edg.sonar.gitlab.audit.dora.JiraKeys;
import ch.ge.edg.sonar.gitlab.audit.gocd.GocdApi;
import ch.ge.edg.sonar.gitlab.audit.gocd.Material;
import ch.ge.edg.sonar.gitlab.audit.gocd.Run;
import ch.ge.edg.sonar.gitlab.audit.gocd.Run.MaterialRevision;
import ch.ge.edg.sonar.gitlab.audit.gocd.Run.Modification;
import ch.ge.edg.sonar.gitlab.audit.gocd.Upstream;
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
final class DeployedCommits {

  /** prod ← preprod ← recette ← intégration ← build ← git, et un peu de marge. */
  static final int MAX_DEPTH = 6;

  private final GocdApi gocd;
  private final int maxUpstream;

  public DeployedCommits(GocdApi gocd, int maxUpstream) {
    this.gocd = gocd;
    this.maxUpstream = maxUpstream;
  }

  public List<Commit> of(String pipeline, Run run) {
    Walk walk = new Walk();
    walk.collect(pipeline, run, "direct", 0);
    return new ArrayList<>(walk.found.values());
  }

  /** Le compteur du pipeline amont qu'un run a pris, -1 si inconnu. */
  static int upstreamCounter(Optional<Run> run, String upstream) {
    if (run.isEmpty()) return -1;
    for (MaterialRevision mr : run.get().buildCause().materialRevisions()) {
      for (Modification mod : mr.modifications()) {
        Upstream up = Upstream.parse(mod.revision());
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

    void collect(String pipeline, Run run, String via, int depth) {
      if (depth > MAX_DEPTH) return;
      for (MaterialRevision mr : run.buildCause().materialRevisions()) {
        if (!mr.changed()) continue;
        Material material = mr.material();
        if (material.isGit()) git(mr, via);
        else if (material.isPipeline()) upstream(pipeline, run.counter(), mr, depth);
      }
    }

    private void git(MaterialRevision mr, String via) {
      String repo = mr.material().gitUrl();
      for (Modification mod : mr.modifications()) {
        found.putIfAbsent(
            mod.revision(),
            new Commit(via, repo, mod.revision(), mod.modifiedTime(), JiraKeys.in(mod.comment())));
      }
    }

    private void upstream(String pipeline, int counter, MaterialRevision mr, int depth) {
      for (Modification mod : mr.modifications()) {
        Upstream to = Upstream.parse(mod.revision());
        if (to == null) continue;
        int previous = upstreamCounter(fetch(pipeline, counter - 1), to.pipeline());
        for (int c = firstCounter(previous, to.counter()); c <= to.counter(); c++) {
          String via = to.pipeline() + "/" + c;
          fetch(to.pipeline(), c).ifPresent(up -> collect(to.pipeline(), up, via, depth + 1));
        }
      }
    }

    /** Un run déjà lu ne coûte rien ; les autres consomment le budget du run. */
    private Optional<Run> fetch(String pipeline, int counter) {
      if (gocd.cached(pipeline, counter)) return gocd.instance(pipeline, counter);
      if (counter <= 0 || budget <= 0) return Optional.empty();
      budget--;
      return gocd.instance(pipeline, counter);
    }
  }
}
