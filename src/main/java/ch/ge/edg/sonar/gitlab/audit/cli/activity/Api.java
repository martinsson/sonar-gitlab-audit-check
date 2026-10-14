package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Ce qui sert à parler à l'API : paramètres, champs JSON, dates, chemins encodés. */
final class Api {

  private Api() {}

  /** Map.of refuse les valeurs nulles ; ici une valeur nulle signifie « ne pas envoyer ». */
  static Map<String, String> params(@Nullable String... kv) {
    Map<String, String> m = new LinkedHashMap<>();
    for (int i = 0; i + 1 < kv.length; i += 2) {
      String k = kv[i];
      String v = kv[i + 1];
      if (k != null && v != null) m.put(k, v);
    }
    return m;
  }

  static @Nullable OffsetDateTime parse(@Nullable String s) {
    return Gitlab.parse(s);
  }

  static String enc(String s) {
    return Gitlab.enc(s);
  }

  static String text(@Nullable JsonNode n, String field) {
    return n == null ? "" : n.path(field).asText("");
  }

  /** Les commits de la branche par défaut depuis le début de la fenêtre, une page de cent. */
  static Map<String, String> commitPage(Proj p, OffsetDateTime since, int page, boolean stats) {
    return params(
        "ref_name",
        p.defaultBranch,
        "since",
        Fmt.iso(since),
        "with_stats",
        stats ? "true" : null,
        "per_page",
        "100",
        "page",
        String.valueOf(page));
  }
}
