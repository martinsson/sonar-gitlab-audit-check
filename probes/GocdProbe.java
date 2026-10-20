///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2
//DEPS info.picocli:picocli:4.7.6
//SOURCES Probe.java

// Sonde GoCD : avec quels moyens de connexion on entre, ce que le compte voit, et si
// l'historique des pipelines suffit aux métriques DORA. GET seulement.

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "GocdProbe",
    mixinStandardHelpOptions = true,
    description = {
      "Vérifie l'accès à GoCD et ce qu'on peut en tirer pour DORA. Lecture seule.",
      "Essaie chaque mode de connexion dont l'identifiant est fourni, puis creuse avec le meilleur."
    })
public class GocdProbe implements Callable<Integer> {

  @Option(names = "--url", defaultValue = "${env:GOCD_URL}", description = "Racine de GoCD, sans /go (env GOCD_URL)")
  String url;

  @Option(
      names = "--auth",
      split = ",",
      description = "Modes à essayer parmi none,token,basic,cookie. Défaut : none + ceux dont l'identifiant est fourni")
  List<String> auth;

  @Option(names = "--token", defaultValue = "${env:GOCD_TOKEN}", description = "Personal Access Token (env GOCD_TOKEN)")
  String token;

  @Option(names = "--user", defaultValue = "${env:GOCD_USER}", description = "Compte pour le Basic auth (env GOCD_USER)")
  String user;

  @Option(names = "--password", defaultValue = "${env:GOCD_PASSWORD}", description = "Mot de passe Basic (env GOCD_PASSWORD)")
  String password;

  @Option(
      names = "--cookie",
      defaultValue = "${env:GOCD_COOKIE}",
      description = "Cookie de session copié du navigateur : « JSESSIONID=… » ou la seule valeur (env GOCD_COOKIE)")
  String cookie;

  @Option(names = "--pipeline", description = "Pipeline à examiner en détail (répétable). Défaut : choisis dans le dashboard")
  List<String> pipelines = new ArrayList<>();

  @Option(names = "--samples", defaultValue = "3", description = "Nombre de pipelines examinés si --pipeline est absent")
  int samples;

  @Option(names = "--days", defaultValue = "365", description = "Fenêtre d'historique en jours")
  int days;

  @Option(names = "--max-pages", defaultValue = "10", description = "Pages d'historique lues par pipeline, au plus")
  int maxPages;

  @Option(names = "--max-upstream", defaultValue = "60", description = "Runs amont lus au plus par pipeline, pour remonter aux commits")
  int maxUpstream;

  @Option(names = "--gitlab-url", defaultValue = "${env:GITLAB_URL}", description = "Pour reconnaître les dépôts GitLab (env GITLAB_URL)")
  String gitlabUrl;

  @Option(names = "--show-names", description = "Affiche les vrais noms. Sans lui, le rapport peut sortir de l'entreprise")
  boolean showNames;

  @Option(names = "--dump-dir", description = "Écrit chaque réponse brute ici (données réelles : ne pas commiter)")
  Path dumpDir;

  @Option(names = "--insecure", description = "Accepte un certificat auto-signé")
  boolean insecure;

  @Option(names = "--timeout", defaultValue = "30", description = "Délai par requête, en secondes")
  int timeout;

  record Auth(String mode, Map<String, String> headers) {}

  /** Une réponse et l'en-tête Accept qui l'a obtenue. */
  record Api(Probe.Resp resp, String accept) {
    boolean ok() {
      return resp.ok();
    }

    JsonNode json() {
      return resp.json();
    }
  }

  Probe probe;
  String base;
  /** La value stream map est une page interne : elle veut une session, pas un jeton. */
  Auth session;
  final Map<String, String> acceptSeen = new LinkedHashMap<>();
  final List<String> verdicts = new ArrayList<>();

  public static void main(String[] args) {
    System.exit(new CommandLine(new GocdProbe()).execute(args));
  }

