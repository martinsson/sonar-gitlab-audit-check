///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2
//DEPS info.picocli:picocli:4.7.6
//SOURCES Probe.java

// Sonde Jira Data Center : le PAT est-il accepté, que voit-il, et de quoi dispose-t-on
// pour le taux de bugs, les incidents et le lien avec GitLab. GET seulement.

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
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
    name = "JiraProbe",
    mixinStandardHelpOptions = true,
    description = "Vérifie l'accès à Jira (Server / Data Center) et ce qu'on peut en tirer. Lecture seule.")
public class JiraProbe implements Callable<Integer> {

  @Option(names = "--url", defaultValue = "${env:JIRA_URL}", description = "Racine de Jira (env JIRA_URL)")
  String url;

  @Option(names = "--token", defaultValue = "${env:JIRA_TOKEN}", description = "Personal Access Token (env JIRA_TOKEN)")
  String token;

  @Option(names = "--user", defaultValue = "${env:JIRA_USER}", description = "Repli Basic auth, si le PAT échoue (env JIRA_USER)")
  String user;

  @Option(names = "--password", defaultValue = "${env:JIRA_PASSWORD}", description = "Mot de passe du repli (env JIRA_PASSWORD)")
  String password;

  @Option(names = "--days", defaultValue = "365", description = "Fenêtre en jours")
  int days;

  @Option(names = "--max-issues", defaultValue = "5000", description = "Tickets lus au plus pour les répartitions")
  int maxIssues;

  @Option(names = "--sample", defaultValue = "15", description = "Tickets récents examinés en détail")
  int sample;

  @Option(names = "--project", description = "Projet Jira (clé) examiné en détail. Défaut : le plus actif")
  String project;

  @Option(names = "--gitlab-url", defaultValue = "${env:GITLAB_URL}", description = "Pour reconnaître les liens GitLab (env GITLAB_URL)")
  String gitlabUrl;

  @Option(names = "--show-names", description = "Affiche les clés et noms de projets. Sans lui, le rapport peut sortir de l'entreprise")
  boolean showNames;

  @Option(names = "--dump-dir", description = "Écrit chaque réponse brute ici (données réelles : ne pas commiter)")
  Path dumpDir;

  @Option(names = "--insecure", description = "Accepte un certificat auto-signé")
  boolean insecure;

  @Option(names = "--timeout", defaultValue = "30", description = "Délai par requête, en secondes")
  int timeout;

  /** Types de tickets qu'on compte comme des défauts. À revoir projet par projet. */
  static final Pattern BUG = Pattern.compile("(?i)bug|anomal|d[ée]faut|defect|incident|probl[eè]me|problem");

  /** Champs dont le nom laisse penser qu'ils disent la plateforme, la gravité ou la livraison. */
  static final Pattern INTERESTING_FIELD = Pattern.compile(
      "(?i)env|plateforme|platform|prod|recette|s[ée]v[ée]rit|severity|gravit|impact|urgen|incident|cause|origin"
          + "|d[ée]tect|version|release|livr|d[ée]ploi|deploy|sprint|epic|story point|point|development|d[ée]velop"
          + "|gitlab|git|commit|merge|composant|component|appli|service|produit|product");

  Probe probe;
  String base;
  Map<String, String> auth = Map.of();
  final Map<String, String> fieldNames = new HashMap<>();
  final List<String> verdicts = new ArrayList<>();

  public static void main(String[] args) {
    System.exit(new CommandLine(new JiraProbe()).execute(args));
  }

  @Override
  public Integer call() throws Exception {
    if (url == null || url.isBlank()) {
      System.err.println("JIRA_URL (ou --url) manquant.");
      return 2;
    }
    base = Probe.trimSlash(url);
    probe = new Probe(timeout, insecure, dumpDir, showNames);
    System.out.println("Sonde Jira — lecture seule. Noms " + (showNames ? "AFFICHÉS : ne pas diffuser ce rapport." : "masqués."));

    instance();
    if (!authenticate()) {
      footer();
      return 2;
    }
    projects();
    Map<String, JsonNode> types = configuration();
    fields();
    counts(types);
    List<JsonNode> issues = scan();
    details(issues);
    projectDetail(issues);
    agile();

    Probe.section("Bilan");
    verdicts.forEach(Probe::info);
    footer();
    return 0;
  }

