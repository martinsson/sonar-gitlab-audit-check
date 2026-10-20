package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

/** Tout ce que le rapport a mesuré d'un projet : la matière des constats. */
record Measured(
    Project project, Commits commits, Cadence cadence, People people, ReviewMeasures reviews) {}
