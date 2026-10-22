package ch.ge.edg.sonar.gitlab.audit.gocd;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Le tableau de bord ({@code /go/api/dashboard}) : sans droit admin, c'est la seule liste des
 * pipelines qu'on puisse lire, limitée à ceux que le jeton voit.
 */
public record Dashboard(@JsonProperty("_embedded") Embedded embedded) {

  public Dashboard(@Nullable Embedded embedded) {
    this.embedded = embedded == null ? new Embedded(List.of()) : embedded;
  }

  public record Embedded(List<Group> pipelineGroups) {}

  /**
   * Un groupe de pipelines.
   *
   * @param pipelines les noms de ses pipelines
   */
  public record Group(String name, List<String> pipelines) {}

  public static Dashboard of(JsonNode json) {
    return GocdJson.read(json, Dashboard.class);
  }

  /** Pipeline → groupe, dans l'ordre du tableau de bord. */
  public Map<String, String> groupOf() {
    Map<String, String> groupOf = new LinkedHashMap<>();
    for (Group g : embedded.pipelineGroups()) {
      for (String p : g.pipelines()) groupOf.put(p, g.name());
    }
    return groupOf;
  }
}
