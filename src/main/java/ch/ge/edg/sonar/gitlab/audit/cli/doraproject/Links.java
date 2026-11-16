package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.dora.RunOutcome;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Ce qui relie les trois sources : le déploiement qui a mis chaque commit et chaque merge request
 * en production, celui qui a précédé chaque incident.
 *
 * @param delivered déploiement réussi → les SHA qu'il a mis en production pour la première fois par
 *     son pipeline, dans l'ordre où il les a reçus
 * @param features avec la règle « atteint main », les fonctionnalités ; vide sinon
 * @param bySha SHA → le premier déploiement réussi qui l'a mis en production
 * @param deployedBy iid de merge request → le premier déploiement réussi qui en portait un commit
 * @param incidentsAfter déploiement → les incidents créés avant le déploiement suivant
 * @param precededBy clé d'incident → le déploiement qui l'a précédé
 */
record Links(
    Map<Snapshot.Deployment, List<String>> delivered,
    List<Features.Feature> features,
    Map<String, Snapshot.Deployment> bySha,
    Map<Integer, Snapshot.Deployment> deployedBy,
    Map<Snapshot.Deployment, List<Snapshot.Incident>> incidentsAfter,
    Map<String, Snapshot.Deployment> precededBy) {

  /**
   * Les liens d'un instantané.
   *
   * @param runs ses déploiements, réussis ou non, du plus ancien au plus récent
   */
  static Links of(Snapshot s, List<Snapshot.Deployment> runs) {
    List<Features.Feature> features = s.project().reachable() ? Features.of(s) : List.of();
    Map<Snapshot.Deployment, List<String>> delivered =
        s.project().reachable() ? reached(features, runs) : carried(runs);
    Map<String, Snapshot.Deployment> bySha = new HashMap<>();
    for (Snapshot.Deployment d : runs) {
      for (String sha : delivered.getOrDefault(d, List.of())) bySha.putIfAbsent(sha, d);
    }
    Map<Integer, Snapshot.Deployment> deployedBy = new HashMap<>();
    for (Snapshot.MergeRequest mr : s.mergeRequests()) {
      Snapshot.Deployment d = earliest(mr.shas(), bySha);
      if (d != null) deployedBy.put(mr.iid(), d);
    }
    Map<Snapshot.Deployment, List<Snapshot.Incident>> after = new HashMap<>();
    Map<String, Snapshot.Deployment> precededBy = new HashMap<>();
    for (Snapshot.Incident i : s.incidents()) {
      Snapshot.Deployment d = lastBefore(runs, i.createdAt());
      if (d == null) continue;
      after.computeIfAbsent(d, k -> new ArrayList<>()).add(i);
      precededBy.put(i.key(), d);
    }
    return new Links(delivered, features, bySha, deployedBy, after, precededBy);
  }

  /**
   * Un run en échec relancé sans nouveau matériau n'embarque rien aux yeux de GoCD : les commits du
   * run en échec passent au prochain run réussi du même pipeline.
   */
  private static Map<Snapshot.Deployment, List<String>> carried(List<Snapshot.Deployment> runs) {
    Map<Snapshot.Deployment, List<String>> out = new HashMap<>();
    Map<String, Set<String>> pending = new HashMap<>();
    Map<String, Set<String>> deployed = new HashMap<>();
    for (Snapshot.Deployment d : runs) {
      Set<String> waiting = pending.computeIfAbsent(d.pipeline(), k -> new LinkedHashSet<>());
      d.commits().forEach(c -> waiting.add(c.sha()));
      if (d.outcome() != RunOutcome.SUCCESS) continue;
      Set<String> done = deployed.computeIfAbsent(d.pipeline(), k -> new HashSet<>());
      List<String> shas = waiting.stream().filter(done::add).toList();
      out.put(d, shas);
      waiting.clear();
    }
    return out;
  }

  /** La règle « atteint main » : chaque fonctionnalité au premier succès qui suit son arrivée. */
  private static Map<Snapshot.Deployment, List<String>> reached(
      List<Features.Feature> features, List<Snapshot.Deployment> runs) {
    List<Snapshot.Deployment> successes =
        runs.stream().filter(d -> d.outcome() == RunOutcome.SUCCESS).toList();
    Map<Snapshot.Deployment, List<String>> out = new HashMap<>();
    Features.deliveries(features, successes)
        .forEach((d, fs) -> out.put(d, fs.stream().flatMap(f -> f.shas().stream()).toList()));
    return out;
  }

  private static Snapshot.@Nullable Deployment earliest(
      List<String> shas, Map<String, Snapshot.Deployment> bySha) {
    Snapshot.Deployment found = null;
    for (String sha : shas) {
      Snapshot.Deployment d = bySha.get(sha);
      if (d != null && (found == null || d.at().isBefore(found.at()))) found = d;
    }
    return found;
  }

  /** Le dernier déploiement à {@code at} ou avant ; aucun si l'incident les précède tous. */
  private static Snapshot.@Nullable Deployment lastBefore(
      List<Snapshot.Deployment> runs, Instant at) {
    Snapshot.Deployment found = null;
    for (Snapshot.Deployment d : runs) {
      if (d.at().isAfter(at)) break;
      found = d;
    }
    return found;
  }
}
