package ch.ge.edg.sonar.gitlab.audit.jira;

import ch.ge.edg.sonar.gitlab.audit.rest.RestClient;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Jira Server / Data Center, API REST v2, en lecture. Chaque méthode rend la réponse telle quelle :
 * c'est à l'appelant de dire si un échec arrête tout (une extraction) ou se rapporte (une sonde).
 */
public final class JiraApi {

  private final RestClient http;

  public JiraApi(RestClient http) {
    this.http = http;
  }

  public RestClient http() {
    return http;
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
    if (r.status() == 404 || r.status() == 410) r = http.get("/rest/api/latest/search" + q);
    return r;
  }

  /** Le nombre de tickets d'une requête, sans en lire aucun ; -1 si la recherche échoue. */
  public long count(String jql) {
    RestResponse r = search(jql, 0, 0, "fields=none");
    return r.ok() ? r.json().path("total").asLong(-1) : -1;
  }

  /** id de statut → catégorie (new, indeterminate, done), vide si illisible. */
  public Map<String, String> statusCategories() {
    RestResponse r = statuses();
    return r.ok() ? statusCategories(r.json()) : new HashMap<>();
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
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
