///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2
//DEPS info.picocli:picocli:4.7.6
//SOURCES Dora.java

// Extraction de pipelines GoCD (PAT) : chaque run sur la période, ses stages, et les commits
// qu'il a déployés, y compris ceux arrivés par les pipelines amont. GET seulement.

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "GocdExtract",
    mixinStandardHelpOptions = true,
    description = {
      "Extrait l'historique de pipelines GoCD et les commits que chaque run a embarqués.",
      "Écrit gocd-runs.csv, gocd-stages.csv, gocd-commits.csv et, sauf --no-raw, gocd-runs.jsonl."
    })
public class GocdExtract implements Callable<Integer> {

  @Option(names = "--pipeline", split = ",", description = "Nom(s) de pipeline")
  List<String> pipelines = new ArrayList<>();

  @Option(names = "--group", split = ",", description = "Groupe(s) de pipelines : tous leurs pipelines")
  List<String> groups = new ArrayList<>();

  @Option(names = "--days", defaultValue = "365", description = "Runs planifiés dans les N derniers jours")
  int days;

  @Option(names = "--max-upstream", defaultValue = "30", description = "Runs amont lus au plus pour un seul run")
  int maxUpstream;

  @Option(names = "--durations", description = "Lit chaque stage pour sa fin et sa durée (un appel de plus par stage)")
  boolean durations;

  @Option(names = "--out-dir", defaultValue = "extraction/gocd", description = "Dossier de sortie")
  Path outDir;

  @Option(names = "--no-raw", description = "N'écrit pas les runs bruts (jsonl)")
  boolean noRaw;

  @Option(names = "--url", defaultValue = "${env:GOCD_URL}", description = "env GOCD_URL")
  String url;

  @Option(names = "--token", defaultValue = "${env:GOCD_TOKEN}", description = "env GOCD_TOKEN")
  String token;

  @Option(names = "--insecure", description = "Accepte un certificat auto-signé")
  boolean insecure;

  /** Un commit embarqué par un run, et par quel chemin il est arrivé. */
  record Commit(String via, String repo, String revision, Instant at, String keys) {}

  Dora.Api gocd;
  final Map<String, String> groupOf = new HashMap<>();
  final Map<String, JsonNode> instances = new HashMap<>();

  public static void main(String[] args) {
    System.exit(new CommandLine(new GocdExtract()).execute(args));
  }

  @Override
  public Integer call() {
    try {
      gocd = Dora.Api.gocd(url, token, insecure);
      Set<String> wanted = resolvePipelines();
      if (wanted.isEmpty()) throw new Dora.Fatal("aucun pipeline : donner --pipeline ou --group");
      Instant since = Instant.now().minus(Duration.ofDays(days));
      try (Dora.Csv runs = new Dora.Csv(outDir.resolve("gocd-runs.csv"),
              "pipeline", "groupe", "plateforme_supposee", "compteur", "label", "planifie_le", "declenchement",
              "resultat", "stages", "depots_git", "amont", "nb_commits", "premier_commit_le", "dernier_commit_le",
              "delai_premier_commit_h", "cles_jira");
           Dora.Csv stages = new Dora.Csv(outDir.resolve("gocd-stages.csv"),
              "pipeline", "compteur", "stage", "compteur_stage", "plateforme_supposee", "lance", "resultat",
              "approbation", "relance", "debut_le", "fin_le", "duree_min");
           Dora.Csv commits = new Dora.Csv(outDir.resolve("gocd-commits.csv"),
              "pipeline", "compteur", "via", "depot", "revision", "commit_le", "cles_jira");
           Dora.Jsonl raw = noRaw ? null : new Dora.Jsonl(outDir.resolve("gocd-runs.jsonl"))) {
        for (String p : wanted) {
          List<JsonNode> history = history(p, since);
          System.err.printf("%s : %d runs%n", p, history.size());
          for (JsonNode run : history) {
            if (raw != null) raw.write(run);
            List<Commit> found = commitsOf(p, run);
            writeRun(p, run, found, runs);
            writeStages(p, run, stages);
            for (Commit c : found)
              commits.row(p, run.path("counter").asInt(), c.via(), c.repo(), c.revision(), Dora.fmt(c.at()), c.keys());
          }
        }
        System.err.printf("%s : %d runs, %d stages, %d commits%n", outDir, runs.rows, stages.rows, commits.rows);
      }
      System.err.println(gocd.calls + " requêtes GET.");
      return 0;
    } catch (Dora.Fatal e) {
      System.err.println("Arrêt : " + e.getMessage());
      return 2;
    }
  }

