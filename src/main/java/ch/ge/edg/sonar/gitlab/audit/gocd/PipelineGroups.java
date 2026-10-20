package ch.ge.edg.sonar.gitlab.audit.gocd;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ce que le tableau de bord ({@code /go/api/dashboard}) dit des groupes : sans droit admin, c'est
 * la seule liste des pipelines qu'on puisse lire, limitée à ceux que le jeton voit.
 */
public final class PipelineGroups {

  private PipelineGroups() {}

  /** Pipeline → groupe, dans l'ordre du tableau de bord. */
  public static Map<String, String> of(JsonNode dashboard) {
    Map<String, String> groupOf = new LinkedHashMap<>();
    for (JsonNode g : dashboard.path("_embedded").path("pipeline_groups")) {
      String name = g.path("name").asText();
      for (JsonNode p : g.path("pipelines")) groupOf.put(p.asText(), name);
    }
    return groupOf;
  }
}
