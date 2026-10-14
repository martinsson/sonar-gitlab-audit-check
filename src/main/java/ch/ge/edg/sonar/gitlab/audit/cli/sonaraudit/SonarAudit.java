package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.BOLD;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.YELLOW;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.c;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.isBlank;

import ch.ge.edg.sonar.gitlab.audit.sonar.Sonar;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

/**
 * Diagnostic préalable à un audit de parc SonarQube.
 *
 * <p>1. L'API répond-elle et mon token est-il valide ? 2. Quelles requêtes puis-je réellement faire
 * (parmi celles qui comptent) ? 3. Combien de projets je vois, et combien m'échappent ? 4. Quels
 * signaux d'activité sont disponibles sans passer par Git ?
 */
public final class SonarAudit {

  /** Les options de la ligne de commande, par groupe. */
  public record Options(InstanceOptions instance, OutputOptions output, ScanOptions scan) {}

  private final Options options;
  private final PrintStream out;
  private final PrintStream err;

  /**
   * @param out le rapport
   * @param err les erreurs d'usage, et les captures qui n'ont pas pu être écrites
   */
  public SonarAudit(Options options, PrintStream out, PrintStream err) {
    this.options = options;
    this.out = out;
    this.err = err;
  }

  /** Le code de sortie : 0 fait, 1 instance injoignable ou jeton refusé, 2 usage. */
  public int run() throws IOException, GeneralSecurityException {
    InstanceOptions instance = options.instance();
    OutputOptions output = options.output();
    ScanOptions scan = options.scan();
    if (!usable(instance, output)) return 2;
    Session session = Session.open(options, out, err);
    Sonar sq = session.sq();

    out.println(
        c("\nInstance : " + (sq.replaying() ? "rejeu de " + output.replayDir : sq.base), BOLD));

    if (!new Connectivity(session).check()) return 1;

    String sample =
        instance.project != null ? instance.project : Lookups.firstVisibleProject(session);

    // Avant la première mesure : savoir ce que cette instance sait nommer.
    Metrics metrics = Metrics.negotiate(session);
    new Capabilities(session, metrics).check(sample);
    new Inventory(session, metrics, output, scan).run();
    if (sample != null) new ActivitySignals(session, sample, scan.activityDays).report();

    summary(session);
    return 0;
  }

  private boolean usable(InstanceOptions instance, OutputOptions output) {
    Path replayDir = output.replayDir;
    // En rejeu, l'instance n'est jamais appelée : exiger une URL et un jeton
    // interdirait le seul mode qui tourne sans elle.
    if (replayDir == null && (isBlank(instance.url) || isBlank(instance.token))) {
      err.println("SONAR_URL et SONAR_TOKEN sont requis " + "(variables d'env ou --url/--token).");
      return false;
    }
    if (replayDir != null && !Files.isDirectory(replayDir)) {
      err.println("--replay-dir : " + replayDir + " n'est pas un répertoire.");
      return false;
    }
    return true;
  }

  private void summary(Session session) {
    Sonar sq = session.sq();
    int concurrency = session.concurrency();
    Presentation.title(out, "Synthèse");
    out.printf(
        "  %d appels API effectués%s.%n",
        sq.calls(),
        concurrency > 1 && !sq.replaying() ? " (%d en vol)".formatted(concurrency) : "");
    // Une reprise silencieuse est une mesure qu'on ne sait pas justifier :
    // si l'instance a refusé puis accepté, le rapport doit le dire.
    if (sq.throttled() > 0 || sq.retried() > 0) {
      out.println(
          c(
              "  dont %d ralenti(s) (429) et %d repris (5xx)."
                  .formatted(sq.throttled(), sq.retried()),
              DIM));
    }
    // En rejeu, une capture manquante est la seule panne possible — et elle
    // ressemblerait sinon à un parc plus petit qu'il n'est.
    if (sq.replaying() && sq.missingCaptures() > 0) {
      out.println(
          c(
              "  %d capture(s) manquante(s) : le rejeu est incomplet."
                  .formatted(sq.missingCaptures()),
              YELLOW));
    }
    out.println(
        c(
            """
                  Rappel : search_projects filtre silencieusement sur ce que le token
                  peut voir. Une permission manquante ne produit pas d'erreur, seulement
                  un classement incomplet — et les projets manquants sont souvent ceux
                  qui auraient le plus besoin d'aide.
                """,
            DIM));
  }
}
