package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import java.time.LocalDateTime;

/** Les trois sections de mesure du rapport, exécutées dans l'ordre sur un projet. */
final class Sections {

  private final CadenceSection cadence;
  private final PeopleSection people;
  private final ReviewSection reviews;
  private final ProjectApi api;

  Sections(Report report, ProjectApi api, Settings settings, Dates dates) {
    this.api = api;
    this.cadence = new CadenceSection(report, settings, dates);
    this.people = new PeopleSection(report, api, settings, dates);
    this.reviews = new ReviewSection(report, api, settings, dates);
  }

  /** Chaque section affiche ce qu'elle mesure, puis le tout est remis aux constats. */
  Measured measure(Project project, LocalDateTime since) {
    Commits commits = api.commits(project, since);
    return new Measured(
        project,
        commits,
        cadence.measure(commits),
        people.measure(project, commits),
        reviews.measure(project, since));
  }
}
