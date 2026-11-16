package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.dora.JiraKeys;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * La règle « atteint main », première étape : quand chaque commit est arrivé sur la branche par
 * défaut, et en quelles fonctionnalités les regrouper. Les fusions se font en avance rapide : il
 * n'y a pas de commit de fusion, l'arrivée sur main n'est donc écrite dans aucun commit et se lit
 * ailleurs, dans cet ordre de préférence :
 *
 * <ol>
 *   <li>le {@code merged_at} de la merge request qui contient le commit ;
 *   <li>l'heure du push sur main qui l'a apporté (événements GitLab) ;
 *   <li>sa date de commit, qu'un rebase avant fusion en avance rapide réécrit : une approximation.
 * </ol>
 *
 * <p>Une fonctionnalité est une merge request ; à défaut, les commits qui citent la même clé Jira ;
 * à défaut, ceux d'un même push ; à défaut, le commit seul. Elle arrive sur main quand son dernier
 * commit y arrive, et commence à la date d'auteur de son premier.
 */
final class Features {

  /** D'où vient l'heure d'arrivée sur main. */
  enum Reached {
    MERGE_REQUEST("merged_at de la merge request"),
    PUSH("heure du push sur main"),
    COMMIT_DATE("date de commit (approximation : un rebase la réécrit)");

    private final String label;

    Reached(String label) {
      this.label = label;
    }

    String label() {
      return label;
    }
  }

  /**
   * Une fonctionnalité : des commits arrivés ensemble sur main.
   *
   * @param key « !12 » pour une merge request, une clé Jira, « push … » ou un SHA court
   * @param firstAuthoredAt la date d'auteur du premier commit ; absente si aucune n'est connue
   * @param reachedMain l'arrivée sur main de son dernier commit
   * @param reachedBy d'où vient cette heure, pour le dernier commit
   */
  record Feature(
      String key,
      List<String> shas,
      @Nullable Instant firstAuthoredAt,
      Instant reachedMain,
      Reached reachedBy) {

    Feature {
      shas = List.copyOf(shas);
    }
  }

  private record Arrival(Instant at, Reached by, @Nullable String push) {}

  private Features() {}

  /** Les fonctionnalités de l'instantané, de la plus tôt arrivée sur main à la plus tard. */
  static List<Feature> of(Snapshot s) {
    Map<String, Arrival> arrivals = arrivals(s);
    List<Feature> out = new ArrayList<>();
    Set<String> inMergeRequest = new HashSet<>();
    for (Snapshot.MergeRequest mr : s.mergeRequests()) {
      inMergeRequest.addAll(mr.shas());
      Instant start = mr.firstCommitAt() != null ? mr.firstCommitAt() : mr.createdAt();
      out.add(new Feature("!" + mr.iid(), mr.shas(), start, mr.mergedAt(), Reached.MERGE_REQUEST));
    }
    Map<String, List<Snapshot.MainCommit>> groups = new LinkedHashMap<>();
    for (Snapshot.MainCommit c : oldestFirst(s.mainCommits())) {
      if (inMergeRequest.contains(c.sha())) continue;
      Arrival a = arrivals.get(c.sha());
      if (a == null) continue;
      groups.computeIfAbsent(groupKey(c, a), k -> new ArrayList<>()).add(c);
    }
    groups.forEach((key, commits) -> out.add(feature(key, commits, arrivals)));
    out.sort(Comparator.comparing(Feature::reachedMain));
    return List.copyOf(out);
  }

  /** Combien de fonctionnalités ont leur heure d'arrivée de chaque source. */
  static Map<Reached, Integer> sources(List<Feature> features) {
    Map<Reached, Integer> counts = new EnumMap<>(Reached.class);
    for (Feature f : features) counts.merge(f.reachedBy(), 1, Integer::sum);
    return counts;
  }

  private static Feature feature(
      String key, List<Snapshot.MainCommit> commits, Map<String, Arrival> arrivals) {
    Instant first = null;
    Arrival last = null;
    for (Snapshot.MainCommit c : commits) {
      Instant authored = c.authoredAt();
      if (authored != null && (first == null || authored.isBefore(first))) first = authored;
      Arrival a = arrivals.get(c.sha());
      if (a != null && (last == null || a.at().isAfter(last.at()))) last = a;
    }
    Arrival end = last == null ? new Arrival(Instant.EPOCH, Reached.COMMIT_DATE, null) : last;
    List<String> shas = commits.stream().map(Snapshot.MainCommit::sha).toList();
    return new Feature(key, shas, first, end.at(), end.by());
  }

  /** La clé Jira du message, sinon le push, sinon le commit seul. */
  private static String groupKey(Snapshot.MainCommit c, Arrival a) {
    Set<String> keys = JiraKeys.in(c.title());
    if (!keys.isEmpty()) return keys.iterator().next();
    String push = a.push();
    if (push != null) return push;
    return c.sha().substring(0, Math.min(8, c.sha().length()));
  }

  /** L'arrivée sur main de chaque commit de main hors merge request. */
  private static Map<String, Arrival> arrivals(Snapshot s) {
    List<Snapshot.MainCommit> commits = oldestFirst(s.mainCommits());
    Map<String, Integer> position = new HashMap<>();
    for (int i = 0; i < commits.size(); i++) position.putIfAbsent(commits.get(i).sha(), i);
    Map<String, Arrival> out = new HashMap<>();
    List<Snapshot.Push> pushes = new ArrayList<>(s.pushes());
    pushes.sort(Comparator.comparing(Snapshot.Push::at));
    int done = -1;
    for (Snapshot.Push p : pushes) {
      Integer to = position.get(p.commitTo());
      if (to == null || to <= done) continue;
      Arrival a = new Arrival(p.at(), Reached.PUSH, "push " + p.at());
      for (int i = done + 1; i <= to; i++) out.put(commits.get(i).sha(), a);
      done = to;
    }
    for (Snapshot.MainCommit c : commits) {
      Instant at = c.committedAt() != null ? c.committedAt() : c.authoredAt();
      if (at != null) out.putIfAbsent(c.sha(), new Arrival(at, Reached.COMMIT_DATE, null));
    }
    return out;
  }

  /** GitLab liste les commits du plus récent au plus ancien : on les remet dans l'ordre de main. */
  private static List<Snapshot.MainCommit> oldestFirst(List<Snapshot.MainCommit> newestFirst) {
    return new ArrayList<>(newestFirst).reversed();
  }

  /**
   * La seconde étape : chaque fonctionnalité va au premier déploiement réussi planifié à son
   * arrivée sur main ou après.
   *
   * @param successes les déploiements réussis, du plus ancien au plus récent
   * @return déploiement → les fonctionnalités qu'il a livrées, dans l'ordre d'arrivée
   */
  static Map<Snapshot.Deployment, List<Feature>> deliveries(
      List<Feature> features, List<Snapshot.Deployment> successes) {
    Map<Snapshot.Deployment, List<Feature>> out = new LinkedHashMap<>();
    for (Feature f : features) {
      for (Snapshot.Deployment d : successes) {
        if (d.at().isBefore(f.reachedMain())) continue;
        out.computeIfAbsent(d, k -> new ArrayList<>()).add(f);
        break;
      }
    }
    return out;
  }
}
