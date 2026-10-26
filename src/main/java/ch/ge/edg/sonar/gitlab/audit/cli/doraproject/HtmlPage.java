package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import static ch.ge.edg.sonar.gitlab.audit.cli.doraproject.Show.html;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * dora.html : une page autonome, sans script ni ressource externe, qui s'ouvre d'un double-clic.
 * Les quatre mesures en tête, puis les déploiements, les merge requests et les incidents dont elles
 * sont faites. Les données brutes sont aussi dans la page, en JSON, pour qui voudrait les
 * reprendre.
 */
final class HtmlPage {

  private static final String STYLE =
      """
      :root{--bg:#fbfbfa;--fg:#1d1d1b;--muted:#6b6b66;--line:#e2e1dc;--card:#fff;--accent:#2d5fa8;
      --ok:#2e7d4f;--ko:#b3412e;--warn:#9a6b00}
      @media (prefers-color-scheme:dark){:root{--bg:#161615;--fg:#ecebe6;--muted:#a3a29b;
      --line:#34332f;--card:#1f1f1d;--accent:#8db3ec;--ok:#7bc79a;--ko:#ec8f7d;--warn:#e0b45a}}
      *{box-sizing:border-box}
      body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.5 system-ui,-apple-system,
      "Segoe UI",Roboto,sans-serif}
      main{max-width:1180px;margin:0 auto;padding:24px 16px 48px}
      h1{font-size:24px;margin:0 0 4px}h2{font-size:18px;margin:32px 0 8px}
      a{color:var(--accent)}
      .sub,.note{color:var(--muted)}.note{font-size:13px}
      .tiles{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:12px;
      margin-top:20px}
      .tile{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:14px 16px}
      .tile h3{font-size:13px;font-weight:600;color:var(--muted);margin:0;text-transform:uppercase;
      letter-spacing:.03em}
      .tile .v{font-size:30px;font-weight:650;margin:6px 0 2px;font-variant-numeric:tabular-nums}
      .tile p{margin:0;font-size:13px;color:var(--muted)}
      .scroll{overflow-x:auto;border:1px solid var(--line);border-radius:10px;background:var(--card)}
      table{border-collapse:collapse;width:100%;font-size:14px}
      th,td{padding:7px 10px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top;
      white-space:nowrap}
      td.t{white-space:normal;min-width:220px}
      th{font-weight:600;color:var(--muted);background:var(--card);position:sticky;top:0}
      tr:last-child td{border-bottom:0}
      td.n{text-align:right;font-variant-numeric:tabular-nums}
      .SUCCESS{color:var(--ok)}.FAILURE{color:var(--ko)}.CANCELLED,.PENDING{color:var(--warn)}
      .empty{padding:12px 16px;color:var(--muted)}
      dl{display:grid;grid-template-columns:max-content 1fr;gap:4px 16px;margin:0}
      dt{color:var(--muted)}dd{margin:0}
      """;

  private final Snapshot s;
  private final Metrics m;
  private final ZoneId zone;
  private final StringBuilder out = new StringBuilder();

  private HtmlPage(Snapshot s, ZoneId zone) {
    this.s = s;
    this.m = Metrics.of(s);
    this.zone = zone;
  }

  /**
   * La page.
   *
   * @param json l'instantané en JSON, glissé tel quel dans la page
   */
  static String render(Snapshot s, String json, ZoneId zone) {
    return new HtmlPage(s, zone).page(json);
  }

