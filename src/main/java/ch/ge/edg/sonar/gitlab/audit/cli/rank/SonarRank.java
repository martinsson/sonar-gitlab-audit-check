package ch.ge.edg.sonar.gitlab.audit.cli.rank;

import ch.ge.edg.sonar.gitlab.audit.cli.shared.StyleOptions;
import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.runtime.Host;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * Étape 1 du classement : uniquement du post-traitement du CSV d'inventaire. Aucun appel API — donc
 * rejouable à volonté, et sans coût.
 *
 * <p>La démarche est décrite dans ANALYSIS.md. En résumé :
 *
 * <ol>
 *   <li>Séparer les cohortes. Jamais analysé et obsolète ne se classent pas : ce sont des constats
 *       de gouvernance, pas des mesures de qualité.
 *   <li>Classer les seuls projets actifs, par percentile *à taille comparable*, sur quatre signaux
 *       orientés vélocité plutôt que niveau de dette.
 *   <li>Une métrique absente est retirée de la moyenne, jamais remplacée par 0.
 * </ol>
 *
 * <p>Usage :
 *
 * <pre>
 * jbang SonarAuditCheck.java --csv projets.csv   # produit l'inventaire
 * jbang SonarRank.java --in projets.csv          # produit le classement
 * </pre>
 */
@Command(
    name = "SonarRank",
    mixinStandardHelpOptions = true,
    sortOptions = false,
    usageHelpAutoWidth = true,
    description = "Classement de première passe à partir du CSV d'inventaire.",
    synopsisHeading = "",
    customSynopsis = {
      "Usage : SonarRank --in <inventaire.csv> [--out <classement.csv>] [options]",
    },
    footer = {
      "",
      "Cas d'usage courants :",
      "",
      "  Le classement, à partir du CSV produit par SonarAuditCheck --csv :",
      "    SonarRank --in inventaire.csv",
      "",
      "  Une shortlist plus large, sur un parc où le décile ne suffit pas :",
      "    SonarRank --in inventaire.csv --top-percent 20",
      "",
      "  Pour un outil plutôt qu'Excel — virgules, sans BOM :",
      "    SonarRank --in inventaire.csv --out classement.csv --comma",
      "",
      "Aucun appel API : le fichier d'entrée suffit, donc relancer avec",
      "d'autres seuils est gratuit. Colonnes de sortie : COLUMNS.md.",
      "Filtrer 'liste', puis 'retenu' = O pour la shortlist.",
    })
public class SonarRank implements Callable<Integer> {

  // ----------------------------------------------------------------------
  // Options
  // ----------------------------------------------------------------------

  @Option(
      names = "--in",
      required = true,
      description = "CSV d'inventaire produit par SonarAuditCheck --csv")
  Path in;

  @Option(
      names = "--out",
      defaultValue = "classement.csv",
      description = "CSV de sortie (défaut : ${DEFAULT-VALUE})")
  Path out = Path.of("classement.csv");

  @Option(
      names = "--stale-days",
      defaultValue = "90",
      description = "seuil d'obsolescence (défaut : ${DEFAULT-VALUE})")
  int staleDays;

  @Option(
      names = "--abandoned-days",
      defaultValue = "365",
      description = "au-delà, un projet obsolète est dormant (défaut : ${DEFAULT-VALUE})")
  int abandonedDays;

  @Option(
      names = "--min-ncloc",
      defaultValue = "500",
      description = "en deçà, aucun ratio n'a de sens (défaut : ${DEFAULT-VALUE})")
  int minNcloc;

  @Option(
      names = "--top-percent",
      defaultValue = "10",
      description = "part de chaque strate retenue (défaut : ${DEFAULT-VALUE})")
  int topPercent;

  @Option(
      names = "--min-signals",
      defaultValue = "2",
      description = "signaux minimum pour retenir un score (défaut : ${DEFAULT-VALUE})")
  int minSignals;

  @Mixin StyleOptions style = new StyleOptions();

  private final Host host;

  SonarRank(Host host) {
    this.host = host;
  }

  public static void main(String[] args) {
    Host host = Host.system();
    // Un CSV illisible est le cas d'erreur courant ici : une trace de pile
    // n'y apprend rien à personne, la ligne de message si.
    int code =
        host.commandLine(new SonarRank(host))
            .setExecutionExceptionHandler(
                (e, cmd, parse) -> {
                  if (e instanceof IOException) {
                    cmd.getErr().println("Lecture impossible : " + e.getMessage());
                    return 2;
                  }
                  throw e;
                })
            .execute(args);
    System.exit(code);
  }

  @Override
  public Integer call() throws IOException {
    Console console = host.console(style.colorMode());
    // required = true : picocli refuse la ligne de commande avant d'appeler call().
    Path input = in;
    List<InventoryRow> rows = InventoryRow.read(input);
    if (rows.isEmpty()) {
      console.err().println("Aucune ligne exploitable dans " + input);
      return 1;
    }
    new Ranking(
            new RankSettings(staleDays, abandonedDays, minNcloc, topPercent, minSignals), console)
        .run(rows, out, style.comma());
    return 0;
  }
}
