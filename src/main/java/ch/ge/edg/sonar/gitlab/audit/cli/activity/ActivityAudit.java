package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Screen.BOLD;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Screen.YELLOW;

import ch.ge.edg.sonar.gitlab.audit.cli.activity.OutputOptions.Outputs;
import ch.ge.edg.sonar.gitlab.audit.console.Console;
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
      GitlabOptions gitlab, ScopeOptions scope, FunnelOptions funnel, OutputOptions output) {}

  private final Settings settings;
  private final Screen con;
  private final Console out;
  private final Clock clock;
  private final Sleeper sleeper;
  private final Path workDir;

  /**
   * Un audit.
   *
   * @param con le rapport ; sur l'erreur de sa console, les erreurs d'usage et d'écriture du cache
   * @param clock maintenant, d'où part la fenêtre, et le fuseau de la course
   * @param sleeper l'attente que demande un 429
   * @param workDir où chercher exclusions.txt quand --exclusions n'est pas donné
   */
  ActivityAudit(Settings settings, Screen con, Clock clock, Sleeper sleeper, Path workDir) {
    this.settings = settings;
    this.con = con;
    this.out = con.out();
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
    Gitlab gl = new Gitlab(connection.get(), new GitlabWarnings(out), sleeper);
    Counters counters = new Counters();
    Funnel funnel = Funnel.start(settings.funnel(), clock);
    Outputs outputs = settings.output().resolve();

    con.say("\nInstance : " + gl.base, BOLD);
    if (!new Preflight(gl, con, counters).run()) return 1;

    List<Proj> all =
        new Inventory(gl, con, counters, funnel, settings.scope())
            .run(Exclusions.file(settings.scope().exclusionsFile, workDir));
    if (all.isEmpty()) {
      con.say("\n  Aucun projet dans le périmètre. Rien à faire.", YELLOW);
      return 1;
    }
    FunnelStages.run(gl, con, counters, funnel, all);
    write(outputs, all, funnel.sinceDays());
    if (outputs.deep()) new DeepPass(gl, con, counters, funnel).run(outputs, all);
    new Summary(gl, con, counters, funnel, outputs).print(all);
    return 0;
  }

  private void write(Outputs outputs, List<Proj> all, int sinceDays) throws IOException {
    Path inventory = outputs.inventory();
    if (inventory == null) return;
    CsvFiles.write(inventory, outputs.comma(), CsvFiles.INVENTORY, all, p -> p.row(sinceDays));
    out.out().printf("%n  Inventaire : %d lignes.%n", all.size());
  }
}