  private String page(String json) {
    String path = s.project().path();
    line("<!doctype html>");
    line("<html lang=\"fr\"><head><meta charset=\"utf-8\">");
    line("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
    line("<title>DORA " + html(path) + "</title>");
    line("<style>" + STYLE + "</style></head><body><main>");
    line("<h1>DORA — <a href=\"" + html(s.project().webUrl()) + "\">" + html(path) + "</a></h1>");
    line(
        "<p class=\"sub\">Du "
            + Show.day(s.from(), zone)
            + " au "
            + Show.day(s.to(), zone)
            + " ("
            + m.days()
            + " jours), extrait le "
            + Show.dateTime(s.to(), zone)
            + ".</p>");
    tiles();
    rules();
    deployments();
    mergeRequests();
    incidents();
    line(
        "<script type=\"application/json\" id=\"donnees\">"
            + json.replace("</", "<\\/")
            + "</script>");
    line("</main></body></html>");
    return out.toString();
  }

  private void tiles() {
    line("<section class=\"tiles\">");
    String unit = s.project().deploySource() == Snapshot.DeploySource.GOCD ? "commits" : "MR";
    tile(
        "Fréquence de déploiement",
        Show.tenths(m.perWeek()) + " / sem.",
        m.successes().size() + " déploiements réussis en " + m.days() + " jours");
    tile(
        "Délai de mise en production",
        Show.duration(m.leadTime()),
        "médiane sur " + m.leads().size() + " " + unit);
    if (m.incidentsRead()) {
      tile(
          "Taux d'échec des changements",
          Show.percent(m.changeFailureRate()),
          m.failedChanges()
              + " déploiement(s) sur "
              + m.runs().size()
              + " suivi(s) d'un incident"
              + (m.failedRunRate() == null
                  ? ""
                  : " ; runs en échec : " + Show.percent(m.failedRunRate())));
      tile(
          "Temps de rétablissement",
          Show.duration(m.timeToRestore()),
          "médiane sur "
              + m.recoveries().size()
              + " incident(s) résolu(s) sur "
              + s.incidents().size());
    } else {
      tile("Taux d'échec des changements", Show.NONE, "pas de projet Jira : aucun incident lu");
      tile("Temps de rétablissement", Show.NONE, "pas de projet Jira : aucun incident lu");
    }
    line("</section>");
  }

  private void tile(String title, String value, String detail) {
    line(
        "<div class=\"tile\"><h3>"
            + html(title)
            + "</h3><div class=\"v\">"
            + html(value)
            + "</div><p>"
            + html(detail)
            + "</p></div>");
  }

  private void rules() {
    Snapshot.Project p = s.project();
    line("<h2>Règles appliquées</h2><dl>");
    String deploys =
        p.deploySource() == Snapshot.DeploySource.GOCD
            ? "un run réussi de " + String.join(", ", p.prodPipelines()) + " (GoCD)"
            : "une merge request fusionnée sur " + p.defaultBranch() + " (pas de GoCD)";
    rule("Déploiement", deploys);
    rule(
        "Délai",
        p.deploySource() == Snapshot.DeploySource.GOCD
            ? "de la date d'auteur d'un commit à son premier déploiement réussi"
            : "du premier commit d'une merge request à sa fusion");
    rule(
        "Incident",
        p.jiraProjects().isEmpty() ? "aucun : pas de projet Jira donné" : p.incidentRule());
    rule(
        "Échec d'un changement",
        "un déploiement, réussi ou non, suivi d'au moins un incident avant le déploiement"
            + " suivant, rapporté à tous les déploiements (la règle de Middleware)");
    rule("Rétablissement", "de la création de l'incident à sa date de résolution Jira");
    line("</dl>");
  }

  private void rule(String name, String text) {
    line("<dt>" + html(name) + "</dt><dd>" + html(text) + "</dd>");
  }

  private void deployments() {
    List<Snapshot.Deployment> all = s.deployments();
    line("<h2>Déploiements (" + all.size() + ")</h2>");
    if (all.isEmpty()) {
      empty("Aucun déploiement sur la période.");
      return;
    }
    table("Date", "Run", "Résultat", "Commits", "Incidents qui suivent");
    for (Snapshot.Deployment d : all.reversed()) {
      List<Snapshot.Incident> after = m.links().incidentsAfter().getOrDefault(d, List.of());
      row(
          td(Show.dateTime(d.at(), zone)),
          "<td><a href=\""
              + html(d.url())
              + "\">"
              + html(d.pipeline() + " #" + d.counter())
              + "</a>"
              + (d.forced() ? " (forcé)" : "")
              + "</td>",
          "<td class=\"" + d.outcome().name() + "\">" + html(d.outcome().label()) + "</td>",
          num(d.commits().size()),
          td(String.join(", ", after.stream().map(Snapshot.Incident::key).toList())));
    }
    endTable();
  }

  private void mergeRequests() {
    List<Snapshot.MergeRequest> all = s.mergeRequests();
    line("<h2>Merge requests fusionnées (" + all.size() + ")</h2>");
    if (all.isEmpty()) {
      empty("Aucune merge request fusionnée sur la branche par défaut pendant la période.");
      return;
    }
    table("MR", "Titre", "Auteur", "Fusionnée", "En production", "Écriture", "Revue", "Attente");
    for (Snapshot.MergeRequest mr : all.reversed()) {
      Snapshot.Deployment d = m.links().deployedBy().get(mr.iid());
      Instant deployed = d == null ? null : d.at();
      row(
          "<td><a href=\"" + html(mr.url()) + "\">!" + mr.iid() + "</a></td>",
          "<td class=\"t\">" + html(mr.title()) + "</td>",
          td(mr.author()),
          td(Show.dateTime(mr.mergedAt(), zone)),
          td(Show.dateTime(deployed, zone)),
          td(Show.duration(Metrics.between(mr.firstCommitAt(), mr.createdAt()))),
          td(Show.duration(Metrics.between(mr.createdAt(), mr.mergedAt()))),
          td(Show.duration(Metrics.between(mr.mergedAt(), deployed))));
    }
    endTable();
    line(
        "<p class=\"note\">Écriture : du premier commit à l'ouverture. Revue : de l'ouverture à"
            + " la fusion. Attente : de la fusion à la mise en production.</p>");
  }

  private void incidents() {
    List<Snapshot.Incident> all = s.incidents();
    line("<h2>Incidents (" + all.size() + ")</h2>");
    if (all.isEmpty()) {
      empty(
          s.project().jiraProjects().isEmpty()
              ? "Pas de projet Jira donné : ni taux d'échec ni rétablissement."
              : "Aucun incident sur la période.");
      return;
    }
    table("Clé", "Titre", "Type", "Créé", "Résolu", "Durée", "Après le déploiement");
    for (Snapshot.Incident i : all.reversed()) {
      Snapshot.Deployment d = m.links().precededBy().get(i.key());
      row(
          "<td><a href=\"" + html(i.url()) + "\">" + html(i.key()) + "</a></td>",
          "<td class=\"t\">" + html(i.title()) + "</td>",
          td(i.type()),
          td(Show.dateTime(i.createdAt(), zone)),
          td(Show.dateTime(i.resolvedAt(), zone)),
          td(Show.duration(Metrics.between(i.createdAt(), i.resolvedAt()))),
          td(d == null ? Show.NONE : d.pipeline() + " #" + d.counter()));
    }
    endTable();
  }

  private void table(String... headers) {
    StringBuilder h = new StringBuilder("<div class=\"scroll\"><table><thead><tr>");
    for (String header : headers) h.append("<th>").append(html(header)).append("</th>");
    line(h.append("</tr></thead><tbody>").toString());
  }

  private void row(String... cells) {
    line("<tr>" + String.join("", cells) + "</tr>");
  }

  private void endTable() {
    line("</tbody></table></div>");
  }

  private void empty(String text) {
    line("<div class=\"scroll empty\">" + html(text) + "</div>");
  }

  private static String td(String text) {
    return "<td>" + html(text) + "</td>";
  }

  private static String num(int n) {
    return "<td class=\"n\">" + n + "</td>";
  }

  private void line(String s) {
    out.append(s).append('\n');
  }
}
