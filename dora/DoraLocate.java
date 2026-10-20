///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2
//DEPS info.picocli:picocli:4.7.6
//SOURCES Dora.java

// Aide exploratoire : pour un projet GitLab, quels projets Jira (clés citées dans les commits,
// les MR et les branches) et quels pipelines GoCD (dépôt git, puis pipelines en aval) lui
// correspondent. Propose ensuite les commandes d'extraction. GET seulement.

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "DoraLocate",
    mixinStandardHelpOptions = true,
    description = {
      "Pour un projet GitLab, propose le projet Jira et les pipelines GoCD qui vont avec.",
      "Chaque partie ne tourne que si son URL et son jeton sont fournis."
    })
public class DoraLocate implements Callable<Integer> {

  @Option(names = "--gitlab-project", required = true, description = "Chemin (groupe/projet), URL ou id du projet GitLab")
  String project;

  @Option(names = "--days", defaultValue = "365", description = "Commits et MR des N derniers jours")
  int days;

  @Option(names = "--max-commits", defaultValue = "3000", description = "Commits lus au plus")
  int maxCommits;

  @Option(names = "--gocd-index", defaultValue = "extraction/gocd-index.json",
      description = "Index des pipelines GoCD (dépôts et amont), réutilisé d'une recherche à l'autre")
  Path index;

  @Option(names = "--refresh-index", description = "Reconstruit l'index GoCD même s'il a moins d'un jour")
  boolean refresh;

  @Option(names = "--gitlab-url", defaultValue = "${env:GITLAB_URL}") String gitlabUrl;
  @Option(names = "--gitlab-token", defaultValue = "${env:GITLAB_TOKEN}") String gitlabToken;
  @Option(names = "--jira-url", defaultValue = "${env:JIRA_URL}") String jiraUrl;
  @Option(names = "--jira-token", defaultValue = "${env:JIRA_TOKEN}") String jiraToken;
  @Option(names = "--gocd-url", defaultValue = "${env:GOCD_URL}") String gocdUrl;
  @Option(names = "--gocd-token", defaultValue = "${env:GOCD_TOKEN}") String gocdToken;

  @Option(names = "--insecure", description = "Accepte un certificat auto-signé")
  boolean insecure;

  Dora.Api gitlab;
  JsonNode gl;
  final List<String> commands = new ArrayList<>();

  public static void main(String[] args) {
    System.exit(new CommandLine(new DoraLocate()).execute(args));
  }

  @Override
  public Integer call() {
    try {
      gitlab = Dora.Api.gitlab(gitlabUrl, gitlabToken, insecure);
      gl = gitlab.must("/api/v4/projects/" + Dora.enc(projectRef(project)));
      System.out.println("Projet GitLab : " + gl.path("path_with_namespace").asText() + " (id " + gl.path("id").asText() + ")");
      if (present(jiraUrl) && present(jiraToken)) jira();
      else System.out.println("\n(Jira ignoré : JIRA_URL / JIRA_TOKEN absents)");
      if (present(gocdUrl) && present(gocdToken)) gocd();
      else System.out.println("\n(GoCD ignoré : GOCD_URL / GOCD_TOKEN absents)");
      if (!commands.isEmpty()) {
        System.out.println("\n== Extractions proposées (à vérifier avant de lancer)");
        commands.forEach(c -> System.out.println("  " + c));
      }
      return 0;
    } catch (Dora.Fatal e) {
      System.err.println("Arrêt : " + e.getMessage());
      return 2;
    }
  }

  /** « groupe/projet », son URL web ou git, ou un id numérique. */
  static String projectRef(String p) {
    String s = p.trim();
    if (s.matches("\\d+")) return s;
    // Comme Dora.repoPath, mais sans passer en minuscules : on garde le chemin tel qu'il a été donné.
    if (s.contains("@") && !s.contains("://")) s = s.replaceFirst("^[^@]+@[^:/]+[:/]", "");
    else if (s.contains("://")) s = s.replaceFirst("^[a-zA-Z+]+://[^/]+/", "");
    return s.replaceFirst("/-/.*$", "").replaceAll("\\.git$", "").replaceAll("^/+|/+$", "");
  }

  static boolean present(String s) {
    return s != null && !s.isBlank();
  }

  // ------------------------------------------------------------ Jira

  /** Par préfixe de clé : commits, MR et branches qui le citent, et tickets distincts. */
  static final class Mentions {
    int commits, mrs, branches;
    final Set<String> tickets = new LinkedHashSet<>();
  }

