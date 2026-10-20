package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.YELLOW;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.orEmptyList;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.params;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Component;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.ComponentMeasures;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Measure;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.MeasuresSearch;
import ch.ge.edg.sonar.gitlab.audit.console.Console;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les mesures de chaque projet.
 *
 * <p>Les valeurs restent des String : c'est le format que renvoie l'API, et l'absence de clé se
 * distingue ainsi naturellement d'une valeur à zéro.
 */
final class Measures {

  private final Session session;
  private final Metrics metrics;

  Measures(Session session, Metrics metrics) {
    this.session = session;
    this.metrics = metrics;
  }

  Map<String, Map<String, String>> fetch(List<Component> projects) {
    Map<String, Map<String, String>> byKey = new LinkedHashMap<>();
    List<String> keys =
        projects.stream().filter(p -> !p.onOtherBranch()).map(Component::key).toList();
    for (int i = 0; i < keys.size(); i += 10) {
      List<String> chunk = keys.subList(i, Math.min(i + 10, keys.size()));
      MeasuresSearch m =
          session.read(
              "api/measures/search",
              params("projectKeys", String.join(",", chunk), "metricKeys", metrics.keys()),
              MeasuresSearch.class);
      if (m == null) continue;
      for (Measure measure : orEmptyList(m.measures())) {
        byKey
            .computeIfAbsent(measure.component(), k -> new HashMap<>())
            .put(measure.metric(), measure.effectiveValue());
      }
    }
    // measures/search ignore les branches : chaque projet lu ailleurs que sur
    // la principale coûte son propre appel. Même raison qu'au-dessus de les
    // faire se recouvrir.
    List<Component> others = projects.stream().filter(Component::onOtherBranch).toList();
    if (!others.isEmpty()) {
      List<Map<String, String>> read =
          session.eachProject("  Mesures de branche", others, p -> branchMeasures(p));
      for (int i = 0; i < others.size(); i++) byKey.put(others.get(i).key(), read.get(i));
    }
    return byKey;
  }

  /** Mesures d'une branche précise : measures/search ne sait lire que la principale. */
  private Map<String, String> branchMeasures(Component p) {
    Map<String, String> out = new HashMap<>();
    ComponentMeasures cm =
        session.read(
            "api/measures/component",
            params("component", p.key(), "branch", p.branch(), "metricKeys", metrics.keys()),
            ComponentMeasures.class);
    if (cm == null) return out;
    for (Measure m : cm.measures()) out.put(m.metric(), m.effectiveValue());
    return out;
  }

  static void reportMissingCoverage(
      Console out, List<Component> projects, Map<String, Map<String, String>> measures) {
    long missing =
        projects.stream()
            .filter(p -> !measures.getOrDefault(p.key(), Map.of()).containsKey("coverage"))
            .count();
    out.printf(
        "  Sans aucune donnée de couverture: %s%n", out.color(String.valueOf(missing), YELLOW));
    if (missing > 0) {
      out.println(
          out.color(
              "    (coverage absente ≠ coverage à 0 : " + "ces projets sont invisibles", DIM));
      out.println(
          out.color("     dans tout tri par couverture, " + "et souvent les plus à risque)", DIM));
    }
  }
}
