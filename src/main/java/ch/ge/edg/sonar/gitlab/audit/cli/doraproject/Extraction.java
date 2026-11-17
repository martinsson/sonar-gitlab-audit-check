package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import ch.ge.edg.sonar.gitlab.audit.runtime.Sleeper;
import java.io.IOException;
import java.io.PrintStream;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Une extraction : le projet GitLab, puis ses déploiements, puis ses incidents. */
final class Extraction {

  /**
   * Ce qu'on demande d'extraire.
   *
   * @param project chemin, URL ou id du projet GitLab
   * @param pipelines les pipelines GoCD de production ; vide : les tags de version ou les fusions
   *     en tiennent lieu
   * @param jiraProjects les projets Jira des incidents ; vide : pas d'incident
   * @param incidentTypes les types de ticket qui sont des incidents
   * @param incidentJql une requête qui remplace la recherche et le filtre par type
   * @param maxMrs merge requests lues au plus
   * @param maxUpstream runs GoCD amont lus au plus pour un déploiement
   */
  record Request(
      String project,
      List<String> pipelines,
      List<String> jiraProjects,
      List<String> incidentTypes,
      @Nullable String incidentJql,
      int days,
      int maxMrs,
      int maxUpstream) {

    Request {
      pipelines = List.copyOf(pipelines);
      jiraProjects = List.copyOf(jiraProjects);
      incidentTypes = List.copyOf(incidentTypes);
    }
  }

  /** Le choix de --deploy-mapping. */
  enum Mapping {
    /**
     * Avec GoCD, ses runs ; sans, les tags de version s'il y en a sur la période, sinon les
     * fusions.
     */
    AUTO,
    /** Les runs GoCD, ou sans GoCD les fusions : jamais les tags. */
    MATERIALS,
    TAGS
  }

  /**
   * Ce qu'est un déploiement et ce qu'il livre.
   *
   * @param releaseTagPattern l'expression des tags de version, cherchée dans leur nom
   */
  record DeployRule(Mapping mapping, Pattern releaseTagPattern) {}

  private final Request request;
  private final DeployRule rule;
  private final Connections connections;
  private final Console console;
  private final Sleeper sleeper;

  /** Une extraction à faire. */
  Extraction(
      Request request, DeployRule rule, Connections connections, Console console, Sleeper sleeper) {
    this.request = request;
    this.rule = rule;
    this.connections = connections;
    this.console = console;
    this.sleeper = sleeper;
  }

  /** L'instantané de la période qui finit à {@code to}. */
  Snapshot run(Instant to) throws Stop, IOException, GeneralSecurityException {
    PrintStream err = console.err();
    Instant from = to.minus(Duration.ofDays(request.days()));
    Gitlab client = connections.gitlab(console, sleeper);
    GitlabReader gitlab = new GitlabReader(client, err);
    GitlabReader.Project p = gitlab.project(request.project());
    err.println("Projet GitLab : " + p.path() + " (id " + p.id() + ")");
    List<Snapshot.MergeRequest> mrs = gitlab.mergedSince(p, from, request.maxMrs());
    err.println(mrs.size() + " merge requests fusionnées sur " + p.defaultBranch());
    Deployments.Read deploys =
        new Deployments(request, rule, connections, sleeper, err).read(client, p, mrs, from, to);
    List<Snapshot.Incident> incidents =
        request.jiraProjects().isEmpty()
            ? List.of()
            : new JiraReader(connections.jira(sleeper), err).incidents(jql(), types());
    boolean tags = deploys.source() == Snapshot.DeploySource.TAGS;
    Snapshot.Project project =
        new Snapshot.Project(
            p.id(),
            p.path(),
            p.webUrl(),
            p.defaultBranch(),
            deploys.source(),
            tags ? List.of() : request.pipelines(),
            request.jiraProjects(),
            incidentRule());
    return new Snapshot(
        project, from, to, mrs, deploys.deployments(), incidents, deploys.releases());
  }

  private String jql() {
    String jql = request.incidentJql();
    return jql != null ? jql : JiraReader.defaultJql(request.jiraProjects(), request.days());
  }

  /** Une requête donnée prend tous ses tickets. */
  private List<String> types() {
    return request.incidentJql() != null ? List.of() : request.incidentTypes();
  }

  /** La règle, en clair, pour la page ; vide sans projet Jira. */
  String incidentRule() {
    if (request.jiraProjects().isEmpty()) return "";
    if (request.incidentJql() != null) return "les tickets de la requête « " + jql() + " »";
    return "un ticket de type "
        + String.join(" ou ", request.incidentTypes())
        + " créé dans "
        + String.join(", ", request.jiraProjects());
  }
}
