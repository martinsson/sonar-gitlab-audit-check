package ch.ge.edg.sonar.gitlab.audit.jira;

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
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Jira Server / Data Center, API REST v2, en lecture. Chaque méthode rend la réponse telle quelle :
 * c'est à l'appelant de dire si un échec arrête tout (une extraction) ou se rapporte (une sonde).
 */
public final class JiraApi {

  private final RestClient http;

  private JiraApi(RestClient http) {
    this.http = http;
  }

  /**
   * Un client authentifié par un PAT Jira Data Center, envoyé en Bearer.
   *
   * @param url l'option, ou JIRA_URL
   * @param token l'option, ou JIRA_TOKEN
   * @param sleeper l'attente avant un nouvel essai
   * @throws Stop si l'URL ou le jeton manque
   */
  public static JiraApi connect(
      @Nullable String url, @Nullable String token, boolean insecure, Sleeper sleeper)
      throws Stop, IOException {
    String base = Stop.require(url, "JIRA_URL");
    Map<String, String> headers =
        new HashMap<>(AuthHeaders.bearer(Stop.require(token, "JIRA_TOKEN")));
    headers.put("Accept", "application/json");
    return new JiraApi(
        new RestClient(Settings.of(base, headers, insecure), Capture.none(), sleeper));
  }

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

  public RestResponse serverInfo() {
    return http.get("/rest/api/2/serverInfo");
  }

  public RestResponse myself() {
    return http.get("/rest/api/2/myself");
  }

  public RestResponse projects() {
    return http.get("/rest/api/2/project");
  }

  public RestResponse project(String key) {
    return http.get("/rest/api/2/project/" + enc(key));
  }

  public RestResponse versions(String projectKey) {
    return http.get("/rest/api/2/project/" + enc(projectKey) + "/versions");
  }

  public RestResponse fields() {
    return http.get("/rest/api/2/field");
  }

  public RestResponse statuses() {
    return http.get("/rest/api/2/status");
  }

  public RestResponse issue(String key, String query) {
    return http.get("/rest/api/2/issue/" + enc(key) + (query.isEmpty() ? "" : "?" + query));
  }

  /**
   * Une page de recherche JQL. Si une version retire /rest/api/2/search, comme Jira Cloud l'a fait,
   * on retente sur /rest/api/latest/search.
   *
   * @param params fields, expand… déjà encodés, sans « & » initial ; vide si aucun
   */
  public RestResponse search(String jql, int startAt, int maxResults, String params) {
    String q =
        "?jql="
            + enc(jql)
            + "&startAt="
            + startAt
            + "&maxResults="
            + maxResults
            + (params.isEmpty() ? "" : "&" + params);
    RestResponse r = http.get("/rest/api/2/search" + q);
    if (r.is(404) || r.is(410)) r = http.get("/rest/api/latest/search" + q);
    return r;
  }

  /** Le nombre de tickets d'une requête, sans en lire aucun ; -1 si la recherche échoue. */
  public long count(String jql) {
    RestResponse r = search(jql, 0, 0, "fields=none");
    return r.ok() ? r.tree().path("total").asLong(-1) : -1;
  }

  /** id de statut → catégorie (new, indeterminate, done), vide si illisible. */
  public Map<String, String> statusCategories() {
    RestResponse r = statuses();
    return r.ok() ? statusCategories(r.tree()) : new HashMap<>();
  }

  /** id de statut → catégorie, lu dans une réponse de /rest/api/2/status déjà obtenue. */
  public static Map<String, String> statusCategories(JsonNode statuses) {
    Map<String, String> out = new HashMap<>();
    for (JsonNode s : statuses) {
      out.put(s.path("id").asText(), s.path("statusCategory").path("key").asText(""));
    }
    return out;
  }

  public static String enc(String s) {
    return Query.segment(s);
  }
}
