package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import ch.ge.edg.sonar.gitlab.audit.gocd.PipelineGroups;
import java.io.PrintStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Les pipelines à extraire : ceux nommés, puis tous ceux des groupes nommés, dans l'ordre du
 * tableau de bord. Le tableau de bord ne montre que ce que le jeton voit : un nom qu'il ne montre
 * pas est inconnu ou invisible, on le dit et on essaie quand même.
 *
 * @param groupOf pipeline → groupe, pour tout ce que le tableau de bord montre
 */
record Selection(Set<String> pipelines, Map<String, String> groupOf) {

  public Selection {
    pipelines = Collections.unmodifiableSet(new LinkedHashSet<>(pipelines));
    groupOf = Map.copyOf(groupOf);
  }

  public static Selection resolve(
      Gocd gocd, List<String> pipelines, List<String> groups, PrintStream err) throws Stop {
    String path = "/go/api/dashboard";
    Map<String, String> groupOf = PipelineGroups.of(gocd.must(gocd.api().dashboard(), path).json());
    Set<String> out = new LinkedHashSet<>(pipelines);
    groupOf.forEach(
        (pipeline, group) -> {
          if (groups.contains(group)) out.add(pipeline);
        });
    for (String g : groups) {
      if (!groupOf.containsValue(g)) err.println("Groupe invisible ou inconnu : " + g);
    }
    for (String p : pipelines) {
      if (!groupOf.containsKey(p)) err.println("Pipeline invisible ou inconnu : " + p);
    }
    return new Selection(out, groupOf);
  }

  public String group(String pipeline) {
    return groupOf.getOrDefault(pipeline, "");
  }
}
