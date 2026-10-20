///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2
//DEPS info.picocli:picocli:4.7.6
//SOURCES Dora.java

// Extraction d'un ou plusieurs projets Jira (Server / Data Center, PAT) : tous les tickets touchés
// sur la période, leur historique de statuts, et les versions du projet. GET seulement.

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "JiraExtract",
    mixinStandardHelpOptions = true,
    description = {
      "Extrait les tickets d'un projet Jira, leurs changements de statut et les versions du projet.",
      "Écrit jira-tickets.csv, jira-transitions.csv, jira-versions.csv et, sauf --no-raw, jira-tickets.jsonl."
    })
public class JiraExtract implements Callable<Integer> {

  @Option(names = "--project", required = true, split = ",", description = "Clé(s) du projet Jira, par exemple ABC")
  List<String> projects;

  @Option(names = "--days", defaultValue = "365", description = "Tickets mis à jour dans les N derniers jours")
  int days;

  @Option(names = "--jql", description = "Remplace la requête par défaut (project in (…) AND updated >= -Nd)")
  String jql;

  @Option(
      names = "--field",
      split = ",",
      description = "Champs en plus dans le CSV, par id (customfield_10010) ou par nom. Les champs dont le nom évoque"
          + " la plateforme ou la gravité y sont déjà")
  List<String> extraFields = new ArrayList<>();

  @Option(names = "--out-dir", defaultValue = "extraction/jira", description = "Dossier de sortie")
  Path outDir;

  @Option(names = "--no-raw", description = "N'écrit pas les tickets bruts (jsonl)")
  boolean noRaw;

  @Option(names = "--url", defaultValue = "${env:JIRA_URL}", description = "env JIRA_URL")
  String url;

  @Option(names = "--token", defaultValue = "${env:JIRA_TOKEN}", description = "env JIRA_TOKEN")
  String token;

  @Option(names = "--insecure", description = "Accepte un certificat auto-signé")
  boolean insecure;

  /** Champs personnalisés repris d'office : ceux qui pourraient dire la plateforme ou la gravité. */
  static final Pattern AUTO_FIELD = Pattern.compile(
      "(?i)env|plateforme|platform|s[ée]v[ée]rit|severity|gravit|impact|incident|cause|origine|d[ée]tect");

  Dora.Api jira;
  final Map<String, String> fieldName = new HashMap<>();
  final Map<String, String> statusCategory = new HashMap<>();
  final Map<String, String> columns = new LinkedHashMap<>(); // id → en-tête
  String epicField;

  public static void main(String[] args) {
    System.exit(new CommandLine(new JiraExtract()).execute(args));
  }

  @Override
  public Integer call() {
    try {
      jira = Dora.Api.jira(url, token, insecure);
      loadConfig();
      String query = jql != null ? jql
          : "project in (" + String.join(",", projects.stream().map(p -> "\"" + p + "\"").toList()) + ") AND updated >= -"
              + days + "d ORDER BY key ASC";
      System.err.println("JQL : " + query);
      extract(query);
      versions();
      System.err.println(jira.calls + " requêtes GET.");
      return 0;
    } catch (Dora.Fatal e) {
      System.err.println("Arrêt : " + e.getMessage());
      return 2;
    }
  }