  @Override
  public Integer call() throws Exception {
    if (url == null || url.isBlank()) {
      System.err.println("GOCD_URL (ou --url) manquant.");
      return 2;
    }
    base = Probe.trimSlash(url).replaceFirst("/go$", "");
    probe = new Probe(timeout, insecure, dumpDir, showNames);

    System.out.println("Sonde GoCD — lecture seule. Noms " + (showNames ? "AFFICHÉS : ne pas diffuser ce rapport." : "masqués."));

    Auth best = compareAuthModes();
    if (best == null) {
      Probe.section("Bilan");
      Probe.info("Aucun mode ne donne accès aux pipelines. Pistes : créer un PAT (menu utilisateur >");
      Probe.info("Personal Access Tokens), copier le cookie JSESSIONID du navigateur, ou demander un compte à l'admin.");
      footer();
      return 2;
    }

    instance(best);
    List<JsonNode> visible = dashboard(best);
    configAccess(best, visible);
    for (String p : chooseSamples(visible)) sample(best, p);
    feeds(best);

    Probe.section("Bilan pour DORA");
    verdicts.forEach(Probe::info);
    footer();
    return 0;
  }

  // ------------------------------------------------------------ connexion

  Auth compareAuthModes() {
    Probe.section("Modes de connexion");
    List<Auth> modes = authModes();
    Auth best = null;
    int bestCount = -1;
    for (Auth a : modes) {
      if (a.mode().equals("cookie")) session = a;
      Api me = api(a, "/go/api/current_user", "v1");
      Api dash = api(a, "/go/api/dashboard", "v4", "v3", "v2", "v1");
      int count = dash.ok() ? dash.json().path("_embedded").path("pipelines").size() : -1;
      if (count < 0) {
        // Dashboard indisponible (ancienne version) : cctray liste aussi les pipelines.
        Probe.Resp cc = probe.get(base + "/go/cctray.xml", a.headers());
        if (cc.ok()) count = cctrayPipelines(cc.body()).size();
      }
      String detail =
          "utilisateur courant " + me.resp().tag()
              + ", dashboard " + dash.resp().tag()
              + (count >= 0 ? ", " + count + " pipelines visibles" : "")
              + (dash.ok() ? "" : " (" + Probe.why(dash.resp()) + ")");
      Probe.line(count >= 0 ? "OK" : dash.resp().tag(), a.mode(), detail);
      if (a.mode().equals("none") && count >= 0)
        Probe.info("→ accès anonyme : la sécurité GoCD semble désactivée, ou l'anonyme a des droits.");
      if (dash.resp().status() == 401) {
        String www = dash.resp().headers().getOrDefault("www-authenticate", "");
        if (!www.isEmpty()) Probe.info("  WWW-Authenticate: " + www.replaceAll("realm=\"[^\"]*\"", "realm=…"));
      }
      if (count > bestCount) {
        best = a;
        bestCount = count;
      }
    }
    if (bestCount < 0) return null;
    Probe.info("Mode retenu pour la suite : " + best.mode());
    if (modes.size() == 1) Probe.info("(aucun identifiant fourni : GOCD_TOKEN, GOCD_USER/GOCD_PASSWORD ou GOCD_COOKIE)");
    return best;
  }

  List<Auth> authModes() {
    List<String> wanted = new ArrayList<>();
    if (auth == null || auth.isEmpty()) {
      wanted.add("none");
      if (present(token)) wanted.add("token");
      if (present(user) && present(password)) wanted.add("basic");
      if (present(cookie)) wanted.add("cookie");
    } else {
      wanted.addAll(auth);
    }
    List<Auth> out = new ArrayList<>();
    for (String m : wanted) {
      switch (m.trim().toLowerCase(Locale.ROOT)) {
        case "none" -> out.add(new Auth("none", Map.of()));
        case "token" -> {
          if (present(token)) out.add(new Auth("token", Map.of("Authorization", "Bearer " + token.trim())));
          else System.err.println("--auth token sans GOCD_TOKEN : ignoré");
        }
        case "basic" -> {
          if (present(user) && present(password)) {
            String b64 = Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
            out.add(new Auth("basic", Map.of("Authorization", "Basic " + b64)));
          } else System.err.println("--auth basic sans GOCD_USER/GOCD_PASSWORD : ignoré");
        }
        case "cookie" -> {
          if (present(cookie)) {
            String c = cookie.trim().contains("=") ? cookie.trim() : "JSESSIONID=" + cookie.trim();
            out.add(new Auth("cookie", Map.of("Cookie", c)));
          } else System.err.println("--auth cookie sans GOCD_COOKIE : ignoré");
        }
        default -> System.err.println("Mode inconnu : " + m);
      }
    }
    return out;
  }

  static boolean present(String s) {
    return s != null && !s.isBlank();
  }

