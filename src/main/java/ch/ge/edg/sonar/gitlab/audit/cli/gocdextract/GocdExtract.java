package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import ch.ge.edg.sonar.gitlab.audit.runtime.Host;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Extraction de pipelines GoCD (PAT) : chaque run sur la période, ses stages, et les commits qu'il
 * a déployés, y compris ceux arrivés par les pipelines amont. GET seulement. Les règles sont
 * décrites dans dora/README.md.
 */
@Command(
    name = "GocdExtract",
    mixinStandardHelpOptions = true,
    sortOptions = false,
    description = {
      "Extrait l'historique de pipelines GoCD et les commits que chaque run a embarqués.",
      "Écrit gocd-runs.csv, gocd-stages.csv, gocd-commits.csv et, sauf --no-raw, gocd-runs.jsonl."
    })
public class GocdExtract implements Callable<Integer> {

  @Option(names = "--pipeline", split = ",", description = "Nom(s) de pipeline")
  List<String> pipelines = new ArrayList<>();

  @Option(
      names = "--group",
      split = ",",
      description = "Groupe(s) de pipelines : tous leurs pipelines")
  List<String> groups = new ArrayList<>();

  @Option(
      names = "--days",
      defaultValue = "365",
      description = "Runs planifiés dans les N derniers jours")
  int days;

  @Option(
      names = "--max-upstream",
      defaultValue = "30",
      description = "Runs amont lus au plus pour un seul run")
  int maxUpstream;

  @Option(
      names = "--durations",
      description = "Lit chaque stage pour sa fin et sa durée (un appel de plus par stage)")
  boolean durations;

  @Option(names = "--out-dir", defaultValue = "extraction/gocd", description = "Dossier de sortie")
  Path outDir = Path.of("extraction/gocd");

  @Option(names = "--no-raw", description = "N'écrit pas les runs bruts (jsonl)")
  boolean noRaw;

  @Option(
      names = "--comma",
      description = "CSV séparés par des virgules, sans BOM (pour un outil, pas Excel)")
  boolean comma;

  @Option(names = "--url", defaultValue = "${env:GOCD_URL}", description = "env GOCD_URL")
  @Nullable String url;

  @Option(names = "--token", defaultValue = "${env:GOCD_TOKEN}", description = "env GOCD_TOKEN")
  @Nullable String token;

  @Option(names = "--insecure", description = "Accepte un certificat auto-signé")
  boolean insecure;

  private final Host host;

  /**
   * La date de départ vient de l'horloge de {@code host}, les dates écrites sont dans son fuseau.
   */
  GocdExtract(Host host) {
    this.host = host;
  }

  public static void main(String[] args) {
    Host host = Host.system();
    System.exit(host.commandLine(new GocdExtract(host)).execute(args));
  }

  @Override
  public Integer call() throws IOException {
    PrintStream err = host.err();
    Clock clock = host.clock();
    try {
      Gocd gocd = Gocd.connect(url, token, insecure, host.sleeper());
      Selection selection = Selection.resolve(gocd, pipelines, groups, err);
      if (selection.pipelines().isEmpty()) {
        throw new Stop("aucun pipeline : donner --pipeline ou --group");
      }
      Extraction.Settings settings =
          new Extraction.Settings(outDir, comma, !noRaw, maxUpstream, durations, clock.getZone());
      new Extraction(gocd, selection, settings, err)
          .run(clock.instant().minus(Duration.ofDays(days)));
      err.println(gocd.calls() + " requêtes GET.");
      return 0;
    } catch (Stop e) {
      err.println("Arrêt : " + e.getMessage());
      return 2;
    }
  }
}
