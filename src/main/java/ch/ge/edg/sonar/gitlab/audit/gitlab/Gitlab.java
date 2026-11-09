package ch.ge.edg.sonar.gitlab.audit.gitlab;

import ch.ge.edg.sonar.gitlab.audit.parallel.Parallel;
import ch.ge.edg.sonar.gitlab.audit.rest.Query;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import ch.ge.edg.sonar.gitlab.audit.runtime.Sleeper;
import ch.ge.edg.sonar.gitlab.audit.text.Times;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Accès GitLab partagé par les deux outils qui en lisent : GitlabActivityAudit, qui classe un
 * groupe entier, et GitLabProjectReport, qui lit un projet en détail.
 *
 * <p>Les deux avaient été écrits séparément, et étaient tombés d'accord sans se concerter sur les
 * pièges qui comptent — les bots, les commits de fusion, une pagination plafonnée qui doit se lire
 * comme un plancher. Deux accords fortuits ne survivent pas à la première correction apportée d'un
 * seul côté : d'où ce fichier, qui rend l'accord explicite.
 *
 * <p>Ce qui reste chez chaque outil : ce qu'il mesure, et ce qu'il en conclut. Ce qui vient ici :
 * parler à l'API, et la pagination plafonnée. Qui compte comme contributeur (bots, identités,
 * fusions) vit dans {@code contributors.Contributors}.
 */
public final class Gitlab {

  /** Les réponses GitLab : un champ inconnu est ignoré. */
  public static final ObjectReader READER = mapper().reader();

  /** Les requêtes GitLab, en JSON compact. */
  public static final ObjectWriter WRITER = mapper().writer();

  /** Le JSON lisible, pour un fichier de cache. */
  public static final ObjectWriter PRETTY = mapper().writerWithDefaultPrettyPrinter();

  private static ObjectMapper mapper() {
    return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  /**
   * Une date d'une réponse GitLab : ISO-8601 avec décalage ; absente ou illisible, null. Un nombre
   * y est lu comme un epoch en millisecondes, comme il l'a toujours été.
   */
  public static @Nullable Instant instant(@Nullable JsonNode n) {
    if (n == null || n.isMissingNode() || n.isNull()) return null;
    if (n.isNumber()) return Instant.ofEpochMilli(n.asLong());
    return Times.parse(n.asText(""));
  }

  // ----------------------------------------------------------------------
  // Pages
  // ----------------------------------------------------------------------

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
  private final GitlabListener listener;

  public int calls() {
    return http.calls();
  }

  public int forbidden() {
    return http.forbiddenCount.get();
  }

  public int throttled() {
    return http.throttled();
  }

  /**
   * Un client.
   *
   * @param listener reçoit une pagination interrompue et une capture qui n'a pas pu s'écrire
   * @param sleeper l'attente que demande un 429
   */
  public Gitlab(Connection c, GitlabListener listener, Sleeper sleeper) throws IOException {
    this.base = c.url().replaceAll("/+$", "");
    this.listener = listener;
    this.http = new GitlabHttp(c, listener, sleeper);
  }

  public RestResponse get(String path, Map<String, String> params) {
    return http.send("GET", base + "/api/v4/" + path.replaceAll("^/+", "") + query(params), null);
  }

  /** Existence d'un fichier sans transférer son contenu. */
  public boolean exists(String path, Map<String, String> params) {
    return http.send("HEAD", base + "/api/v4/" + path.replaceAll("^/+", "") + query(params), null)
        .is(200);
  }

  public RestResponse graphql(String q) {
    String payload;
    try {
      payload = WRITER.writeValueAsString(Map.of("query", q));
    } catch (IOException e) {
      return RestResponse.unreachable(e.toString());
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
      RestResponse r = get(path, pageParams(params, page));
      if (!r.is(200)) {
        listener.pagingInterrupted(page, r.statusLine());
        break;
      }
      JsonNode arr = r.tree();
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
      RestResponse r = get(path, pageParams(params, page));
      if (!r.ok()) {
        return new Page<>(items, false, r.statusLine() + " — " + r.message());
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
  private static String nextPage(RestResponse r) {
    return r.header("x-next-page");
  }

  private static Map<String, String> pageParams(Map<String, String> params, String page) {
    Map<String, String> p = new LinkedHashMap<>(params);
    p.put("per_page", "100");
    p.put("page", page);
    return p;
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
    return Query.enc(s);
  }

  private static String query(Map<String, String> params) {
    return Query.string(params);
  }
}
