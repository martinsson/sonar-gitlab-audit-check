///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Inventaire des technos frontend de toute une instance GitLab.
 *
 * Pour chaque projet non archivé : les langages calculés par GitLab donnent la part
 * de code front. Si elle est notable, on cherche les package.json du dépôt jusqu'à
 * une profondeur donnée, sinon seulement celui de la racine. Les package.json disent la techno
 * (Vue 2 / Vue 3, React, Angular...). Puis l'activité sur N jours (commits, MR,
 * lignes changées). La colonne a_verifier signale les cas que ces règles ne
 * savent pas trancher.
 *
 * Lecture seule, que des GET. Autonome : ne dépend d'aucun autre fichier du dépôt.
 *
 *   export GITLAB_URL=https://gitlab.example.com GITLAB_TOKEN=glpat-xxxx
 *   jbang FrontendInventory.java [--out frontend.csv] [--days 90] [--limit N] [--threads 10] [--seuil-front 5]
 *                                [--profondeur 3]
 */
public class FrontendInventory {

    static final ObjectMapper JSON = new ObjectMapper();

    // Ordre = priorité pour la colonne "techno" quand plusieurs frameworks coexistent.
    static final Map<String, String> FRAMEWORKS = new LinkedHashMap<>();
    static {
        FRAMEWORKS.put("vue", "Vue");
        FRAMEWORKS.put("react", "React");
        FRAMEWORKS.put("@angular/core", "Angular");
        FRAMEWORKS.put("angular", "AngularJS");
        FRAMEWORKS.put("svelte", "Svelte");
        FRAMEWORKS.put("jquery", "jQuery");
        FRAMEWORKS.put("htmx", "htmx");
        FRAMEWORKS.put("alpinejs", "AlpineJS");
        FRAMEWORKS.put("react-native", "React Native");
        FRAMEWORKS.put("@ionic/core", "Ionic");
        FRAMEWORKS.put("preact", "Preact");
    }
    // Méta-frameworks notés à part, ils ne changent pas la techno de base.
    static final List<String> META = List.of("nuxt", "next", "@vue/cli-service", "vite", "react-scripts", "typescript");
    // Langages GitLab (Linguist) qui comptent comme du front.
    static final Set<String> FRONT_LANGUAGES = Set.of("JavaScript", "TypeScript", "TSX", "JSX", "Vue", "Svelte",
            "HTML", "CSS", "SCSS", "Sass", "Less", "Stylus");
    // Dossiers où l'on ne descend pas : dépendances commitées, sorties de build, sources Java.
    // Leurs package.json ne disent rien du projet, et ce sont eux qui coûtent des appels.
    static final Pattern PRUNED = Pattern.compile(
            "(^|/)(node_modules|bower_components|vendor|target|build|dist)$|(^|/)src/(main/java|test)$");
    // Garde-fou : appels /repository/tree au plus par projet, quelle que soit la profondeur.
    static final int MAX_TREE_CALLS = 200;

    // "Nj" est remplacé par la fenêtre, ex. commits_90j.
    static final List<String> COLUMNS = List.of("id", "projet", "url", "branche", "derniere_activite",
            "commits_Nj", "mr_Nj", "lignes_ajoutees_Nj", "lignes_supprimees_Nj", "commits_total",
            "part_front_pct", "langages",
            "package_json", "vue", "react", "angular", "autres_front", "outillage",
            "taille_depot_octets", "estimation_lignes",
            "techno", "a_verifier", "erreur");

    final String baseUrl;
    final String token;
    final double frontThreshold;
    final int maxDepth;
    final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    FrontendInventory(String baseUrl, String token, double frontThreshold, int maxDepth) {
        this.baseUrl = baseUrl.replaceAll("/+$", "") + "/api/v4";
        this.token = token;
        this.frontThreshold = frontThreshold;
        this.maxDepth = maxDepth;
    }

