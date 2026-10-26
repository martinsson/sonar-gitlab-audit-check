package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.dora.RunOutcome;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Ce qui relie les trois sources : le déploiement qui a mis chaque commit et chaque merge request
 * en production, celui qui a précédé chaque incident.
 *
 * @param bySha SHA → le premier déploiement réussi qui l'a mis en production
 * @param deployedBy iid de merge request → le premier déploiement réussi qui en portait un commit
 * @param incidentsAfter déploiement → les incidents créés avant le déploiement suivant
 * @param precededBy clé d'incident → le déploiement qui l'a précédé
 */
record Links(
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
    Map<String, Snapshot.Deployment> bySha = bySha(runs);
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
    return new Links(bySha, deployedBy, after, precededBy);
  }

  /**
   * Un run en échec relancé sans nouveau matériau n'embarque rien aux yeux de GoCD : les commits du
   * run en échec passent au prochain run réussi du même pipeline.
   */
  private static Map<String, Snapshot.Deployment> bySha(List<Snapshot.Deployment> runs) {
    Map<String, Snapshot.Deployment> first = new HashMap<>();
    Map<String, List<Snapshot.Change>> pending = new HashMap<>();
    for (Snapshot.Deployment d : runs) {
      List<Snapshot.Change> waiting = pending.computeIfAbsent(d.pipeline(), k -> new ArrayList<>());
      waiting.addAll(d.commits());
      if (d.outcome() != RunOutcome.SUCCESS) continue;
      for (Snapshot.Change c : waiting) first.putIfAbsent(c.sha(), d);
      waiting.clear();
    }
    return first;
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
