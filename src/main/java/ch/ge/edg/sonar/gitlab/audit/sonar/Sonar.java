package ch.ge.edg.sonar.gitlab.audit.sonar;

import ch.ge.edg.sonar.gitlab.audit.parallel.Parallel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Accès SonarQube partagé par les outils qui en lisent.
 *
 * <p>Pendant de {@code Gitlab} pour l'autre moitié de l'audit, et écrit après lui pour la même
 * raison : l'attente réseau domine tout le reste. Depuis que l'inventaire demande ses branches à
 * chaque projet, un parc de 500 projets coûte 500 allers-retours — faits un par un, la machine et
 * l'instance passent leur temps à ne rien faire.
 *
 * <p>Trois choses vivent ici, et rien de ce que l'audit conclut :
 *
 * <p>parler à l'API — un GET, un jeton, l'organisation quand il le faut ({@link Transport}) ; tenir
 * la cadence — appels concurrents bornés, 429 et 5xx retentés ; rejouer une capture — {@code
 * --dump-dir} enregistre, {@code --replay-dir} relit, et l'outil tourne alors sans instance ({@link
 * Captures}).
 */
public final class Sonar {

  public static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  /** Appels concurrents par défaut : assez pour que l'attente se recouvre. */
  public static final int DEFAULT_CONCURRENCY = 8;

  /**
   * Endpoints qui exigent (ou acceptent) 'organization' sur SonarQube Cloud.
   *
   * <p>api/projects/search en fait partie : sans le paramètre, Cloud répond 400 et le diagnostic
   * conclut à tort « nécessite Administer System » — soit un faux point aveugle sur le calcul le
   * plus important de l'outil.
   *
   * <p>La liste reste volontairement courte : les endpoints portés par un composant (measures/*,
   * components/tree, sources/scm, project_analyses, settings/values) n'acceptent pas le paramètre,
   * et l'ajouter provoquerait l'erreur qu'on cherche à éviter. Le paramètre n'est de toute façon
   * envoyé que si --organization est fourni, ce qui ne concerne que Cloud.
   *
   * <p>NON VÉRIFIÉ sur une instance Cloud réelle : sonarcloud.io était injoignable depuis
   * l'environnement de test. Déduit de la documentation de l'API.
   */
  private static final List<String> ORGANIZATION_PATHS =
      List.of(
          "api/components/search_projects",
          "api/qualityprofiles",
          "api/projects/search",
          "api/issues/search",
          "api/qualitygates/get_by_project");

  // ----------------------------------------------------------------------
  // Réponse
  // ----------------------------------------------------------------------

  /** Statut + corps brut. Le corps est toujours présent, y compris sur erreur. */
  public record Response(int status, @Nullable String body) {

    public boolean unreachable() {
      return status == 0;
    }

    public boolean ok() {
      return status == 200;
    }

    /** null si le statut n'est pas 200 ou si le corps n'est pas le JSON attendu. */
    public <T> @Nullable T as(Class<T> type) {
      if (status != 200 || body == null) return null;
      try {
        return MAPPER.readValue(body, type);
      } catch (IOException e) {
        return null;
      }
    }

    public String errorMessage() {
      if (body == null) return "";
      try {
        ErrorResponse e = MAPPER.readValue(body, ErrorResponse.class);
        List<ErrorMessage> errors = e.errors();
        if (errors != null && !errors.isEmpty()) {
          return truncate(
              errors.stream()
                  .map(ErrorMessage::msg)
                  .filter(Objects::nonNull)
                  .collect(Collectors.joining("; ")),
              90);
        }
      } catch (IOException ignored) {
        // corps non-JSON : on le montre tel quel
      }
      return truncate(body.replace("\n", " "), 90);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ErrorResponse(@Nullable List<ErrorMessage> errors) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ErrorMessage(@Nullable String msg) {}

  // ----------------------------------------------------------------------
  // Client
  // ----------------------------------------------------------------------

  public final String base;
  private final @Nullable String organization;
  private final Captures captures;
  private final Transport transport;
  private final AtomicInteger callCount = new AtomicInteger();

  public Sonar(SonarConnection connection) throws IOException, GeneralSecurityException {
    this(connection, Captures.none());
  }

  public Sonar(SonarConnection connection, Captures captures)
      throws IOException, GeneralSecurityException {
    String url = connection.url();
    this.base = url == null ? "" : url.replaceAll("/+$", "");
    String org = connection.organization();
    this.organization = org == null || org.isBlank() ? null : org;
    this.captures = captures;
    captures.prepare();
    this.transport = new Transport(connection, callCount);
  }

  public int calls() {
    return callCount.get();
  }

  public int throttled() {
    return transport.throttled();
  }

  public int retried() {
    return transport.retried();
  }

  /** Captures réclamées et absentes : en rejeu, c'est la seule panne possible. */
  public int missingCaptures() {
    return captures.missing();
  }

  public boolean replaying() {
    return captures.replaying();
  }

  public Response get(String path, Map<String, String> params) {
    String cleanPath = path.replaceAll("^/+", "");
    Map<String, String> all = new LinkedHashMap<>(params);
    if (organization != null && needsOrganization(cleanPath)) {
      all.putIfAbsent("organization", organization);
    }
    String capture = Captures.name(cleanPath, all);
    Optional<Response> replayed = captures.replay(capture, cleanPath, all);
    if (replayed.isPresent()) {
      callCount.incrementAndGet();
      return replayed.get();
    }
    Response r = transport.send(base + "/" + cleanPath + queryString(all));
    captures.dump(capture, cleanPath + queryString(all), r.body());
    return r;
  }

  // ----------------------------------------------------------------------
  // Parallélisme
  // ----------------------------------------------------------------------

  /** Voir {@link Parallel#map} : largeur bornée, ordre conservé, exceptions relancées. */
  public <T, R> List<R> map(List<T> items, int concurrency, Function<T, R> work) {
    return Parallel.map(items, concurrency, work);
  }

  public <T> void parallel(List<T> items, int concurrency, Consumer<T> work) {
    Parallel.forEach(items, concurrency, work);
  }

  // ----------------------------------------------------------------------
  // Détails de transport
  // ----------------------------------------------------------------------

  /** Voir {@link #ORGANIZATION_PATHS}. */
  static boolean needsOrganization(String path) {
    return ORGANIZATION_PATHS.stream().anyMatch(path::startsWith);
  }

  static String queryString(Map<String, String> params) {
    if (params.isEmpty()) return "";
    return params.entrySet().stream()
        .filter(e -> e.getValue() != null)
        .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
        .collect(Collectors.joining("&", "?", ""));
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  static String truncate(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max);
  }
}