  Probe.Resp get(String path) {
    Map<String, String> h = new LinkedHashMap<>(auth);
    h.put("Accept", "application/json");
    return probe.get(base + path, h);
  }

  // ------------------------------------------------------------ connexion

  void instance() {
    Probe.section("Instance");
    Probe.Resp r = probe.get(base + "/rest/api/2/serverInfo", Probe.headers("Accept", "application/json"));
    JsonNode j = r.json();
    Probe.line(r, "serverInfo (anonyme)", r.ok()
        ? "version " + j.path("version").asText("?") + ", " + j.path("deploymentType").asText("?") : "");
    if (r.ok() && "Cloud".equalsIgnoreCase(j.path("deploymentType").asText()))
      Probe.info("→ Jira Cloud : les PAT n'y existent pas, il faut email + jeton API en Basic auth.");
  }

  boolean authenticate() {
    Probe.section("Connexion");
    List<Map.Entry<String, Map<String, String>>> modes = new ArrayList<>();
    if (present(token)) modes.add(Map.entry("pat", Map.of("Authorization", "Bearer " + token.trim())));
    if (present(user) && present(password)) {
      String b64 = Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
      modes.add(Map.entry("basic", Map.of("Authorization", "Basic " + b64)));
    }
    if (modes.isEmpty()) {
      Probe.line("ERR", "identifiant", "JIRA_TOKEN absent (créer un PAT : profil > Personal Access Tokens)");
      return false;
    }
    for (var m : modes) {
      auth = m.getValue();
      Probe.Resp r = get("/rest/api/2/myself");
      String login = r.headers().getOrDefault("x-ausername", "");
      String reason = r.headers().getOrDefault("x-seraph-loginreason", "");
      String detail = r.ok() ? "authentifié" : "";
      if (login.equalsIgnoreCase("anonymous")) detail += " (Jira nous voit anonyme : jeton ignoré)";
      if (!reason.isEmpty()) detail += " login-reason=" + reason;
      Probe.line(r, m.getKey(), detail.trim());
      if (reason.contains("CAPTCHA") || reason.contains("DENIED"))
        Probe.info("→ CAPTCHA demandé : se connecter une fois dans le navigateur pour le lever.");
      if (r.ok()) {
        Probe.info("Mode retenu : " + m.getKey());
        verdicts.add("Connexion : " + m.getKey() + " accepté.");
        return true;
      }
    }
    verdicts.add("Connexion : refusée avec tous les modes fournis.");
    Probe.section("Bilan");
    verdicts.forEach(Probe::info);
    return false;
  }

  static boolean present(String s) {
    return s != null && !s.isBlank();
  }

  // ------------------------------------------------------------ configuration

  List<JsonNode> projects() {
    Probe.section("Projets visibles");
    Probe.Resp r = get("/rest/api/2/project");
    List<JsonNode> list = new ArrayList<>();
    if (!r.ok()) {
      Probe.line(r, "project", "");
      return list;
    }
    r.json().forEach(list::add);
    Map<String, Integer> kinds = Probe.counter();
    for (JsonNode p : list) Probe.inc(kinds, p.path("projectTypeKey").asText("?"));
    Probe.line("OK", "project", list.size() + " projets ; types : " + Probe.tally(kinds));
    Probe.Resp perm = get("/rest/api/2/mypermissions?permissions=BROWSE_PROJECTS");
    Probe.line(perm, "mypermissions", perm.ok()
        ? "BROWSE_PROJECTS=" + perm.json().path("permissions").path("BROWSE_PROJECTS").path("havePermission").asText("?") : "");
    verdicts.add("Projets : " + list.size() + " visibles.");
    return list;
  }

