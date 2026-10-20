package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import ch.ge.edg.sonar.gitlab.audit.runtime.Host;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * Pré-sélection d'un parc GitLab pour analyse approfondie des pratiques.
 *
 * <p>Le problème : l'analyse en profondeur coûte ~25 appels par projet. Sur 2000 projets, c'est 50
 * 000 appels et un rapport que personne ne lit. Cet outil dépense ~90 à 850 appels pour choisir les
 * ~200 projets qui la méritent, et — ce qui compte davantage — rend ce choix auditable.
 *
 * <p>L'entonnoir (cf. GITLAB_ANALYSIS.md §2) :
 *
 * <p>étape 0 inventaire, exclusions dures ~20 appels étape 1 filtre de fraîcheur 0 appel (+
 * échantillon de validation) étape 2 activité réelle en commits ~40 (GraphQL) ou ~800 (REST) étape
 * 3 plancher d'activité 0 appel étape 4 sélection par quotas 0 appel
 *
 * <p>Puis, optionnellement, --deep : les signaux de pratique sur les sélectionnés.
 *
 * <p>Le coût de tout cela n'est pas le calcul, c'est l'attente. Les appels qui ne dépendent pas les
 * uns des autres — un projet n'a rien à dire d'un autre — se recouvrent, à concurrence de
 * --concurrency, ce qui laisse le limiteur de débit de l'instance décider du reste. Deux réductions
 * s'y ajoutent, sans changer une seule mesure : --active-within fait trancher la date du dernier
 * commit, déjà payée par lot en GraphQL, avant la pagination REST qui coûte un appel par projet ;
 * et la clé Sonar, seule donnée non fenêtrée de l'audit, est relue d'un cache disque tant que la
 * tête de la branche par défaut n'a pas bougé.
 *
 * <p>Usage : export GITLAB_URL=https://gitlab.example.com export GITLAB_TOKEN=glpat-xxxxxxxx #
 * portée read_api suffit jbang GitlabActivityAudit.java --group mon/groupe jbang
 * GitlabActivityAudit.java --top 200 --csv inventaire.csv jbang GitlabActivityAudit.java --group
 * mon/groupe --deep --pratiques prat.csv
 *
 * <p>Lecture seule de bout en bout : aucune requête autre que GET (et le POST GraphQL de l'étape 2,
 * qui ne fait que lire).
 */
@Command(
    name = "GitlabActivityAudit",
    sortOptions = false,
    usageHelpAutoWidth = true,
    description = "Pré-sélection par activité de commits, puis signaux de pratique.",
    synopsisHeading = "",
    customSynopsis = {
      "Usage : GitlabActivityAudit [--group <chemin>] [--deep] [--out-dir <rép>]",
      "                            [autres options]",
    },
    footer = {
      "",
      "Cas d'usage courants :",
      "",
      "  L'audit complet d'un groupe — c'est la commande à retenir :",
      "    GitlabActivityAudit --group mon/groupe --deep --out-dir ./audit",
      "",
      "  La sélection seule, sans la passe coûteuse — pour dimensionner :",
      "    GitlabActivityAudit --group mon/groupe --out-dir ./audit",
      "",
      "  Tout l'instance, budget réduit parce que la passe profonde coûte cher :",
      "    GitlabActivityAudit --deep --top 150 --out-dir ./audit",
      "",
      "  Une liste de projets, sur un an au lieu de 90 jours :",
      "    GitlabActivityAudit --projects liste.txt --since 365 \\",
      "        --deep --out-dir ./audit",
      "",
      "  Sans --out-dir, rien n'est écrit : le rapport s'affiche et repart.",
      "",
      "Deux fichiers en sortie, à lire ensemble : inventaire.csv (le parc et",
      "ce qui en a été écarté) et pratiques.csv (les projets sélectionnés).",
      "Le dictionnaire des colonnes est dans COLUMNS.md, la méthode dans",
      "GITLAB_ANALYSIS.md.",
      "",
      "GITLAB_URL et GITLAB_TOKEN peuvent remplacer --url et --token.",
      "Le jeton n'a besoin que de la portée read_api ; son porteur doit être",
      "Reporter sur les projets à mesurer.",
    })
public class GitlabActivityAudit implements Callable<Integer> {

  @Mixin GitlabOptions gitlab = new GitlabOptions();
  @Mixin ScopeOptions scope = new ScopeOptions();
  @Mixin FunnelOptions funnel = new FunnelOptions();
  @Mixin OutputOptions output = new OutputOptions();

  // Les options standard de picocli, déclarées à la main : réparties en groupes, les options
  // portent un ordre explicite, et celles de mixinStandardHelpOptions, sans ordre, passeraient
  // en tête de l'aide au lieu de la fermer.
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      order = 29,
      description = "Show this help message and exit.")
  boolean help;

  @Option(
      names = {"-V", "--version"},
      versionHelp = true,
      order = 30,
      description = "Print version information and exit.")
  boolean version;

  private final Host host;

  GitlabActivityAudit(Host host) {
    this.host = host;
  }

  public static void main(String[] args) {
    Host host = Host.system();
    System.exit(host.commandLine(new GitlabActivityAudit(host)).execute(args));
  }

  @Override
  public Integer call() throws Exception {
    return new ActivityAudit(
            new ActivityAudit.Settings(gitlab, scope, funnel, output),
            new Screen(host.console(output.colorMode)),
            host.clock(),
            host.sleeper(),
            host.workDir())
        .run();
  }
}
