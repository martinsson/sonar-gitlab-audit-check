///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inventaire des technos frontend de toute une instance GitLab.
 *
 * Pour chaque projet non archivé : le package.json à la racine de la branche par
 * défaut dit la techno (Vue 2 / Vue 3, React, Angular...), puis l'activité sur
 * N jours (commits, MR, lignes changées) et la taille du dépôt.
 *
 * Lecture seule, que des GET. Autonome : ne dépend d'aucun autre fichier du dépôt.
 *
 *   export GITLAB_URL=https://gitlab.example.com GITLAB_TOKEN=glpat-xxxx
 *   jbang FrontendInventory.java [--out frontend.csv] [--days 90] [--limit N]
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
    }
    // Méta-frameworks notés à part, ils ne changent pas la techno de base.
    static final List<String> META = List.of("nuxt", "next", "@vue/cli-service", "vite", "react-scripts", "typescript");

    final String baseUrl;
    final String token;
    final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    FrontendInventory(String baseUrl, String token) {
        this.baseUrl = baseUrl.replaceAll("/+$", "") + "/api/v4";
        this.token = token;
    }

    public static void main(String[] args) throws Exception {
        String out = "frontend.csv";
        int days = 90;
        int limit = Integer.MAX_VALUE;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--out" -> out = args[++i];
                case "--days" -> days = Integer.parseInt(args[++i]);
                case "--limit" -> limit = Integer.parseInt(args[++i]);
                case "-h", "--help" -> {
                    System.out.println("Usage: FrontendInventory [--out frontend.csv] [--days 90] [--limit N]\n"
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
        new FrontendInventory(url, token).run(Path.of(out), days, limit);
    }

    void run(Path out, int days, int limit) throws Exception {
        String since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"id", "projet", "url", "branche", "derniere_activite",
                "techno", "vue", "react", "angular", "autres_front", "outillage",
                "commits_" + days + "j", "mr_" + days + "j", "lignes_ajoutees_" + days + "j",
                "lignes_supprimees_" + days + "j", "taille_depot_mo", "commits_total", "erreur"});

        int n = 0;
        String next = baseUrl + "/projects?archived=false&statistics=true&simple=false"
                + "&per_page=100&order_by=id&sort=asc&pagination=keyset";
        while (next != null && n < limit) {
            HttpResponse<String> resp = get(next);
            if (resp.statusCode() != 200) throw new IOException("GET projects → " + resp.statusCode() + " " + resp.body());
            for (JsonNode p : JSON.readTree(resp.body())) {
                if (n++ >= limit) break;
                rows.add(project(p, since));
                System.err.printf("\r%d projets", n);
            }
            next = nextLink(resp);
        }
        System.err.println();

        writeCsv(out, rows);
        System.err.println("Écrit : " + out.toAbsolutePath());
    }

    String[] project(JsonNode p, String since) {
        long id = p.path("id").asLong();
        String branch = p.path("default_branch").asText("");
        JsonNode stats = p.path("statistics");
        String[] row = {
                String.valueOf(id), p.path("path_with_namespace").asText(), p.path("web_url").asText(),
                branch, p.path("last_activity_at").asText(""),
                "", "", "", "", "", "",
                "", "", "", "",
                stats.has("repository_size") ? String.format("%.1f", stats.path("repository_size").asLong() / 1048576.0) : "",
                stats.has("commit_count") ? stats.path("commit_count").asText() : "",
                ""
        };
        if (branch.isEmpty()) {
            row[5] = "aucun";
            row[17] = "dépôt vide";
            return row;
        }
        try {
            front(id, branch, row);
            activity(id, branch, since, row);
        } catch (Exception e) {
            row[17] = e.getMessage();
        }
        return row;
    }

    /** Remplit techno / vue / react / angular / autres_front / outillage. */
    void front(long id, String branch, String[] row) throws Exception {
        HttpResponse<String> resp = get(baseUrl + "/projects/" + id + "/repository/files/package.json/raw?ref=" + enc(branch));
        if (resp.statusCode() == 404) {
            row[5] = "aucun";
            return;
        }
        if (resp.statusCode() != 200) throw new IOException("package.json → " + resp.statusCode());

        Map<String, String> deps = new LinkedHashMap<>();
        JsonNode pkg;
        try {
            pkg = JSON.readTree(resp.body());
        } catch (IOException e) {
            row[5] = "package.json illisible";
            return;
        }
        for (String section : List.of("dependencies", "devDependencies", "peerDependencies")) {
            pkg.path(section).fields().forEachRemaining(f -> deps.putIfAbsent(f.getKey(), f.getValue().asText()));
        }

        List<String> found = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (var fw : FRAMEWORKS.entrySet()) {
            String version = deps.get(fw.getKey());
            if (version == null) continue;
            String label = fw.getValue();
            String major = major(version);
            switch (label) {
                case "Vue" -> row[6] = version;
                case "React" -> row[7] = version;
                case "Angular", "AngularJS" -> row[8] = row[8].isEmpty() ? version : row[8] + " / " + version;
                default -> others.add(label + " " + version);
            }
            found.add(label.equals("Vue") || label.equals("Angular") ? label + " " + major : label);
        }
        row[5] = found.isEmpty() ? "node sans framework UI" : String.join(" + ", found);
        row[9] = String.join(", ", others);

        List<String> tooling = new ArrayList<>();
        for (String m : META) if (deps.containsKey(m)) tooling.add(m + " " + deps.get(m));
        row[10] = String.join(", ", tooling);
    }

    /** Remplit commits / MR / lignes ajoutées / supprimées sur la fenêtre. */
    void activity(long id, String branch, String since, String[] row) throws Exception {
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
        row[11] = String.valueOf(commits);
        row[13] = String.valueOf(added);
        row[14] = String.valueOf(deleted);

        HttpResponse<String> mrs = get(baseUrl + "/projects/" + id + "/merge_requests?state=all&created_after="
                + enc(since) + "&per_page=1");
        if (mrs.statusCode() == 200) {
            row[12] = mrs.headers().firstValue("X-Total")
                    .orElseGet(() -> String.valueOf(countAll(baseUrl + "/projects/" + id
                            + "/merge_requests?state=all&created_after=" + enc(since) + "&per_page=100")));
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

    /** Point-virgule + BOM UTF-8 + CRLF : s'ouvre tel quel dans Excel. */
    static void writeCsv(Path out, List<String[]> rows) throws IOException {
        StringBuilder sb = new StringBuilder("﻿");
        for (String[] r : rows) {
            for (int i = 0; i < r.length; i++) {
                if (i > 0) sb.append(';');
                String v = r[i] == null ? "" : r[i];
                if (v.contains(";") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
                    v = "\"" + v.replace("\"", "\"\"") + "\"";
                }
                sb.append(v);
            }
            sb.append("\r\n");
        }
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
    }
}
