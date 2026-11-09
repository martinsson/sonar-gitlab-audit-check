package ch.ge.edg.sonar.gitlab.audit.cli.activity.model;

import ch.ge.edg.sonar.gitlab.audit.contributors.Contributors;
import picocli.CommandLine.Option;

/** Les réglages de l'entonnoir : fenêtre, budget, plancher, tirages, et le coût des appels. */
public final class FunnelOptions {

  @Option(
      names = "--since",
      order = 7,
      defaultValue = "90",
      description = "fenêtre d'activité en jours (défaut : ${DEFAULT-VALUE})")
  public int sinceDays = 90;

  @Option(
      names = "--top",
      order = 8,
      defaultValue = "200",
      description = "budget d'analyse approfondie (défaut : ${DEFAULT-VALUE})")
  public int top = 200;

  @Option(
      names = "--active-within",
      order = 9,
      defaultValue = "0",
      description =
          "ne payer la passe REST que pour les projets ayant commité "
              + "dans les N derniers jours ; 0 = toute la fenêtre --since")
  public int activeWithinDays;

  @Option(
      names = "--floor",
      order = 10,
      defaultValue = "-1",
      description = "plancher d'activité en commits ; -1 = déduit des données")
  public int floor = -1;

  @Option(
      names = "--validation-sample",
      order = 14,
      defaultValue = "30",
      description = "projets tirés SOUS le filtre de fraîcheur pour le valider")
  public int validationSample = 30;

  @Option(
      names = "--graphql",
      order = 15,
      defaultValue = "true",
      description = "tenter la route GraphQL à l'étape 2 (repli REST automatique)")
  public boolean graphql = true;

  @Option(
      names = "--seed",
      order = 16,
      defaultValue = "20260818",
      description = "graine du tirage aléatoire (reproductibilité)")
  public long seed = 20_260_818L;

  @Option(
      names = "--bot-pattern",
      order = 19,
      defaultValue = Contributors.BOT_PATTERN,
      description = "regex identifiant les auteurs automatiques")
  String botPattern = Contributors.BOT_PATTERN;

  @Option(
      names = "--max-commit-pages",
      order = 21,
      defaultValue = "10",
      description = "pages de commits max par projet (100/page)")
  public int maxCommitPages = 10;

  @Option(
      names = "--concurrency",
      order = 22,
      defaultValue = "8",
      description = "appels GitLab simultanés ; 1 = séquentiel " + "(défaut : ${DEFAULT-VALUE})")
  public int concurrency = 8;
}