  Set<String> resolvePipelines() {
    Set<String> out = new LinkedHashSet<>(pipelines);
    JsonNode dash = gocd.must("/go/api/dashboard");
    for (JsonNode g : dash.path("_embedded").path("pipeline_groups")) {
      String name = g.path("name").asText();
      for (JsonNode p : g.path("pipelines")) {
        groupOf.put(p.asText(), name);
        if (groups.contains(name)) out.add(p.asText());
      }
    }
    for (String g : groups)
      if (!groupOf.containsValue(g)) System.err.println("Groupe invisible ou inconnu : " + g);
    for (String p : pipelines)
      if (!groupOf.containsKey(p)) System.err.println("Pipeline invisible ou inconnu : " + p);
    return out;
  }

  /** Les runs de la fenêtre, du plus récent au plus ancien, comme GoCD les rend. */
  List<JsonNode> history(String pipeline, Instant since) {
    List<JsonNode> out = new ArrayList<>();
    String next = "/go/api/pipelines/" + Dora.enc(pipeline) + "/history?page_size=100";
    while (next != null) {
      Dora.Resp r = gocd.get(next);
      if (!r.ok()) {
        System.err.println(pipeline + " : historique illisible (" + r.why() + ")");
        break;
      }
      JsonNode page = r.json();
      boolean older = false;
      for (JsonNode run : page.path("pipelines")) {
        Instant at = Dora.instant(run.path("scheduled_date"));
        if (at != null && at.isBefore(since)) {
          older = true;
          break;
        }
        out.add(run);
        instances.put(pipeline + "/" + run.path("counter").asInt(), run);
      }
      String href = page.path("_links").path("next").path("href").asText("");
      next = older || href.isEmpty() || page.path("pipelines").size() == 0 ? null : href;
    }
    return out;
  }

  /**
   * Les commits qu'un run a embarqués. Directs : ses matériaux git changés. Par l'amont : si le run
   * précédent du même pipeline avait build/9 et celui-ci build/12, les commits des builds 10, 11 et
   * 12, et ainsi de suite à chaque niveau (prod ← recette ← build ← git).
   */
  List<Commit> commitsOf(String pipeline, JsonNode run) {
    Map<String, Commit> out = new LinkedHashMap<>();
    collect(pipeline, run, "direct", 0, out, new int[] {maxUpstream});
    return new ArrayList<>(out.values());
  }

  void collect(String pipeline, JsonNode run, String via, int depth, Map<String, Commit> out, int[] budget) {
    if (depth > 6) return;
    int counter = run.path("counter").asInt();
    for (JsonNode mr : run.path("build_cause").path("material_revisions")) {
      if (!mr.path("changed").asBoolean(false)) continue;
      String type = mr.path("material").path("type").asText();
      if (type.equalsIgnoreCase("git")) {
        addGit(mr, via, out);
      } else if (type.equalsIgnoreCase("pipeline")) {
        for (JsonNode mod : mr.path("modifications")) {
          String[] rev = mod.path("revision").asText().split("/");
          if (rev.length < 2 || parse(rev[1]) <= 0) continue;
          int to = parse(rev[1]);
          int previous = upstreamCounter(instance(pipeline, counter - 1, budget), rev[0]);
          int from = previous > 0 && previous < to ? previous + 1 : to;
          for (int c = Math.max(from, to - maxUpstream + 1); c <= to; c++) {
            JsonNode up = instance(rev[0], c, budget);
            if (up != null) collect(rev[0], up, rev[0] + "/" + c, depth + 1, out, budget);
          }
        }
      }
    }
  }

  /** Le compteur du pipeline amont qu'un run a pris, -1 si inconnu. */
  static int upstreamCounter(JsonNode run, String upstream) {
    if (run == null) return -1;
    for (JsonNode mr : run.path("build_cause").path("material_revisions"))
      for (JsonNode mod : mr.path("modifications")) {
        String[] rev = mod.path("revision").asText().split("/");
        if (rev.length >= 2 && rev[0].equals(upstream)) return parse(rev[1]);
      }
    return -1;
  }

  JsonNode instance(String pipeline, int counter, int[] budget) {
    String key = pipeline + "/" + counter;
    if (instances.containsKey(key)) return instances.get(key);
    if (counter <= 0 || budget[0] <= 0) return null;
    budget[0]--;
    Dora.Resp r = gocd.get("/go/api/pipelines/" + Dora.enc(pipeline) + "/" + counter);
    JsonNode run = r.ok() ? r.json() : null;
    instances.put(key, run);
    return run;
  }

