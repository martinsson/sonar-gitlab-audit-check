package ch.ge.edg.sonar.gitlab.audit.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * Une réponse HTTP. {@code status} 0 : pas de réponse du tout, la raison est dans {@code error}.
 *
 * @param headers en-têtes en minuscules, première valeur seulement
 */
public record RestResponse(int status, String body, Map<String, String> headers, String error) {

  private static final ObjectMapper JSON = new ObjectMapper();

  public static RestResponse unreachable(String error) {
    return new RestResponse(0, "", Map.of(), error);
  }

  public String header(String name) {
    return headers.getOrDefault(name.toLowerCase(Locale.ROOT), "");
  }

  /**
   * Un proxy SSO ou une page de login répondent 200 avec du HTML : compter ça comme un succès
   * ferait croire à un accès qu'on n'a pas.
   */
  public boolean html() {
    String start = body.stripLeading().toLowerCase(Locale.ROOT);
    return header("content-type").contains("text/html")
        || start.startsWith("<!doctype")
        || start.startsWith("<html");
  }

  public boolean ok() {
    return status >= 200 && status < 300 && !html();
  }

  /** Le corps en JSON ; un nœud absent s'il n'en est pas. */
  public JsonNode json() {
    try {
      return JSON.readTree(body);
    } catch (IOException e) {
      return MissingNode.getInstance();
    }
  }

  /** Ce qu'on affiche à la place d'un code : OK, 401, 403, HTML, ERR… */
  public String tag() {
    if (status == 0) return "ERR";
    if (status >= 200 && status < 300) return html() ? "HTML" : "OK";
    return String.valueOf(status);
  }

  /** Une raison courte, sans recopier le corps : il peut contenir des noms. */
  public String why() {
    if (status == 0) return error;
    if (html()) return "page HTML (login, SSO ou proxy) au lieu de JSON";
    if (status >= 300 && status < 400) return "redirection vers " + pathOnly(header("location"));
    return switch (status) {
      case 401 -> "non authentifié";
      case 403 -> "authentifié mais pas autorisé";
      case 404 -> "introuvable (ou masqué faute de droit)";
      case 406 -> "en-tête Accept refusé";
      default -> "HTTP " + status;
    };
  }

  static String pathOnly(String url) {
    return url.replaceFirst("^https?://[^/]+", "").replaceFirst("\\?.*$", "");
  }
}
