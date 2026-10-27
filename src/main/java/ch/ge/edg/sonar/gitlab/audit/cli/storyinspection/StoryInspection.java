package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

import ch.ge.edg.sonar.gitlab.audit.jira.JiraApi;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import ch.ge.edg.sonar.gitlab.audit.runtime.Host;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Inspection de la qualité des User Stories Jira selon la checklist section 6.1.
 *
 * <p>Usage :
 *
 * <pre>
 * export JIRA_URL=https://prod.etat-ge.ch/csbugtrack
 * export JIRA_TOKEN=<your-jira-token>
 * jbang StoryInspection.java --project-key VA --points 1,2,3,4,5,6
 * jbang StoryInspection.java --project-key VA --points 1-15 --out rapport.md
 * jbang StoryInspection.java --project-key VA --replay-dir ./jira-dump --out rapport.md
 * </pre>
 */
@Command(
    name = "StoryInspection",
    mixinStandardHelpOptions = true,
    sortOptions = false,
    usageHelpAutoWidth = true,
    description = "Inspection de la qualité des User Stories Jira (checklist section 6.1).",
    synopsisHeading = "",
    customSynopsis = {
      "Usage : StoryInspection [options]",
    },
    footer = {
      "",
      "Points de contrôle (1-15) :",
      "  1. Respect du titrage",
      "  2. Respect du type de story",
      "  3. Respect de la description",
      "  4. Numérotation des AC",
      "  5. Présence de l'AC de contexte",
      "  6. Qualité de la forme des AC",
      "  7. Respect du modèle INVEST",
      "  8. Respect du cycle de vie",
      "  9. Respect de l'attribution",
      " 10. Découpage en sous-tâches",
      " 11. Estimation des sous-tâches en heures",
      " 12. Reste à faire des tâches en heures",
      " 13. Lien vers une Feature ou épic",
      " 14. Lien vers le composant concerné",
      " 15. Lien vers la Release concernée",
      "",
      "JIRA_URL et JIRA_TOKEN peuvent remplacer --url et --token.",
    })
public class StoryInspection implements Callable<Integer> {

  @Option(names = "--url", defaultValue = "${env:JIRA_URL}", description = "URL de l'instance Jira")
  @Nullable String jiraUrl;

  @Option(
      names = "--token",
      defaultValue = "${env:JIRA_TOKEN}",
      description = "Token d'authentification Jira (PAT)")
  @Nullable String jiraToken;

  @Option(
      names = "--project-key",
      required = true,
      description = "Clé du projet Jira à inspecter (ex: VA)")
  String projectKey = "";

  @Option(
      names = "--points",
      defaultValue = "1,2,3,4,5,6",
      description = "Points de contrôle à vérifier (ex: 1,2,3 ou 1-15)")
  String pointsOption = "1,2,3,4,5,6";

  @Option(names = "--out", description = "chemin du rapport Markdown à écrire")
  @Nullable Path outPath;

  @Option(names = "--dump-dir", description = "répertoire où consigner les réponses brutes")
  @Nullable Path dumpDir;

  @Option(
      names = "--replay-dir",
      description = "rejouer un --dump-dir au lieu d'appeler Jira (hors ligne)")
  @Nullable Path replayDir;

  @Option(
      names = "--max-results",
      defaultValue = "100",
      description = "nombre maximal de résultats par requête")
  int maxResults;

  @Option(names = "--insecure", description = "ignorer les erreurs TLS")
  boolean insecure;

  private final Host host;

  StoryInspection(Host host) {
    this.host = host;
  }

  public static void main(String[] args) {
    Host host = Host.system();
    System.exit(host.commandLine(new StoryInspection(host)).execute(args));
  }

  @Override
  public Integer call() {
    try {
      return inspect();
    } catch (Stop | IOException e) {
      host.err().println("Arrêt : " + e.getMessage());
      return 2;
    }
  }

  private int inspect() throws Stop, IOException {
    PrintStream out = host.out();
    List<ChecklistPoint> points = PointSelection.parse(pointsOption);
    if (points.isEmpty()) {
      out.println("Aucun point à vérifier.");
      return 1;
    }
    out.println("Projet : " + projectKey);
    out.println("Points : " + PointSelection.numbers(points));
    out.println();

    List<StoryResult> results =
        issues(out).stream().map(Story::from).map(s -> StoryResult.of(s, points)).toList();
    if (results.isEmpty()) {
      host.err().println("Aucune story trouvée.");
      return 1;
    }

    String report = MarkdownReport.render(results, points);
    out.println(report);
    writeReport(report);
    printSummary(Conformity.of(results));
    return 0;
  }

  /** Les tickets, relus d'un répertoire consigné ou demandés à Jira. */
  private List<JsonNode> issues(PrintStream out) throws Stop, IOException {
    Path replay = replayDir;
    if (replay != null) {
      out.println("Mode replay depuis: " + replay);
      return ReplayDump.issues(replay);
    }
    JiraApi jira = JiraApi.connect(jiraUrl, jiraToken, insecure, host.sleeper());
    Path dump = dumpDir;
    if (dump != null) {
      Files.createDirectories(dump);
    }
    return new SearchPages(jira, projectKey, maxResults, dump, host.err()).issues();
  }

  private void writeReport(String report) throws IOException {
    Path path = outPath;
    if (path != null) {
      Files.writeString(path, report);
      host.out().println("\nRapport écrit dans: " + path);
    }
  }

  private void printSummary(Conformity conformity) {
    PrintStream out = host.out();
    out.println();
    out.println("=== Résumé ===");
    out.println("Total stories: " + conformity.total());
    out.println("100% conformes: " + conformity.conform());
    out.println("Non-conformités critiques: " + conformity.critical());
    out.println("Non-conformités mineures: " + conformity.minor());
  }
}
