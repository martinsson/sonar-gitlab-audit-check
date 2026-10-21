package ch.ge.edg.sonar.gitlab.audit.rest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Les paramètres d'une requête : les poser par paires, puis les encoder après le chemin. */
public final class Query {

  private Query() {}

  /**
   * Des paires clé, valeur. Map.of refuse les valeurs nulles ; ici une valeur nulle signifie « ne
   * pas envoyer », et une clé sans valeur en fin de liste est ignorée.
   */
  public static Map<String, String> params(@Nullable String... kv) {
    Map<String, String> m = new LinkedHashMap<>();
    for (int i = 0; i + 1 < kv.length; i += 2) {
      String k = kv[i];
      String v = kv[i + 1];
      if (k != null && v != null) m.put(k, v);
    }
    return m;
  }

  /** « ?a=1&b=2 », encodé ; vide sans paramètre. */
  public static String string(Map<String, String> params) {
    String joined =
        params.entrySet().stream()
            .filter(e -> e.getValue() != null)
            .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
            .collect(Collectors.joining("&"));
    return joined.isEmpty() ? "" : "?" + joined;
  }

  /** Encodage de formulaire : une espace devient « + ». */
  public static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  /** Encodage d'un segment de chemin : une espace devient « %20 ». */
  public static String segment(String s) {
    return enc(s).replace("+", "%20");
  }
}