  void loadConfig() {
    for (JsonNode f : jira.must("/rest/api/2/field")) {
      String id = f.path("id").asText(), name = f.path("name").asText();
      fieldName.put(id, name);
      String custom = f.path("schema").path("custom").asText("");
      if (custom.endsWith(":gh-epic-link")) epicField = id;
      if (f.path("custom").asBoolean(false) && AUTO_FIELD.matcher(name).find()) columns.put(id, name);
    }
    for (String wanted : extraFields) {
      String id = fieldName.containsKey(wanted) ? wanted
          : fieldName.entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(wanted)).map(Map.Entry::getKey)
              .findFirst().orElse(null);
      if (id == null) System.err.println("Champ inconnu, ignoré : " + wanted);
      else columns.put(id, fieldName.get(id));
    }
    for (JsonNode s : jira.must("/rest/api/2/status"))
      statusCategory.put(s.path("id").asText(), s.path("statusCategory").path("key").asText(""));
    if (!columns.isEmpty()) System.err.println("Champs en plus : " + String.join(", ", columns.values()));
  }

  void extract(String query) {
    List<String> header = new ArrayList<>(List.of(
        "cle", "projet", "type", "sous_tache", "statut", "categorie_statut", "priorite", "resolution",
        "cree_le", "en_cours_le", "termine_le", "resolu_le", "mis_a_jour_le", "delai_resolution_j", "delai_cycle_j",
        "nb_changements_statut", "composants", "versions_corrigees", "versions_affectees", "labels", "parent", "epic"));
    columns.forEach((id, name) -> header.add(name + " [" + id + "]"));

    try (Dora.Csv tickets = new Dora.Csv(outDir.resolve("jira-tickets.csv"), header.toArray(String[]::new));
         Dora.Csv transitions = new Dora.Csv(outDir.resolve("jira-transitions.csv"),
             "cle", "le", "de", "vers", "categorie_vers");
         Dora.Jsonl raw = noRaw ? null : new Dora.Jsonl(outDir.resolve("jira-tickets.jsonl"))) {
      int startAt = 0, total = -1;
      while (total < 0 || startAt < total) {
        JsonNode page = search(query, startAt);
        total = page.path("total").asInt();
        JsonNode issues = page.path("issues");
        if (issues.size() == 0) break;
        for (JsonNode issue : issues) {
          JsonNode full = completeChangelog(issue);
          if (raw != null) raw.write(full);
          row(full, tickets, transitions);
        }
        startAt += issues.size();
        System.err.printf("  %d / %d tickets%n", startAt, total);
      }
      System.err.println(tickets.path + " : " + tickets.rows + " tickets, " + transitions.rows + " changements de statut");
    }
  }

  JsonNode search(String query, int startAt) {
    String q = "?jql=" + Dora.enc(query) + "&startAt=" + startAt + "&maxResults=100&fields=*all&expand=changelog";
    Dora.Resp r = jira.get("/rest/api/2/search" + q);
    // Repli si une version future retire /rest/api/2/search (comme Jira Cloud l'a fait).
    if (r.status() == 404 || r.status() == 410) r = jira.get("/rest/api/latest/search" + q);
    if (!r.ok()) throw new Dora.Fatal("search : " + r.why() + " " + r.body().replaceAll("\\s+", " ").substring(0, Math.min(300, r.body().length())));
    return r.json();
  }

  /** La recherche peut tronquer l'historique d'un ticket : on relit alors le ticket seul. */
  JsonNode completeChangelog(JsonNode issue) {
    JsonNode cl = issue.path("changelog");
    if (cl.path("total").asInt(0) <= cl.path("histories").size()) return issue;
    return jira.must("/rest/api/2/issue/" + Dora.enc(issue.path("key").asText()) + "?expand=changelog&fields=*all");
  }

  void row(JsonNode issue, Dora.Csv tickets, Dora.Csv transitions) {
    String key = issue.path("key").asText();
    JsonNode f = issue.path("fields");
    Instant created = Dora.instant(f.path("created")), resolved = Dora.instant(f.path("resolutiondate"));
    Instant inProgress = null, done = null;
    int statusChanges = 0;
    List<JsonNode> histories = new ArrayList<>();
    issue.path("changelog").path("histories").forEach(histories::add);
    histories.sort((a, b) -> a.path("created").asText().compareTo(b.path("created").asText()));
    for (JsonNode h : histories) {
      Instant at = Dora.instant(h.path("created"));
      for (JsonNode item : h.path("items")) {
        if (!"status".equals(item.path("field").asText())) continue;
        statusChanges++;
        String cat = statusCategory.getOrDefault(item.path("to").asText(), "");
        transitions.row(key, Dora.fmt(at), item.path("fromString").asText(""), item.path("toString").asText(""), cat);
        if (cat.equals("indeterminate") && inProgress == null) inProgress = at;
        if (cat.equals("done")) done = at; // le dernier passage à « terminé » compte
        else if (!cat.isEmpty()) done = null; // réouvert
      }
    }
    List<Object> cells = new ArrayList<>(List.of(
        key,
        f.path("project").path("key").asText(),
        f.path("issuetype").path("name").asText(),
        f.path("issuetype").path("subtask").asBoolean(false) ? "O" : "N",
        f.path("status").path("name").asText(),
        f.path("status").path("statusCategory").path("key").asText(),
        f.path("priority").path("name").asText(""),
        f.path("resolution").path("name").asText(""),
        Dora.fmt(created),
        Dora.fmt(inProgress),
        Dora.fmt(done),
        Dora.fmt(resolved),
        Dora.fmt(Dora.instant(f.path("updated"))),
        Dora.days(created, resolved),
        Dora.days(inProgress, done),
        statusChanges,
        value(f.path("components")),
        value(f.path("fixVersions")),
        value(f.path("versions")),
        value(f.path("labels")),
        f.path("parent").path("key").asText(""),
        epicField == null ? "" : value(f.path(epicField))));
    columns.keySet().forEach(id -> cells.add(value(f.path(id))));
    tickets.row(cells.toArray());
  }

  /** Une valeur de champ Jira en texte : option, utilisateur, version, liste… */
  static String value(JsonNode v) {
    if (v == null || v.isMissingNode() || v.isNull()) return "";
    if (v.isArray()) {
      List<String> parts = new ArrayList<>();
      v.forEach(x -> parts.add(value(x)));
      return String.join("|", parts);
    }
    if (v.isObject()) {
      for (String k : List.of("value", "name", "key", "displayName"))
        if (v.has(k)) return v.path(k).asText() + (v.has("child") ? " / " + value(v.path("child")) : "");
      return v.toString();
    }
    String s = v.asText();
    // Les sprints de Jira Server arrivent en texte « …[id=1,name=Sprint 3,…] ».
    if (s.contains("com.atlassian.greenhopper") && s.contains("name=")) s = s.replaceAll(".*name=([^,\\]]*).*", "$1");
    return s.length() > 300 ? s.substring(0, 300) + "…" : s;
  }

  void versions() {
    try (Dora.Csv csv = new Dora.Csv(outDir.resolve("jira-versions.csv"),
        "projet", "version", "livree", "archivee", "debut_le", "livree_le", "description")) {
      for (String p : projects) {
        Dora.Resp r = jira.get("/rest/api/2/project/" + Dora.enc(p.toUpperCase(Locale.ROOT)) + "/versions");
        if (!r.ok()) {
          System.err.println("versions de " + p + " : " + r.why());
          continue;
        }
        for (JsonNode v : r.json())
          csv.row(p, v.path("name").asText(), v.path("released").asBoolean(false) ? "O" : "N",
              v.path("archived").asBoolean(false) ? "O" : "N", v.path("startDate").asText(""),
              v.path("releaseDate").asText(""), v.path("description").asText(""));
      }
      System.err.println(csv.path + " : " + csv.rows + " versions");
    }
  }
}