  /**
   * GoCD versionne chaque API par l'en-tête Accept et répond 404 à une version qu'il ne connaît
   * pas. On essaie d'abord la forme sans version (la plus récente), puis les versions nommées.
   */
  Api api(Auth a, String path, String... versions) {
    List<String> accepts = new ArrayList<>();
    accepts.add("application/vnd.go.cd+json");
    for (String v : versions) accepts.add("application/vnd.go.cd." + v + "+json");
    Api last = null;
    for (String accept : accepts) {
      Map<String, String> h = new LinkedHashMap<>(a.headers());
      h.put("Accept", accept);
      Probe.Resp r = probe.get(base + path, h);
      last = new Api(r, accept);
      if (r.status() != 404 && r.status() != 406) break;
    }
    String[] parts = path.replaceAll("/go/api/", "").replaceAll("\\?.*", "").split("/");
    String key = parts[0].equals("admin") && parts.length > 1 ? "admin/" + parts[1] : parts[0];
    if (last.ok()) acceptSeen.putIfAbsent(key, last.accept());
    return last;
  }

  // ------------------------------------------------------------ instance et dashboard

  void instance(Auth a) {
    Probe.section("Instance");
    Api v = api(a, "/go/api/version", "v1");
    Probe.line(v.resp(), "version", v.ok() ? v.json().path("full_version").asText(v.json().path("version").asText("?")) : "");
  }

  List<JsonNode> dashboard(Auth a) {
    Probe.section("Ce que voit le compte");
    Api dash = api(a, "/go/api/dashboard", "v4", "v3", "v2", "v1");
    List<JsonNode> pipes = new ArrayList<>();
    if (!dash.ok()) {
      Probe.line(dash.resp(), "dashboard", "");
      Probe.Resp cc = probe.get(base + "/go/cctray.xml", a.headers());
      Probe.line(cc, "cctray.xml (repli)", cc.ok() ? cctrayPipelines(cc.body()).size() + " pipelines" : "");
      if (cc.ok())
        for (String n : cctrayPipelines(cc.body())) pipes.add(Probe.JSON.createObjectNode().put("name", n));
      return pipes;
    }
    JsonNode emb = dash.json().path("_embedded");
    JsonNode groups = emb.path("pipeline_groups");
    emb.path("pipelines").forEach(pipes::add);

    int admin = 0;
    List<Double> perGroup = new ArrayList<>();
    Map<String, Integer> groupWords = Probe.counter();
    for (JsonNode g : groups) {
      if (g.path("can_administer").asBoolean(false)) admin++;
      perGroup.add((double) g.path("pipelines").size());
      Probe.platformWords(g.path("name").asText(), groupWords);
    }
    int withRun = 0, fromRepo = 0, operate = 0, adminPipe = 0, noWord = 0;
    long recent = 0;
    Instant since = Instant.now().minus(Duration.ofDays(days));
    Map<String, Integer> pipeWords = Probe.counter();
    Map<String, Integer> stageWords = Probe.counter();
    Set<String> stageNames = new LinkedHashSet<>();
    for (JsonNode p : pipes) {
      if (p.path("from_config_repo").asBoolean(false)) fromRepo++;
      if (p.path("can_operate").asBoolean(false)) operate++;
      if (p.path("can_administer").asBoolean(false)) adminPipe++;
      Map<String, Integer> w = Probe.counter();
      Probe.platformWords(p.path("name").asText(), w);
      if (w.isEmpty()) noWord++;
      w.forEach((k, n) -> pipeWords.merge(k, n, Integer::sum));
      JsonNode inst = p.path("_embedded").path("instances");
      if (inst.size() > 0) {
        withRun++;
        JsonNode last = inst.get(inst.size() - 1);
        Instant at = parseInstant(last.path("scheduled_at"));
        if (at != null && at.isAfter(since)) recent++;
        for (JsonNode s : last.path("_embedded").path("stages")) {
          String sn = s.path("name").asText();
          if (stageNames.add(sn)) Probe.platformWords(sn, stageWords);
        }
      }
    }
    Probe.line("OK", "dashboard", "accepté avec " + dash.accept());
    Probe.info("groupes de pipelines : " + groups.size() + " (administrables par ce compte : " + admin + ")");
    Probe.info("pipelines par groupe : " + Probe.quantiles(perGroup, ""));
    Probe.info("pipelines : " + pipes.size() + ", avec au moins un run : " + withRun + ", run dans les " + days + " j : " + recent);
    Probe.info("définis par un config repo (pipelines-as-code) : " + fromRepo);
    Probe.info("pipelines que ce compte peut lancer : " + operate + ", administrer : " + adminPipe);
    Probe.info("mots de plateforme dans les noms de groupes : " + Probe.tally(groupWords));
    Probe.info("mots de plateforme dans les noms de pipelines : " + Probe.tally(pipeWords));
    Probe.info("pipelines sans aucun de ces mots : " + noWord);
    Probe.info("noms de stages distincts : " + stageNames.size() + ", mots de plateforme : " + Probe.tally(stageWords));
    JsonNode envs = emb.path("environments");
    if (envs.isArray() && envs.size() > 0) Probe.info("environnements dans le dashboard : " + envs.size());
    verdicts.add("Accès : " + pipes.size() + " pipelines visibles, " + recent + " avec un run sur la période.");
    return pipes;
  }

