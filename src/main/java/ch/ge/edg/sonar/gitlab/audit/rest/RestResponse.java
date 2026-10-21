package ch.ge.edg.sonar.gitlab.audit.rest;

import ch.ge.edg.sonar.gitlab.audit.text.Text;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Ce qu'un appel HTTP a donné, pour tous les clients : une réponse du serveur ({@link Answered}),
 * ou aucune ({@link Unreachable}). Il n'y a pas de statut 0 : une absence de réponse est un état à
 * part, et sa raison ne se confond pas avec un corps.
 *
 * <p>{@link #ok()} est le seul succès : un 2xx qui n'est pas une page HTML. Un appelant qui attend
 * un statut précis le dit par {@link #is(int)}.
 */
public sealed interface RestResponse {

  ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  /**
   * Le serveur a répondu.
   *
   * @param headers en-têtes en minuscules, première valeur seulement
   */
  record Answered(int status, String body, Map<String, String> headers) implements RestResponse {

    public Answered {
      headers = Map.copyOf(headers);
    }
  }

  /** Pas de réponse : coupure, délai, TLS, URL refusée, capture absente… */
  record Unreachable(String reason) implements RestResponse {}

  static RestResponse unreachable(String reason) {
    return new Unreachable(reason);
  }

  /** Vrai si le serveur a répondu exactement ce statut. */
  default boolean is(int status) {
    return this instanceof Answered a && a.status() == status;
  }

  default boolean unreachable() {
    return this instanceof Unreachable;
  }

  /** Un 401 : le jeton est refusé, pour cette requête comme pour toutes les suivantes. */
  default boolean refused() {
    return is(401);
  }

  /**
   * Un 2xx qui n'est pas du HTML. Un proxy SSO ou une page de login répondent 200 avec du HTML :
   * compter ça comme un succès ferait croire à un accès qu'on n'a pas.
   */
  default boolean ok() {
    return this instanceof Answered a && a.status() >= 200 && a.status() < 300 && !html();
  }

  /** Le corps reçu ; vide sans réponse. */
  default String body() {
    return this instanceof Answered a ? a.body() : "";
  }

  default String header(String name) {
    return this instanceof Answered a
        ? a.headers().getOrDefault(name.toLowerCase(Locale.ROOT), "")
        : "";
  }

  /** Un en-tête numérique, vide s'il manque ou n'est pas un entier. */
  default Optional<Integer> intHeader(String name) {
    try {
      String v = header(name).trim();
      return v.isEmpty() ? Optional.empty() : Optional.of(Integer.parseInt(v));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  default boolean html() {
    if (!(this instanceof Answered a)) return false;
    String start = a.body().stripLeading().toLowerCase(Locale.ROOT);
    return header("content-type").contains("text/html")
        || start.startsWith("<!doctype")
        || start.startsWith("<html");
  }

  /** Le corps en JSON, quel que soit le statut ; vide sans réponse ou si le corps n'en est pas. */
  default Optional<JsonNode> json() {
    if (!(this instanceof Answered a) || a.body().isBlank()) return Optional.empty();
    try {
      return Optional.ofNullable(MAPPER.readTree(a.body()));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  /**
   * Le corps en JSON pour s'y promener : un nœud absent quand {@link #json()} est vide, que {@code
   * path()} traverse sans rien trouver.
   */
  default JsonNode tree() {
    return json().orElse(MissingNode.getInstance());
  }

  /** Lecture typée d'un succès ; vide sur un échec ou un corps illisible. */
  default <T> Optional<T> as(Class<T> type) {
    if (!ok() || body().isBlank()) return Optional.empty();
    try {
      return Optional.ofNullable(MAPPER.readValue(body(), type));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  /** Comme {@link #as}, pour un tableau : vide n'est pas « aucun élément », c'est « illisible ». */
  default <T> Optional<List<T>> asList(Class<T> type) {
    if (!ok() || body().isBlank()) return Optional.empty();
    try {
      return Optional.ofNullable(
          MAPPER.readValue(
              body(), MAPPER.getTypeFactory().constructCollectionType(List.class, type)));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  /** « HTTP 404 », ou « injoignable » : le statut tel qu'on l'affiche. */
  default String statusLine() {
    return this instanceof Answered a ? "HTTP " + a.status() : "injoignable";
  }

  /** Ce qu'on affiche à la place d'un code : OK, 401, 403, HTML, ERR… */
  default String tag() {
    if (!(this instanceof Answered a)) return "ERR";
    if (a.status() >= 200 && a.status() < 300) return html() ? "HTML" : "OK";
    return String.valueOf(a.status());
  }

  /** Une raison courte, sans recopier le corps : il peut contenir des noms. */
  default String why() {
    return switch (this) {
      case Unreachable u -> u.reason();
      case Answered _ when html() -> "page HTML (login, SSO ou proxy) au lieu de JSON";
      case Answered a when a.status() >= 300 && a.status() < 400 ->
          "redirection vers " + pathOnly(header("location"));
      case Answered a -> refusal(a.status());
    };
  }

  private static String refusal(int status) {
    return switch (status) {
      case 401 -> "non authentifié";
      case 403 -> "authentifié mais pas autorisé";
      case 404 -> "introuvable (ou masqué faute de droit)";
      case 406 -> "en-tête Accept refusé";
      default -> "HTTP " + status;
    };
  }

  /**
   * Le message d'erreur du serveur, tronqué : Sonar le met dans errors[].msg, GitLab dans message,
   * error ou error_description selon l'endpoint ; à défaut, le corps lui-même. Sans réponse, la
   * raison.
   */
  default String message() {
    if (this instanceof Unreachable u) return Text.truncate(u.reason().replace("\n", " "), 90);
    if (is(429)) return "limite de débit atteinte (429)";
    JsonNode j = tree();
    JsonNode errors = j.path("errors");
    if (errors.isArray() && !errors.isEmpty()) {
      String msgs =
          StreamSupport.stream(errors.spliterator(), false)
              .map(e -> e.path("msg"))
              .filter(JsonNode::isTextual)
              .map(JsonNode::asText)
              .filter(Objects::nonNull)
              .collect(Collectors.joining("; "));
      if (!msgs.isEmpty()) return Text.truncate(msgs, 90);
    }
    for (String k : List.of("message", "error", "error_description")) {
      if (j.hasNonNull(k) && j.get(k).isTextual()) return Text.truncate(j.get(k).asText(), 90);
    }
    return Text.truncate(body().replace("\n", " "), 90);
  }

  static String pathOnly(String url) {
    return url.replaceFirst("^https?://[^/]+", "").replaceFirst("\\?.*$", "");
  }
}
