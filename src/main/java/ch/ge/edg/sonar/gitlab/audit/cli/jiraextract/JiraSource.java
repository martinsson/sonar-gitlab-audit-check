package ch.ge.edg.sonar.gitlab.audit.cli.jiraextract;

import ch.ge.edg.sonar.gitlab.audit.jira.JiraApi;
import ch.ge.edg.sonar.gitlab.audit.rest.AuthHeaders;
import ch.ge.edg.sonar.gitlab.audit.rest.Capture;
import ch.ge.edg.sonar.gitlab.audit.rest.RestClient;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import ch.ge.edg.sonar.gitlab.audit.runtime.Sleeper;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Les appels que fait l'extraction, et ce qu'un échec veut dire : un 401 arrête tout, où qu'il
 * arrive ; les champs, les statuts, la recherche et la relecture d'un ticket sont indispensables ;
 * les versions d'un projet ne le sont pas.
 */
final class JiraSource {

  /** Tickets par page de recherche. */
  static final int PAGE = 100;

  /** Ce que la recherche demande en plus : tous les champs, et l'historique. */
  static final String EVERYTHING = "fields=*all&expand=changelog";

  private static final int EXCERPT = 300;

  private final JiraApi api;

  public JiraSource(JiraApi api) {
    this.api = api;
  }

  /** Un PAT Jira Data Center, envoyé en Bearer. */
  public static JiraSource connect(
      @Nullable String url, @Nullable String token, boolean insecure, Sleeper sleeper)
      throws ExtractionStop, IOException {
    String base = require(url, "JIRA_URL");
    Map<String, String> headers = new HashMap<>(AuthHeaders.bearer(require(token, "JIRA_TOKEN")));
    headers.put("Accept", "application/json");
    RestClient http =
        new RestClient(RestClient.Settings.of(base, headers, insecure), Capture.none(), sleeper);
    return new JiraSource(new JiraApi(http));
  }

  static String require(@Nullable String value, String env) throws ExtractionStop {
    if (value == null || value.isBlank()) throw new ExtractionStop(env + " manquant");
    return value.trim();
  }

  public int calls() {
    return api.http().calls();
  }

  public JsonNode fields() throws ExtractionStop {
    return must(api.fields(), "/rest/api/2/field");
  }

  /** id de statut → catégorie (new, indeterminate, done). */
  public Map<String, String> statusCategories() throws ExtractionStop {
    return JiraApi.statusCategories(must(api.statuses(), "/rest/api/2/status"));
  }

  /** Une page de recherche ; l'erreur garde un extrait du corps, où Jira dit ce qui cloche. */
  public JsonNode search(String jql, int startAt) throws ExtractionStop {
    RestResponse r = guard(api.search(jql, startAt, PAGE, EVERYTHING));
    if (!r.ok()) {
      throw new ExtractionStop(("search : " + r.why() + " " + excerpt(r.body())).strip());
    }
    return r.json();
  }

  /** Un ticket seul, avec tous ses champs et son historique complet. */
  public JsonNode issue(String key) throws ExtractionStop {
    return must(api.issue(key, "expand=changelog&fields=*all"), "/rest/api/2/issue/" + key);
  }

  /** Les versions d'un projet ; un échec se rapporte, il n'arrête pas. */
  public RestResponse versions(String project) throws ExtractionStop {
    return guard(api.versions(project.toUpperCase(Locale.ROOT)));
  }

  static String excerpt(String body) {
    String flat = body.replaceAll("\\s+", " ");
    return flat.substring(0, Math.min(EXCERPT, flat.length()));
  }

  private JsonNode must(RestResponse r, String path) throws ExtractionStop {
    if (!guard(r).ok()) throw new ExtractionStop(path + " : " + r.why());
    return r.json();
  }

  private RestResponse guard(RestResponse r) throws ExtractionStop {
    if (r.status() == 401) throw new ExtractionStop(api.http().base() + " : jeton refusé (401)");
    return r;
  }
}