  // ------------------------------------------------------------ configuration

  void configAccess(Auth a, List<JsonNode> visible) {
    Probe.section("Configuration (souvent réservée aux admins)");
    Api groups = api(a, "/go/api/admin/pipeline_groups", "v2", "v1");
    Probe.line(groups.resp(), "admin/pipeline_groups",
        groups.ok() ? groups.json().path("_embedded").path("groups").size() + " groupes" : "");

    Api envs = api(a, "/go/api/admin/environments", "v3", "v2");
    if (envs.ok()) {
      JsonNode list = envs.json().path("_embedded").path("environments");
      Map<String, Integer> words = Probe.counter();
      int inEnv = 0;
      List<String> names = new ArrayList<>();
      for (JsonNode e : list) {
        Probe.platformWords(e.path("name").asText(), words);
        inEnv += e.path("pipelines").size();
        names.add(probe.name("env", e.path("name").asText()));
      }
      Probe.line("OK", "admin/environments", list.size() + " environnements, " + inEnv + " rattachements de pipelines");
      if (!names.isEmpty()) Probe.info("noms : " + String.join(", ", names) + " ; mots de plateforme : " + Probe.tally(words));
      if (list.size() == 0) Probe.info("liste filtrée selon les droits : 0 ne veut pas dire qu'il n'y en a aucun");
    } else Probe.line(envs.resp(), "admin/environments", "");

    Api repos = api(a, "/go/api/admin/config_repos", "v4", "v3", "v2");
    if (repos.ok()) {
      JsonNode list = repos.json().path("_embedded").path("config_repos");
      Map<String, Integer> plugins = Probe.counter();
      int onGitlab = 0;
      for (JsonNode r : list) {
        Probe.inc(plugins, r.path("plugin_id").asText("?"));
        if (isGitlab(r.path("material").path("attributes").path("url").asText())) onGitlab++;
      }
      Probe.line("OK", "admin/config_repos", list.size() + " dépôts de config, dont " + onGitlab + " sur GitLab");
      if (list.size() > 0) Probe.info("plugins : " + Probe.tally(plugins));
      else Probe.info("liste filtrée selon les droits : 0 ne veut pas dire qu'il n'y en a aucun");
    } else Probe.line(repos.resp(), "admin/config_repos", "");

    Api tpl = api(a, "/go/api/admin/templates", "v7", "v6", "v5");
    Probe.line(tpl.resp(), "admin/templates", tpl.ok() ? tpl.json().path("_embedded").path("templates").size() + " templates" : "");

    boolean pipelineConfig = false;
    if (!visible.isEmpty()) {
      String p = visible.get(0).path("name").asText();
      Api cfg = api(a, "/go/api/admin/pipelines/" + Probe.enc(p), "v11", "v10", "v9", "v8", "v7", "v6");
      if (cfg.ok()) {
        Map<String, Integer> types = Probe.counter();
        int gl = 0;
        for (JsonNode m : cfg.json().path("materials")) {
          Probe.inc(types, m.path("type").asText("?"));
          if (isGitlab(m.path("attributes").path("url").asText())) gl++;
        }
        Probe.line("OK", "admin/pipelines/<" + probe.name("pipeline", p) + ">", "matériaux : " + Probe.tally(types) + ", dont GitLab : " + gl);
        pipelineConfig = true;
      } else Probe.line(cfg.resp(), "admin/pipelines/<" + probe.name("pipeline", p) + ">", "");
    }
    // environments et config_repos répondent 200 avec une liste vide à un compte sans droit :
    // seul un 200 sur une API réservée prouve que la configuration est lisible.
    boolean anyAdmin = groups.ok() || tpl.ok() || pipelineConfig;
    verdicts.add("Configuration : " + (anyAdmin
        ? "lisible, le lien pipeline → dépôt GitLab peut se lire dans la config."
        : "non lisible avec ce compte ; le lien vers GitLab devra venir de l'historique (matériaux des runs)."));
  }