  void jira() {
    System.out.println("\n== Jira");
    String id = gl.path("id").asText();
    Dora.Resp integ = gitlab.get("/api/v4/projects/" + id + "/integrations/jira");
    if (integ.ok() && integ.json().path("active").asBoolean(false)) {
      JsonNode props = integ.json().path("properties");
      System.out.println("  Intégration Jira active dans GitLab : clé(s) " + props.path("project_keys").toString()
          + " " + props.path("project_key").asText("") + " " + props.path("jira_issue_prefix").asText(""));
    } else {
      System.out.println("  Intégration Jira de GitLab : " + (integ.ok() ? "inactive" : "illisible (" + integ.why() + ", droit Maintainer requis)"));
    }

    Map<String, Mentions> byPrefix = new TreeMap<>();
    String since = Instant.now().minus(Duration.ofDays(days)).toString();
    int commits = 0;
    for (int page = 1; commits < maxCommits; page++) {
      Dora.Resp r = gitlab.get("/api/v4/projects/" + id + "/repository/commits?all=true&per_page=100&page=" + page
          + "&since=" + Dora.enc(since));
      if (!r.ok() || r.json().size() == 0) break;
      for (JsonNode c : r.json()) {
        commits++;
        for (String prefix : prefixes(Dora.jiraKeys(c.path("message").asText(c.path("title").asText())), byPrefix))
          byPrefix.get(prefix).commits++;
      }
    }
    int mrs = 0;
    for (int page = 1; page <= 20; page++) {
      Dora.Resp r = gitlab.get("/api/v4/projects/" + id + "/merge_requests?state=all&per_page=100&page=" + page
          + "&updated_after=" + Dora.enc(since));
      if (!r.ok() || r.json().size() == 0) break;
      for (JsonNode m : r.json()) {
        mrs++;
        String text = m.path("title").asText() + " " + m.path("source_branch").asText() + " " + m.path("description").asText("");
        for (String prefix : prefixes(Dora.jiraKeys(text), byPrefix)) byPrefix.get(prefix).mrs++;
      }
    }
    int branches = 0;
    for (int page = 1; page <= 20; page++) {
      Dora.Resp r = gitlab.get("/api/v4/projects/" + id + "/repository/branches?per_page=100&page=" + page);
      if (!r.ok() || r.json().size() == 0) break;
      for (JsonNode b : r.json()) {
        branches++;
        // Une branche s'écrit souvent feature/abc-123 : on met en majuscules avant de chercher.
        for (String prefix : prefixes(Dora.jiraKeys(b.path("name").asText().toUpperCase(Locale.ROOT)), byPrefix))
          byPrefix.get(prefix).branches++;
      }
    }
    System.out.printf("  Lus : %d commits, %d MR, %d branches sur %d jours%n", commits, mrs, branches, days);
    if (byPrefix.isEmpty()) {
      System.out.println("  Aucune clé de ticket trouvée.");
      return;
    }

    Dora.Api jira = Dora.Api.jira(jiraUrl, jiraToken, insecure);
    List<Map.Entry<String, Mentions>> ranked = new ArrayList<>(byPrefix.entrySet());
    ranked.sort((a, b) -> (b.getValue().commits + b.getValue().mrs) - (a.getValue().commits + a.getValue().mrs));
    System.out.printf("  %-12s %8s %5s %8s %8s  %s%n", "préfixe", "commits", "MR", "branches", "tickets", "projet Jira");
    int shown = 0;
    for (var e : ranked) {
      if (shown++ >= 15) break;
      Mentions m = e.getValue();
      Dora.Resp p = jira.get("/rest/api/2/project/" + Dora.enc(e.getKey()));
      String name = p.ok() ? p.json().path("name").asText() : p.status() == 404 ? "(pas un projet Jira visible)" : p.why();
      if (p.ok() && commits > 0 && m.commits * 20 >= commits && commands.stream().noneMatch(c -> c.startsWith("jbang dora/JiraExtract")))
        commands.add("jbang dora/JiraExtract.java --project " + e.getKey() + " --out-dir extraction/" + slug() + "/jira");
      System.out.printf("  %-12s %8d %5d %8d %8d  %s%n", e.getKey(), m.commits, m.mrs, m.branches, m.tickets.size(), name);
    }
    if (commits > 0) {
      int best = ranked.get(0).getValue().commits;
      System.out.printf("  Le préfixe le plus cité l'est dans %.0f %% des commits.%n", 100.0 * best / commits);
    }
  }

  static Set<String> prefixes(Set<String> keys, Map<String, Mentions> into) {
    Set<String> out = new LinkedHashSet<>();
    for (String k : keys) {
      String prefix = k.substring(0, k.lastIndexOf('-'));
      into.computeIfAbsent(prefix, x -> new Mentions()).tickets.add(k);
      out.add(prefix);
    }
    return out;
  }

  String slug() {
    return gl.path("path").asText("projet");
  }

  // ------------------------------------------------------------ GoCD

