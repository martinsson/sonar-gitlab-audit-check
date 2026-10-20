package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import java.time.LocalDateTime;

/** The three measuring sections of the report, run in order over one project. */
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

  /** Each section prints what it measures, then the whole is handed to the findings. */
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