  // ------------------------------------------------------------ échantillon

  List<String> chooseSamples(List<JsonNode> visible) {
    if (!pipelines.isEmpty()) return pipelines;
    Set<String> chosen = new LinkedHashSet<>();
    // Un pipeline par type de plateforme, si les noms le permettent, puis les plus récents.
    for (List<String> family : List.of(
        List.of("prod", "prd", "production"),
        List.of("rec", "recette", "rct", "qual", "qualif", "uat", "int", "integration"),
        List.of("dev", "develop", "test"))) {
      for (JsonNode p : visible) {
        Map<String, Integer> w = Probe.counter();
        Probe.platformWords(p.path("name").asText(), w);
        if (hasRun(p) && family.stream().anyMatch(w::containsKey)) {
          chosen.add(p.path("name").asText());
          break;
        }
      }
    }
    visible.stream()
        .filter(GocdProbe::hasRun)
        .sorted((x, y) -> Long.compare(lastRunMillis(y), lastRunMillis(x)))
        .map(p -> p.path("name").asText())
        .forEach(n -> { if (chosen.size() < samples) chosen.add(n); });
    if (chosen.isEmpty() && !visible.isEmpty()) chosen.add(visible.get(0).path("name").asText());
    return chosen.stream().limit(Math.max(samples, 1)).toList();
  }

  static boolean hasRun(JsonNode p) {
    // Sans dashboard (repli cctray), on ne sait pas : on suppose qu'il a tourné.
    return !p.has("_embedded") || p.path("_embedded").path("instances").size() > 0;
  }

  static long lastRunMillis(JsonNode p) {
    JsonNode inst = p.path("_embedded").path("instances");
    if (inst.size() == 0) return 0;
    Instant at = parseInstant(inst.get(inst.size() - 1).path("scheduled_at"));
    return at == null ? 0 : at.toEpochMilli();
  }

