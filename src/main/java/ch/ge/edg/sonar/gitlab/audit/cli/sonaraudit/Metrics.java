package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.YELLOW;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.params;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Metric;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.MetricsSearch;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Paging;
import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.exchange.SonarInventory;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Les mesures demandées à SonarQube, restreintes à ce que l'instance sait nommer. */
final class Metrics {

  /**
   * Ce qu'on demandera réellement : {@link SonarInventory#METRICS} restreint aux métriques que
   * *cette* instance connaît.
   *
   * <p>{@code api/measures/search} refuse la requête entière — 400 — dès qu'une seule clé lui est
   * inconnue. Une métrique retirée ou renommée d'une version de SonarQube à l'autre ferait donc
   * perdre les treize autres, et l'audit afficherait un parc sans aucune mesure plutôt qu'une
   * mesure de moins. On demande la liste une fois, et on n'envoie que l'intersection.
   */
  private final List<String> supported;

  private Metrics(List<String> supported) {
    this.supported = supported;
  }

  boolean supports(String metric) {
    return supported.contains(metric);
  }

  String keys() {
    return String.join(",", supported);
  }

  /**
   * Un appel, paginé, avant toute mesure. Si l'instance ne répond pas, on garde la liste complète :
   * le comportement d'avant, pas une dégradation de plus.
   */
  static Metrics negotiate(Session session) {
    Set<String> known = known(session);
    if (known.isEmpty()) return new Metrics(SonarInventory.METRICS);
    List<String> unsupported =
        SonarInventory.METRICS.stream().filter(k -> !known.contains(k)).toList();
    if (!unsupported.isEmpty()) report(session.out(), unsupported);
    return new Metrics(SonarInventory.METRICS.stream().filter(known::contains).toList());
  }

  private static Set<String> known(Session session) {
    Set<String> known = new HashSet<>();
    for (int page = 1; page <= 10; page++) {
      MetricsSearch m =
          session.read(
              "api/metrics/search",
              params("ps", "500", "p", String.valueOf(page)),
              MetricsSearch.class);
      List<Metric> metrics = m == null ? null : m.metrics();
      if (m == null || metrics == null || metrics.isEmpty()) break;
      metrics.forEach(x -> known.add(x.key()));
      if (complete(m.paging(), known.size())) break;
    }
    return known;
  }

  private static boolean complete(@Nullable Paging pg, int seen) {
    return pg != null && pg.total() != null && seen >= Values.orZero(pg.total());
  }

  private static void report(Console out, List<String> unsupported) {
    out.printf(
        "  Métriques inconnues de cette instance : %s%n",
        out.color(String.join(", ", unsupported), YELLOW));
    out.println(
        out.color(
            "    Retirées de la requête. Demandées quand même, " + "elles feraient échouer", DIM));
    out.println(
        out.color("    l'appel entier et le parc ressortirait sans " + "aucune mesure.", DIM));
  }
}
