package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

/**
 * Ce que le rapport est prêt à lire d'un projet : la fenêtre d'observation en jours, les plafonds
 * de commits et de merge requests rapatriés, et le nombre de MR tirées pour le délai de revue.
 */
record Budget(int days, int maxCommits, int maxMrs, int sample) {}
