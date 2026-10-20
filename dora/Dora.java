// Ce que partagent DoraLocate, JiraExtract et GocdExtract : un client GET authentifié par PAT,
// l'écriture des CSV au format du dépôt (point-virgule, BOM, CRLF), les dates, et la lecture
// des matériaux GoCD.

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ProxySelector;
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

final class Dora {

  private Dora() {}

  static final ObjectMapper JSON = new ObjectMapper();

  /** Arrêt propre sur une erreur qu'on ne sait pas contourner (401, URL manquante…). */
  static final class Fatal extends RuntimeException {
    Fatal(String message) {
      super(message);
    }
  }

  // ------------------------------------------------------------ HTTP

  record Resp(int status, String body, String error) {
    boolean ok() {
      return status >= 200 && status < 300 && !html();
    }

    boolean html() {
      String b = body.stripLeading().toLowerCase(Locale.ROOT);
      return b.startsWith("<!doctype") || b.startsWith("<html");
    }

    JsonNode json() {
      try {
        return JSON.readTree(body);
      } catch (IOException e) {
        return MissingNode.getInstance();
      }
    }

    String why() {
      if (status == 0) return error;
      if (html()) return "page HTML (login ou SSO) au lieu de JSON";
      return "HTTP " + status;
    }
  }

  /** Un client par serveur : la racine, l'en-tête d'authentification, et rien d'autre que GET. */
  static final class Api {
    final String base;
    private final String authHeader;
    private final String authValue;
    private final String accept;
    private final HttpClient client;
    int calls;

    Api(String base, String authHeader, String authValue, String accept, boolean insecure) {
      this.base = base.replaceAll("/+$", "");
      this.authHeader = authHeader;
      this.authValue = authValue;
      this.accept = accept;
      HttpClient.Builder b =
          HttpClient.newBuilder()
              .connectTimeout(java.time.Duration.ofSeconds(30))
              .proxy(ProxySelector.getDefault())
              .followRedirects(HttpClient.Redirect.NEVER);
      if (insecure) {
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
        try {
          SSLContext ctx = SSLContext.getInstance("TLS");
          ctx.init(null, new TrustManager[] {new TrustAll()}, new SecureRandom());
          b.sslContext(ctx);
        } catch (java.security.GeneralSecurityException e) {
          throw new Fatal("TLS : " + e.getMessage());
        }
      }
      this.client = b.build();
    }

    static Api jira(String url, String token, boolean insecure) {
      return new Api(require(url, "JIRA_URL"), "Authorization", "Bearer " + require(token, "JIRA_TOKEN"),
          "application/json", insecure);
    }

    static Api gocd(String url, String token, boolean insecure) {
      return new Api(require(url, "GOCD_URL").replaceAll("/+$", "").replaceFirst("/go$", ""), "Authorization",
          "Bearer " + require(token, "GOCD_TOKEN"), "application/vnd.go.cd+json", insecure);
    }

    static Api gitlab(String url, String token, boolean insecure) {
      return new Api(require(url, "GITLAB_URL"), "PRIVATE-TOKEN", require(token, "GITLAB_TOKEN"),
          "application/json", insecure);
    }

    /** path commence par « / ». Un 429 attend et recommence ; un 401 arrête tout. */
    Resp get(String path) {
      String url = path.startsWith("http") ? path : base + path;
      for (int attempt = 0; ; attempt++) {
        calls++;
        try {
          HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET()
              .timeout(java.time.Duration.ofSeconds(60))
              .header("Accept", accept).header(authHeader, authValue).build();
          HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
          if (res.statusCode() == 429 && attempt < 5) {
            long wait = res.headers().firstValue("retry-after").map(Dora::parseLongOr5).orElse(5L);
            Thread.sleep(Math.min(wait, 60) * 1000);
            continue;
          }
          if (res.statusCode() == 401) throw new Fatal(base + " : jeton refusé (401)");
          return new Resp(res.statusCode(), res.body(), "");
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new Fatal("interrompu");
        } catch (IOException e) {
          if (attempt < 2) continue;
          return new Resp(0, "", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
      }
    }

    /** Comme get, mais un échec arrête l'outil : pour les appels sans lesquels rien n'a de sens. */
    JsonNode must(String path) {
      Resp r = get(path);
      if (!r.ok()) throw new Fatal(path.replaceFirst("\\?.*", "") + " : " + r.why());
      return r.json();
    }
  }

  static String require(String value, String env) {
    if (value == null || value.isBlank()) throw new Fatal(env + " manquant");
    return value.trim();
  }

  static long parseLongOr5(String s) {
    try {
      return Long.parseLong(s.trim());
    } catch (NumberFormatException e) {
      return 5L;
    }
  }

  static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }

  // ------------------------------------------------------------ CSV

  /**
   * Le format des CSV destinés aux gens dans ce dépôt : point-virgule, BOM UTF-8, CRLF. Excel les
   * ouvre d'un double-clic avec les accents.
   */
  static final class Csv implements AutoCloseable {
    private final BufferedWriter w;
    final Path path;
    int rows;

    Csv(Path path, String... header) {
      this.path = path;
      try {
        Files.createDirectories(path.toAbsolutePath().getParent());
        w = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
        w.write('﻿');
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      line(List.of(header));
      rows = 0;
    }

    void row(Object... cells) {
      List<String> l = new ArrayList<>();
      for (Object c : cells) l.add(c == null ? "" : c.toString());
      line(l);
    }

    private void line(List<String> cells) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < cells.size(); i++) {
        if (i > 0) sb.append(';');
        String c = cells.get(i).replace("\r", " ").replace("\n", " ");
        if (c.contains(";") || c.contains("\"")) c = "\"" + c.replace("\"", "\"\"") + "\"";
        sb.append(c);
      }
      try {
        w.write(sb.append("\r\n").toString());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      rows++;
    }

