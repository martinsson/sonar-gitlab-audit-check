package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

/** Un contrôle de la checklist : ce qu'on conclut d'une story pour un point. */
@FunctionalInterface
interface Check {

  Finding inspect(Story story);
}