  void sample(Auth a, String pipeline) {
    String alias = probe.name("pipeline", pipeline);
    Probe.section("Historique de " + alias);
    Map<String, Integer> words = Probe.counter();
    Probe.platformWords(pipeline, words);
    Probe.info("mots de plateforme dans le nom : " + Probe.tally(words));

    Instant since = Instant.now().minus(Duration.ofDays(days));
    String next = "/go/api/pipelines/" + Probe.enc(pipeline) + "/history?page_size=100";
    List<JsonNode> runs = new ArrayList<>();
    int pages = 0;
    boolean exhausted = false, reachedWindow = false;
    Api first = null;
    while (next != null && pages < maxPages) {
      Api page = api(a, next, "v1");
      if (first == null) first = page;
      if (!page.ok()) break;
      pages++;
      JsonNode list = page.json().path("pipelines");
      for (JsonNode r : list) {
        runs.add(r);
        Instant at = millis(r.path("scheduled_date"));
        if (at != null && at.isBefore(since)) reachedWindow = true;
      }
      String href = page.json().path("_links").path("next").path("href").asText("");
      if (href.isEmpty() || list.size() == 0) {
        exhausted = true;
        next = null;
      } else {
        next = href.replaceFirst("^https?://[^/]+", "");
      }
      if (reachedWindow) next = null;
    }
    if (first == null || !first.ok()) {
      Probe.line(first == null ? "ERR" : first.resp().tag(), "pipelines/<p>/history", first == null ? "" : Probe.why(first.resp()));
      verdicts.add("Historique de " + alias + " : illisible (" + (first == null ? "?" : Probe.why(first.resp())) + ").");
      return;
    }

    List<JsonNode> inWindow = runs.stream().filter(r -> {
      Instant at = millis(r.path("scheduled_date"));
      return at != null && !at.isBefore(since);
    }).toList();
    Instant oldest = runs.stream().map(r -> millis(r.path("scheduled_date"))).filter(x -> x != null).min(Instant::compareTo).orElse(null);
    Probe.line("OK", "pipelines/<p>/history", pages + " page(s), " + runs.size() + " runs lus, " + inWindow.size() + " dans la fenêtre");
    if (oldest != null)
      Probe.info("plus ancien run lu : il y a " + Duration.between(oldest, Instant.now()).toDays() + " j"
          + (exhausted ? " (historique complet)" : reachedWindow ? "" : " (arrêté à --max-pages)"));
    if (!inWindow.isEmpty())
      Probe.info("cadence : %.1f runs par mois".formatted(inWindow.size() * 30.0 / days));

    Map<String, Integer> outcome = Probe.counter(), trigger = Probe.counter(), matTypes = Probe.counter();
    Map<String, Integer> stageResults = Probe.counter(), approval = Probe.counter(), stageWords = Probe.counter();
    Set<String> stages = new LinkedHashSet<>();
    int gitMat = 0, gitOnGitlab = 0, withRevision = 0, reruns = 0;
    List<Double> leadHours = new ArrayList<>();
    List<Double> upstreamLead = new ArrayList<>();
    int upstreamBudget = maxUpstream;
    for (JsonNode r : inWindow) {
      Probe.inc(outcome, runOutcome(r));
      JsonNode bc = r.path("build_cause");
      Probe.inc(trigger, bc.path("trigger_forced").asBoolean(false) ? "forcé (manuel)" : "automatique");
      Instant scheduled = millis(r.path("scheduled_date"));
      Instant earliest = null;
      Instant upstream = null;
      for (JsonNode mr : bc.path("material_revisions")) {
        JsonNode m = mr.path("material");
        String type = m.path("type").asText("?");
        Probe.inc(matTypes, type);
        if (type.equalsIgnoreCase("git")) {
          gitMat++;
          if (isGitlab(urlIn(m.path("description").asText()))) gitOnGitlab++;
        }
        for (JsonNode mod : mr.path("modifications")) {
          if (!mod.path("revision").asText().isEmpty()) withRevision++;
          if (type.equalsIgnoreCase("pipeline") && mr.path("changed").asBoolean(false) && upstreamBudget > 0) {
            int before = upstreamCache.size();
            Instant t = upstreamCommit(a, mod.path("revision").asText(), 0);
            upstreamBudget -= upstreamCache.size() - before;
            if (t != null && (upstream == null || t.isBefore(upstream))) upstream = t;
          }
          if (type.equalsIgnoreCase("git") && mr.path("changed").asBoolean(false)) {
            Instant t = millis(mod.path("modified_time"));
            if (t != null && (earliest == null || t.isBefore(earliest))) earliest = t;
          }
        }
      }
      if (earliest != null && scheduled != null && !scheduled.isBefore(earliest))
        leadHours.add(Duration.between(earliest, scheduled).toMinutes() / 60.0);
      if (upstream != null && scheduled != null && !scheduled.isBefore(upstream))
        upstreamLead.add(Duration.between(upstream, scheduled).toMinutes() / 60.0);
      for (JsonNode s : r.path("stages")) {
        String sn = s.path("name").asText();
        if (stages.add(sn)) Probe.platformWords(sn, stageWords);
        if (!s.path("scheduled").asBoolean(true)) Probe.inc(stageResults, "non lancé");
        else Probe.inc(stageResults, s.path("result").asText("?"));
        Probe.inc(approval, s.path("approval_type").asText("?"));
        if (s.path("counter").asInt(1) > 1) reruns++;
      }
    }
    Probe.info("issue des runs : " + Probe.tally(outcome));
    Probe.info("déclenchement : " + Probe.tally(trigger));
    Probe.info("stages : " + stages.size() + " distincts, mots de plateforme : " + Probe.tally(stageWords));
    Probe.info("résultat des stages : " + Probe.tally(stageResults) + ", relances de stage : " + reruns);
    Probe.info("approbation des stages : " + Probe.tally(approval) + " (manual = déclenché à la main)");
    Probe.info("matériaux : " + Probe.tally(matTypes) + " ; git : " + gitMat + ", dont sur l'hôte GitLab : "
        + (present(gitlabUrl) ? gitOnGitlab : "? (GITLAB_URL absent)"));
    Probe.info("modifications avec révision : " + withRevision);
    Probe.info("délai plus ancien commit → run, git direct : " + Probe.quantiles(leadHours, "h"));
    Probe.info("délai plus ancien commit → run, via les pipelines amont : " + Probe.quantiles(upstreamLead, "h")
        + (upstreamBudget <= 0 ? " (arrêté à --max-upstream)" : ""));

    String verdict = "Historique de " + alias + " : " + inWindow.size() + " runs sur " + days + " j";
    if (!leadHours.isEmpty()) verdict += ", délai commit → run calculable directement";
    else if (!upstreamLead.isEmpty()) verdict += ", délai commit → run calculable en remontant les pipelines amont par l'API";
    else if (matTypes.keySet().stream().anyMatch(k -> k.toLowerCase(Locale.ROOT).contains("pipeline") || k.toLowerCase(Locale.ROOT).contains("dependency")))
      verdict += ", pas de git direct : le commit se retrouve en remontant le pipeline amont";
    verdicts.add(verdict + ".");

    if (!inWindow.isEmpty()) {
      JsonNode latest = inWindow.get(0);
      stageDetail(a, pipeline, latest);
      vsm(a, pipeline, latest.path("counter").asInt());
    }
  }