    @Override
    public void close() {
      try {
        w.close();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  /** Une ligne JSON par objet : les réponses brutes, pour retraiter sans ré-extraire. */
  static final class Jsonl implements AutoCloseable {
    private final BufferedWriter w;
    int rows;

    Jsonl(Path path) {
      try {
        Files.createDirectories(path.toAbsolutePath().getParent());
        w = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    void write(JsonNode n) {
      try {
        w.write(JSON.writeValueAsString(n));
        w.write('\n');
        rows++;
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public void close() {
      try {
        w.close();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  // ------------------------------------------------------------ dates

  static final DateTimeFormatter OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  /** Heure locale du poste, lisible dans Excel. Vide si absent. */
  static String fmt(Instant t) {
    return t == null ? "" : OUT.format(t.atZone(ZoneId.systemDefault()));
  }

  /** Epoch en millisecondes (GoCD), ISO-8601 ou le format Jira 2025-03-04T10:15:30.000+0100. */
  static Instant instant(JsonNode n) {
    if (n == null || n.isMissingNode() || n.isNull()) return null;
    if (n.isNumber()) return Instant.ofEpochMilli(n.asLong());
    return instant(n.asText(""));
  }

  static Instant instant(String s) {
    if (s == null || s.isEmpty()) return null;
    try {
      return OffsetDateTime.parse(s).toInstant();
    } catch (RuntimeException e) {
      try {
        return OffsetDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")).toInstant();
      } catch (RuntimeException e2) {
        return null;
      }
    }
  }

  static String hours(Instant from, Instant to) {
    if (from == null || to == null || to.isBefore(from)) return "";
    return "%.1f".formatted(java.time.Duration.between(from, to).toMinutes() / 60.0).replace('.', ',');
  }

  static String days(Instant from, Instant to) {
    if (from == null || to == null || to.isBefore(from)) return "";
    return "%.1f".formatted(java.time.Duration.between(from, to).toHours() / 24.0).replace('.', ',');
  }

  // ------------------------------------------------------------ Jira

  /** Une clé de ticket : ABC-123, précédée et suivie d'un séparateur. */
  static final Pattern JIRA_KEY = Pattern.compile("(?<![A-Za-z0-9_-])([A-Z][A-Z0-9_]{1,14})-([1-9][0-9]{0,6})(?![A-Za-z0-9])");

  static Set<String> jiraKeys(String text) {
    Set<String> keys = new LinkedHashSet<>();
    if (text == null) return keys;
    Matcher m = JIRA_KEY.matcher(text);
    while (m.find()) keys.add(m.group(1) + "-" + m.group(2));
    return keys;
  }

  // ------------------------------------------------------------ GoCD et dépôts

  /** Mots qui, dans un nom de pipeline ou de stage, désignent une plateforme. */
  static final Map<String, String> PLATFORMS = Map.ofEntries(
      Map.entry("prod", "prod"), Map.entry("prd", "prod"), Map.entry("production", "prod"),
      Map.entry("preprod", "preprod"), Map.entry("pprod", "preprod"), Map.entry("ppr", "preprod"),
      Map.entry("rec", "recette"), Map.entry("recette", "recette"), Map.entry("rct", "recette"),
      Map.entry("qual", "recette"), Map.entry("qualif", "recette"), Map.entry("uat", "recette"),
      Map.entry("val", "recette"), Map.entry("validation", "recette"), Map.entry("homol", "recette"),
      Map.entry("int", "integration"), Map.entry("integ", "integration"), Map.entry("integration", "integration"),
      Map.entry("test", "test"), Map.entry("tst", "test"),
      Map.entry("dev", "dev"), Map.entry("develop", "dev"));

  /** Les plateformes reconnues dans un nom, séparées par « | ». Une supposition, à vérifier. */
  static String platforms(String... names) {
    Set<String> out = new LinkedHashSet<>();
    for (String name : names)
      for (String t : name.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
        if (PLATFORMS.containsKey(t)) out.add(PLATFORMS.get(t));
    return String.join("|", out);
  }

  /** La description d'un matériau git : « URL: https://…, Branch: main ». */
  static String gitUrl(String description) {
    Matcher m = Pattern.compile("URL:\\s*(\\S+?)(?:,|\\s|$)").matcher(description);
    if (m.find()) return m.group(1);
    m = Pattern.compile("(https?://\\S+?|[\\w.-]+@[\\w.-]+:\\S+?)(?:,|\\s|$)").matcher(description);
    return m.find() ? m.group(1) : "";
  }

  /** La description d'un matériau pipeline : « amont [ stage ] ». */
  static String upstreamName(JsonNode material) {
    String d = material.path("description").asText("");
    int i = d.indexOf(" [");
    return i > 0 ? d.substring(0, i).trim() : material.path("name").asText("");
  }

  /**
   * Le chemin d'un dépôt, sans schéma, identifiants, hôte ni « .git » : https://u@h/g/p.git et
   * git@h:g/p.git donnent tous deux « g/p ». C'est ce qu'on compare au path_with_namespace GitLab.
   */
  static String repoPath(String url) {
    if (url == null) return "";
    String u = url.trim().toLowerCase(Locale.ROOT);
    Matcher ssh = Pattern.compile("^(?:ssh://)?[^@/]+@[^:/]+(?::\\d+)?[:/](.+)$").matcher(u);
    if (ssh.find()) u = ssh.group(1);
    else u = u.replaceFirst("^[a-z+]+://", "").replaceFirst("^[^/]*@", "").replaceFirst("^[^/]+/", "");
    return u.replaceAll("/+$", "").replaceAll("\\.git$", "");
  }

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