  static void addGit(JsonNode mr, String via, Map<String, Commit> out) {
    String repo = Dora.gitUrl(mr.path("material").path("description").asText(""));
    for (JsonNode mod : mr.path("modifications"))
      out.putIfAbsent(mod.path("revision").asText(), new Commit(via, repo, mod.path("revision").asText(), Dora.instant(mod.path("modified_time")),
          String.join("|", Dora.jiraKeys(mod.path("comment").asText("")))));
  }

  void writeRun(String p, JsonNode run, List<Commit> found, Dora.Csv csv) {
    JsonNode bc = run.path("build_cause");
    Instant scheduled = Dora.instant(run.path("scheduled_date"));
    Set<String> repos = new LinkedHashSet<>(), upstream = new LinkedHashSet<>(), keys = new LinkedHashSet<>();
    List<String> stageResults = new ArrayList<>();
    for (JsonNode mr : bc.path("material_revisions")) {
      String type = mr.path("material").path("type").asText();
      if (type.equalsIgnoreCase("git")) repos.add(Dora.gitUrl(mr.path("material").path("description").asText("")));
      if (type.equalsIgnoreCase("pipeline"))
        for (JsonNode mod : mr.path("modifications")) upstream.add(mod.path("revision").asText());
    }
    for (JsonNode s : run.path("stages"))
      stageResults.add(s.path("name").asText() + ":" + (s.path("scheduled").asBoolean(true) ? s.path("result").asText() : "NonLancé"));
    Instant first = null, last = null;
    for (Commit c : found) {
      if (c.at() != null && (first == null || c.at().isBefore(first))) first = c.at();
      if (c.at() != null && (last == null || c.at().isAfter(last))) last = c.at();
      if (!c.keys().isEmpty()) keys.addAll(List.of(c.keys().split("\\|")));
    }
    csv.row(p, groupOf.getOrDefault(p, ""), Dora.platforms(p), run.path("counter").asInt(), run.path("label").asText(),
        Dora.fmt(scheduled), bc.path("trigger_forced").asBoolean(false) ? "forcé" : "automatique", outcome(run),
        String.join("|", stageResults), String.join("|", repos), String.join("|", upstream), found.size(),
        Dora.fmt(first), Dora.fmt(last), Dora.hours(first, scheduled), String.join("|", keys));
  }

  void writeStages(String p, JsonNode run, Dora.Csv csv) {
    int counter = run.path("counter").asInt();
    for (JsonNode s : run.path("stages")) {
      boolean scheduled = s.path("scheduled").asBoolean(true);
      Instant start = null, end = null;
      for (JsonNode j : s.path("jobs")) {
        Instant at = Dora.instant(j.path("scheduled_date"));
        if (at != null && (start == null || at.isBefore(start))) start = at;
      }
      if (durations && scheduled) {
        Dora.Resp r = gocd.get("/go/api/stages/" + Dora.enc(p) + "/" + counter + "/" + Dora.enc(s.path("name").asText())
            + "/" + s.path("counter").asText("1"));
        if (r.ok())
          for (JsonNode j : r.json().path("jobs"))
            for (JsonNode t : j.path("job_state_transitions")) {
              Instant at = Dora.instant(t.path("state_change_time"));
              if ("Completed".equals(t.path("state").asText()) && at != null && (end == null || at.isAfter(end))) end = at;
            }
      }
      String minutes = start != null && end != null ? "%.1f".formatted(Duration.between(start, end).toSeconds() / 60.0).replace('.', ',') : "";
      csv.row(p, counter, s.path("name").asText(), s.path("counter").asText(), Dora.platforms(p, s.path("name").asText()),
          scheduled ? "O" : "N", scheduled ? s.path("result").asText() : "", s.path("approval_type").asText(),
          s.path("counter").asInt(1) > 1 ? "O" : "N", Dora.fmt(start), Dora.fmt(end), minutes);
    }
  }

  static String outcome(JsonNode run) {
    boolean failed = false, cancelled = false, unknown = false;
    for (JsonNode s : run.path("stages")) {
      if (!s.path("scheduled").asBoolean(true)) continue;
      switch (s.path("result").asText("")) {
        case "Passed" -> {}
        case "Failed" -> failed = true;
        case "Cancelled" -> cancelled = true;
        default -> unknown = true;
      }
    }
    return failed ? "échec" : cancelled ? "annulé" : unknown ? "en cours" : "succès";
  }

  static int parse(String s) {
    try {
      return Integer.parseInt(s.trim());
    } catch (NumberFormatException e) {
      return -1;
    }
  }
}