  void gocd() {
    System.out.println("\n== GoCD");
    Dora.Api gocd = Dora.Api.gocd(gocdUrl, gocdToken, insecure);
    JsonNode idx = loadOrBuildIndex(gocd);
    String path = gl.path("path_with_namespace").asText().toLowerCase(Locale.ROOT);
    JsonNode pipes = idx.path("pipelines");

    // Pipelines qui prennent ce dépôt directement.
    Map<String, Integer> depth = new LinkedHashMap<>();
    pipes.fields().forEachRemaining(e -> {
      for (JsonNode url : e.getValue().path("git")) {
        String rp = Dora.repoPath(url.asText());
        if (rp.equals(path) || rp.endsWith("/" + path)) depth.put(e.getKey(), 0);
      }
    });
    // Puis tout ce qui en dépend, de proche en proche.
    Map<String, List<String>> downstream = new HashMap<>();
    pipes.fields().forEachRemaining(e -> {
      for (JsonNode up : e.getValue().path("upstream"))
        downstream.computeIfAbsent(up.asText(), k -> new ArrayList<>()).add(e.getKey());
    });
    Deque<String> todo = new ArrayDeque<>(depth.keySet());
    while (!todo.isEmpty()) {
      String p = todo.poll();
      for (String d : downstream.getOrDefault(p, List.of()))
        if (!depth.containsKey(d)) {
          depth.put(d, depth.get(p) + 1);
          todo.add(d);
        }
    }
    int neverRun = 0;
    for (JsonNode p : pipes) if (p.path("git").size() == 0 && p.path("upstream").size() == 0) neverRun++;
    System.out.printf("  Index : %d pipelines visibles (dont %d sans run lisible, donc sans matériau connu), construit le %s%n",
        pipes.size(), neverRun, idx.path("built_at").asText());
    if (depth.isEmpty()) {
      System.out.println("  Aucun pipeline ne prend " + path + " comme dépôt git.");
      return;
    }
    System.out.printf("  %-40s %-20s %-18s %-12s %s%n", "pipeline", "groupe", "rôle", "plateforme?", "dernier run");
    Set<String> groups = new LinkedHashSet<>();
    for (var e : depth.entrySet()) {
      JsonNode p = pipes.path(e.getKey());
      groups.add(p.path("group").asText());
      String role = e.getValue() == 0 ? "dépôt direct" : "aval niveau " + e.getValue();
      System.out.printf("  %-40s %-20s %-18s %-12s %s%n", e.getKey(), p.path("group").asText(), role,
          Dora.platforms(e.getKey()), p.path("last_run").asText(""));
    }
    commands.add("jbang dora/GocdExtract.java --pipeline " + String.join(",", depth.keySet())
        + " --out-dir extraction/" + slug() + "/gocd");
    if (groups.size() == 1)
      System.out.println("  Tous dans le groupe « " + groups.iterator().next() + " » : --group " + groups.iterator().next()
          + " prendrait aussi les autres pipelines du groupe.");
  }

  /**
   * Sans droit admin, le dépôt d'un pipeline ne se lit que dans ses runs : un appel d'historique
   * par pipeline. L'index garde le résultat pour les recherches suivantes.
   */
  JsonNode loadOrBuildIndex(Dora.Api gocd) {
    try {
      if (!refresh && Files.exists(index)
          && Files.getLastModifiedTime(index).toInstant().isAfter(Instant.now().minus(Duration.ofDays(1))))
        return Dora.JSON.readTree(index.toFile());
    } catch (IOException e) {
      System.err.println("Index illisible, reconstruit : " + e.getMessage());
    }
    ObjectNode root = Dora.JSON.createObjectNode();
    root.put("built_at", Dora.fmt(Instant.now()));
    ObjectNode pipes = root.putObject("pipelines");
    JsonNode dash = gocd.must("/go/api/dashboard");
    Map<String, String> groupOf = new HashMap<>();
    for (JsonNode g : dash.path("_embedded").path("pipeline_groups"))
      for (JsonNode p : g.path("pipelines")) groupOf.put(p.asText(), g.path("name").asText());
    int n = 0;
    for (var e : groupOf.entrySet()) {
      if (++n % 50 == 0) System.err.printf("  index GoCD : %d / %d pipelines%n", n, groupOf.size());
      ObjectNode p = pipes.putObject(e.getKey());
      p.put("group", e.getValue());
      ArrayNode git = p.putArray("git"), up = p.putArray("upstream");
      Dora.Resp r = gocd.get("/go/api/pipelines/" + Dora.enc(e.getKey()) + "/history?page_size=10");
      if (!r.ok()) continue;
      JsonNode runs = r.json().path("pipelines");
      if (runs.size() == 0) continue;
      JsonNode last = runs.get(0);
      p.put("last_run", Dora.fmt(Dora.instant(last.path("scheduled_date"))));
      Set<String> g = new LinkedHashSet<>(), u = new LinkedHashSet<>();
      for (JsonNode run : runs)
        for (JsonNode mr : run.path("build_cause").path("material_revisions")) {
          JsonNode m = mr.path("material");
          String type = m.path("type").asText();
          if (type.equalsIgnoreCase("git")) g.add(Dora.gitUrl(m.path("description").asText("")));
          if (type.equalsIgnoreCase("pipeline")) u.add(Dora.upstreamName(m));
        }
      g.forEach(git::add);
      u.forEach(up::add);
    }
    try {
      Files.createDirectories(index.toAbsolutePath().getParent());
      Dora.JSON.writerWithDefaultPrettyPrinter().writeValue(index.toFile(), root);
    } catch (IOException e) {
      System.err.println("Index non écrit : " + e.getMessage());
    }
    return root;
  }
}
