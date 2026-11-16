package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.dora.JiraKeys;
import ch.ge.edg.sonar.gitlab.audit.dora.RunOutcome;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Un instantané tel que le modèle d'Apache DevLake le voit, nos règles appliquées : ses
 * identifiants, un commit déployé par run et ce que chaque run réussi a livré, une pull request par
 * merge request ou par fonctionnalité arrivée sur main sans merge request. {@link DevlakeSql}
 * l'écrit en SQL.
 */
final class DevlakeModel {

  /** Le SHA du déploiement de référence : aucun commit réel. */
  static final String NO_COMMIT = "0".repeat(40);

  /**
   * DevLake ne date la mise en production d'un changement qu'à partir du deuxième déploiement
   * réussi d'un pipeline : le premier n'a pas de prédécesseur auquel se comparer. Un déploiement de
   * référence, en 1970, hors de toute période qu'un tableau de bord affiche, lui en donne un.
   */
  static final Instant BASELINE = Instant.EPOCH;

  private final Snapshot s;
  private final Metrics m;
  private final String prefix;

  DevlakeModel(Snapshot s) {
    this.s = s;
    this.m = Metrics.of(s);
    this.prefix = "dora-import:" + s.project().gitlabId();
  }

  /** Le début de tous les identifiants écrits pour ce projet GitLab. */
  String prefix() {
    return prefix;
  }

  /** Un scope par pipeline de production ; sans GoCD, un pour les fusions. */
  List<String> pipelines() {
    if (s.project().deploySource() == Snapshot.DeploySource.MERGE) return List.of(Merges.PIPELINE);
    return s.project().prodPipelines();
  }

  String scopeId(String pipeline) {
    return s.project().deploySource() == Snapshot.DeploySource.MERGE
        ? prefix + ":fusion"
        : prefix + ":gocd:" + pipeline;
  }

  String deploymentId(Snapshot.Deployment d) {
    return scopeId(d.pipeline()) + ":" + (baseline(d) ? "reference" : String.valueOf(d.counter()));
  }

  private static boolean baseline(Snapshot.Deployment d) {
    return d.at().equals(BASELINE) && d.counter() < 0;
  }

  /** Résultat et statut DevLake : un run annulé ou en cours n'a ni succès ni échec. */
  static String[] result(RunOutcome outcome) {
    return switch (outcome) {
      case SUCCESS -> new String[] {"SUCCESS", "DONE"};
      case FAILURE -> new String[] {"FAILURE", "DONE"};
      case CANCELLED -> new String[] {"", "DONE"};
      case PENDING -> new String[] {"", "IN_PROGRESS"};
    };
  }

  /** Le nom d'un run dans DevLake. */
  static String title(Snapshot.Deployment d) {
    return baseline(d) ? "référence, pas un déploiement" : d.pipeline() + " #" + d.counter();
  }

  String issueId(Snapshot.Incident n) {
    return prefix + ":jira:" + n.key();
  }

  String boardId(String jiraProject) {
    return prefix + ":jira:" + jiraProject;
  }

  /** Le projet Jira d'un ticket, s'il est l'un de ceux donnés ; sinon le premier. */
  String board(String key) {
    String p = JiraKeys.prefix(key);
    List<String> projects = s.project().jiraProjects();
    return projects.contains(p) ? p : projects.getFirst();
  }

  /**
   * Un run tel que DevLake le voit.
   *
   * @param head son commit déployé
   * @param previous le commit du déploiement réussi d'avant sur le même pipeline, pour un run
   *     réussi
   * @param delivered les commits qu'il a mis en production, selon nos règles
   */
  record Row(
      Snapshot.Deployment run, String head, @Nullable String previous, List<String> delivered) {}

  /**
   * Les runs dans l'ordre, chacun avec un commit déployé : le dernier qu'il a mis en production ou
   * embarqué, sinon celui du run d'avant sur le même pipeline. Chaque pipeline qui a réussi
   * commence par son déploiement de référence.
   */
  List<Row> rows() {
    Map<String, List<Snapshot.Deployment>> byPipeline = new LinkedHashMap<>();
    for (Snapshot.Deployment d : m.runs()) {
      byPipeline.computeIfAbsent(d.pipeline(), k -> new ArrayList<>()).add(d);
    }
    List<Row> out = new ArrayList<>();
    for (Map.Entry<String, List<Snapshot.Deployment>> e : byPipeline.entrySet()) {
      out.addAll(pipelineRows(e.getKey(), e.getValue()));
    }
    return out;
  }

  private List<Row> pipelineRows(String pipeline, List<Snapshot.Deployment> runs) {
    List<Row> out = new ArrayList<>();
    String head = NO_COMMIT;
    String previous = null;
    if (runs.stream().anyMatch(d -> d.outcome() == RunOutcome.SUCCESS)) {
      Snapshot.Deployment ref =
          new Snapshot.Deployment(pipeline, -1, BASELINE, RunOutcome.SUCCESS, false, List.of(), "");
      out.add(new Row(ref, NO_COMMIT, null, List.of()));
      previous = NO_COMMIT;
    }
    for (Snapshot.Deployment d : runs) {
      List<String> own = delivered(d);
      if (own.isEmpty()) own = d.commits().stream().map(Snapshot.Change::sha).toList();
      if (!own.isEmpty()) head = own.getLast();
      boolean success = d.outcome() == RunOutcome.SUCCESS;
      out.add(new Row(d, head, success ? previous : null, delivered(d)));
      if (success) previous = head;
    }
    return out;
  }

  private List<String> delivered(Snapshot.Deployment d) {
    return m.links().delivered().getOrDefault(d, List.of());
  }

  /**
   * Une pull request DevLake : une merge request, ou, avec la règle « atteint main », des commits
   * arrivés sur main sans merge request, décrits comme une merge request ouverte à leur premier
   * commit et fusionnée à leur arrivée sur main, pour que DevLake compte leur délai comme le nôtre.
   *
   * @param direct des commits sans merge request : pas de numéro
   */
  record Pr(String id, String mergeSha, Snapshot.MergeRequest mr, boolean direct) {}

  List<Pr> prs() {
    List<Pr> out = new ArrayList<>();
    for (Snapshot.MergeRequest mr : s.mergeRequests()) {
      out.add(new Pr(prefix + ":mr:" + mr.iid(), mergeSha(mr), mr, false));
    }
    for (Features.Feature f : m.links().features()) {
      if (f.key().startsWith("!") || f.shas().isEmpty()) continue;
      Instant start = f.firstAuthoredAt() != null ? f.firstAuthoredAt() : f.reachedMain();
      String last = f.shas().getLast();
      Snapshot.MergeRequest direct =
          new Snapshot.MergeRequest(
              0,
              "Commits sur " + s.project().defaultBranch() + " sans merge request : " + f.key(),
              "",
              start,
              f.reachedMain(),
              f.firstAuthoredAt(),
              f.shas(),
              s.project().webUrl());
      out.add(new Pr(prefix + ":main:" + last, last, direct, true));
    }
    return out;
  }

  /** Le commit de la merge request que son déploiement a mis en production, sinon son dernier. */
  String mergeSha(Snapshot.MergeRequest mr) {
    Snapshot.Deployment d = m.links().deployedBy().get(mr.iid());
    for (String sha : mr.shas()) {
      if (d != null && d.equals(m.links().bySha().get(sha))) return sha;
    }
    return mr.shas().isEmpty() ? "" : mr.shas().getLast();
  }
}
