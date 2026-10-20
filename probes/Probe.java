// Ce qui est commun aux deux sondes : un transport en GET seulement, la capture
// facultative des réponses brutes, et un rapport qu'on peut recopier hors de
// l'entreprise sans y faire passer de données (noms masqués par défaut).

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

final class Probe {

  static final ObjectMapper JSON = new ObjectMapper();

  /** Une réponse. status 0 : pas de réponse du tout, la raison est dans error. */
  record Resp(int status, String body, Map<String, String> headers, String error) {

    String contentType() {
      return headers.getOrDefault("content-type", "");
    }

    /**
     * Un proxy SSO ou une page de login répondent 200 avec du HTML : compter ça comme un succès
     * ferait croire à un accès qu'on n'a pas.
     */
    boolean html() {
      String b = body.stripLeading().toLowerCase(Locale.ROOT);
      return contentType().contains("text/html") || b.startsWith("<!doctype") || b.startsWith("<html");
    }

    boolean ok() {
      return status >= 200 && status < 300 && !html();
    }

    JsonNode json() {
      try {
        return JSON.readTree(body);
      } catch (IOException e) {
        return MissingNode.getInstance();
      }
    }

    /** Ce qu'on affiche à la place d'un code : OK, 401, 403, HTML, ERR… */
    String tag() {
      if (status == 0) return "ERR";
      if (status >= 200 && status < 300) return html() ? "HTML" : "OK";
      return String.valueOf(status);
    }
  }

  private final HttpClient client;
  private final Duration timeout;
  private final Path dumpDir;
  private final boolean showNames;
  private final Map<String, String> aliases = new HashMap<>();
  private final Map<String, Integer> aliasCounters = new HashMap<>();
  int calls;

  Probe(int timeoutSeconds, boolean insecure, Path dumpDir, boolean showNames)
      throws IOException, GeneralSecurityException {
    this.timeout = Duration.ofSeconds(timeoutSeconds);
    this.dumpDir = dumpDir;
    this.showNames = showNames;
    if (dumpDir != null) Files.createDirectories(dumpDir);
    HttpClient.Builder b =
        HttpClient.newBuilder()
            .connectTimeout(timeout)
            .proxy(ProxySelector.getDefault())
            // On ne suit pas les redirections : un 302 vers une page de login
            // est précisément ce qu'on veut voir.
            .followRedirects(HttpClient.Redirect.NEVER);
    if (insecure) {
      System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
      SSLContext ctx = SSLContext.getInstance("TLS");
      ctx.init(null, new TrustManager[] {new TrustAll()}, new SecureRandom());
      b.sslContext(ctx);
    }
    this.client = b.build();
  }

