package ch.ge.edg.sonar.gitlab.audit.sonar;

import ch.ge.edg.sonar.gitlab.audit.parallel.Parallel;
import ch.ge.edg.sonar.gitlab.audit.rest.Capture;
import ch.ge.edg.sonar.gitlab.audit.rest.RestClient;
import ch.ge.edg.sonar.gitlab.audit.rest.RestClient.Retries;
import ch.ge.edg.sonar.gitlab.audit.rest.RestClient.Settings;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import ch.ge.edg.sonar.gitlab.audit.runtime.Sleeper;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * <p>parler à l'API — un GET, un jeton, l'organisation quand il le faut ; tenir la cadence — appels
 * concurrents bornés, 429 et 5xx retentés ({@link RestClient}) ; rejouer une capture — {@code
 * --dump-dir} enregistre, {@code --replay-dir} relit, et l'outil tourne alors sans instance ({@link
 * Capture}).
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
          return Text.truncate(
              errors.stream()
                  .map(ErrorMessage::msg)
                  .filter(Objects::nonNull)
                  .collect(Collectors.joining("; ")),
              90);
        }
      } catch (IOException ignored) {
        // corps non-JSON : on le montre tel quel
      }
      return Text.truncate(body.replace("\n", " "), 90);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ErrorResponse(@Nullable List<ErrorMessage> errors) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ErrorMessage(@Nullable String msg) {}

  // ----------------------------------------------------------------------
  // Client
  // ----------------------------------------------------------------------

  /**
   * SonarQube ne limite pas le débit lui-même, mais ce qu'on a devant lui le fait souvent — et un
   * 502 isolé d'un reverse proxy, au milieu d'une passe concurrente, ne ressemble à rien d'autre
   * qu'à un projet sans mesures. Une panne de transport doit coûter une seconde, pas une ligne
   * fausse.
   */
  private static final Retries RETRIES = new Retries(3, 2, 0);

  public final String base;
  private final @Nullable String organization;
  private final RestClient client;

  /**
   * Un client.
   *
   * @param capture où écrire chaque réponse, ou d'où la rejouer
   * @param sleeper l'attente avant un nouvel essai
   */
  public Sonar(SonarConnection connection, Capture capture, Sleeper sleeper) throws IOException {
    String url = connection.url();
    this.base = url == null ? "" : url.replaceAll("/+$", "");
    String org = connection.organization();
    this.organization = org == null || org.isBlank() ? null : org;
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Accept", "application/json");
    String token = connection.token();
    if (token != null) headers.put("Authorization", "Bearer " + token);
    this.client =
        new RestClient(
            new Settings(
                base, headers, connection.timeoutSeconds(), connection.insecure(), true, RETRIES),
            capture,
            sleeper);
  }

  public int calls() {
    return client.calls();
  }

  public int throttled() {
    return client.throttled();
  }

  public int retried() {
    return client.retried();
  }

  /** Captures réclamées et absentes : en rejeu, c'est la seule panne possible. */
  public int missingCaptures() {
    return client.missingCaptures();
  }

  public boolean replaying() {
    return client.replaying();
  }

  public Response get(String path, Map<String, String> params) {
    String cleanPath = path.replaceAll("^/+", "");
    Map<String, String> all = new LinkedHashMap<>(params);
    if (organization != null && needsOrganization(cleanPath)) {
      all.putIfAbsent("organization", organization);
    }
    RestResponse r = client.get("/" + cleanPath + queryString(all));
    return new Response(r.status(), r.status() == 0 ? r.error() : r.body());
  }

  // ----------------------------------------------------------------------
  // Parallélisme
  // ----------------------------------------------------------------------

  /** Voir {@link Parallel#map} : largeur bornée, ordre conservé, exceptions relancées. */
  public <T, R> List<R> map(List<T> items, int concurrency, Function<T, R> work) {
    return Parallel.map(items, concurrency, work::apply);
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
}
