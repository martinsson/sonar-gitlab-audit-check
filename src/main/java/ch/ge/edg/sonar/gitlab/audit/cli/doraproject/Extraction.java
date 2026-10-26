package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import ch.ge.edg.sonar.gitlab.audit.runtime.Sleeper;
import java.io.IOException;
import java.io.PrintStream;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Une extraction : le projet GitLab, puis ses déploiements, puis ses incidents. */
final class Extraction {

  /**
   * Ce qu'on demande d'extraire.
   *
   * @param project chemin, URL ou id du projet GitLab
   * @param pipelines les pipelines GoCD de production ; vide : les fusions en tiennent lieu
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

  private final Request request;
  private final Connections connections;
  private final Console console;
  private final Sleeper sleeper;

  Extraction(Request request, Connections connections, Console console, Sleeper sleeper) {
    this.request = request;
    this.connections = connections;
    this.console = console;
    this.sleeper = sleeper;
  }

  /** L'instantané de la période qui finit à {@code to}. */
  Snapshot run(Instant to) throws Stop, IOException, GeneralSecurityException {
    PrintStream err = console.err();
    Instant from = to.minus(Duration.ofDays(request.days()));
    GitlabReader gitlab = new GitlabReader(connections.gitlab(console, sleeper), err);
    GitlabReader.Project p = gitlab.project(request.project());
    err.println("Projet GitLab : " + p.path() + " (id " + p.id() + ")");
    List<Snapshot.MergeRequest> mrs = gitlab.mergedSince(p, from, request.maxMrs());
    err.println(mrs.size() + " merge requests fusionnées sur " + p.defaultBranch());
    boolean gocd = !request.pipelines().isEmpty();
    List<Snapshot.Deployment> deployments =
        gocd
            ? new GocdReader(connections.gocd(sleeper), request.maxUpstream(), err)
                .deployments(request.pipelines(), p.path(), from)
            : Merges.asDeployments(mrs);
    List<Snapshot.Incident> incidents =
        request.jiraProjects().isEmpty()
            ? List.of()
            : new JiraReader(connections.jira(sleeper), err).incidents(jql(), types());
    Snapshot.Project project =
        new Snapshot.Project(
            p.id(),
            p.path(),
            p.webUrl(),
            p.defaultBranch(),
            gocd ? Snapshot.DeploySource.GOCD : Snapshot.DeploySource.MERGE,
            request.pipelines(),
            request.jiraProjects(),
            incidentRule());
    return new Snapshot(project, from, to, mrs, deployments, incidents);
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
