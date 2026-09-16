//JAVA 25

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Accès SonarQube partagé par les outils qui en lisent.
 *
 * Pendant de {@link Gitlab} pour l'autre moitié de l'audit, et écrit après lui
 * pour la même raison : l'attente réseau domine tout le reste. Depuis que
 * l'inventaire demande ses branches à chaque projet, un parc de 500 projets
 * coûte 500 allers-retours — faits un par un, la machine et l'instance passent
 * leur temps à ne rien faire.
 *
 * Trois choses vivent ici, et rien de ce que l'audit conclut :
 *
 *   parler à l'API       — un GET, un jeton, l'organisation quand il le faut ;
 *   tenir la cadence     — appels concurrents bornés, 429 et 5xx retentés ;
 *   rejouer une capture  — {@code --dump-dir} enregistre, {@code --replay-dir}
 *                          relit, et l'outil tourne alors sans instance.
 */
public final class Sonar {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** Appels concurrents par défaut : assez pour que l'attente se recouvre. */
    public static final int DEFAULT_CONCURRENCY = 8;

    // ----------------------------------------------------------------------
    // Réponse
    // ----------------------------------------------------------------------

    /** Statut + corps brut. Le corps est toujours présent, y compris sur erreur. */
    public record Response(int status, String body) {

        public boolean unreachable() { return status == 0; }

        public boolean ok() { return status == 200; }

        /** null si le statut n'est pas 200 ou si le corps n'est pas le JSON attendu. */
        public <T> T as(Class<T> type) {
            if (status != 200 || body == null) return null;
            try {
                return MAPPER.readValue(body, type);
            } catch (IOException e) {
                return null;
            }
        }

