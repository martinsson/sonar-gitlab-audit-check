package ch.ge.edg.sonar.gitlab.audit.cli.jiraextract;

import ch.ge.edg.sonar.gitlab.audit.cli.shared.CommaOption;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import ch.ge.edg.sonar.gitlab.audit.runtime.Host;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * Extraction d'un ou plusieurs projets Jira (Server / Data Center, PAT) : tous les tickets touchés
 * sur la période, leur historique de statuts, et les versions du projet. GET seulement. La démarche
 * est décrite dans dora/README.md.
 */
@Command(
    name = "JiraExtract",
    mixinStandardHelpOptions = true,
    description = {
      "Extrait les tickets d'un projet Jira, leurs changements de statut et les versions du projet.",
      "Écrit jira-tickets.csv, jira-transitions.csv, jira-versions.csv et, sauf --no-raw,"
          + " jira-tickets.jsonl."
    })
public class JiraExtract implements Callable<Integer> {

  @Option(
      names = "--project",
      required = true,
      split = ",",
      description = "Clé(s) du projet Jira, par exemple ABC")
  List<String> projects = new ArrayList<>();

  @Option(
      names = "--days",
      defaultValue = "365",
      description = "Tickets mis à jour dans les N derniers jours")
  int days;

  @Option(
      names = "--jql",
      description = "Remplace la requête par défaut (project in (…) AND updated >= -Nd)")
  @Nullable String jql;

  @Option(
      names = "--field",
      split = ",",
      description =
          "Champs en plus dans le CSV, par id (customfield_10010) ou par nom. Les champs dont le"
              + " nom évoque la plateforme ou la gravité y sont déjà")
  List<String> extraFields = new ArrayList<>();

  @Option(names = "--out-dir", defaultValue = "extraction/jira", description = "Dossier de sortie")
  Path outDir = Path.of("extraction/jira");

  @Option(names = "--no-raw", description = "N'écrit pas les tickets bruts (jsonl)")
  boolean noRaw;

  @Mixin CommaOption comma = new CommaOption();

  @Option(names = "--url", defaultValue = "${env:JIRA_URL}", description = "env JIRA_URL")
  @Nullable String url;

  @Option(names = "--token", defaultValue = "${env:JIRA_TOKEN}", description = "env JIRA_TOKEN")
  @Nullable String token;

  @Option(names = "--insecure", description = "Accepte un certificat auto-signé")
  boolean insecure;

  private final Host host;

  /** Les dates s'écrivent dans le fuseau de l'horloge de {@code host} : celui du poste. */
  JiraExtract(Host host) {
    this.host = host;
  }

  public static void main(String[] args) {
    Host host = Host.system();
    System.exit(host.commandLine(new JiraExtract(host)).execute(args));
  }

  @Override
  public Integer call() {
    PrintStream err = host.err();
    try {
      extract(err);
      return 0;
    } catch (Stop | IOException e) {
      err.println("Arrêt : " + e.getMessage());
      return 2;
    }
  }

  private void extract(PrintStream err) throws Stop, IOException {
    JiraSource jira = JiraSource.connect(url, token, insecure, host.sleeper());
    FieldSelection fields = FieldSelection.of(jira.fields(), extraFields, err);
    Map<String, String> categories = jira.statusCategories();
    fields.announce(err);
    String query = jql != null ? jql : IssuePages.defaultJql(projects, days);
    err.println("JQL : " + query);
    Files.createDirectories(outDir);
    Output output = new Output(outDir, !noRaw, comma.enabled(), host.clock().getZone());
    try (TicketFiles files = new TicketFiles(output, fields, categories)) {
      new IssuePages(jira, err).each(query, files::write);
      err.println(files.summary());
    }
    VersionsCsv.write(jira, projects, output, err);
    err.println(jira.calls() + " requêtes GET.");
  }
}
