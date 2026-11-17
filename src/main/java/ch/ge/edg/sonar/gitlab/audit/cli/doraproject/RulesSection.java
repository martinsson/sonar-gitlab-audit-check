package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import static ch.ge.edg.sonar.gitlab.audit.cli.doraproject.Show.html;

import java.time.ZoneId;

/**
 * La section « Règles appliquées » de dora.html : chaque règle en clair, et, avec les tags de
 * version, ce que chacun a livré ({@link DeliveriesSection}).
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
    out.line("<h2>Règles appliquées</h2>");
    out.line(
        "<div class=\"scroll\"><table class=\"rules\"><thead><tr><th>Mesure</th>"
            + "<th>Notre règle</th></tr></thead><tbody>");
    rule("Déploiement", deploys());
    rule("Rattachement d'un commit", mapping(p));
    rule("Délai", lead(p));
    rule(
        "Incident",
        p.jiraProjects().isEmpty() ? "aucun : pas de projet Jira donné" : p.incidentRule());
    rule(
        "Échec d'un changement",
        "un déploiement, réussi ou non, suivi d'au moins un incident avant le déploiement"
            + " suivant, rapporté à tous les déploiements (la règle de Middleware)");
    rule("Rétablissement", "de la création de l'incident à sa date de résolution Jira");
    out.line("</tbody></table></div>");
    if (p.tags()) new DeliveriesSection(m, zone, out).write();
  }

  /** Ce qu'est un déploiement, en clair. */
  private String deploys() {
    Snapshot.Project p = s.project();
    Snapshot.Releases releases = s.releases();
    if (p.tags()) {
      return "un tag de version de "
          + p.defaultBranch()
          + " (nom qui contient "
          + (releases == null ? "" : releases.tagPattern())
          + "), à sa création : la date du tagueur pour un tag annoté, sinon la date de commit du"
          + " commit tagué. Toujours réussi ; fréquence : tags par semaine";
    }
    return p.gocd()
        ? "un run réussi de " + String.join(", ", p.prodPipelines()) + " (GoCD)"
        : "une merge request fusionnée sur " + p.defaultBranch() + " (pas de GoCD)";
  }

  /** Le délai, en clair. */
  private static String lead(Snapshot.Project p) {
    if (p.tags()) {
      return "pour chaque fonctionnalité et chaque tag qui en livre une part : de la date d'auteur"
          + " de son dernier commit livré par ce tag à la date du tag ; médiane. La variante par"
          + " date de commit (la plus récente de ses commits) est montrée à côté";
    }
    return p.gocd()
        ? "de la date d'auteur d'un commit à son premier déploiement réussi"
        : "du premier commit d'une merge request à sa fusion";
  }

  /** La règle de rattachement, en clair. */
  private static String mapping(Snapshot.Project p) {
    if (p.deploySource() == Snapshot.DeploySource.MERGE) {
      return "les commits de la merge request, à sa fusion";
    }
    if (p.tags()) {
      return "les commits atteignables depuis le tag et pas depuis le tag de version précédent"
          + " (comparaison GitLab), sans les commits de fusion (deux parents ou plus) ni les"
          + " commits ci(release):. Fonctionnalité, tag par tag : la clé Jira du titre"
          + " (type(scope)!: [ABC-123] …) ; sinon la clé de la branche (feature/ABC-123_… ou"
          + " bugfix/ABC-123_…) dont la fusion dans "
          + p.defaultBranch()
          + " a apporté le commit ; sinon un feat: ou un fix: est une fonctionnalité seule,"
          + " « sans story ». Un commit d'un autre type (chore, test, refactor, docs…) sans"
          + " story ne compte pas";
    }
    return "les commits que le run a embarqués, pipelines amont compris ; ceux d'un run en échec"
        + " relancé sans nouveau matériau vont au run réussi suivant du même pipeline";
  }

  private void rule(String name, String ours) {
    out.line("<tr><td>" + html(name) + "</td><td>" + html(ours) + "</td></tr>");
  }
}
