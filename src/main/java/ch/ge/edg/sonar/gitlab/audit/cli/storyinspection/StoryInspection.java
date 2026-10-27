package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

import ch.ge.edg.sonar.gitlab.audit.jira.JiraApi;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import ch.ge.edg.sonar.gitlab.audit.runtime.Sleeper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
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
 * jbang StoryInspection.java --projectKey VA --points 1,2,3,4,5,6
 * jbang StoryInspection.java --projectKey VA --points 1-15 --out rapport.md
 * jbang StoryInspection.java --projectKey VA --replay-dir ./jira-dump --out rapport.md
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
  String projectKey;

  @Option(
      names = "--points",
      defaultValue = "1,2,3,4,5,6",
      description = "Points de contrôle à vérifier (ex: 1,2,3 ou 1-15)")
  String pointsOption;

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

  private final ch.ge.edg.sonar.gitlab.audit.runtime.Host host;

  StoryInspection(ch.ge.edg.sonar.gitlab.audit.runtime.Host host) {
    this.host = host;
  }

  public static void main(String[] args) {
    ch.ge.edg.sonar.gitlab.audit.runtime.Host host =
        ch.ge.edg.sonar.gitlab.audit.runtime.Host.system();
    System.exit(host.commandLine(new StoryInspection(host)).execute(args));
  }

  @Override
  public Integer call() throws Exception {
    String url = Stop.require(jiraUrl, "JIRA_URL");
    String token = Stop.require(jiraToken, "JIRA_TOKEN");

    List<Integer> points = parsePoints(pointsOption);
    if (points.isEmpty()) {
      host.console().println("Aucun point à vérifier.");
      return 1;
    }

    host.console().println("Projet : " + projectKey);
    host.console().println("Points : " + points);
    host.console().println();

    List<StoryInspectionResult> results;

    if (replayDir != null) {
      host.console().println("Mode replay depuis: " + replayDir);
      results = replay(replayDir, points);
    } else {
      Sleeper sleeper = host.sleeper();
      JiraApi jira = JiraApi.connect(url, token, insecure, sleeper);

      if (dumpDir != null) {
        Files.createDirectories(dumpDir);
      }

      results = inspectOnline(jira, projectKey, points, maxResults, dumpDir);
    }

    String report = generateReport(results, points);
    host.console().println(report);

    if (outPath != null) {
      Files.write(outPath, report.getBytes());
      host.console().println("\nRapport écrit dans: " + outPath);
    }

    long critical = results.stream().filter(r -> r.criticalCount() > 0).count();
    long minor = results.stream().filter(r -> r.minorCount() > 0 && r.criticalCount() == 0).count();
    long conform =
        results.stream().filter(r -> r.criticalCount() == 0 && r.minorCount() == 0).count();

    host.console().println();
    host.console().println("=== Résumé ===");
    host.console().println("Total stories: " + results.size());
    host.console().println("100% conformes: " + conform);
    host.console().println("Non-conformités critiques: " + critical);
    host.console().println("Non-conformités mineures: " + minor);

    return 0;
  }

  private List<Integer> parsePoints(String option) {
    List<Integer> points = new ArrayList<>();
    for (String part : option.split(",")) {
      part = part.trim();
      if (part.contains("-")) {
        String[] range = part.split("-");
        int start = Integer.parseInt(range[0].trim());
        int end = Integer.parseInt(range[1].trim());
        for (int i = start; i <= end; i++) {
          points.add(i);
        }
      } else {
        points.add(Integer.parseInt(part));
      }
    }
    Collections.sort(points);
    return points;
  }

  private List<StoryInspectionResult> inspectOnline(
      JiraApi jira, String projectKey, List<Integer> points, int maxResults, @Nullable Path dumpDir)
      throws IOException {
    List<StoryInspectionResult> results = new ArrayList<>();
    int startAt = 0;
    int total = -1;

    do {
      String jql = "project=" + projectKey + " AND issuetype=Story";
      RestResponse r = jira.search(jql, startAt, maxResults, "fields=max");

      if (dumpDir != null) {
        try {
          Files.write(dumpDir.resolve("search_" + startAt + ".json"), r.body().getBytes());
        } catch (IOException e) {
          // ignore dump errors
        }
      }

      if (total == -1 && r.ok()) {
        total = (int) r.tree().path("total").asLong(-1);
      }

      if (!r.ok()) {
        host.console()
            .println(
                "Erreur Jira: "
                    + r.code()
                    + " "
                    + r.body().substring(0, Math.min(200, r.body().length())));
        break;
      }

      JsonNode issues = r.tree().path("issues");
      for (JsonNode issue : issues) {
        StoryInspectionResult result = inspectStory(issue, points);
        results.add(result);
      }

      startAt += issues.size();
    } while (startAt < total && total > 0 && !results.isEmpty());

    return results;
  }

  private List<StoryInspectionResult> replay(Path replayDir, List<Integer> points)
      throws IOException {
    List<StoryInspectionResult> results = new ArrayList<>();
    ObjectMapper mapper = new ObjectMapper();

    // Find search files
    List<Path> searchFiles = new ArrayList<>();
    Files.list(replayDir)
        .filter(p -> p.toString().startsWith("search_") && p.toString().endsWith(".json"))
        .sorted()
        .forEach(searchFiles::add);

    if (searchFiles.isEmpty()) {
      throw new IOException("Aucun fichier de replay trouvé dans " + replayDir);
    }

    for (Path file : searchFiles) {
      JsonNode root = mapper.readTree(file.toFile());
      JsonNode issues = root.path("issues");
      for (JsonNode issue : issues) {
        StoryInspectionResult result = inspectStory(issue, points);
        results.add(result);
      }
    }

    return results;
  }

  StoryInspectionResult inspectStory(JsonNode issue, List<Integer> points) {
    String key = issue.path("key").asText();
    String summary = issue.path("fields").path("summary").asText("");
    String description = issue.path("fields").path("description").asText("");
    String status = issue.path("fields").path("status").path("name").asText("");
    String assignee =
        issue.path("fields").path("assignee").path("displayName").asText("Unassigned");
    String issueType = issue.path("fields").path("issuetype").path("name").asText("");

    List<InspectionPoint> inspectionPoints = new ArrayList<>();

    for (int point : points) {
      InspectionPoint result =
          inspectPoint(point, key, summary, description, status, assignee, issueType, issue);
      inspectionPoints.add(result);
    }

    return new StoryInspectionResult(key, summary, status, assignee, inspectionPoints);
  }

  private InspectionPoint inspectPoint(
      int point,
      String key,
      String summary,
      String description,
      String status,
      String assignee,
      String issueType,
      JsonNode issue) {
    switch (point) {
      case 1 -> {
        if (summary.isEmpty()) {
          return new InspectionPoint(point, "❌", "Titre vide", "La story n'a pas de titre");
        }
        if (summary.length() < 10) {
          return new InspectionPoint(
              point,
              "⚠️",
              "Titre trop court: '" + summary + "'",
              "Le titre devrait être plus descriptif");
        }
        return new InspectionPoint(
            point, "✅", "Titre: '" + summary + "'", "Titre présent et de longueur raisonnable");
      }
      case 2 -> {
        if ("Story".equals(issueType)) {
          return new InspectionPoint(
              point, "✅", "Type: " + issueType, "Type correct pour une User Story");
        }
        return new InspectionPoint(
            point,
            "⚠️",
            "Type: " + issueType,
            "Le type ne correspond pas à une User Story standard");
      }
      case 3 -> {
        if (description.isEmpty()) {
          return new InspectionPoint(
              point, "❌", "Description absente", "La story n'a pas de description");
        }
        if (description.length() < 50) {
          return new InspectionPoint(
              point,
              "⚠️",
              "Description courte (" + description.length() + " caractères)",
              "La description semble insuffisante");
        }
        boolean hasContext =
            description.matches("(?i).*(?:en tant que|user|utilisateur|contexte|objectif).*");
        boolean hasRequirements =
            description.matches(
                "(?i).*(?:doit|faut|require|need|should|will|va pouvoir|va devoir).*");
        if (hasContext && hasRequirements) {
          return new InspectionPoint(
              point,
              "✅",
              "Description: " + description.length() + " caractères",
              "Description complète avec contexte et exigences");
        }
        String comment = "Description présente";
        if (!hasContext) comment += " - Ajouter le contexte métier";
        if (!hasRequirements) comment += " - Clarifier les exigences";
        return new InspectionPoint(
            point, "⚠️", "Description: " + description.length() + " caractères", comment);
      }
      case 4 -> {
        if (description.isEmpty()) {
          return new InspectionPoint(
              point, "⏭️", "Pas de description", "Non applicable - pas de description");
        }
        java.util.regex.Pattern acPattern =
            java.util.regex.Pattern.compile(
                "(AC\\s*[.:\\s]?\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = acPattern.matcher(description);
        java.util.List<Integer> acNumbers = new ArrayList<>();
        int count = 0;
        while (matcher.find()) {
          count++;
          String match = matcher.group(1);
          java.util.regex.Pattern numPattern = java.util.regex.Pattern.compile("\\d+");
          java.util.regex.Matcher numMatcher = numPattern.matcher(match);
          if (numMatcher.find()) {
            acNumbers.add(Integer.parseInt(numMatcher.group()));
          }
        }
        if (count == 0) {
          return new InspectionPoint(
              point, "⚠️", "Aucun AC trouvé", "Pas de critères d'acceptation numérotés");
        }
        if (acNumbers.size() > 1) {
          java.util.List<Integer> sorted = new ArrayList<>(acNumbers);
          Collections.sort(sorted);
          boolean sequential = true;
          for (int i = 1; i < sorted.size(); i++) {
            if (!sorted.get(i).equals(sorted.get(i - 1) + 1)) {
              sequential = false;
              break;
            }
          }
          if (sequential) {
            return new InspectionPoint(
                point, "✅", count + " AC numérotés séquentiellement", "Numérotation correcte");
          }
          return new InspectionPoint(
              point,
              "⚠️",
              count + " AC mais numérotation non séquentielle",
              "Vérifier la numérotation des AC");
        }
        return new InspectionPoint(point, "✅", count + " AC trouvé(s)", "Numérotation OK");
      }
      case 5 -> {
        if (description.isEmpty()) {
          return new InspectionPoint(point, "⏭️", "Pas de description", "Non applicable");
        }
        boolean hasContextAC =
            description.matches(
                "(?i).*(?:AC\\s*\\d+.*(?:contexte|scénario|utilisateur|utilisateur final|flux|workflow|processus|journey)).*");
        if (hasContextAC) {
          return new InspectionPoint(
              point, "✅", "AC de contexte trouvé", "Un AC décrit le contexte global");
        }
        return new InspectionPoint(
            point,
            "❌",
            "Aucun AC de contexte détecté",
            "Ajouter un AC décrivant le scénario métier complet");
      }
      case 6 -> {
        if (description.isEmpty()) {
          return new InspectionPoint(point, "⏭️", "Pas de description", "Non applicable");
        }
        java.util.regex.Pattern acPattern =
            java.util.regex.Pattern.compile(
                "(AC\\s*[.:\\s]?\\d+[.)]?\\s*.+)", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = acPattern.matcher(description);
        int count = 0;
        boolean hasVague = false;
        boolean hasTestable = false;
        String[] vagueTerms = {
          "bien",
          "rapidement",
          "facilement",
          "correctement",
          "appropriément",
          "good",
          "fast",
          "easy"
        };
        String[] testableTerms = {
          "si",
          "lorsque",
          "quand",
          "then",
          "alors",
          "vérifier",
          "validate",
          "test",
          "should",
          "must"
        };
        while (matcher.find()) {
          count++;
          String acText = matcher.group(1).toLowerCase();
          for (String term : vagueTerms) {
            if (acText.matches("(?s).*\\b" + term + "\\b.*")) {
              hasVague = true;
              break;
            }
          }
          for (String term : testableTerms) {
            if (acText.matches("(?s).*\\b" + term + "\\b.*")) {
              hasTestable = true;
              break;
            }
          }
        }
        if (count == 0) {
          return new InspectionPoint(
              point, "⚠️", "Aucun AC trouvé", "Pas de critères d'acceptation à évaluer");
        }
        String evidence = count + " AC analysés";
        if (hasTestable && !hasVague) {
          return new InspectionPoint(point, "✅", evidence, "AC bien rédigés et testables");
        }
        String comment = "AC ";
        if (hasTestable) comment += "testables ";
        if (hasVague) comment += "mais termes vagues détectés ";
        if (!hasTestable) comment += "pourraient être plus testables";
        return new InspectionPoint(point, "⚠️", evidence, comment.trim());
      }
      default -> {
        // Points 7-15: placeholder
        return new InspectionPoint(
            point,
            "⏭️",
            "Point " + point + " non implémenté",
            "Inspection non implémentée pour ce point");
      }
    }
  }

  private String generateReport(List<StoryInspectionResult> results, List<Integer> points) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Rapport d'Inspection - Projet ")
        .append(results.get(0).key().split("-")[0])
        .append("\n\n");
    sb.append("## Points de contrôle vérifiés: ")
        .append(String.join(", ", points.stream().map(String::valueOf).toArray(String[]::new)))
        .append("\n\n");
    sb.append("---\n\n");

    // Global stats per point
    Map<String, int[]> pointStats = new LinkedHashMap<>();
    String[] pointNames = {
      "1. Respect du titrage",
      "2. Respect du type de story",
      "3. Respect de la description",
      "4. Numérotation des AC",
      "5. Présence de l'AC de contexte",
      "6. Qualité de la forme des AC",
      "7. Respect du modèle INVEST",
      "8. Respect du cycle de vie",
      "9. Respect de l'attribution",
      "10. Découpage en sous-tâches",
      "11. Estimation des sous-tâches en heures",
      "12. Reste à faire des tâches en heures",
      "13. Lien vers une Feature ou épic",
      "14. Lien vers le composant concerné",
      "15. Lien vers la Release concernée"
    };

    for (int i = 1; i <= 15; i++) {
      pointStats.put(pointNames[i - 1], new int[] {0, 0, 0, 0}); // ✅, ⚠️, ❌, ⏭️
    }

    for (StoryInspectionResult result : results) {
      for (InspectionPoint ip : result.inspectionPoints()) {
        String name = pointNames[ip.point() - 1];
        if (pointStats.containsKey(name)) {
          int[] stats = pointStats.get(name);
          switch (ip.status()) {
            case "✅" -> stats[0]++;
            case "⚠️" -> stats[1]++;
            case "❌" -> stats[2]++;
            case "⏭️" -> stats[3]++;
          }
        }
      }
    }

    // Per-story reports
    for (StoryInspectionResult result : results) {
      sb.append("## Story : ")
          .append(result.key())
          .append(" - ")
          .append(result.summary())
          .append("\n\n");
      sb.append("- **Statut actuel :** ").append(result.status()).append("\n");
      sb.append("- **Assigné à :** ").append(result.assignee()).append("\n\n");
      sb.append("### Résultats d'inspection\n\n");
      sb.append("| # | Point de contrôle | Statut | Preuve | Commentaires |\n");
      sb.append("|---|------------------|--------|--------|--------------|\n");

      for (InspectionPoint ip : result.inspectionPoints()) {
        String name = pointNames[ip.point() - 1];
        sb.append("| ")
            .append(ip.point())
            .append(" | ")
            .append(name)
            .append(" | ")
            .append(ip.status())
            .append(" | ")
            .append(ip.evidence())
            .append(" | ")
            .append(ip.comment())
            .append(" |\n");
      }

      sb.append("\n### Synthèse\n");
      int passed = 0, partial = 0, failed = 0, na = 0;
      for (InspectionPoint ip : result.inspectionPoints()) {
        switch (ip.status()) {
          case "✅" -> passed++;
          case "⚠️" -> partial++;
          case "❌" -> failed++;
          case "⏭️" -> na++;
        }
      }
      int totalChecked = result.inspectionPoints().size() - na;
      int score = totalChecked > 0 ? (int) Math.round((double) passed / totalChecked * 100) : 0;

      sb.append("- **Points respectés :** ")
          .append(passed)
          .append("/")
          .append(result.inspectionPoints().size())
          .append("\n");
      sb.append("- **Points partiellement respectés :** ").append(partial).append("\n");
      sb.append("- **Points non respectés :** ").append(failed).append("\n");
      sb.append("- **Points non applicables :** ").append(na).append("\n");
      sb.append("- **Score de qualité :** ").append(score).append("%\n\n");

      sb.append("### Actions recommandées\n");
      if (failed == 0 && partial == 0) {
        sb.append("Aucune action requise - Story conforme\n");
      } else {
        for (InspectionPoint ip : result.inspectionPoints()) {
          if (ip.status().equals("❌") || ip.status().equals("⚠️")) {
            sb.append("- [")
                .append(pointNames[ip.point() - 1])
                .append("] ")
                .append(ip.comment())
                .append("\n");
          }
        }
      }
      sb.append("\n---\n\n");
    }

    // Global summary
    sb.append("## Rapport global d'inspection - Projet ")
        .append(results.get(0).key().split("-")[0])
        .append("\n\n");
    sb.append("- **Total de stories inspectées :** ").append(results.size()).append("\n");
    long conform =
        results.stream().filter(r -> r.criticalCount() == 0 && r.minorCount() == 0).count();
    long critical = results.stream().filter(r -> r.criticalCount() > 0).count();
    long minor = results.stream().filter(r -> r.minorCount() > 0 && r.criticalCount() == 0).count();
    sb.append("- **Stories 100% conformes :** ")
        .append(conform)
        .append(" (")
        .append((int) Math.round((double) conform / results.size() * 100))
        .append("%)\n");
    sb.append("- **Stories avec non-conformités critiques :** ")
        .append(critical)
        .append(" (")
        .append((int) Math.round((double) critical / results.size() * 100))
        .append("%)\n");
    sb.append("- **Stories avec non-conformités mineures :** ")
        .append(minor)
        .append(" (")
        .append((int) Math.round((double) minor / results.size() * 100))
        .append("%)\n\n");

    sb.append("### Répartition par point de contrôle\n\n");
    sb.append("| Point de contrôle | ✅ Respecté | ⚠️ Partiel | ❌ Non respecté | ⏭️ N/A |\n");
    sb.append("|------------------|------------|-----------|----------------|--------|\n");
    for (Map.Entry<String, int[]> entry : pointStats.entrySet()) {
      int[] stats = entry.getValue();
      sb.append("| ")
          .append(entry.getKey())
          .append(" | ")
          .append(stats[0])
          .append(" | ")
          .append(stats[1])
          .append(" | ")
          .append(stats[2])
          .append(" | ")
          .append(stats[3])
          .append(" |\n");
    }

    sb.append("\n### Points de vigilance majeurs\n");
    pointStats.entrySet().stream()
        .filter(e -> e.getValue()[1] + e.getValue()[2] > 0)
        .sorted((a, b) -> (b.getValue()[1] + b.getValue()[2]) - (a.getValue()[1] + a.getValue()[2]))
        .limit(5)
        .forEach(
            e -> {
              int idx = pointStats.entrySet().indexOf(e) + 1;
              sb.append(idx)
                  .append(". ")
                  .append(e.getKey())
                  .append(": ")
                  .append(e.getValue()[1] + e.getValue()[2])
                  .append(" problèmes (")
                  .append(e.getValue()[2])
                  .append(" critiques, ")
                  .append(e.getValue()[1])
                  .append(" mineurs)\n");
            });

    sb.append("\n### Recommandations prioritaires\n");
    sb.append("1. Former les équipes aux conventions de titrage et description de stories\n");
    sb.append("2. Vérifier que chaque story contient au moins un AC de contexte\n");
    sb.append("3. Améliorer la qualité des AC en évitant les termes vagues\n");
    sb.append("4. Mettre en place une revue par les pairs avant le passage en 'Ready'\n");
    sb.append("5. Utiliser des templates pour la description et les AC\n");

    return sb.toString();
  }

  // Record classes for results
  record StoryInspectionResult(
      String key,
      String summary,
      String status,
      String assignee,
      List<InspectionPoint> inspectionPoints) {
    int criticalCount() {
      return (int)
          inspectionPoints.stream()
              .filter(ip -> ip.point() <= 6 && ip.status().equals("❌"))
              .count();
    }

    int minorCount() {
      return (int)
          inspectionPoints.stream()
              .filter(ip -> ip.point() <= 6 && ip.status().equals("⚠️"))
              .count();
    }
  }

  record InspectionPoint(int point, String status, String evidence, String comment) {}
}
