//JAVA 25

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Accès GitLab partagé par les deux outils qui en lisent : GitlabActivityAudit,
 * qui classe un groupe entier, et GitLabProjectReport, qui lit un projet en
 * détail.
 *
 * Les deux avaient été écrits séparément, et étaient tombés d'accord sans se
 * concerter sur les trois pièges qui comptent — les bots, les commits de fusion,
 * une pagination plafonnée qui doit se lire comme un plancher. Deux accords
 * fortuits ne survivent pas à la première correction apportée d'un seul côté :
 * d'où ce fichier, qui rend l'accord explicite.
 *
 * Ce qui reste chez chaque outil : ce qu'il mesure, et ce qu'il en conclut.
 * Ce qui vient ici : parler à l'API, et les trois conventions ci-dessous.
 */
public final class Gitlab {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String YELLOW = "\033[33m";

    // ----------------------------------------------------------------------
    // Réponse
    // ----------------------------------------------------------------------

    /** Statut, corps brut, en-têtes. GitLab met la pagination dans les en-têtes. */
    public record Response(int status, String body, Map<String, String> headers) {

        public boolean unreachable() { return status == 0; }

        public boolean ok() { return status >= 200 && status < 300; }

        public JsonNode json() {
            if (body == null) return MAPPER.nullNode();
            try {
                return MAPPER.readTree(body);
            } catch (IOException e) {
                return MAPPER.nullNode();
            }
        }

        /** Lecture typée, pour l'outil qui préfère des records à un arbre. */
        public <T> T as(Class<T> type) {
            if (!ok() || isBlank(body)) return null;
            try {
                return MAPPER.readValue(body, type);
            } catch (IOException e) {
                return null;
            }
        }

        public <T> List<T> asList(Class<T> type) {
            if (!ok() || isBlank(body)) return null;
            try {
                return MAPPER.readValue(body,
                        MAPPER.getTypeFactory().constructCollectionType(List.class, type));
            } catch (IOException e) {
                return null;
            }
        }

        public Integer intHeader(String name) {
            String v = headers.get(name.toLowerCase(Locale.ROOT));
            if (isBlank(v)) return null;
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /** GitLab répond message, error ou error_description selon l'endpoint. */
        public String errorMessage() {
            if (status == 429) return "limite de débit atteinte (429)";
            if (body == null) return "";
            JsonNode j = json();
            for (String k : List.of("message", "error", "error_description")) {
                if (j.hasNonNull(k) && j.get(k).isTextual()) return truncate(j.get(k).asText(), 90);
            }
            return truncate(body.replace("\n", " "), 90);
        }
    }

    /**
     * Une page de résultats typés, avec de quoi distinguer les trois issues :
     * complet, plafonné, ou refusé. Un appelant qui ne peut pas les distinguer
     * finit par présenter un plancher comme une mesure — l'erreur que les deux
     * outils documentent par ailleurs.
     */
    public record Page<T>(List<T> items, boolean truncated, String error) { }

    // ----------------------------------------------------------------------
    // Client
    // ----------------------------------------------------------------------

    public final String base;
    private final String token;
    private final Duration timeout;
    private final Path dumpDir;
    private final HttpClient client;

    // Comptés depuis plusieurs fils dès que parallel() est utilisé : un int nu
    // perdrait des incréments, et le total d'appels est ce sur quoi on juge le
    // coût de l'outil. Il doit être juste, pas approximativement juste.
    private final AtomicInteger callCount = new AtomicInteger();
    private final AtomicInteger forbiddenCount = new AtomicInteger();
    private final AtomicInteger throttledCount = new AtomicInteger();

    public int calls() { return callCount.get(); }

    public int forbidden() { return forbiddenCount.get(); }

    public int throttled() { return throttledCount.get(); }

