package ch.ge.edg.sonar.gitlab.audit.cli;

import ch.ge.edg.sonar.gitlab.audit.cli.doralocate.Access;
import ch.ge.edg.sonar.gitlab.audit.cli.doralocate.Context;
import ch.ge.edg.sonar.gitlab.audit.cli.doralocate.GitlabProject;
import ch.ge.edg.sonar.gitlab.audit.cli.doralocate.GocdSection;
import ch.ge.edg.sonar.gitlab.audit.cli.doralocate.JiraSection;
import ch.ge.edg.sonar.gitlab.audit.cli.doralocate.Stop;
import ch.ge.edg.sonar.gitlab.audit.cli.doralocate.Suggestions;
import ch.ge.edg.sonar.gitlab.audit.console.ConsoleOut;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Aide exploratoire : pour un projet GitLab, quels projets Jira (clés citées dans les commits, les
 * MR et les branches) et quels pipelines GoCD (dépôt git, puis pipelines en aval) lui
 * correspondent. Propose ensuite les commandes d'extraction. GET seulement.
 */
@Command(
    name = "DoraLocate",
    mixinStandardHelpOptions = true,
    sortOptions = false,
    description = {
      "Pour un projet GitLab, propose le projet Jira et les pipelines GoCD qui vont avec.",
      "Chaque partie ne tourne que si son URL et son jeton sont fournis."
    })
public class DoraLocate implements Callable<Integer> {

  @Option(
      names = "--gitlab-project",
      required = true,
      description = "Chemin (groupe/projet), URL ou id du projet GitLab")
  String project = "";

  @Option(
      names = "--days",
      defaultValue = "365",
      description = "Commits et MR des N derniers jours")
  int days;

  @Option(names = "--max-commits", defaultValue = "3000", description = "Commits lus au plus")
  int maxCommits;

  @Option(
      names = "--gocd-index",
      defaultValue = "extraction/gocd-index.json",
      description =
          "Index des pipelines GoCD (dépôts et amont), réutilisé d'une recherche à l'autre")
  Path index = Path.of("extraction/gocd-index.json");

  @Option(
      names = "--refresh-index",
      description = "Reconstruit l'index GoCD même s'il a moins d'un jour")
  boolean refresh;

  @Option(names = "--gitlab-url", defaultValue = "${env:GITLAB_URL}")
  @Nullable String gitlabUrl;

  @Option(names = "--gitlab-token", defaultValue = "${env:GITLAB_TOKEN}")
  @Nullable String gitlabToken;

  @Option(names = "--jira-url", defaultValue = "${env:JIRA_URL}")
  @Nullable String jiraUrl;

  @Option(names = "--jira-token", defaultValue = "${env:JIRA_TOKEN}")
  @Nullable String jiraToken;

  @Option(names = "--gocd-url", defaultValue = "${env:GOCD_URL}")
  @Nullable String gocdUrl;

  @Option(names = "--gocd-token", defaultValue = "${env:GOCD_TOKEN}")
  @Nullable String gocdToken;

  @Option(names = "--insecure", description = "Accepte un certificat auto-signé")
  boolean insecure;

  private final Clock clock;
  private final ZoneId zone;

  public DoraLocate() {
    this(Clock.systemUTC(), ZoneId.systemDefault());
  }

  /** L'âge de l'index et la période lue viennent de {@code clock}, les dates de {@code zone}. */
  DoraLocate(Clock clock, ZoneId zone) {
    this.clock = clock;
    this.zone = zone;
  }

  public static void main(String[] args) {
    ConsoleOut.install();
    System.exit(new CommandLine(new DoraLocate()).execute(args));
  }

  @Override
  public Integer call() throws IOException, GeneralSecurityException {
    PrintStream out = System.out;
    PrintStream err = System.err;
    try {
      Gitlab gitlab = Access.gitlab(gitlabUrl, gitlabToken, insecure, err);
      GitlabProject p = GitlabProject.find(gitlab, project);
      out.println("Projet GitLab : " + p.pathWithNamespace() + " (id " + p.id() + ")");
      Context ctx = new Context(out, err, clock, zone, new Suggestions());
      if (present(jiraUrl) && present(jiraToken)) {
        new JiraSection(gitlab, Access.jira(jiraUrl, jiraToken, insecure, err), ctx)
            .run(p, days, maxCommits);
      } else {
        out.println("\n(Jira ignoré : JIRA_URL / JIRA_TOKEN absents)");
      }
      if (present(gocdUrl) && present(gocdToken)) {
        new GocdSection(Access.gocd(gocdUrl, gocdToken, insecure, err), ctx).run(p, index, refresh);
      } else {
        out.println("\n(GoCD ignoré : GOCD_URL / GOCD_TOKEN absents)");
      }
      ctx.suggestions().print(out);
      return 0;
    } catch (Stop e) {
      err.println("Arrêt : " + e.getMessage());
      return 2;
    }
  }

  static boolean present(@Nullable String s) {
    return s != null && !s.isBlank();
  }
}
