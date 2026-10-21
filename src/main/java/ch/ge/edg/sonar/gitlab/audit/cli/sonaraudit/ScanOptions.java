package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import picocli.CommandLine.Option;

/** Comment le parc est lu : largeur, passes facultatives, fenêtres, transport. */
final class ScanOptions {

  @Option(
      names = "--concurrency",
      defaultValue = "8",
      description = "appels en vol à la fois ; 1 = séquentiel " + "(défaut : ${DEFAULT-VALUE})")
  int concurrency;

  @Option(
      names = "--main-branch-only",
      description =
          "ne lire que la branche principale, comme search_projects "
              + "(sans chercher le dernier scan sur les autres branches)")
  boolean mainBranchOnly;

  @Option(
      names = "--no-bindings",
      description =
          "ne pas lire la liaison GitLab ni les liens de chaque projet "
              + "(deux appels de moins par projet ; CrossAudit perd sa jointure exacte côté Sonar)")
  boolean noBindings;

  @Option(
      names = "--no-trend",
      description =
          "ne pas lire l'historique de chaque projet (un appel de moins par "
              + "projet ; les colonnes de tendance restent vides)")
  boolean noTrend;

  @Option(
      names = "--stale-days",
      defaultValue = "90",
      description = "seuil d'obsolescence (défaut : ${DEFAULT-VALUE})")
  int staleDays;

  @Option(
      names = "--activity-days",
      defaultValue = "90",
      description = "fenêtre d'activité (défaut : ${DEFAULT-VALUE})")
  int activityDays;

  @Option(names = "--timeout", defaultValue = "30")
  int timeout;

  @Option(names = "--insecure", description = "ignorer la validation TLS")
  boolean insecure;
}