  Map<String, JsonNode> configuration() {
    Probe.section("Configuration (noms de types, statuts… : de la config, pas des données)");
    Map<String, JsonNode> types = new LinkedHashMap<>();
    Probe.Resp it = get("/rest/api/2/issuetype");
    if (it.ok()) {
      List<String> names = new ArrayList<>();
      for (JsonNode t : it.json()) {
        types.put(t.path("id").asText(), t);
        names.add(t.path("name").asText() + (t.path("subtask").asBoolean(false) ? " (sous-tâche)" : ""));
      }
      Probe.line("OK", "issuetype", types.size() + " types");
      Probe.info(String.join(", ", names));
      long bugTypes = types.values().stream().filter(t -> BUG.matcher(t.path("name").asText()).find()).count();
      Probe.info("types reconnus comme défauts (" + BUG.pattern() + ") : " + bugTypes);
    } else Probe.line(it, "issuetype", "");

    Probe.Resp pr = get("/rest/api/2/priority");
    Probe.line(pr, "priority", pr.ok() ? names(pr.json()) : "");
    Probe.Resp res = get("/rest/api/2/resolution");
    Probe.line(res, "resolution", res.ok() ? names(res.json()) : "");
    Probe.Resp st = get("/rest/api/2/status");
    if (st.ok()) {
      Map<String, Integer> cat = Probe.counter();
      for (JsonNode s : st.json()) Probe.inc(cat, s.path("statusCategory").path("key").asText("?"));
      Probe.line("OK", "status", st.json().size() + " statuts ; catégories : " + Probe.tally(cat));
    } else Probe.line(st, "status", "");
    return types;
  }

  void fields() {
    Probe.section("Champs");
    Probe.Resp r = get("/rest/api/2/field");
    if (!r.ok()) {
      Probe.line(r, "field", "");
      return;
    }
    int custom = 0;
    List<String> hits = new ArrayList<>();
    for (JsonNode f : r.json()) {
      String id = f.path("id").asText(), name = f.path("name").asText();
      fieldNames.put(id, name);
      if (!f.path("custom").asBoolean(false)) continue;
      custom++;
      if (INTERESTING_FIELD.matcher(name).find()) {
        String type = f.path("schema").path("custom").asText(f.path("schema").path("type").asText("?"));
        hits.add(name + " [" + id + ", " + type.replaceFirst("^.*:", "") + "]");
      }
    }
    Probe.line("OK", "field", r.json().size() + " champs dont " + custom + " personnalisés");
    Probe.info("champs personnalisés susceptibles de dire plateforme, gravité, livraison ou lien git :");
    hits.forEach(h -> Probe.info("  " + h));
  }

  static String names(JsonNode arr) {
    List<String> n = new ArrayList<>();
    arr.forEach(x -> n.add(x.path("name").asText()));
    return String.join(", ", n);
  }

  // ------------------------------------------------------------ volumes

  void counts(Map<String, JsonNode> types) {
    Probe.section("Volumes sur " + days + " j (JQL, totaux seulement)");
    long created = total("created >= -" + days + "d");
    long resolved = total("resolved >= -" + days + "d");
    Probe.info("créés : " + created + ", résolus : " + resolved);
    Map<String, Integer> byType = new LinkedHashMap<>();
    long bugs = 0;
    for (JsonNode t : types.values()) {
      if (t.path("subtask").asBoolean(false)) continue;
      long n = total("issuetype = " + t.path("id").asText() + " AND created >= -" + days + "d");
      if (n > 0) byType.put(t.path("name").asText(), (int) n);
      if (n > 0 && BUG.matcher(t.path("name").asText()).find()) bugs += n;
    }
    Probe.info("par type : " + Probe.tally(byType));
    if (created > 0) Probe.info("part des défauts : %.0f %%".formatted(100.0 * bugs / created));
    verdicts.add("Volumes : " + created + " tickets créés sur la période, " + bugs + " de type défaut.");
  }

  long total(String jql) {
    Probe.Resp r = get("/rest/api/2/search?maxResults=0&fields=none&jql=" + Probe.enc(jql));
    if (!r.ok()) {
      Probe.line(r, "search " + jql, "");
      return -1;
    }
    return r.json().path("total").asLong(-1);
  }