  final Map<String, Instant> upstreamCache = new LinkedHashMap<>();
  static final Instant NONE = Instant.EPOCH;

  /**
   * Une révision de pipeline amont s'écrit « nom/compteur/stage/compteur ». On lit ce run, on prend
   * ses commits git, et on remonte encore si lui-même dépend d'un pipeline. Borne basse : un
   * déploiement qui saute des builds embarque aussi les commits des builds sautés.
   */
  Instant upstreamCommit(Auth a, String revision, int depth) {
    String[] parts = revision.split("/");
    if (parts.length < 2 || depth > 4) return null;
    String key = parts[0] + "/" + parts[1];
    Instant cached = upstreamCache.get(key);
    if (cached != null) return cached == NONE ? null : cached;
    upstreamCache.put(key, NONE);
    Api r = api(a, "/go/api/pipelines/" + Probe.enc(parts[0]) + "/" + Probe.enc(parts[1]), "v1");
    if (!r.ok()) return null;
    Instant earliest = null;
    for (JsonNode mr : r.json().path("build_cause").path("material_revisions")) {
      String type = mr.path("material").path("type").asText();
      if (!mr.path("changed").asBoolean(false)) continue;
      for (JsonNode mod : mr.path("modifications")) {
        Instant t = type.equalsIgnoreCase("git") ? millis(mod.path("modified_time"))
            : type.equalsIgnoreCase("pipeline") ? upstreamCommit(a, mod.path("revision").asText(), depth + 1) : null;
        if (t != null && (earliest == null || t.isBefore(earliest))) earliest = t;
      }
    }
    upstreamCache.put(key, earliest == null ? NONE : earliest);
    return earliest;
  }

  /** Les durées des jobs ne sont pas dans l'historique : on regarde si l'instance de stage les donne. */
  void stageDetail(Auth a, String pipeline, JsonNode run) {
    JsonNode stage = null;
    for (JsonNode s : run.path("stages")) if (s.path("scheduled").asBoolean(true)) { stage = s; }
    if (stage == null) return;
    String path = "/go/api/stages/" + Probe.enc(pipeline) + "/" + run.path("counter").asInt() + "/"
        + Probe.enc(stage.path("name").asText()) + "/" + stage.path("counter").asText("1");
    Api st = api(a, path, "v3", "v2", "v1");
    if (!st.ok()) {
      Probe.line(st.resp(), "stages/<p>/<n>/<s>/<n>", "");
      return;
    }
    int transitions = 0;
    List<Double> minutes = new ArrayList<>();
    for (JsonNode j : st.json().path("jobs")) {
      Instant start = null, end = null;
      for (JsonNode t : j.path("job_state_transitions")) {
        transitions++;
        Instant at = millis(t.path("state_change_time"));
        String state = t.path("state").asText();
        if (state.equals("Scheduled") || state.equals("Assigned")) { if (start == null) start = at; }
        if (state.equals("Completed")) end = at;
      }
      if (start != null && end != null) minutes.add(Duration.between(start, end).toSeconds() / 60.0);
    }
    Probe.line("OK", "stages/<p>/<n>/<s>/<n>", "transitions de jobs : " + transitions + ", durée des jobs (planifié → terminé) : " + Probe.quantiles(minutes, "min"));
  }

