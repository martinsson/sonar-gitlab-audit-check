package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.YELLOW;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.params;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Metric;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.MetricsSearch;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Paging;
import ch.ge.edg.sonar.gitlab.audit.console.Console;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Les mesures demandées à SonarQube, restreintes à ce que l'instance sait nommer. */
final class Metrics {

  /**
   * Les mesures demandées à SonarQube, et — c'est le même objet — les colonnes du CSV. Il y avait
   * ici deux listes à tenir d'accord, une pour l'appel et une pour l'écriture : {@code sqale_index}
   * figurait dans la première et pas dans la seconde, donc il était demandé à chaque projet du parc
   * puis jeté. Une mesure absente du CSV se lit comme une mesure que SonarQube ne donne pas, ce qui
   * est faux et coûte cher : c'est la dette en valeur absolue, celle sur laquelle
   * GITLAB_ANALYSIS.md raisonne.
   *
   * <p>L'ordre est celui du CSV. Quatre groupes :
   *
   * <p>1. taille et forme — de quoi stratifier, et lire tout le reste ; 2. tests et couverture —
   * voir la note ci-dessous ; 3. dette et défauts — les comptes derrière les notes A–E ; 4. code
   * neuf — ce qui se crée, plutôt que ce qui est porté.
   *
   * <p>**Pourquoi autant autour de la couverture.** {@code coverage} seule ne distingue pas les
   * deux situations qui appellent des actions opposées : une équipe qui n'a pas de tests, et une
   * équipe qui en a mais dont le rapport de couverture n'arrive pas jusqu'à SonarQube. {@code
   * tests} et {@code lines_to_cover} les séparent — 0 % avec 400 tests est un problème de
   * tuyauterie CI, 0 % avec 0 test est un problème d'ingénierie — et {@code uncovered_lines} donne
   * enfin un volume à l'effort, là où un pourcentage sur un projet de 200 lignes et un sur un
   * monolithe se ressemblent.
   *
   * <p>**Pourquoi les comptes en plus des notes.** {@code *_rating} est ordinal : un 4 ne vaut pas
   * deux 2, et la moyenne d'une colonne de notes n'a pas de sens. {@code bugs}, {@code
   * vulnerabilities}, {@code code_smells} se somment, se comparent, se rapportent à {@code ncloc}.
   */
  static final List<String> COLUMNS =
      List.of(
          // 1. Taille et forme
          "ncloc",
          "files",
          "complexity",
          "cognitive_complexity",
          "comment_lines_density",
          "duplicated_lines_density",
          "new_duplicated_lines_density",
          // 2. Tests et couverture
          "coverage",
          "line_coverage",
          "branch_coverage",
          "new_coverage",
          "lines_to_cover",
          "uncovered_lines",
          "tests",
          "test_failures",
          "test_errors",
          "skipped_tests",
          "test_success_density",
          // 3. Dette et défauts
          "sqale_index",
          "sqale_debt_ratio",
          "sqale_rating",
          "bugs",
          "reliability_rating",
          "vulnerabilities",
          "security_rating",
          "security_hotspots",
          "security_hotspots_reviewed",
          "code_smells",
          "violations",
          // 4. Code neuf
          "new_lines",
          "new_violations",
          "new_bugs",
          "new_vulnerabilities",
          "new_code_smells",
          "alert_status");

  /**
   * Ce qu'on demandera réellement : {@link #COLUMNS} restreint aux métriques que *cette* instance
   * connaît.
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
    if (known.isEmpty()) return new Metrics(COLUMNS);
    List<String> unsupported = COLUMNS.stream().filter(k -> !known.contains(k)).toList();
    if (!unsupported.isEmpty()) report(session.out(), unsupported);
    return new Metrics(COLUMNS.stream().filter(known::contains).toList());
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
    out.out()
        .printf(
            "  Métriques inconnues de cette instance : %s%n",
            out.color(String.join(", ", unsupported), YELLOW));
    out.println(
        out.color(
            "    Retirées de la requête. Demandées quand même, " + "elles feraient échouer", DIM));
    out.println(
        out.color("    l'appel entier et le parc ressortirait sans " + "aucune mesure.", DIM));
  }
}