  /** Une lecture des tickets récents, champs minimaux, pour les répartitions par projet. */
  List<JsonNode> scan() {
    Probe.section("Répartition par projet (" + maxIssues + " tickets au plus)");
    List<JsonNode> issues = new ArrayList<>();
    String jql = "created >= -" + days + "d ORDER BY created DESC";
    int pageSize = 0;
    while (issues.size() < maxIssues) {
      Probe.Resp r = get("/rest/api/2/search?startAt=" + issues.size() + "&maxResults=1000"
          + "&fields=project,issuetype,created,resolutiondate,priority,status&jql=" + Probe.enc(jql));
      if (!r.ok()) {
        Probe.line(r, "search (pages)", "");
        break;
      }
      JsonNode list = r.json().path("issues");
      if (pageSize == 0) pageSize = r.json().path("maxResults").asInt();
      list.forEach(issues::add);
      if (list.size() == 0 || issues.size() >= r.json().path("total").asInt()) break;
    }
    Probe.line("OK", "search (pages)", issues.size() + " tickets lus, taille de page accordée : " + pageSize);

    Map<String, int[]> perProject = new HashMap<>();
    List<Double> bugDays = new ArrayList<>();
    Map<String, Integer> bugPriority = Probe.counter();
    for (JsonNode i : issues) {
      JsonNode f = i.path("fields");
      boolean bug = BUG.matcher(f.path("issuetype").path("name").asText()).find();
      int[] c = perProject.computeIfAbsent(f.path("project").path("key").asText(), k -> new int[2]);
      c[0]++;
      if (bug) {
        c[1]++;
        Probe.inc(bugPriority, f.path("priority").path("name").asText("(aucune)"));
        Instant a = jiraTime(f.path("created").asText()), b = jiraTime(f.path("resolutiondate").asText());
        if (a != null && b != null) bugDays.add(Duration.between(a, b).toHours() / 24.0);
      }
    }
    List<Double> share = new ArrayList<>();
    List<Double> volume = new ArrayList<>();
    perProject.values().forEach(c -> {
      volume.add((double) c[0]);
      if (c[0] >= 20) share.add(100.0 * c[1] / c[0]);
    });
    Probe.info("projets ayant des tickets : " + perProject.size() + " ; tickets par projet : " + Probe.quantiles(volume, ""));
    Probe.info("part de défauts par projet (projets ≥ 20 tickets) : " + Probe.quantiles(share, "%"));
    Probe.info("défauts par priorité : " + Probe.tally(bugPriority));
    Probe.info("délai de résolution des défauts : " + Probe.quantiles(bugDays, "j"));
    return issues;
  }

  // ------------------------------------------------------------ détail

  /** Un échantillon de tickets récents : historique, champs remplis, traces de GitLab. */
  void details(List<JsonNode> issues) {
    Probe.section("Détail de " + Math.min(sample, issues.size()) + " tickets récents");
    if (issues.isEmpty()) return;
    String glHost = present(gitlabUrl) ? Probe.host(gitlabUrl) : "";
    Map<String, Integer> filled = Probe.counter();
    int withChangelog = 0, transitions = 0, commentGitlab = 0, remoteGitlab = 0, remoteAny = 0, devAny = 0;
    String devStatus = "?", remoteStatus = "?";
    int n = 0;
    for (JsonNode i : issues.subList(0, Math.min(sample, issues.size()))) {
      n++;
      String key = i.path("key").asText(), id = i.path("id").asText();
      Probe.Resp r = get("/rest/api/2/issue/" + Probe.enc(key) + "?expand=changelog");
      if (!r.ok()) {
        Probe.line(r, "issue/<clé>", "");
        continue;
      }
      JsonNode j = r.json();
      j.path("fields").fields().forEachRemaining(e -> {
        JsonNode v = e.getValue();
        boolean empty = v.isNull() || v.isMissingNode() || (v.isArray() && v.size() == 0) || (v.isTextual() && v.asText().isBlank());
        if (!empty) Probe.inc(filled, fieldNames.getOrDefault(e.getKey(), e.getKey()));
      });
      JsonNode histories = j.path("changelog").path("histories");
      if (histories.size() > 0) withChangelog++;
      for (JsonNode h : histories)
        for (JsonNode item : h.path("items")) if ("status".equals(item.path("field").asText())) transitions++;
      boolean mention = false;
      for (JsonNode c : j.path("fields").path("comment").path("comments")) {
        String body = c.path("body").asText().toLowerCase(Locale.ROOT);
        if (body.contains("gitlab") || (!glHost.isEmpty() && body.contains(glHost))) mention = true;
      }
      if (mention) commentGitlab++;

      Probe.Resp rl = get("/rest/api/2/issue/" + Probe.enc(key) + "/remotelink");
      remoteStatus = rl.tag();
      if (rl.ok() && rl.json().size() > 0) {
        remoteAny++;
        for (JsonNode l : rl.json()) {
          String u = l.path("object").path("url").asText();
          if (u.toLowerCase(Locale.ROOT).contains("gitlab") || (!glHost.isEmpty() && glHost.equals(Probe.host(u)))) {
            remoteGitlab++;
            break;
          }
        }
      }
      Probe.Resp dev = get("/rest/dev-status/latest/issue/summary?issueId=" + Probe.enc(id));
      devStatus = dev.tag();
      if (dev.ok()) {
        JsonNode s = dev.json().path("summary");
        int total = s.path("repository").path("overall").path("count").asInt()
            + s.path("pullrequest").path("overall").path("count").asInt()
            + s.path("branch").path("overall").path("count").asInt();
        if (total > 0) devAny++;
      }
    }
    Probe.info("avec historique (changelog) : " + withChangelog + "/" + n + ", changements de statut : " + transitions);
    Probe.info("commentaires qui citent GitLab : " + commentGitlab + "/" + n);
    Probe.info("liens distants (remotelink " + remoteStatus + ") : " + remoteAny + "/" + n + ", vers GitLab : " + remoteGitlab);
    Probe.info("panneau développement (dev-status " + devStatus + ") avec commits/branches/MR : " + devAny + "/" + n);
    Probe.info("champs remplis (sur " + n + ") :");
    filled.entrySet().stream()
        .sorted((a, b) -> b.getValue() - a.getValue())
        .forEach(e -> Probe.info("  " + e.getValue() + "  " + e.getKey()));
    verdicts.add("Historique des statuts : " + (withChangelog > 0 ? "lisible (délais par étape calculables)." : "absent de l'échantillon."));
    verdicts.add("Traces GitLab côté Jira : commentaires " + commentGitlab + ", liens " + remoteGitlab + ", panneau dev " + devAny
        + " sur " + n + " tickets (l'autre sens, clés Jira dans les commits, se mesure côté GitLab).");
  }

