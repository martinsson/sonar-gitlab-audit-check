package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

/** Everything the report measured about one project: what the findings are drawn from. */
record Measured(
    Project project, Commits commits, Cadence cadence, People people, ReviewMeasures reviews) {}