    public Gitlab(String url, String token, int timeoutSeconds, boolean insecure, Path dumpDir)
            throws Exception {
        this.base = url.replaceAll("/+$", "");
        this.token = token;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.dumpDir = dumpDir;
        if (dumpDir != null) Files.createDirectories(dumpDir);

        HttpClient.Builder b = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (insecure) {
            System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
            b.sslContext(trustEverything());
        }
        this.client = b.build();
    }

    public Response get(String path, Map<String, String> params) {
        return send("GET", base + "/api/v4/" + path.replaceAll("^/+", "") + query(params), null);
    }

    /** Existence d'un fichier sans transférer son contenu. */
    public boolean exists(String path, Map<String, String> params) {
        return send("HEAD", base + "/api/v4/" + path.replaceAll("^/+", "") + query(params),
                null).status() == 200;
    }

    public Response graphql(String q) {
        String payload;
        try {
            payload = MAPPER.writeValueAsString(Map.of("query", q));
        } catch (IOException e) {
            return new Response(0, e.toString(), Map.of());
        }
        return send("POST", base + "/api/graphql", payload);
    }

    /**
     * Pagination par page. GitLab expose X-Next-Page ; un en-tête vide signe la
     * fin. On ne se fie pas au nombre d'éléments retournés : une page pleine en
     * dernière position ferait boucler à l'infini.
     */
    public List<JsonNode> paged(String path, Map<String, String> params) {
        List<JsonNode> out = new ArrayList<>();
        String page = "1";
        for (int i = 0; i < 500 && !isBlank(page); i++) {
            Response r = get(path, pageParams(params, page));
            if (r.status() != 200) {
                System.out.println(color("    Pagination interrompue page %s : HTTP %d"
                        .formatted(page, r.status())));
                break;
            }
            JsonNode arr = r.json();
            if (!arr.isArray()) break;
            arr.forEach(out::add);
            page = r.headers().get("x-next-page");
        }
        return out;
    }

    /**
     * Même pagination, typée et plafonnée. Le plafond n'est pas un détail de
     * confort : atteint, il change le dénominateur de tout ce qu'on calcule
     * ensuite, et l'appelant doit pouvoir le dire.
     */
    public <T> Page<T> paged(String path, Map<String, String> params, Class<T> type, int max) {
        List<T> out = new ArrayList<>();
        String page = "1";
        while (!isBlank(page) && out.size() < max) {
            Response r = get(path, pageParams(params, page));
            if (!r.ok()) {
                return new Page<>(out, false, "HTTP " + r.status() + " — " + r.errorMessage());
            }
            List<T> batch = r.asList(type);
            if (batch == null) return new Page<>(out, false, "réponse illisible");
            out.addAll(batch);
            page = r.headers().get("x-next-page");
        }
        boolean truncated = out.size() > max || !isBlank(page);
        return new Page<>(out.size() > max ? new ArrayList<>(out.subList(0, max)) : out,
                truncated, null);
    }

    // ----------------------------------------------------------------------
    // Parallélisme
    // ----------------------------------------------------------------------

    /**
     * Le coût de ces outils n'est pas le calcul, c'est l'attente : un appel
     * GitLab tourne autour de 100 à 300 ms, et jusqu'ici on les faisait un par
     * un. Sur un parc de 6 000 projets, la passe profonde y passe des heures
     * sans que la machine ni l'instance ne travaillent — les deux attendent le
     * réseau.
     *
     * Les faire se recouvrir ne change aucune mesure : chaque projet est lu
     * indépendamment des autres, et rien dans les étapes par projet ne dépend
     * de l'ordre. La borne, elle, compte : le limiteur de débit de GitLab est
     * configuré par instance et souvent abaissé, donc on plafonne le nombre
     * d'appels en vol plutôt que de lâcher 200 requêtes d'un coup. Le 429 reste
     * traité comme avant, avec Retry-After — la borne le rend rare, elle ne le
     * rend pas impossible.
     *
     * Les exceptions ne sont pas avalées : une tâche qui échoue relance son
     * exception à l'appelant, comme si la boucle était restée séquentielle.
     */
    public <T> void parallel(List<T> items, int concurrency, Consumer<T> work) {
        map(items, concurrency, item -> {
            work.accept(item);
            return null;
        });
    }

