package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import ch.ge.edg.sonar.gitlab.audit.runtime.Host;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * Les mesures DORA d'un projet, maintenant : à partir du projet GitLab, et s'ils sont donnés, du
 * projet Jira de ses incidents et des pipelines GoCD qui le mettent en production. Écrit les
 * données lues, une page HTML qui les montre, et un script SQL qui les importe dans Middleware HQ.
 * GET seulement. La démarche est décrite dans dora/README.md.
 */
@Command(
    name = "DoraProject",
    mixinStandardHelpOptions = true,
    sortOptions = false,
    description = {
      "Mesures DORA d'un projet GitLab, avec ses déploiements GoCD et ses incidents Jira.",
      "Écrit dora-donnees.json, dora.html et middleware-import.sql dans --out-dir."
    })
public class DoraProject implements Callable<Integer> {

  @Option(
      names = "--gitlab-project",
      description = "Chemin (groupe/projet), URL ou id du projet GitLab")
  @Nullable String project;

  @Option(
      names = "--gocd-pipeline",
      split = ",",
      description = "Pipeline(s) GoCD dont un run réussi est une mise en production")
  List<String> pipelines = new ArrayList<>();

  @Option(
      names = "--jira",
      split = ",",
      description = "Projet(s) Jira dont certains tickets sont des incidents de production")
  List<String> jiraProjects = new ArrayList<>();

  @Option(
      names = "--incident-types",
      split = ",",
      defaultValue = "Incident,Bug",
      description = "Types de ticket comptés comme incidents (défaut : ${DEFAULT-VALUE})")
  List<String> incidentTypes = new ArrayList<>();

  @Option(
      names = "--incident-jql",
      description = "Remplace la recherche des incidents : tous ses tickets en sont")
  @Nullable String incidentJql;

  @Option(names = "--days", defaultValue = "90", description = "Période lue (défaut : 90 jours)")
  int days;

  @Option(
      names = "--max-mrs",
      defaultValue = "500",
      description = "Merge requests lues au plus, les plus récentes")
  int maxMrs;

  @Option(
      names = "--max-upstream",
      defaultValue = "30",
      description = "Runs GoCD amont lus au plus pour un seul déploiement")
  int maxUpstream;

  @Option(
      names = "--out-dir",
      description = "Dossier de sortie (défaut : extraction/<projet>/dora)")
  @Nullable Path outDir;

  @Option(
      names = "--from",
      description = "Refait la page et l'import depuis un dora-donnees.json, sans rien extraire")
  @Nullable Path from;

  @Mixin Connections connections = new Connections();

  private final Host host;

  /** La période finit à l'heure de l'horloge de {@code host} ; la page écrit dans son fuseau. */
  DoraProject(Host host) {
    this.host = host;
  }

  public static void main(String[] args) {
    Host host = Host.system();
    System.exit(host.commandLine(new DoraProject(host)).execute(args));
  }

  @Override
  public Integer call() throws IOException, GeneralSecurityException {
    Console console = host.console("auto");
    PrintStream err = console.err();
    try {
      Path source = from;
      Snapshot snapshot = source != null ? SnapshotFile.read(source) : extract(console);
      Path dir = outDir != null ? outDir : defaultDir(snapshot, source);
      write(snapshot, host.workDir().resolve(dir), console.out());
      return 0;
    } catch (Stop e) {
      err.println("Arrêt : " + e.getMessage());
      return 2;
    }
  }

  private Snapshot extract(Console console) throws Stop, IOException, GeneralSecurityException {
    String ref = project;
    if (ref == null || ref.isBlank()) throw new Stop("donner --gitlab-project, ou --from");
    Extraction.Request request =
        new Extraction.Request(
            ref, pipelines, jiraProjects, incidentTypes, incidentJql, days, maxMrs, maxUpstream);
    return new Extraction(request, connections, console, host.sleeper())
        .run(host.clock().instant());
  }

  /** À côté du fichier relu, ou extraction/<dernier segment du chemin>/dora. */
  private static Path defaultDir(Snapshot s, @Nullable Path source) {
    if (source != null) {
      Path parent = source.toAbsolutePath().getParent();
      return parent != null ? parent : Path.of(".");
    }
    String path = s.project().path();
    return Path.of("extraction", path.substring(path.lastIndexOf('/') + 1), "dora");
  }

  private void write(Snapshot s, Path dir, PrintStream out) throws IOException {
    Files.createDirectories(dir);
    String json = SnapshotFile.json(s);
    Files.writeString(dir.resolve(SnapshotFile.NAME), json + "\n", StandardCharsets.UTF_8);
    Files.writeString(
        dir.resolve("dora.html"),
        HtmlPage.render(s, json, host.clock().getZone()),
        StandardCharsets.UTF_8);
    Files.writeString(
        dir.resolve(MiddlewareSql.NAME), MiddlewareSql.render(s), StandardCharsets.UTF_8);
    Summary.print(s, dir, out);
  }
}
