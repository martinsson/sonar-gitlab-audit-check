package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import ch.ge.edg.sonar.gitlab.audit.cli.shared.ColorOption;
import ch.ge.edg.sonar.gitlab.audit.runtime.Host;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

/**
 * Diagnostic préalable à un audit de parc SonarQube — la ligne de commande ; le diagnostic lui-même
 * est dans {@link SonarAudit}.
 *
 * <p>Usage : export SONAR_URL=https://sonar.example.com export SONAR_TOKEN=squ_xxxxxxxx jbang
 * SonarAuditCheck.java --csv projets.csv jbang SonarAuditCheck.java --organization my-org #
 * SonarQube Cloud jbang SonarAuditCheck.java --dump-dir ./captures # captures brutes
 */
@Command(
    name = "SonarAuditCheck",
    mixinStandardHelpOptions = true,
    sortOptions = false,
    usageHelpAutoWidth = true,
    description = "Diagnostic d'accès Sonar avant audit de parc.",
    synopsisHeading = "",
    customSynopsis = {
      "Usage : SonarAuditCheck [--csv <fichier>] [options]",
    },
    footer = {
      "",
      "Cas d'usage courants :",
      "",
      "  Vérifier l'accès avant de commencer — aucun fichier écrit :",
      "    SonarAuditCheck",
      "",
      "  Le diagnostic et l'inventaire du parc, en un passage :",
      "    SonarAuditCheck --csv inventaire.csv",
      "",
      "  Puis le classement, qui ne rappelle pas l'API :",
      "    SonarRank --in inventaire.csv --out classement.csv",
      "",
      "  SonarQube Cloud :",
      "    SonarAuditCheck --organization mon-org --csv inventaire.csv",
      "",
      "  Capturer, puis rejouer hors ligne — ni URL ni jeton au rejeu :",
      "    SonarAuditCheck --csv inventaire.csv --dump-dir ./captures",
      "    SonarAuditCheck --replay-dir ./captures --csv rejoue.csv",
      "",
      "SONAR_URL et SONAR_TOKEN peuvent remplacer --url et --token. Le jeton",
      "doit être de type 'User' (squ_...), pas un jeton d'analyse.",
      "Colonnes du CSV : COLUMNS.md. Méthode de classement : ANALYSIS.md.",
    })
public class SonarAuditCheck implements Callable<Integer> {

  // L'ordre des groupes est celui de l'aide : --url … --project, --csv … --replay-dir,
  // --concurrency … --insecure, puis --color.

  @Mixin InstanceOptions instance = new InstanceOptions();

  @Mixin OutputOptions output = new OutputOptions();

  @Mixin ScanOptions scan = new ScanOptions();

  @Mixin ColorOption color = new ColorOption();

  private final Host host;

  SonarAuditCheck(Host host) {
    this.host = host;
  }

  public static void main(String[] args) {
    Host host = Host.system();
    System.exit(host.commandLine(new SonarAuditCheck(host)).execute(args));
  }

  @Override
  public Integer call() throws Exception {
    return new SonarAudit(
            new SonarAudit.Options(instance, output, scan),
            host.console(color.mode()),
            host.clock(),
            host.sleeper(),
            host.workDir())
        .run();
  }
}