    /** Même chose, en conservant les résultats dans l'ordre des entrées. */
    public <T, R> List<R> map(List<T> items, int concurrency, Function<T, R> work) {
        if (items.isEmpty()) return List.of();
        int width = Math.max(1, concurrency);
        if (width == 1 || items.size() == 1) {
            return items.stream().map(work).collect(Collectors.toCollection(ArrayList::new));
        }
        // Un fil virtuel par tâche, un sémaphore pour la largeur : les fils sont
        // gratuits, les connexions ne le sont pas. Le sémaphore borne les appels
        // en vol, pas les objets créés.
        Semaphore inFlight = new Semaphore(width);
        List<R> out = new ArrayList<>(java.util.Collections.nCopies(items.size(), (R) null));
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<R>> futures = new ArrayList<>(items.size());
            for (T item : items) {
                futures.add(pool.submit(() -> {
                    inFlight.acquire();
                    try {
                        return work.apply(item);
                    } finally {
                        inFlight.release();
                    }
                }));
            }
            for (int i = 0; i < futures.size(); i++) {
                try {
                    out.set(i, futures.get(i).get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrompu", e);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException re) throw re;
                    if (cause instanceof Error err) throw err;
                    throw new IllegalStateException(cause);
                }
            }
        }
        return out;
    }

    private static Map<String, String> pageParams(Map<String, String> params, String page) {
        Map<String, String> p = new LinkedHashMap<>(params);
        p.put("per_page", "100");
        p.put("page", page);
        return p;
    }

    /**
     * Le limiteur de débit de GitLab est configuré par instance et souvent
     * abaissé. On respecte Retry-After dès la première exécution plutôt qu'après
     * le premier 429 rencontré en production.
     */
    private Response send(String method, String uri, String body) {
        for (int attempt = 0; ; attempt++) {
            int seq = callCount.incrementAndGet();
            try {
                HttpRequest.BodyPublisher pub = body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
                HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(uri))
                        .method(method, pub)
                        .timeout(timeout)
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json");
                // PRIVATE-TOKEN accepte les PAT comme les jetons de groupe ; Bearer
                // ne vaut que pour un jeton OAuth. Sans jeton du tout, on ne pose pas
                // l'en-tête : un projet public se lit anonymement, et c'est le seul
                // moyen de vérifier l'outil contre une instance réelle qu'on ne
                // possède pas.
                if (!isBlank(token)) rb.header("PRIVATE-TOKEN", token);
                HttpResponse<String> res = client.send(rb.build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (res.statusCode() == 429 && attempt < 3) {
                    throttledCount.incrementAndGet();
                    long wait = res.headers().firstValue("retry-after")
                            .map(v -> { try { return Long.parseLong(v.trim()); }
                                        catch (NumberFormatException e) { return 5L; } })
                            .orElse(5L);
                    Thread.sleep(Math.min(wait, 60) * 1000);
                    continue;
                }
                if (res.statusCode() == 403) forbiddenCount.incrementAndGet();

                Map<String, String> h = new HashMap<>();
                res.headers().map().forEach((k, v) ->
                        h.put(k.toLowerCase(Locale.ROOT), v.isEmpty() ? "" : v.get(0)));
                dump(seq, uri, res.body());
                return new Response(res.statusCode(), res.body(), h);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Response(0, "interrompu", Map.of());
            } catch (Exception e) {
                // ConnectException.getMessage() est souvent null : sans le nom de la
                // classe, « injoignable » ne dit pas s'il s'agit d'un refus, d'un
                // délai dépassé ou d'un échec TLS.
                String msg = isBlank(e.getMessage()) ? e.getClass().getSimpleName()
                        : e.getClass().getSimpleName() + ": " + e.getMessage();
                return new Response(0, msg, Map.of());
            }
        }
    }

