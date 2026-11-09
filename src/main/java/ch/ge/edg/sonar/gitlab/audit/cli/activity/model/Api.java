package ch.ge.edg.sonar.gitlab.audit.cli.activity.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Ce qui sert à lire la réponse de l'API : un champ JSON, les parents d'un commit. */
public final class Api {

  private Api() {}

  public static String text(@Nullable JsonNode n, String field) {
    return n == null ? "" : n.path(field).asText("");
  }

  /** Les identifiants des parents d'un commit ; le domaine ne voit que cette liste. */
  public static List<String> parentIds(JsonNode commit) {
    List<String> ids = new ArrayList<>();
    commit.path("parent_ids").forEach(p -> ids.add(p.asText()));
    return ids;
  }
}
