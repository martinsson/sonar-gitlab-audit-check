package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.dora.RunOutcome;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Les quatre mesures DORA d'un instantané.
 *
 * <ul>
 *   <li>Fréquence : les déploiements réussis, par semaine sur la période.
 *   <li>Délai : de l'écriture d'un commit à sa première mise en production réussie (GoCD), ou du
 *       premier commit d'une merge request à sa fusion (sans GoCD). Médiane.
 *   <li>Taux d'échec : la part des déploiements, réussis ou non, suivis d'au moins un incident
 *       avant le déploiement suivant. C'est la règle de Middleware, à l'identique.
 *   <li>Rétablissement : de la création d'un incident à sa résolution. Médiane.
 * </ul>
 *
 * @param days la durée de la période, en jours entiers, au moins 1
 * @param incidentsRead faux sans projet Jira : aucun incident n'a pu être vu
 * @param runs tous les déploiements, réussis ou non, du plus ancien au plus récent
 * @param successes les déploiements réussis, du plus ancien au plus récent
 * @param leads les délais, un par changement mis en production
 * @param recoveries les temps de rétablissement des incidents résolus
 */
record Metrics(
    long days,
    boolean incidentsRead,
    List<Snapshot.Deployment> runs,
    List<Snapshot.Deployment> successes,
    List<Duration> leads,
    List<Duration> recoveries,
    Links links) {

  static Metrics of(Snapshot s) {
    List<Snapshot.Deployment> runs = new ArrayList<>(s.deployments());
    runs.sort((a, b) -> a.at().compareTo(b.at()));
    List<Snapshot.Deployment> successes =
        runs.stream().filter(d -> d.outcome() == RunOutcome.SUCCESS).toList();
    Links links = Links.of(s, runs);
    List<Duration> recoveries = new ArrayList<>();
    for (Snapshot.Incident i : s.incidents()) {
      Duration d = between(i.createdAt(), i.resolvedAt());
      if (d != null) recoveries.add(d);
    }
    return new Metrics(
        Math.max(1, Duration.between(s.from(), s.to()).toDays()),
        !s.project().jiraProjects().isEmpty(),
        List.copyOf(runs),
        successes,
        leads(s, links.bySha()),
        recoveries,
        links);
  }

  /** Les délais : par commit avec GoCD, par merge request sans. */
  private static List<Duration> leads(Snapshot s, Map<String, Snapshot.Deployment> bySha) {
    return s.project().deploySource() == Snapshot.DeploySource.MERGE
        ? mergeLeads(s.mergeRequests())
        : commitLeads(s.deployments(), bySha);
  }

  /** Du premier commit d'une merge request, à défaut de son ouverture, à sa fusion. */
  private static List<Duration> mergeLeads(List<Snapshot.MergeRequest> mrs) {
    List<Duration> out = new ArrayList<>();
    for (Snapshot.MergeRequest mr : mrs) {
      Instant start = mr.firstCommitAt() != null ? mr.firstCommitAt() : mr.createdAt();
      Duration d = between(start, mr.mergedAt());
      if (d != null) out.add(d);
    }
    return out;
  }

  /** De la date d'auteur d'un commit à son premier déploiement réussi. */
  private static List<Duration> commitLeads(
      List<Snapshot.Deployment> deployments, Map<String, Snapshot.Deployment> bySha) {
    Map<String, Instant> written = new HashMap<>();
    for (Snapshot.Deployment dep : deployments) {
      for (Snapshot.Change c : dep.commits()) {
        Instant at = c.at();
        if (at != null) written.putIfAbsent(c.sha(), at);
      }
    }
    List<Duration> out = new ArrayList<>();
    for (Map.Entry<String, Snapshot.Deployment> e : bySha.entrySet()) {
      Duration d = between(written.get(e.getKey()), e.getValue().at());
      if (d != null) out.add(d);
    }
    return out;
  }

  /** La durée de {@code from} à {@code to} ; aucune si un bout manque ou si elle est négative. */
  static @Nullable Duration between(@Nullable Instant from, @Nullable Instant to) {
    if (from == null || to == null || to.isBefore(from)) return null;
    return Duration.between(from, to);
  }

  /** Déploiements réussis par semaine. */
  double perWeek() {
    return successes.size() * 7.0 / days;
  }

  /** La médiane des délais ; aucune sans délai. */
  @Nullable Duration leadTime() {
    return median(leads);
  }

  /** Les déploiements suivis d'au moins un incident. */
  int failedChanges() {
    return links.incidentsAfter().size();
  }

  /**
   * La part des déploiements suivis d'un incident ; aucune sans déploiement, ni sans incidents lus.
   */
  @Nullable Double changeFailureRate() {
    if (!incidentsRead || runs.isEmpty()) return null;
    return (double) failedChanges() / runs.size();
  }

  /** La part des runs de production terminés en échec ; aucune sans run terminé. */
  @Nullable Double failedRunRate() {
    long failures = runs.stream().filter(d -> d.outcome() == RunOutcome.FAILURE).count();
    long ended = successes.size() + failures;
    return ended == 0 ? null : (double) failures / ended;
  }

  /** La médiane des temps de rétablissement ; aucune sans incident résolu. */
  @Nullable Duration timeToRestore() {
    return median(recoveries);
  }

  /** La médiane, la moyenne des deux du milieu pour un nombre pair ; aucune pour une liste vide. */
  static @Nullable Duration median(List<Duration> values) {
    if (values.isEmpty()) return null;
    List<Duration> sorted = new ArrayList<>(values);
    Collections.sort(sorted);
    int n = sorted.size();
    if (n % 2 == 1) return sorted.get(n / 2);
    return sorted.get(n / 2 - 1).plus(sorted.get(n / 2)).dividedBy(2);
  }
}