    private void dump(int seq, String uri, String body) {
        if (dumpDir == null) return;
        String name = "%04d-%s.json".formatted(seq,
                uri.replaceFirst("^https?://[^/]+/", "").replaceAll("[^A-Za-z0-9]+", "_"));
        try {
            Files.writeString(dumpDir.resolve(truncate(name, 120)), body == null ? "" : body);
        } catch (IOException e) {
            System.err.println("  (capture non écrite : " + e.getMessage() + ")");
        }
    }

    // ----------------------------------------------------------------------
    // Les trois conventions
    // ----------------------------------------------------------------------

    /**
     * Non filtrés, Renovate et consorts finissent premiers contributeurs et
     * faussent tout : cadence, concentration, taille des commits.
     *
     * Le motif est l'union de ceux que les deux outils s'étaient donnés
     * séparément. Il reste étroit à dessein : « bot » collé à d'autres lettres
     * attraperait Abbot ou Talbot, d'où les bornes autour du mot nu. Les
     * identités écartées se rapportent, elles ne se cachent pas.
     */
    public static final String BOT_PATTERN =
            "(?i)(renovate|dependabot|semantic-release|\\[bot\\]|bot@|gitlab-ci-token"
            + "|jenkins|sonarqube|automation)"
            + "|(^|[^\\p{L}])bots?([^\\p{L}]|$)"
            + "|(^|[^\\p{L}])project_\\d+_bot";

    public static boolean isBot(Pattern bots, String email, String name) {
        return bots.matcher(orEmpty(email) + " " + orEmpty(name)).find();
    }

    /**
     * Deux adresses pour la même personne fragmentent le compte d'auteurs. On
     * normalise sur la partie locale de l'adresse, à défaut sur le nom. Le
     * résultat vaut à ±1 : il alimente un jugement de bus factor, il lui suffit
     * d'être juste entre 1 et 5.
     */
    public static String identity(String email, String name) {
        String raw = (!isBlank(email) && email.contains("@"))
                ? email.substring(0, email.indexOf('@'))
                : name;
        if (isBlank(raw)) return "?";
        // Les deux branches doivent atterrir dans le même espace de noms : sinon
        // « Dev 0 » (commit sans adresse) et « dev0@example.com » comptent pour
        // deux personnes, et le bus factor double silencieusement.
        String norm = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return norm.isEmpty() ? "?" : norm;
    }

    /**
     * Un commit à plus d'un parent est une fusion. Il ne porte aucun travail
     * propre — ses lignes sont celles des commits qu'il réunit — et sa date est
     * celle du clic sur « fusionner », par quelqu'un qui n'a pas écrit le code.
     * Mesuré sur gitlab-org/cli : les compter changeait le contributeur
     * principal, de la personne qui écrit à celle qui fusionne.
     */
    public static boolean isMerge(JsonNode commit) {
        return commit != null && commit.path("parent_ids").size() > 1;
    }

    public static boolean isMerge(List<String> parentIds) {
        return parentIds != null && parentIds.size() > 1;
    }

    // ----------------------------------------------------------------------
    // Dates et chaînes
    // ----------------------------------------------------------------------

    /**
     * GitLab date en ISO-8601 avec décalage : « 2026-08-18T09:12:33.000+02:00 »,
     * ou en Z. Le décalage porte du sens — c'est le fuseau de la machine qui a
     * commité, donc l'heure locale de la personne — et se conserve ici.
     */
    public static OffsetDateTime parse(String s) {
        if (isBlank(s)) return null;
        try {
            return OffsetDateTime.parse(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String query(Map<String, String> params) {
        if (params.isEmpty()) return "";
        return params.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&", "?", ""));
    }

    private static String color(String s) {
        return ConsoleOut.color(s, YELLOW);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static String orEmpty(String s) { return s == null ? "" : s; }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static SSLContext trustEverything() throws Exception {
        TrustManager[] trustAll = {new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] c, String a) { }
            public void checkServerTrusted(X509Certificate[] c, String a) { }
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }};
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, trustAll, new SecureRandom());
        return ctx;
    }
}