    public static void main(String[] args) throws Exception {
        String out = "frontend.csv";
        int days = 90;
        int limit = Integer.MAX_VALUE;
        int threads = Runtime.getRuntime().availableProcessors();
        double frontThreshold = 5;
        int maxDepth = 3;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--out" -> out = args[++i];
                case "--days" -> days = Integer.parseInt(args[++i]);
                case "--limit" -> limit = Integer.parseInt(args[++i]);
                case "--threads" -> threads = Integer.parseInt(args[++i]);
                case "--seuil-front" -> frontThreshold = Double.parseDouble(args[++i]);
                case "--profondeur" -> maxDepth = Integer.parseInt(args[++i]);
                case "-h", "--help" -> {
                    System.out.println("Usage: FrontendInventory [--out frontend.csv] [--days 90] [--limit N] [--threads N]"
                            + " [--seuil-front 5] [--profondeur 3]\n"
                            + "  --seuil-front : part de code front (%) à partir de laquelle on cherche les package.json"
                            + " dans les sous-dossiers\n"
                            + "  --profondeur : profondeur maximale des dossiers où chercher un package.json"
                            + " (0 = racine seule)\n"
                            + "Env: GITLAB_URL, GITLAB_TOKEN");
                    return;
                }
                default -> throw new IllegalArgumentException("Option inconnue : " + args[i]);
            }
        }
        String url = System.getenv("GITLAB_URL");
        String token = System.getenv("GITLAB_TOKEN");
        if (url == null || url.isBlank()) {
            System.err.println("GITLAB_URL manquant");
            System.exit(2);
        }
        new FrontendInventory(url, token, frontThreshold, maxDepth).run(Path.of(out), days, limit, threads);
    }

    void run(Path requested, int days, int limit, int threads) throws Exception {
        String since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String[] header = COLUMNS.stream().map(c -> c.replace("Nj", days + "j")).toArray(String[]::new);

        // Chaque ligne est écrite dès que son projet est fini : un arrêt en cours de route
        // laisse un CSV partiel mais lisible. Les lignes restent aussi en mémoire pour les rapports de fin.
        List<String[]> rows = new ArrayList<>();
        Path out = freePath(requested);
        try (Writer csv = Files.newBufferedWriter(out, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            csv.write("﻿");
            writeRow(csv, header);
            System.err.println("Écriture au fil de l'eau dans : " + out.toAbsolutePath());
            run(csv, rows, since, limit, threads);
        }
        System.err.println("Écrit : " + out.toAbsolutePath());

        Path front = freePath(sibling(out, "-front.csv"));
        try (Writer csv = Files.newBufferedWriter(front, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            csv.write("﻿");
            writeRow(csv, header);
            for (String[] r : rows) {
                if (frontShare(r) >= frontThreshold) writeRow(csv, r);
            }
        }
        System.err.println("Écrit : " + front.toAbsolutePath());

        Path html = freePath(sibling(out, "-rapport.html"));
        Files.writeString(html, report(rows, days), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.err.println("Écrit : " + html.toAbsolutePath());
    }

    void run(Writer csv, List<String[]> rows, String since, int limit, int threads) throws Exception {
        ThreadFactory tf = r -> {
            Thread t = new Thread(r);
            t.setName("inventory-worker");
            t.setDaemon(true);
            return t;
        };
        AtomicInteger listed = new AtomicInteger();
        AtomicInteger done = new AtomicInteger();
        AtomicBoolean listing = new AtomicBoolean(true);
        try (ExecutorService executor = Executors.newFixedThreadPool(threads, tf)) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            // Chaque page de projets part au traitement dès qu'elle arrive : sur une grosse instance,
            // lister tous les projets d'abord laissait plusieurs minutes sans rien écrire.
            String next = baseUrl + "/projects?archived=false&statistics=true&simple=false"
                    + "&per_page=100&order_by=id&sort=asc&pagination=keyset";
            while (next != null && listed.get() < limit) {
                HttpResponse<String> resp = get(next);
                if (resp.statusCode() != 200) throw new IOException("GET projects → " + resp.statusCode() + " " + resp.body());
                for (JsonNode p : JSON.readTree(resp.body())) {
                    if (listed.get() >= limit) break;
                    listed.incrementAndGet();
                    // Ordre d'achèvement, pas d'id : un projet lent ne retient pas les lignes déjà prêtes.
                    futures.add(CompletableFuture.supplyAsync(() -> project(p, since), executor).thenAccept(row -> {
                        synchronized (csv) {
                            try {
                                writeRow(csv, row);
                                rows.add(row);
                                csv.flush();
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                            progress(done.incrementAndGet(), listed.get(), listing.get());
                        }
                    }));
                }
                next = nextLink(resp);
                synchronized (csv) {
                    listing.set(next != null && listed.get() < limit);
                    progress(done.get(), listed.get(), listing.get());
                }
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }
        System.err.println();
    }

    /** « 120/1300 projets (liste en cours) » : le total grandit tant que des pages arrivent. */
    static void progress(int done, int listed, boolean listing) {
        System.err.printf("\r%d/%d projets%s", done, listed, listing ? " (liste en cours)  " : "                  ");
    }

    /** frontend.csv s'il est libre, sinon frontend-1.csv, frontend-2.csv... : on n'écrase jamais un résultat. */
    static Path freePath(Path requested) {
        String name = requested.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        Path candidate = requested;
        for (int i = 1; Files.exists(candidate); i++) {
            candidate = requested.resolveSibling(base + "-" + i + ext);
        }
        return candidate;
    }

    String[] project(JsonNode p, String since) {
        long id = p.path("id").asLong();
        String branch = p.path("default_branch").asText("");
        JsonNode stats = p.path("statistics");
        Map<String, String> row = new LinkedHashMap<>();
        row.put("id", String.valueOf(id));
        row.put("projet", p.path("path_with_namespace").asText());
        row.put("url", p.path("web_url").asText());
        row.put("branche", branch);
        row.put("derniere_activite", p.path("last_activity_at").asText(""));
        row.put("commits_total", stats.has("commit_count") ? stats.path("commit_count").asText() : "");
        long repoSize = stats.has("repository_size") ? stats.path("repository_size").asLong() : 0;
        row.put("taille_depot_octets", String.valueOf(repoSize));
        row.put("estimation_lignes", repoSize > 0 ? String.valueOf(repoSize / 40) : "");
        List<String> errors = new ArrayList<>();
        if (branch.isEmpty()) {
            row.put("techno", "aucun");
            errors.add("dépôt vide");
        } else {
            // Séparés : un package.json illisible n'empêche pas de mesurer l'activité.
            try {
                front(id, branch, row);
            } catch (Exception e) {
                errors.add(e.getMessage());
            }
            try {
                activity(id, branch, since, row);
            } catch (Exception e) {
                errors.add(e.getMessage());
            }
        }
        row.put("erreur", String.join(", ", errors));
        return COLUMNS.stream().map(c -> row.getOrDefault(c, "")).toArray(String[]::new);
    }

    /** Remplit langages / techno / package_json / vue / react / angular / autres_front / outillage / a_verifier. */
    void front(long id, String branch, Map<String, String> row) throws Exception {
        List<String> toCheck = new ArrayList<>();
        Map<String, Double> languages = languages(id);
        double frontShare = languages.entrySet().stream()
                .filter(e -> FRONT_LANGUAGES.contains(e.getKey()))
                .mapToDouble(Map.Entry::getValue).sum();
        row.put("langages", String.join(", ", languages.entrySet().stream()
                .map(e -> e.getKey() + " " + String.format("%.1f", e.getValue())).toList()));
        row.put("part_front_pct", languages.isEmpty() ? "" : String.format("%.1f", frontShare));

        List<String> warnings = new ArrayList<>();
        List<String> paths;
        if (frontShare >= frontThreshold) {
            paths = packageJsonPaths(id, branch, warnings);
        } else {
            // Peu ou pas de front : on regarde seulement la racine, un appel.
            paths = List.of("package.json");
        }

        // Pour chaque framework, ses plages de version dans l'ordre où on les trouve.
        Map<String, Set<String>> versions = new LinkedHashMap<>();
        Map<String, Set<String>> tooling = new LinkedHashMap<>();
        List<String> found = new ArrayList<>();
        if (languages.isEmpty()) warnings.add("langages non calculés par GitLab");
        for (String path : paths) {
            HttpResponse<String> resp = get(baseUrl + "/projects/" + id + "/repository/files/" + enc(path)
                    + "/raw?ref=" + enc(branch));
            if (resp.statusCode() == 404) continue;
            if (resp.statusCode() != 200) throw new IOException(path + " → " + resp.statusCode());
            found.add(path);
            JsonNode pkg;
            try {
                pkg = JSON.readTree(resp.body());
            } catch (IOException e) {
                warnings.add(path + " illisible");
                continue;
            }
            Map<String, String> deps = new LinkedHashMap<>();
            for (String section : List.of("dependencies", "devDependencies", "peerDependencies")) {
                pkg.path(section).fields().forEachRemaining(f -> deps.putIfAbsent(f.getKey(), f.getValue().asText()));
            }
            for (String fw : FRAMEWORKS.keySet()) {
                if (deps.containsKey(fw)) versions.computeIfAbsent(fw, k -> new LinkedHashSet<>()).add(deps.get(fw));
            }
            for (String m : META) {
                if (deps.containsKey(m)) tooling.computeIfAbsent(m, k -> new LinkedHashSet<>()).add(deps.get(m));
            }
        }
        row.put("package_json", String.join(", ", found));

        Set<String> techno = new LinkedHashSet<>();
        List<String> others = new ArrayList<>();
        List<String> angular = new ArrayList<>();
        for (var fw : FRAMEWORKS.entrySet()) {
            Set<String> ranges = versions.get(fw.getKey());
            if (ranges == null) continue;
            String label = fw.getValue();
            String joined = String.join(" / ", ranges);
            switch (label) {
                case "Vue" -> row.put("vue", joined);
                case "React" -> row.put("react", joined);
                case "Angular", "AngularJS" -> angular.add(joined);
                default -> others.add(label + " " + joined);
            }
            Set<String> majors = new LinkedHashSet<>();
            for (String r : ranges) majors.add(major(r));
            if (label.equals("Vue") || label.equals("Angular")) {
                for (String m : majors) techno.add(label + " " + m);
            } else {
                techno.add(label);
            }
            if (majors.size() > 1) warnings.add("plusieurs versions de " + label);
        }
        row.put("angular", String.join(" / ", angular));
        row.put("autres_front", String.join(", ", others));
        row.put("outillage", String.join(", ", tooling.entrySet().stream()
                .map(e -> e.getKey() + " " + String.join(" / ", e.getValue())).toList()));

        if (found.isEmpty()) {
            row.put("techno", "aucun");
        } else if (techno.isEmpty()) {
            row.put("techno", "node sans framework UI");
        } else {
            row.put("techno", String.join(" + ", techno));
        }

        // Ce que les règles ci-dessus ne savent pas trancher : à regarder à la main.
        if (frontShare >= frontThreshold && found.isEmpty()) {
            warnings.add("front sans package.json");
        } else if (frontShare >= frontThreshold && techno.isEmpty()) {
            warnings.add("front sans framework déclaré");
        }
        if (languages.containsKey("Vue") && !versions.containsKey("vue")) {
            warnings.add("fichiers .vue sans dépendance vue");
        }
        row.put("a_verifier", String.join(", ", warnings));
    }

    /** Langage → % du code (octets), tel que GitLab le calcule. Vide si pas encore calculé ou illisible. */
    Map<String, Double> languages(long id) throws Exception {
        Map<String, Double> languages = new LinkedHashMap<>();
        HttpResponse<String> resp = get(baseUrl + "/projects/" + id + "/languages");
        if (resp.statusCode() != 200) return languages;
        JSON.readTree(resp.body()).fields().forEachRemaining(f -> languages.put(f.getKey(), f.getValue().asDouble()));
        return languages;
    }

    /**
     * Les package.json de la branche jusqu'à maxDepth (dossier racine = 0), parcours en largeur
     * un dossier à la fois sans descendre dans PRUNED. Au-delà de MAX_TREE_CALLS appels on
     * s'arrête et on le signale.
     */
    List<String> packageJsonPaths(long id, String branch, List<String> warnings) throws Exception {
        List<String> paths = new ArrayList<>();
        ArrayDeque<String> dirs = new ArrayDeque<>(List.of(""));
        int calls = 0;
        while (!dirs.isEmpty()) {
            String dir = dirs.poll();
            int depth = dir.isEmpty() ? 0 : dir.split("/").length;
            String next = baseUrl + "/projects/" + id + "/repository/tree?ref=" + enc(branch)
                    + (dir.isEmpty() ? "" : "&path=" + enc(dir)) + "&per_page=100&pagination=keyset";
            while (next != null) {
                if (++calls > MAX_TREE_CALLS) {
                    warnings.add("arborescence tronquée");
                    return paths;
                }
                HttpResponse<String> resp = get(next);
                if (resp.statusCode() != 200) throw new IOException("arborescence " + dir + " → " + resp.statusCode());
                for (JsonNode e : JSON.readTree(resp.body())) {
                    String path = e.path("path").asText();
                    switch (e.path("type").asText()) {
                        case "blob" -> {
                            if (e.path("name").asText().equals("package.json")) paths.add(path);
                        }
                        case "tree" -> {
                            if (depth < maxDepth && !PRUNED.matcher(path).find()) dirs.add(path);
                        }
                        default -> { }
                    }
                }
                next = nextPage(next, resp);
            }
        }
        return paths;
    }

    /** Remplit commits / MR / lignes ajoutées / supprimées sur la fenêtre. */
    void activity(long id, String branch, String since, Map<String, String> row) throws Exception {
        int commits = 0;
        long added = 0, deleted = 0;
        String next = baseUrl + "/projects/" + id + "/repository/commits?ref_name=" + enc(branch)
                + "&since=" + enc(since) + "&with_stats=true&per_page=100";
        while (next != null) {
            HttpResponse<String> resp = get(next);
            if (resp.statusCode() != 200) throw new IOException("commits → " + resp.statusCode());
            for (JsonNode c : JSON.readTree(resp.body())) {
                commits++;
                // Les merges rejouent les lignes de leurs branches : on ne les compte pas deux fois.
                if (c.path("parent_ids").size() > 1) continue;
                added += c.path("stats").path("additions").asLong();
                deleted += c.path("stats").path("deletions").asLong();
            }
            next = nextPage(next, resp);
        }
        row.put("commits_Nj", String.valueOf(commits));
        row.put("lignes_ajoutees_Nj", String.valueOf(added));
        row.put("lignes_supprimees_Nj", String.valueOf(deleted));

        HttpResponse<String> mrs = get(baseUrl + "/projects/" + id + "/merge_requests?state=all&created_after="
                + enc(since) + "&per_page=1");
        if (mrs.statusCode() == 200) {
            row.put("mr_Nj", mrs.headers().firstValue("X-Total")
                    .orElseGet(() -> String.valueOf(countAll(baseUrl + "/projects/" + id
                            + "/merge_requests?state=all&created_after=" + enc(since) + "&per_page=100"))));
        }
    }

    /** Pour les grosses listes où GitLab omet X-Total. */
    int countAll(String url) {
        int n = 0;
        try {
            String next = url;
            while (next != null) {
                HttpResponse<String> resp = get(next);
                if (resp.statusCode() != 200) break;
                n += JSON.readTree(resp.body()).size();
                next = nextPage(next, resp);
            }
        } catch (Exception ignored) {
        }
        return n;
    }

    static final Pattern MAJOR = Pattern.compile("(\\d+)");

    /** "^2.6.14" → "2", "~3.4" → "3", "latest" → "?" */
    static String major(String range) {
        Matcher m = MAJOR.matcher(range);
        return m.find() ? m.group(1) : "?";
    }

    HttpResponse<String> get(String url) throws IOException, InterruptedException {
        for (int attempt = 0; ; attempt++) {
            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET();
            if (token != null && !token.isBlank()) rb.header("PRIVATE-TOKEN", token);
            HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            if ((resp.statusCode() == 429 || resp.statusCode() >= 500) && attempt < 4) {
                long wait = resp.headers().firstValue("Retry-After").map(Long::parseLong).orElse(2L << attempt);
                Thread.sleep(wait * 1000);
                continue;
            }
            return resp;
        }
    }

    static final Pattern NEXT_LINK = Pattern.compile("<([^>]+)>;\\s*rel=\"next\"");

    static String nextLink(HttpResponse<?> resp) {
        String link = resp.headers().firstValue("Link").orElse("");
        Matcher m = NEXT_LINK.matcher(link);
        return m.find() ? m.group(1) : null;
    }

    /** Pagination par page : X-Next-Page, sinon l'en-tête Link. */
    static String nextPage(String current, HttpResponse<?> resp) {
        String np = resp.headers().firstValue("X-Next-Page").orElse("");
        if (!np.isBlank()) {
            String base = current.replaceAll("([?&])page=\\d+&?", "$1").replaceAll("[?&]$", "");
            return base + (base.contains("?") ? "&" : "?") + "page=" + np;
        }
        return nextLink(resp);
    }

    static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Point-virgule + CRLF (le BOM UTF-8 est écrit une fois en tête) : s'ouvre tel quel dans Excel. */
    static void writeRow(Writer csv, String[] r) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r.length; i++) {
            if (i > 0) sb.append(';');
            String v = r[i] == null ? "" : r[i];
            if (v.contains(";") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
                v = "\"" + v.replace("\"", "\"\"") + "\"";
            }
            sb.append(v);
        }
        sb.append("\r\n");
        csv.write(sb.toString());
    }

    /** frontend.csv → frontend-front.csv : les rapports de fin portent le nom du CSV principal. */
    static Path sibling(Path out, String suffix) {
        String name = out.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return out.resolveSibling((dot > 0 ? name.substring(0, dot) : name) + suffix);
    }

    static String col(String[] r, String column) {
        return r[COLUMNS.indexOf(column)];
    }

    static long number(String[] r, String column) {
        String v = col(r, column);
        return v.isEmpty() ? 0 : Long.parseLong(v);
    }

    /**
     * part_front_pct relue depuis la ligne, -1 si GitLab n'a pas calculé les langages. Elle est écrite
     * dans la locale de la machine pour Excel : « 12,5 » en français, d'où la virgule acceptée.
     */
    static double frontShare(String[] r) {
        String v = col(r, "part_front_pct");
        return v.isEmpty() ? -1 : Double.parseDouble(v.replace(',', '.'));
    }

    /** Une ligne du rapport HTML : ce que totalisent les projets d'une techno. */
    static final class Tally {
        int projects, active;
        long lines, activeLines, changed;

        void add(String[] r) {
            boolean isActive = number(r, "commits_Nj") > 0;
            long l = number(r, "estimation_lignes");
            projects++;
            lines += l;
            changed += number(r, "lignes_ajoutees_Nj") + number(r, "lignes_supprimees_Nj");
            if (isActive) {
                active++;
                activeLines += l;
            }
        }
    }

    /**
     * Totaux par valeur de la colonne techno (« Vue 2 + React » compte pour Vue 2 et pour React)
     * et par outil de la colonne outillage. Un projet est actif s'il a au moins un commit sur la fenêtre.
     */
    static String report(List<String[]> rows, int days) {
        Tally all = new Tally();
        Map<String, Tally> byTechno = new LinkedHashMap<>();
        Map<String, Tally> byTool = new LinkedHashMap<>();
        for (String[] r : rows) {
            all.add(r);
            for (String t : col(r, "techno").split(" \\+ ")) {
                if (!t.isBlank()) byTechno.computeIfAbsent(t, k -> new Tally()).add(r);
            }
            // « vite ^4.0.0 / ^5.1.0, typescript ~5.2.0 » → vite, typescript
            for (String t : col(r, "outillage").split(", ")) {
                if (!t.isBlank()) byTool.computeIfAbsent(t.split(" ")[0], k -> new Tally()).add(r);
            }
        }
        StringBuilder sb = new StringBuilder("""
                <!DOCTYPE html>
                <html lang="fr">
                <head>
                <meta charset="UTF-8">
                <title>Inventaire frontend</title>
                <style>
                  body { font-family: system-ui, sans-serif; margin: 24px; color: #222; }
                  table { border-collapse: collapse; margin: 12px 0 28px; }
                  th, td { border: 1px solid #ccc; padding: 6px 10px; }
                  th { background: #f0f0f0; text-align: left; }
                  td { text-align: right; font-variant-numeric: tabular-nums; }
                  td:first-child { text-align: left; }
                  tr.total td { font-weight: bold; }
                  p.note { color: #666; font-size: 0.9em; max-width: 60em; }
                </style>
                </head>
                <body>
                <h1>Inventaire frontend</h1>
                """);
        sb.append("<h2>Par techno</h2>\n");
        table(sb, "Techno", byTechno, all, days);
        sb.append("<h2>Par outillage</h2>\n");
        table(sb, "Outil", byTool, all, days);
        sb.append("<p class=\"note\">Actif : au moins un commit sur la branche par défaut dans les ").append(days)
                .append(" derniers jours. Un projet à plusieurs technos compte dans chacune, la ligne Total ne")
                .append(" le compte qu'une fois. Lignes estimées : taille du dépôt GitLab / 40, historique git")
                .append(" et fichiers binaires compris, donc un ordre de grandeur, pas un comptage. Lignes")
                .append(" modifiées : ajoutées + supprimées sur la fenêtre, hors commits de merge.</p>\n");
        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    static void table(StringBuilder sb, String first, Map<String, Tally> rows, Tally total, int days) {
        sb.append("<table>\n<tr><th>").append(first).append("</th><th>Projets</th><th>Projets actifs</th>")
                .append("<th>Lignes estimées</th><th>Lignes estimées (actifs)</th><th>Lignes modifiées ")
                .append(days).append("j</th></tr>\n");
        rows.entrySet().stream()
                .sorted(Map.Entry.<String, Tally>comparingByValue(Comparator.comparingInt(t -> -t.projects))
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> tableRow(sb, "", e.getKey(), e.getValue()));
        tableRow(sb, " class=\"total\"", "Total", total);
        sb.append("</table>\n");
    }

    static void tableRow(StringBuilder sb, String attributes, String name, Tally t) {
        sb.append("<tr").append(attributes).append("><td>").append(html(name)).append("</td>");
        for (long v : new long[] {t.projects, t.active, t.lines, t.activeLines, t.changed}) {
            sb.append("<td>").append(String.format("%,d", v)).append("</td>");
        }
        sb.append("</tr>\n");
    }

    static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