        public String errorMessage() {
            if (body == null) return "";
            try {
                ErrorResponse e = MAPPER.readValue(body, ErrorResponse.class);
                if (e.errors() != null && !e.errors().isEmpty()) {
                    return truncate(e.errors().stream().map(ErrorMessage::msg)
                            .filter(Objects::nonNull).collect(Collectors.joining("; ")), 90);
                }
            } catch (IOException ignored) {
                // corps non-JSON : on le montre tel quel
            }
            return truncate(body.replace("\n", " "), 90);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorResponse(List<ErrorMessage> errors) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorMessage(String msg) { }

    // ----------------------------------------------------------------------
    // Client
    // ----------------------------------------------------------------------

    public final String base;
    private final String token;
    private final String organization;
    private final Duration timeout;
    private final Path dumpDir;
    private final Path replayDir;
    private final HttpClient client;

    // Comptés depuis plusieurs fils dès que parallel() est utilisé : un int nu
    // perdrait des incréments, et le total d'appels est ce sur quoi on juge le
    // coût de l'outil. Il doit être juste, pas approximativement juste.
    private final AtomicInteger callCount = new AtomicInteger();
    private final AtomicInteger throttledCount = new AtomicInteger();
    private final AtomicInteger retriedCount = new AtomicInteger();
    private final AtomicInteger missingCaptureCount = new AtomicInteger();

    public int calls() { return callCount.get(); }

    public int throttled() { return throttledCount.get(); }

    public int retried() { return retriedCount.get(); }

    /** Captures réclamées et absentes : en rejeu, c'est la seule panne possible. */
    public int missingCaptures() { return missingCaptureCount.get(); }

    public boolean replaying() { return replayDir != null; }

    public Sonar(String url, String token, String organization, int timeoutSeconds,
                 boolean insecure, Path dumpDir, Path replayDir) throws Exception {
        this.base = url == null ? "" : url.replaceAll("/+$", "");
        this.token = token;
        this.organization = isBlank(organization) ? null : organization;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.dumpDir = dumpDir;
        this.replayDir = replayDir;
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
        String cleanPath = path.replaceAll("^/+", "");
        Map<String, String> all = new LinkedHashMap<>(params);
        if (organization != null && needsOrganization(cleanPath)) {
            all.putIfAbsent("organization", organization);
        }
        String capture = captureName(cleanPath, all);
        if (replayDir != null) return replay(capture, cleanPath, all);

        Response r = send(base + "/" + cleanPath + queryString(all));
        dump(capture, cleanPath + queryString(all), r.body());
        return r;
    }

    /**
     * SonarQube ne limite pas le débit lui-même, mais ce qu'on a devant lui le
     * fait souvent — et un 502 isolé d'un reverse proxy, au milieu d'une passe
     * concurrente, ne ressemble à rien d'autre qu'à un projet sans mesures.
     * Une panne de transport doit coûter une seconde, pas une ligne fausse.
     */
    private Response send(String uri) {
        for (int attempt = 0; ; attempt++) {
            callCount.incrementAndGet();
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
                        .GET()
                        .timeout(timeout)
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json")
                        .build();
                HttpResponse<String> res = client.send(req,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                int code = res.statusCode();
                if (code == 429 && attempt < 3) {
                    throttledCount.incrementAndGet();
                    long wait = res.headers().firstValue("retry-after")
                            .map(Sonar::parseSeconds).orElse(5L);
                    Thread.sleep(Math.min(wait, 60) * 1000);
                    continue;
                }
                if ((code == 502 || code == 503 || code == 504) && attempt < 2) {
                    retriedCount.incrementAndGet();
                    Thread.sleep(1000L * (attempt + 1));
                    continue;
                }
                return new Response(code, res.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Response(0, "interrompu");
            } catch (Exception e) {
                // ConnectException.getMessage() est souvent null : sans le nom de la
                // classe, « injoignable » ne dit pas s'il s'agit d'un refus, d'un
                // délai dépassé ou d'un échec TLS.
                return new Response(0, isBlank(e.getMessage())
                        ? e.getClass().getSimpleName()
                        : e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    private static long parseSeconds(String v) {
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return 5L;
        }
    }

    // ----------------------------------------------------------------------
    // Capture et rejeu
    // ----------------------------------------------------------------------

    /**
     * Le nom d'une capture se déduit de la requête, pas de son rang d'appel.
     *
     * Numérotées dans l'ordre, les captures ne se relisent que par un outil qui
     * refait exactement les mêmes appels dans exactement le même ordre — ce que
     * la concurrence a rendu faux. Nommées par (chemin, paramètres), elles se
     * relisent, se complètent à la main, et se versionnent comme des fixtures.
     *
     * Le condensé porte les paramètres pour que deux requêtes du même endpoint
     * ne s'écrasent pas ; le chemin reste en clair pour qu'un humain retrouve
     * le fichier qu'il cherche. L'ordre des appels n'est pas perdu : il est
     * consigné dans index.txt.
     */
    static String captureName(String path, Map<String, String> params) {
        String slug = path.replaceAll("^api/", "").replaceAll("[^A-Za-z0-9]+", "_");
        return truncate(slug, 60) + "-" + shortHash(path + queryString(new TreeMap<>(params)))
                + ".json";
    }

    private Response replay(String capture, String path, Map<String, String> params) {
        callCount.incrementAndGet();
        Path f = replayDir.resolve(capture);
        if (!Files.isRegularFile(f)) {
            missingCaptureCount.incrementAndGet();
            return new Response(0, "aucune capture pour " + path + queryString(params)
                    + " (attendu : " + capture + ")");
        }
        try {
            return new Response(200, Files.readString(f, StandardCharsets.UTF_8));
        } catch (IOException e) {
            missingCaptureCount.incrementAndGet();
            return new Response(0, "capture illisible : " + e.getMessage());
        }
    }

    /**
     * Les captures brutes ne sont pas de l'échafaudage : elles servent à générer
     * les records, à diffuser les dérives de version d'une instance à l'autre,
     * et — depuis --replay-dir — à rejouer un parc entier sans instance.
     */
    private void dump(String capture, String request, String body) {
        if (dumpDir == null) return;
        try {
            Files.writeString(dumpDir.resolve(capture), body == null ? "" : body);
            synchronized (this) {
                Files.writeString(dumpDir.resolve("index.txt"),
                        capture + "  " + request + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            System.err.println("  (capture non écrite : " + e.getMessage() + ")");
        }
    }

    private static String shortHash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1")
                    .digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) sb.append("%02x".formatted(d[i]));
            return sb.toString();
        } catch (Exception e) {
            return "%08x".formatted(s.hashCode());
        }
    }

    // ----------------------------------------------------------------------
    // Parallélisme
    // ----------------------------------------------------------------------

    /**
     * Repris de {@link Gitlab#map} : un fil virtuel par tâche, un sémaphore pour
     * la largeur. Les fils sont gratuits, les connexions ne le sont pas, et le
     * proxy devant l'instance ne doit pas être ce qui découvre la limite.
     *
     * Les exceptions ne sont pas avalées : une tâche qui échoue relance son
     * exception à l'appelant, comme si la boucle était restée séquentielle.
     */
    public <T, R> List<R> map(List<T> items, int concurrency, Function<T, R> work) {
        if (items.isEmpty()) return List.of();
        int width = Math.max(1, concurrency);
        if (width == 1 || items.size() == 1) {
            return items.stream().map(work).collect(Collectors.toCollection(ArrayList::new));
        }
        Semaphore inFlight = new Semaphore(width);
        List<R> out = new ArrayList<>(Collections.nCopies(items.size(), (R) null));
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

    public <T> void parallel(List<T> items, int concurrency, Consumer<T> work) {
        map(items, concurrency, item -> {
            work.accept(item);
            return null;
        });
    }

    // ----------------------------------------------------------------------
    // Détails de transport
    // ----------------------------------------------------------------------

    /**
     * Endpoints qui exigent (ou acceptent) 'organization' sur SonarQube Cloud.
     *
     * api/projects/search en fait partie : sans le paramètre, Cloud répond 400 et
     * le diagnostic conclut à tort « nécessite Administer System » — soit un faux
     * point aveugle sur le calcul le plus important de l'outil.
     *
     * La liste reste volontairement courte : les endpoints portés par un composant
     * (measures/*, components/tree, sources/scm, project_analyses, settings/values)
     * n'acceptent pas le paramètre, et l'ajouter provoquerait l'erreur qu'on cherche
     * à éviter. Le paramètre n'est de toute façon envoyé que si --organization est
     * fourni, ce qui ne concerne que Cloud.
     *
     * NON VÉRIFIÉ sur une instance Cloud réelle : sonarcloud.io était injoignable
     * depuis l'environnement de test. Déduit de la documentation de l'API.
     */
    static boolean needsOrganization(String path) {
        return path.startsWith("api/components/search_projects")
                || path.startsWith("api/qualityprofiles")
                || path.startsWith("api/projects/search")
                || path.startsWith("api/issues/search")
                || path.startsWith("api/qualitygates/get_by_project");
    }

    static String queryString(Map<String, String> params) {
        if (params.isEmpty()) return "";
        return params.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&", "?", ""));
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
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

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
