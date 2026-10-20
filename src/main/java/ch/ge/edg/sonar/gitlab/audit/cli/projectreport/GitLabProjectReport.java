package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import ch.ge.edg.sonar.gitlab.audit.cli.shared.ColorOption;
import ch.ge.edg.sonar.gitlab.audit.cli.shared.GitlabConnectionOptions;
import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.runtime.Host;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * Motif de travail d'un projet GitLab, vu depuis l'API seule.
 *
 * <p>Sonar décrit l'état du code à l'instant de l'analyse. GitLab décrit le procédé qui l'a produit
 * — et c'est le procédé qu'on cherche à qualifier :
 *
 * <ol>
 *   <li>Cadence : à quel rythme, par à-coups ou en continu, quand ?
 *   <li>Contributeurs : combien de gens, et quelle concentration ?
 *   <li>Revue : les MR sont-elles relues, par qui, en combien de temps ?
 *   <li>Constats : ce qui, dans les trois sections précédentes, mérite qu'on aille voir.
 * </ol>
 *
 * <p>Le croisement avec Sonar (fréquence de modification × complexité, obsolète vs abandonné,
 * quality gate décorative) viendra se poser par-dessus : cette moitié-ci se tient debout seule, et
 * c'est voulu — un dépôt jamais analysé par Sonar reste parfaitement lisible ici.
 *
 * <p>Usage :
 *
 * <pre>
 * export GITLAB_URL=https://gitlab.example.com   # défaut : gitlab.com
 * export GITLAB_TOKEN=glpat_xxxxxxxx             # PAT, portée read_api
 * jbang GitLabProjectReport.java --path groupe/sous-groupe/projet
 * jbang GitLabProjectReport.java --path groupe/projet --days 365
 * </pre>
 *
 * <p>Le rapport lui-même est dans {@link ProjectReport}.
 */
@Command(
    name = "GitLabProjectReport",
    mixinStandardHelpOptions = true,
    description = "Motif de travail d'un projet GitLab : cadence, concentration, revue.")
public class GitLabProjectReport implements Callable<Integer> {

  private static final String YELLOW = "\033[33m";

  // ----------------------------------------------------------------------
  // Options
  // ----------------------------------------------------------------------

  @Mixin GitlabConnectionOptions gitlab = new GitlabConnectionOptions();

  @Option(
      names = "--path",
      required = true,
      description = "chemin du projet : groupe/sous-groupe/projet, un id numérique, ou une URL")
  String path = "";

  @Option(
      names = "--days",
      defaultValue = "180",
      description = "fenêtre d'observation (défaut : ${DEFAULT-VALUE})")
  int days;

  @Option(
      names = "--max-commits",
      defaultValue = "5000",
      description = "plafond de commits rapatriés (défaut : ${DEFAULT-VALUE})")
  int maxCommits;

  @Option(
      names = "--max-mrs",
      defaultValue = "500",
      description = "plafond de merge requests rapatriées (défaut : ${DEFAULT-VALUE})")
  int maxMrs;

  @Option(
      names = "--sample",
      defaultValue = "30",
      description = "MR échantillonnées pour le délai de revue (défaut : ${DEFAULT-VALUE})")
  int sample;

  @Option(names = "--include-bots", description = "compter les bots comme des contributeurs")
  boolean includeBots;

  @Option(
      names = "--bot-pattern",
      description = "expression régulière identifiant les bots (remplace celle par défaut)")
  @Nullable String botPattern;

  @Mixin ColorOption color = new ColorOption();

  private final Host host;

  /** {@code host} : les tests en fixent l'horloge des mesures « depuis maintenant ». */
  GitLabProjectReport(Host host) {
    this.host = host;
  }

  public static void main(String[] args) {
    Host host = Host.system();
    System.exit(host.commandLine(new GitLabProjectReport(host)).execute(args));
  }

  @Override
  public Integer call() throws IOException, GeneralSecurityException {
    Console out = host.console(color.mode());
    if (gitlab.anonymous()) {
      // Un projet public de gitlab.com se lit sans jeton. C'est marginal en
      // audit, mais c'est le seul moyen de vérifier l'outil contre une vraie
      // instance sans en posséder une — et le README dit assez pourquoi on
      // ne vérifie pas contre un bouchon.
      out.println(out.color("  Aucun jeton : lecture anonyme, projets publics seulement.", YELLOW));
    }
    String bots = botPattern == null || botPattern.isBlank() ? Gitlab.BOT_PATTERN : botPattern;
    Settings settings =
        new Settings(
            days,
            maxCommits,
            maxMrs,
            sample,
            includeBots,
            Pattern.compile(bots, Pattern.CASE_INSENSITIVE));
    Gitlab client =
        new Gitlab(
            gitlab.connectionOrAnonymous("https://gitlab.com"),
            new GitlabWarnings(out),
            host.sleeper());

    return new ProjectReport(client, settings, out, new Dates(host.clock()))
        .run(ProjectPath.normalize(path));
  }
}
