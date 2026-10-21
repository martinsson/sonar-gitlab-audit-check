package ch.ge.edg.sonar.gitlab.audit.cli.doralocate;

import ch.ge.edg.sonar.gitlab.audit.dora.Times;
import ch.ge.edg.sonar.gitlab.audit.gocd.GocdApi;
import ch.ge.edg.sonar.gitlab.audit.gocd.Materials;
import ch.ge.edg.sonar.gitlab.audit.gocd.PipelineGroups;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.PrintStream;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ce que GoCD dit de chaque pipeline visible : son groupe, ses dépôts git, ses pipelines amont, son
 * dernier run. Sans droit admin, les matériaux d'un pipeline ne se lisent que dans ses runs : un
 * appel d'historique par pipeline, que l'index garde d'une recherche à l'autre.
 *
 * @param builtAt heure locale de construction, telle qu'affichée
 * @param pipelines par nom, dans l'ordre du tableau de bord
 */
record GocdIndex(String builtAt, Map<String, Pipeline> pipelines) {

  static final ObjectMapper JSON = new ObjectMapper();

  /** Les runs lus par pipeline : assez pour voir ses matériaux, peu pour un index de tout GoCD. */
  static final int RUNS = 10;

  /** Une progression tous les tant de pipelines. */
  static final int PROGRESS = 50;

  public GocdIndex {
    pipelines = Collections.unmodifiableMap(new LinkedHashMap<>(pipelines));
  }

  /**
   * Un pipeline de l'index.
   *
   * @param lastRun heure locale du dernier run, vide s'il n'a jamais tourné (ou pas lisiblement)
   */
  record Pipeline(String group, List<String> git, List<String> upstream, String lastRun) {

    public Pipeline {
      git = List.copyOf(git);
      upstream = List.copyOf(upstream);
    }

    /** Sans run lisible, aucun matériau connu : ce pipeline ne peut être rattaché à rien. */
    boolean unknown() {
      return git.isEmpty() && upstream.isEmpty();
    }
  }

  /** Un pipeline de l'index ; un pipeline vide si l'index ne le connaît pas. */
  Pipeline pipeline(String name) {
    return pipelines.getOrDefault(name, new Pipeline("", List.of(), List.of(), ""));
  }

  int unknown() {
    return (int) pipelines.values().stream().filter(Pipeline::unknown).count();
  }

  /** Le tableau de bord, puis une page d'historique par pipeline. */
  static GocdIndex build(GocdApi gocd, String builtAt, ZoneId zone, PrintStream err) throws Stop {
    RestResponse dash = gocd.unlessRefused(gocd.dashboard());
    if (!dash.ok()) throw new Stop("/go/api/dashboard : " + dash.why());
    Map<String, String> groupOf = PipelineGroups.of(dash.tree());
    Map<String, Pipeline> pipelines = new LinkedHashMap<>();
    int n = 0;
    for (Map.Entry<String, String> e : groupOf.entrySet()) {
      if (++n % PROGRESS == 0) {
        err.print(Text.format("  index GoCD : %d / %d pipelines%n", n, groupOf.size()));
      }
      pipelines.put(e.getKey(), pipeline(gocd, e.getKey(), e.getValue(), zone));
    }
    return new GocdIndex(builtAt, pipelines);
  }

  private static Pipeline pipeline(GocdApi gocd, String name, String group, ZoneId zone)
      throws Stop {
    String path = "/go/api/pipelines/" + GocdApi.enc(name) + "/history?page_size=" + RUNS;
    RestResponse r = gocd.unlessRefused(gocd.api(path));
    JsonNode runs = r.tree().path("pipelines");
    if (!r.ok() || runs.isEmpty()) return new Pipeline(group, List.of(), List.of(), "");
    Set<String> git = new LinkedHashSet<>();
    Set<String> upstream = new LinkedHashSet<>();
    for (JsonNode run : runs) {
      for (JsonNode mr : run.path("build_cause").path("material_revisions")) {
        JsonNode m = mr.path("material");
        if (Materials.isGit(m)) git.add(Materials.gitUrl(m));
        if (Materials.isPipeline(m)) upstream.add(Materials.upstreamName(m));
      }
    }
    String last = Times.format(Times.parse(runs.get(0).path("scheduled_date")), zone);
    return new Pipeline(group, new ArrayList<>(git), new ArrayList<>(upstream), last);
  }

  /** Le format du fichier : celui des scripts JBang d'origine, pour relire un index existant. */
  ObjectNode toJson() {
    ObjectNode root = JSON.createObjectNode();
    root.put("built_at", builtAt);
    ObjectNode all = root.putObject("pipelines");
    pipelines.forEach(
        (name, p) -> {
          ObjectNode o = all.putObject(name);
          o.put("group", p.group());
          p.git().forEach(o.putArray("git")::add);
          p.upstream().forEach(o.putArray("upstream")::add);
          if (!p.lastRun().isEmpty()) o.put("last_run", p.lastRun());
        });
    return root;
  }

  static GocdIndex fromJson(JsonNode root) {
    Map<String, Pipeline> pipelines = new LinkedHashMap<>();
    root.path("pipelines")
        .properties()
        .forEach(
            e -> {
              JsonNode p = e.getValue();
              pipelines.put(
                  e.getKey(),
                  new Pipeline(
                      p.path("group").asText(),
                      texts(p.path("git")),
                      texts(p.path("upstream")),
                      p.path("last_run").asText("")));
            });
    return new GocdIndex(root.path("built_at").asText(), pipelines);
  }

  private static List<String> texts(JsonNode array) {
    List<String> out = new ArrayList<>();
    for (JsonNode n : array) out.add(n.asText());
    return out;
  }
}
