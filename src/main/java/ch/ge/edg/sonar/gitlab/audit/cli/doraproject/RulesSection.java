package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import static ch.ge.edg.sonar.gitlab.audit.cli.doraproject.Show.html;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * La section « Règles appliquées » de dora.html : chaque règle, la nôtre à côté de sa traduction
 * dans DevLake, et, avec la règle « atteint main », les fonctionnalités et la source de leur heure
 * d'arrivée sur la branche par défaut.
 */
final class RulesSection {

  private final Snapshot s;
  private final Metrics m;
  private final ZoneId zone;
  private final Markup out;

  RulesSection(Snapshot s, Metrics m, ZoneId zone, Markup out) {
    this.s = s;
    this.m = m;
    this.zone = zone;
    this.out = out;
  }

  void write() {
    Snapshot.Project p = s.project();
    boolean gocd = p.gocd();
    out.line("<h2>Règles appliquées</h2>");
    out.line(
        "<div class=\"scroll\"><table class=\"rules\"><thead><tr><th>Mesure</th><th>Notre règle</th>"
            + "<th>Dans DevLake (devlake-import.sql)</th></tr></thead><tbody>");
    String deploys =
        gocd
            ? "un run réussi de " + String.join(", ", p.prodPipelines()) + " (GoCD)"
            : "une merge request fusionnée sur " + p.defaultBranch() + " (pas de GoCD)";
    rule(
        "Déploiement",
        deploys,
        "un cicd_deployment en PRODUCTION par run ; fréquence : médiane des jours de déploiement"
            + " par semaine");
    rule("Rattachement d'un commit", mapping(p), "les lignes commits_diffs de son déploiement");
    rule(
        "Délai",
        gocd
            ? p.reachable()
                ? "de la date d'auteur du premier commit d'une fonctionnalité à son déploiement"
                : "de la date d'auteur d'un commit à son premier déploiement réussi"
            : "du premier commit d'une merge request à sa fusion",
        p.reachable()
            ? "par pull request (une merge request, ou des commits arrivés sur main sans) : de son"
                + " premier commit au déploiement qui porte son commit de fusion ; médiane basse"
            : "par merge request : de son premier commit au déploiement qui porte son commit de"
                + " fusion ; médiane basse");
    rule(
        "Incident",
        p.jiraProjects().isEmpty() ? "aucun : pas de projet Jira donné" : p.incidentRule(),
        "un ticket de type INCIDENT");
    rule(
        "Échec d'un changement",
        "un déploiement, réussi ou non, suivi d'au moins un incident avant le déploiement"
            + " suivant, rapporté à tous les déploiements (la règle de Middleware)",
        "un déploiement réussi suivi d'un incident, rapporté aux seuls déploiements réussis");
    rule(
        "Rétablissement",
        "de la création de l'incident à sa date de résolution Jira",
        "rapport 2021 : la même durée ; rapport 2023 : du déploiement à la résolution ; médiane"
            + " basse");
    out.line("</tbody></table></div>");
    if (p.reachable()) features();
  }

  /** La règle de rattachement, en clair. */
  private static String mapping(Snapshot.Project p) {
    if (p.deploySource() == Snapshot.DeploySource.MERGE) {
      return "les commits de la merge request, à sa fusion";
    }
    if (p.reachable()) {
      return "« atteint main » : le premier run réussi planifié après l'arrivée de la"
          + " fonctionnalité sur "
          + p.defaultBranch()
          + " (fusion en avance rapide : merged_at de sa merge request, sinon l'heure du push,"
          + " sinon la date de commit, approchée). Une fonctionnalité : une merge request, sinon"
          + " une clé Jira, sinon un push";
    }
    return "les commits que le run a embarqués, pipelines amont compris ; ceux d'un run en échec"
        + " relancé sans nouveau matériau vont au run réussi suivant du même pipeline";
  }

  private void rule(String name, String ours, String devlake) {
    out.line(
        "<tr><td>"
            + html(name)
            + "</td><td>"
            + html(ours)
            + "</td><td>"
            + html(devlake)
            + "</td></tr>");
  }

  /** Les fonctionnalités de la règle « atteint main », et d'où vient leur heure d'arrivée. */
  private void features() {
    List<Features.Feature> all = m.links().features();
    out.line(
        "<h2>Fonctionnalités arrivées sur "
            + html(s.project().defaultBranch())
            + " ("
            + all.size()
            + ")</h2>");
    if (all.isEmpty()) {
      out.empty("Aucune merge request ni aucun commit de la branche par défaut sur la période.");
      return;
    }
    StringBuilder sources = new StringBuilder("Heure d'arrivée : ");
    List<String> parts = new ArrayList<>();
    Features.sources(all).forEach((r, n) -> parts.add(n + " par " + r.label()));
    sources.append(String.join(", ", parts)).append('.');
    out.table(
        "Fonctionnalité",
        "Commits",
        "Premier commit",
        "Sur main",
        "Source",
        "Déployée par",
        "Délai");
    for (Features.Feature f : all.reversed()) {
      Snapshot.Deployment d =
          f.shas().isEmpty() ? null : m.links().bySha().get(f.shas().getFirst());
      out.row(
          Markup.td(f.key()),
          Markup.num(f.shas().size()),
          Markup.td(Show.dateTime(f.firstAuthoredAt(), zone)),
          Markup.td(Show.dateTime(f.reachedMain(), zone)),
          Markup.td(f.reachedBy().label()),
          Markup.td(d == null ? Show.NONE : d.pipeline() + " #" + d.counter()),
          Markup.td(
              Show.duration(d == null ? null : Metrics.between(f.firstAuthoredAt(), d.at()))));
    }
    out.endTable();
    out.line("<p class=\"note\">" + html(sources.toString()) + "</p>");
  }
}
