package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.dora.RunOutcome;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Ce qu'une extraction a lu pour un projet, tel qu'on l'écrit dans dora-donnees.json et qu'on le
 * relit : les merge requests fusionnées, les déploiements en production, les incidents. Les mesures
 * se calculent à partir de là, sans autre appel.
 *
 * @param from début de la période lue
 * @param to fin de la période : l'heure de l'extraction
 */
record Snapshot(
    Project project,
    Instant from,
    Instant to,
    List<MergeRequest> mergeRequests,
    List<Deployment> deployments,
    List<Incident> incidents) {

  Snapshot {
    mergeRequests = List.copyOf(mergeRequests);
    deployments = List.copyOf(deployments);
    incidents = List.copyOf(incidents);
  }

  /** D'où viennent les déploiements. */
  enum DeploySource {
    /** Les runs des pipelines GoCD de production. */
    GOCD,
    /** Faute de GoCD, chaque merge request fusionnée sur la branche par défaut. */
    MERGE
  }

  /**
   * Le projet et les règles appliquées.
   *
   * @param gitlabId l'id numérique, que Middleware garde comme clé du dépôt
   * @param path le path_with_namespace
   * @param prodPipelines les pipelines GoCD dont un run réussi vaut une mise en production
   * @param incidentRule ce qui compte comme incident, en clair
   */
  record Project(
      String gitlabId,
      String path,
      String webUrl,
      String defaultBranch,
      DeploySource deploySource,
      List<String> prodPipelines,
      List<String> jiraProjects,
      String incidentRule) {

    Project {
      prodPipelines = List.copyOf(prodPipelines);
      jiraProjects = List.copyOf(jiraProjects);
    }
  }

  /**
   * Une merge request fusionnée sur la branche par défaut.
   *
   * @param firstCommitAt la date d'auteur de son plus ancien commit
   * @param shas ses commits, et les commits de fusion ou d'écrasement qui la portent
   */
  record MergeRequest(
      int iid,
      String title,
      String author,
      Instant createdAt,
      Instant mergedAt,
      @Nullable Instant firstCommitAt,
      List<String> shas,
      String url) {

    MergeRequest {
      shas = List.copyOf(shas);
    }
  }

  /**
   * Un run de mise en production.
   *
   * @param commits les commits du projet que ce run a embarqués
   */
  record Deployment(
      String pipeline,
      int counter,
      Instant at,
      RunOutcome outcome,
      boolean forced,
      List<Change> commits,
      String url) {

    Deployment {
      commits = List.copyOf(commits);
    }
  }

  /** Un commit du projet, et la date à laquelle il a été écrit. */
  record Change(String sha, @Nullable Instant at) {}

  /**
   * Un ticket Jira compté comme incident de production.
   *
   * @param resolvedAt la date de résolution Jira ; absente tant qu'il est ouvert
   */
  record Incident(
      String key,
      String title,
      String type,
      Instant createdAt,
      @Nullable Instant resolvedAt,
      String url) {}
}
