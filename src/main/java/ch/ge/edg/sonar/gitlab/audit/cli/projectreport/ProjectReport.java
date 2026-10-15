package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.c;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.io.PrintStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Le rapport d'un projet, section par section : 1. identité, 2. cadence, 3. contributeurs, 4.
 * revue, 5. constats. Chaque section imprime ce qu'elle mesure et rend ce que les constats
 * relisent.
 */
public final class ProjectReport {

  private final ProjectApi api;
  private final Settings settings;
  private final Report report;
  private final PrintStream err;
  private final Clock clock;

  /** {@code clock} : l'instant de référence, lu une fois au début du rapport. */
  public ProjectReport(
      Gitlab gl, Settings settings, PrintStream out, PrintStream err, Clock clock) {
    this.api = new ProjectApi(gl, settings);
    this.settings = settings;
    this.report = new Report(out);
    this.err = err;
    this.clock = clock;
  }

  /** 0 quand le rapport est complet, 2 quand le projet est introuvable. */
  public int run(String projectPath) {
    LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
    LocalDateTime since = now.minusDays(settings.days());

    Optional<Project> found = fetchProject(projectPath);
    if (found.isEmpty()) return 2;
    Project project = found.get();

    new IdentitySection(report, now).print(project);
    Commits commits = api.commits(project, since);
    Cadence cadence = new CadenceSection(report, settings, now).measure(commits);
    People people = new PeopleSection(report, api, settings, now).measure(project, commits);
    Reviews reviews = new ReviewSection(report, api, settings, now).measure(project, since);
    new Findings(report, now).print(project, commits, cadence, people, reviews);

    report.println();
    report.println(c("  %d appels API.".formatted(api.calls()), DIM));
    return 0;
  }

  private Optional<Project> fetchProject(String projectPath) {
    Gitlab.Response r = api.project(projectPath);
    Project p = r.as(Project.class);
    if (p != null && p.id() != null) return Optional.of(p);

    err.println("Projet introuvable : " + projectPath);
    err.println("  HTTP " + r.status() + " — " + r.errorMessage());
    if (r.status() == 404) {
      err.println(
          """
          404 ne distingue pas « n'existe pas » de « pas de droit de lecture » :
          GitLab masque les projets privés. Vérifier le chemin, puis la portée
          du jeton (read_api) et l'accès au projet.""");
    }
    return Optional.empty();
  }
}