  /** La value stream map relie un pipeline de déploiement aux commits via ses pipelines amont. */
  void vsm(Auth a, String pipeline, int counter) {
    Auth via = session != null ? session : a;
    Probe.Resp r = probe.get(base + "/go/pipelines/value_stream_map/" + Probe.enc(pipeline) + "/" + counter + ".json",
        merge(via.headers(), "Accept", "application/json"));
    if (!r.ok()) {
      Probe.line(r, "value_stream_map (interne)", "");
      if (session == null) Probe.info("page interne : elle n'accepte qu'un cookie de session (GOCD_COOKIE), pas un jeton");
      return;
    }
    Map<String, Integer> types = Probe.counter();
    int revisions = 0;
    for (JsonNode level : r.json().path("levels"))
      for (JsonNode n : level.path("nodes")) {
        Probe.inc(types, n.path("node_type").asText("?"));
        for (JsonNode mr : n.path("material_revisions")) revisions += mr.path("modifications").size();
      }
    Probe.line("OK", "value_stream_map (interne)", "nœuds : " + Probe.tally(types) + ", modifications git : " + revisions);
  }

  void feeds(Auth a) {
    Probe.section("Flux");
    Probe.Resp cc = probe.get(base + "/go/cctray.xml", a.headers());
    Probe.line(cc, "cctray.xml", cc.ok() ? cctrayPipelines(cc.body()).size() + " pipelines (peut être vide hors admin : ne pas s'y fier)" : "");
    Probe.Resp feed = probe.get(base + "/go/api/feed/pipelines.xml", merge(a.headers(), "Accept", "application/xml"));
    Probe.line(feed, "api/feed/pipelines.xml", feed.ok() ? "lisible" : "");
  }

  void footer() {
    Probe.section("Appels");
    Probe.info(probe.calls + " requêtes GET.");
    if (!acceptSeen.isEmpty()) {
      Probe.info("En-têtes Accept acceptés :");
      acceptSeen.forEach((k, v) -> Probe.info("  " + k + " → " + v));
    }
  }

  // ------------------------------------------------------------ outils

  static String runOutcome(JsonNode run) {
    boolean failed = false, cancelled = false, running = false, all = true;
    for (JsonNode s : run.path("stages")) {
      if (!s.path("scheduled").asBoolean(true)) continue;
      String res = s.path("result").asText("");
      switch (res) {
        case "Failed" -> failed = true;
        case "Cancelled" -> cancelled = true;
        case "Passed" -> {}
        default -> running = true;
      }
      if (!res.equals("Passed")) all = false;
    }
    if (failed) return "échec";
    if (cancelled) return "annulé";
    if (running) return "en cours/inconnu";
    return all ? "succès" : "?";
  }

  boolean isGitlab(String repoUrl) {
    if (!present(gitlabUrl) || !present(repoUrl)) return false;
    String gl = Probe.host(gitlabUrl);
    String h = Probe.host(repoUrl);
    if (h.isEmpty()) {
      // git@hote:groupe/projet.git
      Matcher m = Pattern.compile("^[^@]+@([^:/]+)[:/]").matcher(repoUrl.trim());
      if (m.find()) h = m.group(1).toLowerCase(Locale.ROOT);
    }
    return !h.isEmpty() && (h.equals(gl) || h.endsWith("." + gl) || gl.endsWith("." + h));
  }

  /** La description d'un matériau git ressemble à « URL: https://…, Branch: main ». */
  static String urlIn(String description) {
    Matcher m = Pattern.compile("(https?://\\S+?|[\\w.-]+@[\\w.-]+:\\S+?)(,|\\s|$)").matcher(description);
    return m.find() ? m.group(1) : "";
  }

  static List<String> cctrayPipelines(String xml) {
    Set<String> names = new LinkedHashSet<>();
    Matcher m = Pattern.compile("<Project[^>]*\\sname=\"([^\"]+)\"").matcher(xml);
    while (m.find()) names.add(m.group(1).split(" :: ")[0]);
    return new ArrayList<>(names);
  }

  static Instant millis(JsonNode n) {
    if (n.isNumber()) return Instant.ofEpochMilli(n.asLong());
    return parseInstant(n);
  }

  static Instant parseInstant(JsonNode n) {
    if (n.isNumber()) return Instant.ofEpochMilli(n.asLong());
    String s = n.asText("");
    if (s.isEmpty()) return null;
    try {
      return OffsetDateTime.parse(s).toInstant();
    } catch (RuntimeException e) {
      try {
        return Instant.parse(s);
      } catch (RuntimeException e2) {
        return null;
      }
    }
  }

  static Map<String, String> merge(Map<String, String> h, String k, String v) {
    Map<String, String> m = new LinkedHashMap<>(h);
    m.put(k, v);
    return m;
  }
}