  /** Le seul verbe de la sonde. headers : authentification et Accept, posés par l'appelant. */
  Resp get(String url, Map<String, String> headers) {
    calls++;
    HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url)).GET().timeout(timeout);
    headers.forEach(rb::header);
    try {
      HttpResponse<String> res =
          client.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      Map<String, String> h = new HashMap<>();
      res.headers()
          .map()
          .forEach((k, v) -> h.put(k.toLowerCase(Locale.ROOT), v.isEmpty() ? "" : v.get(0)));
      dump(url, res.statusCode(), res.body());
      return new Resp(res.statusCode(), res.body(), h, "");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new Resp(0, "", Map.of(), "interrompu");
    } catch (IOException | IllegalArgumentException e) {
      String m = e.getMessage();
      return new Resp(
          0, "", Map.of(), e.getClass().getSimpleName() + (m == null ? "" : ": " + m));
    }
  }

  private void dump(String url, int status, String body) {
    if (dumpDir == null) return;
    String name =
        "%04d-%d-%s"
            .formatted(
                calls, status, url.replaceFirst("^https?://[^/]+/", "").replaceAll("[^A-Za-z0-9]+", "_"));
    if (name.length() > 120) name = name.substring(0, 120);
    try {
      Files.writeString(dumpDir.resolve(name + ".txt"), body);
    } catch (IOException e) {
      System.err.println("  (capture non écrite : " + e.getMessage() + ")");
    }
  }

  // ---------------------------------------------------------------- noms

  /**
   * Un nom de projet, de pipeline ou de personne ne sort qu'avec --show-names. Sinon il devient
   * « pipeline#3 », stable sur tout le rapport.
   */
  String name(String kind, String real) {
    if (showNames) return real;
    return aliases.computeIfAbsent(
        kind + "\u0000" + real, k -> kind + "#" + aliasCounters.merge(kind, 1, Integer::sum));
  }

  // ---------------------------------------------------------------- rapport

  static void section(String title) {
    System.out.println();
    System.out.println("== " + title);
  }

  static void line(String tag, String label, String detail) {
    System.out.printf("  [%-4s] %-44s %s%n", tag, label, detail);
  }

  static void line(Resp r, String label, String detail) {
    String d = detail;
    if (!r.ok()) d = why(r) + (detail.isEmpty() ? "" : " — " + detail);
    line(r.tag(), label, d);
  }

  static void info(String text) {
    System.out.println("         " + text);
  }

  /** Une raison courte pour un échec, sans recopier le corps (il peut contenir des noms). */
  static String why(Resp r) {
    if (r.status == 0) return r.error;
    if (r.html()) return "page HTML (login, SSO ou proxy) au lieu de JSON";
    if (r.status >= 300 && r.status < 400)
      return "redirection vers " + pathOnly(r.headers.getOrDefault("location", "?"));
    return switch (r.status) {
      case 401 -> "non authentifié";
      case 403 -> "authentifié mais pas autorisé";
      case 404 -> "introuvable (ou masqué faute de droit)";
      case 406 -> "en-tête Accept refusé";
      default -> "HTTP " + r.status;
    };
  }

  static String pathOnly(String url) {
    return url.replaceFirst("^https?://[^/]+", "").replaceFirst("\\?.*$", "");
  }

  static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }

  static String host(String url) {
    try {
      String h = URI.create(url.trim()).getHost();
      return h == null ? "" : h.toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException e) {
      return "";
    }
  }

  static String trimSlash(String url) {
    return url.replaceAll("/+$", "");
  }

  /** « a=3, b=1 », trié par effectif décroissant. */
  static String tally(Map<String, Integer> counts) {
    if (counts.isEmpty()) return "(aucun)";
    StringBuilder sb = new StringBuilder();
    counts.entrySet().stream()
        .sorted((a, b) -> b.getValue() - a.getValue())
        .forEach(e -> sb.append(sb.isEmpty() ? "" : ", ").append(e.getKey()).append('=').append(e.getValue()));
    return sb.toString();
  }

  static Map<String, Integer> counter() {
    return new TreeMap<>();
  }

  static void inc(Map<String, Integer> m, String k) {
    m.merge(k, 1, Integer::sum);
  }

  /** Médiane et 90e centile d'une liste de valeurs, en texte. */
  static String quantiles(List<Double> values, String unit) {
    if (values.isEmpty()) return "(aucune valeur)";
    List<Double> s = values.stream().sorted().toList();
    double med = s.get(s.size() / 2);
    double p90 = s.get(Math.min(s.size() - 1, (int) Math.floor(s.size() * 0.9)));
    String u = unit.isEmpty() ? "" : " " + unit;
    return "médiane %.1f%s, p90 %.1f%s (n=%d)".formatted(med, u, p90, u, s.size());
  }

  /** Mots qui, dans un nom de pipeline, de stage ou d'environnement, désignent une plateforme. */
  static final List<String> PLATFORM_WORDS =
      List.of(
          "prod", "prd", "production", "preprod", "pprod", "ppr", "pre", "rec", "recette", "rct",
          "int", "integ", "integration", "qual", "qualif", "qa", "uat", "val", "validation",
          "homol", "homologation", "test", "tst", "dev", "develop", "staging", "stg", "demo",
          "form", "formation", "perf", "sandbox", "deploy", "deploiement", "release", "build", "ci");

  /** Compte les mots de PLATFORM_WORDS présents dans un nom. */
  static void platformWords(String name, Map<String, Integer> into) {
    for (String token : name.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
      if (PLATFORM_WORDS.contains(token)) inc(into, token);
    }
  }

  static Map<String, String> headers(String... kv) {
    Map<String, String> m = new LinkedHashMap<>();
    for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
    return m;
  }

  /** --insecure : accepte tout certificat, pour une instance auto-signée. */
  private static final class TrustAll implements X509TrustManager {
    @Override
    public void checkClientTrusted(X509Certificate[] c, String a) {}

    @Override
    public void checkServerTrusted(X509Certificate[] c, String a) {}

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }
}
