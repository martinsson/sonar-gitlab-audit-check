package ch.ge.edg.sonar.gitlab.audit.rest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/** Les trois façons d'entrer qu'acceptent Jira et GoCD, en en-têtes prêts à poser. */
public final class AuthHeaders {

  private AuthHeaders() {}

  /** Un Personal Access Token, comme Jira Data Center et GoCD les attendent. */
  public static Map<String, String> bearer(String token) {
    return Map.of("Authorization", "Bearer " + token.trim());
  }

  public static Map<String, String> basic(String user, String password) {
    String pair = user + ":" + password;
    return Map.of(
        "Authorization",
        "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8)));
  }

  /** Un cookie copié du navigateur : « JSESSIONID=… », ou la seule valeur du JSESSIONID. */
  public static Map<String, String> cookie(String value) {
    String v = value.trim();
    return Map.of("Cookie", v.contains("=") ? v : "JSESSIONID=" + v);
  }
}
