package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import ch.ge.edg.sonar.gitlab.audit.rest.Query;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Ce qui sert à parler à l'API : un champ JSON, la page de commits d'une fenêtre. */
final class Api {

  private Api() {}

  static String text(@Nullable JsonNode n, String field) {
    return n == null ? "" : n.path(field).asText("");
  }

  /** Les commits de la branche par défaut depuis le début de la fenêtre, une page de cent. */
  static Map<String, String> commitPage(
      GitlabProject p, OffsetDateTime since, int page, boolean stats) {
    return Query.params(
        "ref_name",
        p.defaultBranch(),
        "since",
        CellFormat.iso(since),
        "with_stats",
        stats ? "true" : null,
        "per_page",
        "100",
        "page",
        String.valueOf(page));
  }
}