  void projectDetail(List<JsonNode> issues) {
    String key = project;
    if (key == null) {
      Map<String, Integer> c = Probe.counter();
      for (JsonNode i : issues) Probe.inc(c, i.path("fields").path("project").path("key").asText());
      key = c.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }
    if (key == null) return;
    String alias = probe.name("projet", key);
    Probe.section("Projet " + alias + " : versions et composants");
    Probe.Resp v = get("/rest/api/2/project/" + Probe.enc(key) + "/versions");
    if (v.ok()) {
      int released = 0, recent = 0;
      Instant since = Instant.now().minus(Duration.ofDays(days));
      for (JsonNode x : v.json()) {
        if (x.path("released").asBoolean(false)) {
          released++;
          String d = x.path("releaseDate").asText("");
          if (!d.isEmpty() && java.time.LocalDate.parse(d).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().isAfter(since)) recent++;
        }
      }
      Probe.line("OK", "project/<clé>/versions", v.json().size() + " versions, " + released + " livrées, dont " + recent + " sur la période");
      if (recent > 0) verdicts.add("Versions Jira : livrées avec date sur " + alias + ", utilisables pour recouper les mises en prod.");
    } else Probe.line(v, "project/<clé>/versions", "");
    Probe.Resp c = get("/rest/api/2/project/" + Probe.enc(key) + "/components");
    Probe.line(c, "project/<clé>/components", c.ok() ? c.json().size() + " composants" : "");
  }

  void agile() {
    Probe.section("Agile");
    Probe.Resp b = get("/rest/agile/1.0/board?maxResults=50");
    Probe.line(b, "agile/1.0/board", b.ok()
        ? b.json().path("values").size() + " tableaux lus" + (b.json().path("isLast").asBoolean(true) ? "" : " (il y en a plus)") : "");
  }

  void footer() {
    Probe.section("Appels");
    Probe.info(probe.calls + " requêtes GET.");
  }

  /** Jira DC écrit 2025-03-04T10:15:30.000+0100 : pas d'ISO strict. */
  static Instant jiraTime(String s) {
    if (s == null || s.isEmpty()) return null;
    try {
      return OffsetDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")).toInstant();
    } catch (RuntimeException e) {
      try {
        return OffsetDateTime.parse(s).toInstant();
      } catch (RuntimeException e2) {
        return null;
      }
    }
  }
}
