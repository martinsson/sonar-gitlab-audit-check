package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Console.BOLD;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Console.YELLOW;

import ch.ge.edg.sonar.gitlab.audit.cli.activity.OutputOptions.Outputs;
import ch.ge.edg.sonar.gitlab.audit.console.ConsoleOut;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * L'entonnoir de bout en bout : préambule, inventaire, fraîcheur, activité, plancher, sélection,
 * puis la passe profonde et la synthèse.
 */
public final class ActivityAudit {

  /** Les options de la commande, groupées comme elles se lisent. */
  public record Settings(
      GitlabOptions gitlab, ScopeOptions scope, FunnelOptions funnel, OutputOptions output) {}

  private final Settings settings;
  private final PrintStream out;
  private final PrintStream err;

  /** {@code out} reçoit le rapport, {@code err} les erreurs d'usage et d'écriture du cache. */
  public ActivityAudit(Settings settings, PrintStream out, PrintStream err) {
    this.settings = settings;
    this.out = out;
    this.err = err;
  }

  /** Le code de sortie : 0 fait, 1 rien à faire ou instance inutilisable, 2 erreur d'usage. */
  public int run() throws IOException, GeneralSecurityException {
    ConsoleOut.colorMode(settings.output().colorMode);
    Optional<Gitlab.Connection> connection = settings.gitlab().connection();
    if (connection.isEmpty()) {
      err.println(
          "GITLAB_URL et GITLAB_TOKEN sont requis " + "(variables d'env ou --url/--token).");
      return 2;
    }
    Gitlab gl = new Gitlab(connection.get(), out, err);
    FunnelOptions funnel = settings.funnel();
    Pattern bots = Pattern.compile(funnel.botPattern);
    OffsetDateTime windowStart = OffsetDateTime.now(ZoneOffset.UTC).minusDays(funnel.sinceDays);
    Outputs outputs = settings.output().resolve();
    SonarCache cache = new SonarCache(outputs.cache(), outputs.useCache(), err);
    AuditContext ctx =
        new AuditContext(gl, new Console(out), funnel, windowStart, bots, new Counters());

    ctx.con().say("\nInstance : " + gl.base, BOLD);
    if (!new Preflight(ctx).run()) return 1;

    List<Proj> all = new Inventory(ctx, settings.scope()).run();
    if (all.isEmpty()) {
      ctx.con().say("\n  Aucun projet dans le périmètre. Rien à faire.", YELLOW);
      return 1;
    }
    funnel(ctx, all);
    write(ctx, outputs, all);
    if (outputs.deep()) {
      cache.load();
      new DeepPass(ctx, outputs, cache, all).run();
      cache.save();
    }
    new Summary(ctx, outputs).print(all);
    return 0;
  }

  /** Les étapes 1 à 4 : chacune resserre la liste de la précédente. */
  private static void funnel(AuditContext ctx, List<Proj> all) {
    RecencyGate gate = new RecencyGate(ctx);
    List<Proj> fresh = gate.run(all);
    gate.validate(all);
    new CommitActivity(ctx).run(fresh);
    new ActivityFloor(ctx).run(fresh);
    new Selection(ctx).run(fresh);
  }

  private static void write(AuditContext ctx, Outputs outputs, List<Proj> all) throws IOException {
    Path inventory = outputs.inventory();
    if (inventory == null) return;
    CsvFiles.write(
        inventory, outputs.comma(), CsvFiles.INVENTORY, all, p -> p.row(ctx.sinceDays()));
    ctx.out().printf("%n  Inventaire : %d lignes.%n", all.size());
  }
}
