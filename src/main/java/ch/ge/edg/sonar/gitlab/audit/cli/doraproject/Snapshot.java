package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.dora.RunOutcome;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Ce qu'une extraction a lu pour un projet, tel qu'on l'écrit dans dora-donnees.json et qu'on le
 * relit : les merge requests fusionnées, les déploiements en production, les incidents. Les mesures
 * se calculent à partir de là, sans autre appel.
 *
 * @param from début de la période lue
 * @param to fin de la période : l'heure de l'extraction
 * @param mainBranch la branche par défaut, lue seulement pour la règle « atteint main » ; absente
 *     sinon, et dans un fichier écrit avant elle
 * @param releases avec les tags de version, ce qu'ils ont livré ; absent sinon, et dans un fichier
 *     écrit avant eux
 */
record Snapshot(
    Project project,
    Instant from,
    Instant to,
    List<MergeRequest> mergeRequests,
    List<Deployment> deployments,
    List<Incident> incidents,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable MainBranch mainBranch,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Releases releases) {

  Snapshot {
    mergeRequests = List.copyOf(mergeRequests);
    deployments = List.copyOf(deployments);
    incidents = List.copyOf(incidents);
  }

  /** Les commits de la branche par défaut, du plus récent au plus ancien ; aucun sans elle. */
  List<MainCommit> mainCommits() {
    return mainBranch == null ? List.of() : mainBranch.commits();
  }

  /** Les pushs sur la branche par défaut ; aucun sans elle. */
  List<Push> pushes() {
    return mainBranch == null ? List.of() : mainBranch.pushes();
  }

  /**
   * La branche par défaut, telle que la règle « atteint main » la lit.
   *
   * @param commits ses commits sur la période, du plus récent au plus ancien, comme GitLab les
   *     liste
   * @param pushes les pushs qui les y ont apportés
   */
  record MainBranch(List<MainCommit> commits, List<Push> pushes) {

    MainBranch {
      commits = commits == null ? List.of() : List.copyOf(commits);
      pushes = pushes == null ? List.of() : List.copyOf(pushes);
    }
  }

  /** Ce que les tags de version ont livré ; aucune livraison sans eux. */
  List<Delivery> deliveries() {
    return releases == null ? List.of() : releases.deliveries();
  }

  /**
   * Le nom d'un déploiement : le tag, avec les tags de version (son numéro est son rang dans les
   * livraisons) ; sinon le pipeline et le numéro du run.
   */
  String deployName(Deployment d) {
    int rank = d.counter() - 1;
    List<Delivery> deliveries = deliveries();
    if (project.tags() && rank >= 0 && rank < deliveries.size()) {
      return deliveries.get(rank).deploy().name();
    }
    return d.pipeline() + " #" + d.counter();
  }

  /**
   * Les tags de version lus comme déploiements.
   *
   * @param tagPattern l'expression de leur nom ({@code --release-tag-pattern})
   * @param deliveries chaque tag de la période et ce qu'il a livré depuis le précédent, du plus
   *     ancien au plus récent ; les {@code deployments} de l'instantané en sont tirés ({@link
   *     Delivery#deployment})
   */
  record Releases(String tagPattern, List<Delivery> deliveries) {

    Releases {
      deliveries = deliveries == null ? List.of() : List.copyOf(deliveries);
    }

    /** Les déploiements que les mesures communes lisent, un par livraison. */
    List<Deployment> deployments(String webUrl) {
      List<Deployment> out = new ArrayList<>();
      for (int i = 0; i < deliveries.size(); i++) {
        out.add(deliveries.get(i).deployment(i + 1, webUrl));
      }
      return List.copyOf(out);
    }
  }

  /** D'où viennent les déploiements. */
  enum DeploySource {
    /**
     * Les runs des pipelines GoCD de production, chacun avec les commits que ses matériaux disent
     * avoir embarqués, en remontant les pipelines amont.
     */
    GOCD,
    /**
     * Les runs des pipelines GoCD de production, quand leurs matériaux ne disent rien des commits
     * du projet : un commit va au premier run réussi planifié après son arrivée sur la branche par
     * défaut (la règle « atteint main », {@link Features}).
     */
    REACHABLE,
    /** Faute de GoCD, chaque merge request fusionnée sur la branche par défaut. */
    MERGE,
    /**
     * Les tags de version de la branche par défaut ({@code --release-tag-pattern}) : chacun livre
     * les commits atteignables depuis lui et pas depuis le précédent ({@link Delivery}).
     */
    TAGS
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

    /** Les déploiements sont des runs GoCD, quelle que soit la règle des commits. */
    boolean gocd() {
      return deploySource == DeploySource.GOCD || deploySource == DeploySource.REACHABLE;
    }

    /** Les commits vont aux runs GoCD par leur arrivée sur main. */
    boolean reachable() {
      return deploySource == DeploySource.REACHABLE;
    }

    /** Les déploiements sont les tags de version de la branche par défaut. */
    boolean tags() {
      return deploySource == DeploySource.TAGS;
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

  /**
   * Un commit de la branche par défaut, tel que GitLab le liste.
   *
   * @param committedAt la date de commit : avec des fusions en avance rapide après rebase, à peu
   *     près le moment du rebase, donc de l'arrivée sur main
   */
  record MainCommit(
      String sha, String title, @Nullable Instant authoredAt, @Nullable Instant committedAt) {}

  /**
   * Un push sur la branche par défaut : les commits de {@code commitFrom} (exclu) à {@code
   * commitTo} y sont arrivés à {@code at}.
   *
   * @param commitFrom absent à la création de la branche
   */
  record Push(Instant at, @Nullable String commitFrom, String commitTo, int commitCount) {}
}
