package ch.ge.edg.sonar.gitlab.audit.gitlab;

import ch.ge.edg.sonar.gitlab.audit.console.ConsoleOut;
import ch.ge.edg.sonar.gitlab.audit.parallel.Parallel;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Accès GitLab partagé par les deux outils qui en lisent : GitlabActivityAudit, qui classe un
 * groupe entier, et GitLabProjectReport, qui lit un projet en détail.
 *
 * <p>Les deux avaient été écrits séparément, et étaient tombés d'accord sans se concerter sur les
 * trois pièges qui comptent — les bots, les commits de fusion, une pagination plafonnée qui doit se
 * lire comme un plancher. Deux accords fortuits ne survivent pas à la première correction apportée
 * d'un seul côté : d'où ce fichier, qui rend l'accord explicite.
 *
 * <p>Ce qui reste chez chaque outil : ce qu'il mesure, et ce qu'il en conclut. Ce qui vient ici :
 * parler à l'API, et les trois conventions ci-dessous.
 */
public final class Gitlab {

  public static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static final String YELLOW = "\033[33m";

  // ----------------------------------------------------------------------
  // Réponse
  // ----------------------------------------------------------------------

  /** Statut, corps brut, en-têtes. GitLab met la pagination dans les en-têtes. */
  public record Response(int status, @Nullable String body, Map<String, String> headers) {

    public boolean unreachable() {
      return status == 0;
    }

    public boolean ok() {
      return status >= 200 && status < 300;
    }

    public JsonNode json() {
      if (body == null) return MAPPER.nullNode();
      try {
        return MAPPER.readTree(body);
      } catch (IOException e) {
        return MAPPER.nullNode();
      }
    }

    /** Lecture typée, pour l'outil qui préfère des records à un arbre. */
    public <T> @Nullable T as(Class<T> type) {
      if (!ok() || isBlank(body)) return null;
      try {
        return MAPPER.readValue(body, type);
      } catch (IOException e) {
        return null;
      }
    }

    /**
     * Vide quand la réponse est refusée ou illisible : une liste vide dirait « rien à lire », et la
     * pagination typée doit pouvoir rapporter « réponse illisible » au lieu d'un plancher.
     */
    public <T> Optional<List<T>> asList(Class<T> type) {
      if (!ok() || isBlank(body)) return Optional.empty();
      try {
        List<T> list =
            MAPPER.readValue(
                body, MAPPER.getTypeFactory().constructCollectionType(List.class, type));
        return Optional.ofNullable(list);
      } catch (IOException e) {
        return Optional.empty();
      }
    }

