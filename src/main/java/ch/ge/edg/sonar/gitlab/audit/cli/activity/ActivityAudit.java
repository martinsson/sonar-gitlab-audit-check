package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Screen.BOLD;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Screen.YELLOW;

import ch.ge.edg.sonar.gitlab.audit.cli.activity.OutputOptions.Outputs;
import ch.ge.edg.sonar.gitlab.audit.cli.shared.GitlabConnectionOptions;
import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.csv.Column;
import ch.ge.edg.sonar.gitlab.audit.exclusions.Exclusions;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.runtime.Sleeper;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * L'entonnoir de bout en bout : préambule, inventaire, fraîcheur, activité, plancher, sélection,
 * puis la passe profonde et la synthèse.
 */
final class ActivityAudit {

  /** Les options de la commande, groupées comme elles se lisent. */
  record Settings(
      GitlabConnectionOptions gitlab,
      ScopeOptions scope,
      FunnelOptions funnel,
      OutputOptions output) {}

  private final Settings settings;
  private final Screen screen;
  private final Console out;
  private final Clock clock;
  private final Sleeper sleeper;
  private final Path workDir;

  /**
   * Un audit.
   *
   * @param screen le rapport ; sur l'erreur de sa console, les erreurs d'usage et d'écriture du
   *     cache
   * @param clock maintenant, d'où part la fenêtre, et le fuseau de la course
   * @param sleeper l'attente que demande un 429
   * @param workDir où chercher exclusions.txt quand --exclusions n'est pas donné
   */
  ActivityAudit(Settings settings, Screen screen, Clock clock, Sleeper sleeper, Path workDir) {
    this.settings = settings;
    this.screen = screen;
    this.out = screen.out();
    this.clock = clock;
    this.sleeper = sleeper;
    this.workDir = workDir;
  }

  /** Le code de sortie : 0 fait, 1 rien à faire ou instance inutilisable, 2 erreur d'usage. */
  public int run() throws IOException, GeneralSecurityException {
    Optional<Gitlab.Connection> connection = settings.gitlab().connection();
    if (connection.isEmpty()) {
      out.err()
          .println(
              "GITLAB_URL et GITLAB_TOKEN sont requis " + "(variables d'env ou --url/--token).");
      return 2;
    }
    Gitlab gitlab = new Gitlab(connection.get(), new GitlabWarnings(out), sleeper);
    Funnel funnel = Funnel.start(settings.funnel(), clock);
    Outputs outputs = settings.output().resolve();

    screen.say("\nInstance : " + gitlab.base, BOLD);
    Optional<Preflight.Instance> instance = new PreflightReport(screen).run(gitlab);
    if (instance.isEmpty()) return 1;

    FunnelReport report = new FunnelReport(screen, funnel);
    report.beginInventory();
    Inventory.Census census =
        new Inventory(gitlab, funnel, settings.scope())
            .run(Exclusions.file(settings.scope().exclusions.file(), workDir));
    report.inventory(census);
    if (census.all().isEmpty()) {
      screen.say("\n  Aucun projet dans le périmètre. Rien à faire.", YELLOW);
      return 1;
    }
    FunnelResult result = FunnelStages.run(gitlab, report, funnel, census.all());
    write(outputs, result.projects(), report);
    if (outputs.deep()) new DeepPass(gitlab, screen, instance.get(), funnel).run(outputs, result);
    new Summary(gitlab, screen, funnel, outputs).print(result);
    return 0;
  }

  private static void write(Outputs outputs, List<AuditedProject> all, FunnelReport report)
      throws IOException {
    Path inventory = outputs.inventory();
    if (inventory == null) return;
    Column.write(inventory, outputs.comma(), CsvFiles.inventory(report.sinceDays()), all);
    report.inventoryWritten(all.size());
  }
}
