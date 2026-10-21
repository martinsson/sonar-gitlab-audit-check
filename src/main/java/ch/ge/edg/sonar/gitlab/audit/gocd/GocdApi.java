package ch.ge.edg.sonar.gitlab.audit.gocd;

import ch.ge.edg.sonar.gitlab.audit.rest.AuthHeaders;
import ch.ge.edg.sonar.gitlab.audit.rest.Capture;
import ch.ge.edg.sonar.gitlab.audit.rest.Query;
import ch.ge.edg.sonar.gitlab.audit.rest.RestClient;
import ch.ge.edg.sonar.gitlab.audit.rest.RestClient.Settings;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import ch.ge.edg.sonar.gitlab.audit.runtime.Sleeper;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * GoCD en lecture. GoCD versionne chaque API par l'en-tête Accept et répond 404 à une version qu'il
 * ne connaît pas : on essaie la forme sans version (la plus récente, acceptée partout en 25.3),
 * puis les versions nommées, pour un serveur plus ancien.
 */
public final class GocdApi {

  static final String LATEST = "application/vnd.go.cd+json";

  private static final Pattern SLASH = Pattern.compile("/");

  private final RestClient http;
  private final Map<String, String> acceptSeen = new LinkedHashMap<>();
  private final Map<String, Optional<JsonNode>> instances = new HashMap<>();

  private GocdApi(RestClient http) {
    this.http = http;
  }

  /**
   * Un client authentifié par un PAT en Bearer. L'URL se donne avec ou sans « /go » final : les
   * chemins d'API le remettent.
   *
   * @param url l'option, ou GOCD_URL
   * @param token l'option, ou GOCD_TOKEN
   * @param sleeper l'attente avant un nouvel essai
   * @throws Stop si l'URL ou le jeton manque
   */
  public static GocdApi connect(
      @Nullable String url, @Nullable String token, boolean insecure, Sleeper sleeper)
      throws Stop, IOException {
    String base = Stop.require(url, "GOCD_URL").replaceAll("/+$", "").replaceFirst("/go$", "");
    Map<String, String> headers = AuthHeaders.bearer(Stop.require(token, "GOCD_TOKEN"));
    return new GocdApi(
        new RestClient(Settings.of(base, headers, insecure), Capture.none(), sleeper));
  }

  /** La racine de l'instance, sans « /go ». */
  public String base() {
    return http.base();
  }

  /** Les essais faits, nouveaux essais compris. */
  public int calls() {
    return http.calls();
  }

  /** La réponse telle quelle, sauf un 401 : voir {@link Stop#unlessRefused}. */
  public RestResponse unlessRefused(RestResponse r) throws Stop {
    return Stop.unlessRefused(r, base());
  }

  /** API → en-tête Accept qui a marché, pour le rapport. */
  public Map<String, String> acceptSeen() {
    return Map.copyOf(acceptSeen);
  }

  /** Un GET d'API, en essayant chaque version d'Accept tant que GoCD répond 404 ou 406. */
  public RestResponse api(String path, String... versions) {
    List<String> accepts = new ArrayList<>();
    accepts.add(LATEST);
    for (String v : versions) accepts.add("application/vnd.go.cd." + v + "+json");
    RestResponse last = RestResponse.unreachable("aucun essai");
    String accepted = LATEST;
    for (String accept : accepts) {
      last = http.get(path, Map.of("Accept", accept));
      accepted = accept;
      if (!last.is(404) && !last.is(406)) break;
    }
    if (last.ok()) acceptSeen.putIfAbsent(apiName(path), accepted);
    return last;
  }

  static String apiName(String path) {
    List<String> parts =
        SLASH.splitAsStream(path.replaceFirst("^.*?/go/api/", "").replaceAll("\\?.*", "")).toList();
    return parts.get(0).equals("admin") && parts.size() > 1
        ? "admin/" + parts.get(1)
        : parts.get(0);
  }

  public RestResponse dashboard() {
    return api("/go/api/dashboard", "v4", "v3", "v2", "v1");
  }

  /** Un run, mis en cache : un run amont est souvent lu par plusieurs runs aval. */
  public Optional<JsonNode> instance(String pipeline, int counter) {
    String key = pipeline + "/" + counter;
    Optional<JsonNode> cached = instances.get(key);
    if (cached != null) return cached;
    RestResponse r = api("/go/api/pipelines/" + enc(pipeline) + "/" + counter, "v1");
    Optional<JsonNode> run = r.ok() ? r.json() : Optional.empty();
    instances.put(key, run);
    return run;
  }

  public boolean cached(String pipeline, int counter) {
    return instances.containsKey(pipeline + "/" + counter);
  }

  /**
   * L'historique d'un pipeline, du plus récent au plus ancien, page après page, jusqu'au premier
   * run que {@code stop} refuse (ce run n'est pas gardé) ou jusqu'à {@code maxPages}. Chaque run lu
   * entre dans le cache des instances.
   */
  public History history(String pipeline, Predicate<JsonNode> stop, int maxPages) {
    List<JsonNode> runs = new ArrayList<>();
    String next = "/go/api/pipelines/" + enc(pipeline) + "/history?page_size=100";
    RestResponse first = null;
    int pages = 0;
    boolean complete = false;
    while (next != null && pages < maxPages) {
      RestResponse page = api(next, "v1");
      if (first == null) first = page;
      if (!page.ok()) break;
      pages++;
      Page p = read(pipeline, page.tree(), stop, runs);
      complete = p.exhausted();
      next = p.next();
    }
    return new History(runs, pages, complete, first == null ? RestResponse.unreachable("") : first);
  }

  private record Page(@Nullable String next, boolean exhausted) {}

  private Page read(String pipeline, JsonNode page, Predicate<JsonNode> stop, List<JsonNode> runs) {
    for (JsonNode run : page.path("pipelines")) {
      if (stop.test(run)) return new Page(null, false);
      runs.add(run);
      instances.put(pipeline + "/" + run.path("counter").asInt(), Optional.of(run));
    }
    String href = page.path("_links").path("next").path("href").asText("");
    boolean last = href.isEmpty() || page.path("pipelines").isEmpty();
    return new Page(last ? null : href, last);
  }

  /**
   * Un historique lu.
   *
   * @param runs du plus récent au plus ancien
   * @param complete vrai si GoCD n'a plus de page à donner
   * @param first la première réponse, pour dire pourquoi l'historique est vide
   */
  public record History(List<JsonNode> runs, int pages, boolean complete, RestResponse first) {}

  public static String enc(String s) {
    return Query.segment(s);
  }
}