    public @Nullable Integer intHeader(String name) {
      String v = headers.get(name.toLowerCase(Locale.ROOT));
      if (v == null || v.isBlank()) return null;
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
   * Une page de résultats typés, avec de quoi distinguer les trois issues : complet, plafonné, ou
   * refusé. Un appelant qui ne peut pas les distinguer finit par présenter un plancher comme une
   * mesure — l'erreur que les deux outils documentent par ailleurs.
   */
  public record Page<T>(List<T> items, boolean truncated, @Nullable String error) {}

  // ----------------------------------------------------------------------
  // Client
  // ----------------------------------------------------------------------

  /** De quoi joindre une instance : URL, jeton (facultatif), délai, TLS, capture des réponses. */
  public record Connection(
      String url,
      @Nullable String token,
      int timeoutSeconds,
      boolean insecure,
      @Nullable Path dumpDir) {}

  public final String base;
  private final GitlabHttp http;
  private final PrintStream out;

  public int calls() {
    return http.callCount.get();
  }

  public int forbidden() {
    return http.forbiddenCount.get();
  }

  public int throttled() {
    return http.throttledCount.get();
  }

  /** Les avertissements vont sur la sortie et l'erreur standard telles qu'elles sont à l'appel. */
  public Gitlab(
      String url,
      @Nullable String token,
      int timeoutSeconds,
      boolean insecure,
      @Nullable Path dumpDir)
      throws IOException, GeneralSecurityException {
    this(new Connection(url, token, timeoutSeconds, insecure, dumpDir), System.out, System.err);
  }

  /**
   * {@code out} reçoit l'avertissement d'une pagination interrompue, {@code err} celui d'une
   * capture qui n'a pas pu s'écrire.
   */
  public Gitlab(Connection c, PrintStream out, PrintStream err)
      throws IOException, GeneralSecurityException {
    this.base = c.url().replaceAll("/+$", "");
    this.out = out;
    this.http = new GitlabHttp(c, err);
  }

  public Response get(String path, Map<String, String> params) {
    return http.send("GET", base + "/api/v4/" + path.replaceAll("^/+", "") + query(params), null);
  }

  /** Existence d'un fichier sans transférer son contenu. */
  public boolean exists(String path, Map<String, String> params) {
    return http.send("HEAD", base + "/api/v4/" + path.replaceAll("^/+", "") + query(params), null)
            .status()
        == 200;
  }

  public Response graphql(String q) {
    String payload;
    try {
      payload = MAPPER.writeValueAsString(Map.of("query", q));
    } catch (IOException e) {
      return new Response(0, e.toString(), Map.of());
    }
    return http.send("POST", base + "/api/graphql", payload);
  }

  /**
   * Pagination par page. GitLab expose X-Next-Page ; un en-tête vide signe la fin. On ne se fie pas
   * au nombre d'éléments retournés : une page pleine en dernière position ferait boucler à
   * l'infini.
   */
  public List<JsonNode> paged(String path, Map<String, String> params) {
    List<JsonNode> nodes = new ArrayList<>();
    String page = "1";
    for (int i = 0; i < 500 && !page.isBlank(); i++) {
      Response r = get(path, pageParams(params, page));
      if (r.status() != 200) {
        out.println(
            color("    Pagination interrompue page %s : HTTP %d".formatted(page, r.status())));
        break;
      }
      JsonNode arr = r.json();
      if (!arr.isArray()) break;
      arr.forEach(nodes::add);
      page = nextPage(r);
    }
    return nodes;
  }

  /**
   * Même pagination, typée et plafonnée. Le plafond n'est pas un détail de confort : atteint, il
   * change le dénominateur de tout ce qu'on calcule ensuite, et l'appelant doit pouvoir le dire.
   */
  public <T> Page<T> paged(String path, Map<String, String> params, Class<T> type, int max) {
    List<T> items = new ArrayList<>();
    String page = "1";
    while (!page.isBlank() && items.size() < max) {
      Response r = get(path, pageParams(params, page));
      if (!r.ok()) {
        return new Page<>(items, false, "HTTP " + r.status() + " — " + r.errorMessage());
      }
      Optional<List<T>> batch = r.asList(type);
      if (batch.isEmpty()) return new Page<>(items, false, "réponse illisible");
      items.addAll(batch.get());
      page = nextPage(r);
    }
    boolean truncated = items.size() > max || !page.isBlank();
    return new Page<>(
        items.size() > max ? new ArrayList<>(items.subList(0, max)) : items, truncated, null);
  }

  // ----------------------------------------------------------------------
  // Parallélisme
  // ----------------------------------------------------------------------

  /**
   * Le coût de ces outils n'est pas le calcul, c'est l'attente : un appel GitLab tourne autour de
   * 100 à 300 ms, et jusqu'ici on les faisait un par un. Sur un parc de 6 000 projets, la passe
   * profonde y passe des heures sans que la machine ni l'instance ne travaillent — les deux
   * attendent le réseau.
   *
   * <p>Les faire se recouvrir ne change aucune mesure : chaque projet est lu indépendamment des
   * autres, et rien dans les étapes par projet ne dépend de l'ordre. La borne, elle, compte : le
   * limiteur de débit de GitLab est configuré par instance et souvent abaissé, donc on plafonne le
   * nombre d'appels en vol plutôt que de lâcher 200 requêtes d'un coup. Le 429 reste traité comme
   * avant, avec Retry-After — la borne le rend rare, elle ne le rend pas impossible.
   *
   * <p>Les exceptions ne sont pas avalées : une tâche qui échoue relance son exception à
   * l'appelant, comme si la boucle était restée séquentielle.
   */
  public <T> void parallel(List<T> items, int concurrency, Consumer<T> work) {
    Parallel.forEach(items, concurrency, work);
  }

  /** Même chose, en conservant les résultats dans l'ordre des entrées. */
  public <T, R> List<R> map(List<T> items, int concurrency, Function<T, R> work) {
    return Parallel.map(items, concurrency, work::apply);
  }

  /** Un en-tête X-Next-Page absent vaut un en-tête vide : la fin. */
  private static String nextPage(Response r) {
    return r.headers().getOrDefault("x-next-page", "");
  }

  private static Map<String, String> pageParams(Map<String, String> params, String page) {
    Map<String, String> p = new LinkedHashMap<>(params);
    p.put("per_page", "100");
    p.put("page", page);
    return p;
  }

  // ----------------------------------------------------------------------
  // Les trois conventions
  // ----------------------------------------------------------------------

  /**
   * Non filtrés, Renovate et consorts finissent premiers contributeurs et faussent tout : cadence,
   * concentration, taille des commits.
   *
   * <p>Le motif est l'union de ceux que les deux outils s'étaient donnés séparément. Il reste
   * étroit à dessein : « bot » collé à d'autres lettres attraperait Abbot ou Talbot, d'où les
   * bornes autour du mot nu. Les identités écartées se rapportent, elles ne se cachent pas.
   */
  public static final String BOT_PATTERN =
      "(?i)(renovate|dependabot|semantic-release|\\[bot\\]|bot@|gitlab-ci-token"
          + "|jenkins|sonarqube|automation)"
          + "|(^|[^\\p{L}])bots?([^\\p{L}]|$)"
          + "|(^|[^\\p{L}])project_\\d+_bot";

  public static boolean isBot(Pattern bots, @Nullable String email, @Nullable String name) {
    return bots.matcher(orEmpty(email) + " " + orEmpty(name)).find();
  }

  /**
   * Deux adresses pour la même personne fragmentent le compte d'auteurs. On normalise sur la partie
   * locale de l'adresse, à défaut sur le nom. Le résultat vaut à ±1 : il alimente un jugement de
   * bus factor, il lui suffit d'être juste entre 1 et 5.
   */
  public static String identity(@Nullable String email, @Nullable String name) {
    String raw =
        (email != null && email.contains("@")) ? email.substring(0, email.indexOf('@')) : name;
    if (raw == null || raw.isBlank()) return "?";
    // Les deux branches doivent atterrir dans le même espace de noms : sinon
    // « Dev 0 » (commit sans adresse) et « dev0@example.com » comptent pour
    // deux personnes, et le bus factor double silencieusement.
    String norm = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    return norm.isEmpty() ? "?" : norm;
  }

  /**
   * Un commit à plus d'un parent est une fusion. Il ne porte aucun travail propre — ses lignes sont
   * celles des commits qu'il réunit — et sa date est celle du clic sur « fusionner », par quelqu'un
   * qui n'a pas écrit le code. Mesuré sur gitlab-org/cli : les compter changeait le contributeur
   * principal, de la personne qui écrit à celle qui fusionne.
   */
  public static boolean isMerge(@Nullable JsonNode commit) {
    return commit != null && commit.path("parent_ids").size() > 1;
  }

  public static boolean isMerge(@Nullable List<String> parentIds) {
    return parentIds != null && parentIds.size() > 1;
  }

  // ----------------------------------------------------------------------
  // Dates et chaînes
  // ----------------------------------------------------------------------

  /**
   * GitLab date en ISO-8601 avec décalage : « 2026-08-18T09:12:33.000+02:00 », ou en Z. Le décalage
   * porte du sens — c'est le fuseau de la machine qui a commité, donc l'heure locale de la personne
   * — et se conserve ici.
   */
  public static @Nullable OffsetDateTime parse(@Nullable String s) {
    if (s == null || s.isBlank()) return null;
    try {
      return OffsetDateTime.parse(s);
    } catch (DateTimeParseException e) {
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

  private static boolean isBlank(@Nullable String s) {
    return s == null || s.isBlank();
  }

  private static String orEmpty(@Nullable String s) {
    return s == null ? "" : s;
  }

  static String truncate(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max);
  }
}
