package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.DIM;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Le rapport d'un projet, section par section : 1. identité, 2. cadence, 3. contributeurs, 4.
 * revue, 5. constats. Chaque section imprime ce qu'elle mesure et rend ce que les constats
 * relisent.
 */
final class ProjectReport {

  private final ProjectApi api;
  private final Settings settings;
  private final Report report;
  private final PrintStream err;
  private final Dates dates;

  /**
   * {@code dates} : l'instant de référence, lu une fois avant le rapport, et le fuseau des dates.
   */
  public ProjectReport(Gitlab gitlab, Settings settings, Console out, Dates dates) {
    this.api = new ProjectApi(gitlab, settings);
    this.settings = settings;
    this.report = new Report(out);
    this.err = out.err();
    this.dates = dates;
  }

  /** 0 quand le rapport est complet, 2 quand le projet est introuvable. */
  public int run(String projectPath) {
    LocalDateTime since = dates.now().minusDays(settings.days());

    Optional<Project> found = fetchProject(projectPath);
    if (found.isEmpty()) return 2;
    Project project = found.get();

    new IdentitySection(report, dates).print(project);
    new Findings(report, dates)
        .print(new Sections(report, api, settings, dates).measure(project, since));

    report.println();
    report.println(report.paint("  %d appels API.".formatted(api.calls()), DIM));
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
