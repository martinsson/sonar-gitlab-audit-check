package ch.ge.edg.sonar.gitlab.audit.cli.doralocate;

import ch.ge.edg.sonar.gitlab.audit.dora.RepoPath;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les pipelines d'un dépôt : ceux qui le prennent comme matériau git (niveau 0), puis, de proche en
 * proche, tout ce qui en dépend (build, puis déploiement en recette, puis en prod).
 */
final class RepoPipelines {

  private RepoPipelines() {}

  /** Pipeline → niveau, 0 pour le dépôt direct ; dans l'ordre de l'index, puis de découverte. */
  static Map<String, Integer> of(GocdIndex index, String pathWithNamespace) {
    Map<String, Integer> depth = new LinkedHashMap<>();
    Map<String, List<String>> downstream = new HashMap<>();
    index
        .pipelines()
        .forEach(
            (name, p) -> {
              if (p.git().stream().anyMatch(url -> RepoPath.matches(url, pathWithNamespace))) {
                depth.put(name, 0);
              }
              for (String up : p.upstream()) {
                downstream.computeIfAbsent(up, k -> new ArrayList<>()).add(name);
              }
            });
    Deque<String> todo = new ArrayDeque<>(depth.keySet());
    while (!todo.isEmpty()) {
      String p = todo.poll();
      int next = depth.getOrDefault(p, 0) + 1;
      for (String d : downstream.getOrDefault(p, List.of())) {
        if (depth.putIfAbsent(d, next) == null) todo.add(d);
      }
    }
    return depth;
  }
}
