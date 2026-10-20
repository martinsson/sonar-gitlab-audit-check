package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.GREEN;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.YELLOW;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.params;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Component;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.SearchHistory;
import ch.ge.edg.sonar.gitlab.audit.console.Console;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Tendance : la dérivée, pas la photo. */
final class Trends {

  static final List<String> METRICS = List.of("violations", "ncloc", "sqale_debt_ratio");

  private final Session session;
  private final int activityDays;

  /** Les séries brutes derrière les pentes, pour historique.csv. */
  private final Map<String, SearchHistory> histories = new LinkedHashMap<>();

  Trends(Session session, int activityDays) {
    this.session = session;
    this.activityDays = activityDays;
  }

  Map<String, SearchHistory> histories() {
    return histories;
  }

  /**
   * Un appel {@code search_history} par projet, sur la fenêtre --activity-days.
   *
   * <p>La photo dit où en est un projet ; elle ne dit pas s'il s'améliore. Deux projets à 40
   * issues/kLOC, l'un qui en perd 5 % par mois et l'autre qui en gagne autant, appellent des
   * réponses opposées et sortent identiques de measures/search. La dérivée les sépare.
   */
  Map<String, Trend> fetch(List<Component> projects, Metrics supported) {
    if (projects.isEmpty()) return Map.of();
    String metrics = METRICS.stream().filter(supported::supports).collect(Collectors.joining(","));
    if (metrics.isEmpty()) return Map.of();
    String since =
        LocalDateTime.now(session.clock())
            .minusDays(activityDays)
            .format(DateTimeFormatter.ISO_LOCAL_DATE);

    List<@Nullable SearchHistory> read =
        session.eachProject("  Historiques lus", projects, p -> history(p, metrics, since));

    Map<String, Trend> byKey = new HashMap<>();
    for (int i = 0; i < projects.size(); i++) {
      SearchHistory h = read.get(i);
      if (h == null) continue;
      histories.put(projects.get(i).key(), h);
      byKey.put(projects.get(i).key(), Trend.of(h, session.zone()));
    }
    report(projects.size(), byKey.values());
    return byKey;
  }

  private @Nullable SearchHistory history(Component p, String metrics, String since) {
    // L'historique vit sur la branche dont viennent les mesures : lire
    // la principale d'un projet scanné ailleurs rendrait une série vide.
    Map<String, String> q =
        params("component", p.key(), "metrics", metrics, "from", since, "ps", "1000");
    if (p.onOtherBranch()) q.put("branch", p.branch());
    return session.read("api/measures/search_history", q, SearchHistory.class);
  }

  private void report(int total, Collection<Trend> trends) {
    long up = 0;
    long down = 0;
    long flat = 0;
    for (Trend t : trends) {
      Double d = t.densityPctPerMonth();
      if (d == null) continue;
      if (d > Trend.FLAT_PCT) up++;
      else if (d < -Trend.FLAT_PCT) down++;
      else flat++;
    }
    long measured = up + down + flat;
    Console out = session.out();
    out.println();
    out.out()
        .printf(
            "  Tendance des issues/kLOC sur %d j (%d projets mesurables) :%n",
            activityDays, measured);
    out.out()
        .printf(
            "    en hausse (> +%.0f %%/mois)    : %s%n",
            Trend.FLAT_PCT, out.color(String.valueOf(up), up > 0 ? YELLOW : GREEN));
    out.out()
        .printf(
            "    en baisse (< -%.0f %%/mois)    : %s%n",
            Trend.FLAT_PCT, out.color(String.valueOf(down), GREEN));
    out.out().printf("    stables                      : %d%n", flat);
    out.out().printf("    non mesurables               : %d%n", total - measured);
    out.println(
        out.color(
            ("    (moins de %d analyses ou moins de %d j d'historique dans la "
                    + "fenêtre, ou\n     aucune issue : une pente relative à zéro n'existe pas)")
                .formatted(Trend.MIN_POINTS, Trend.MIN_SPAN_DAYS),
            DIM));
  }
}
