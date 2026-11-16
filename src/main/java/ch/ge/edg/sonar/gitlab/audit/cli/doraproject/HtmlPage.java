package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import static ch.ge.edg.sonar.gitlab.audit.cli.doraproject.Show.html;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * dora.html : une page autonome, sans script ni ressource externe, qui s'ouvre d'un double-clic.
 * Les quatre mesures en tête, puis les déploiements, les merge requests et les incidents dont elles
 * sont faites. Les données brutes sont aussi dans la page, en JSON, pour qui voudrait les
 * reprendre.
 */
final class HtmlPage {

  private static final String STYLE =
      """
      :root{--bg:#f6f7f9;--fg:#16181d;--muted:#5f6672;--line:#e3e6eb;--card:#fff;--accent:#2457c5;
      --ok:#1f7a4a;--ko:#b3392b;--warn:#946200;--elite:#1f7a4a;--high:#3d8b2f;--medium:#b07800;
      --low:#b3392b;--chip:#eef1f5;--shadow:0 1px 2px rgba(20,24,32,.06),0 4px 16px rgba(20,24,32,.04)}
      @media (prefers-color-scheme:dark){:root{--bg:#121417;--fg:#e9ebef;--muted:#9aa2ae;
      --line:#2a2e35;--card:#1a1d22;--accent:#8fb2f5;--ok:#6fcf97;--ko:#f08a7c;--warn:#e7b65a;
      --elite:#6fcf97;--high:#a5d96a;--medium:#e7b65a;--low:#f08a7c;--chip:#23272e;--shadow:none}}
      *{box-sizing:border-box}
      body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.5 system-ui,-apple-system,
      "Segoe UI",Roboto,sans-serif}
      main{max-width:1180px;margin:0 auto;padding:28px 16px 56px}
      header{display:flex;flex-wrap:wrap;align-items:baseline;justify-content:space-between;gap:8px}
      h1{font-size:26px;margin:0;letter-spacing:-.01em}h1 small{color:var(--muted);font-weight:500;
      font-size:15px;display:block;text-transform:uppercase;letter-spacing:.08em}
      h2{font-size:18px;margin:36px 0 10px}
      a{color:var(--accent);text-decoration:none}a:hover{text-decoration:underline}
      .sub,.note{color:var(--muted)}.note{font-size:13px;margin:8px 2px 0}
      .tiles{display:grid;grid-template-columns:repeat(auto-fit,minmax(250px,1fr));gap:14px;
      margin-top:22px}
      .tile{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:16px 18px;
      box-shadow:var(--shadow);border-top:4px solid var(--line)}
      .tile.ELITE{border-top-color:var(--elite)}.tile.HIGH{border-top-color:var(--high)}
      .tile.MEDIUM{border-top-color:var(--medium)}.tile.LOW{border-top-color:var(--low)}
      .tile h3{font-size:12px;font-weight:650;color:var(--muted);margin:0;text-transform:uppercase;
      letter-spacing:.06em}
      .tile .v{font-size:34px;font-weight:700;margin:6px 0 4px;font-variant-numeric:tabular-nums;
      letter-spacing:-.02em}
      .tile p{margin:8px 0 0;font-size:13px;color:var(--muted)}
      .band{display:inline-block;font-size:12px;font-weight:650;padding:2px 9px;border-radius:99px;
      background:var(--chip);color:var(--muted)}
      .band.ELITE{color:var(--elite)}.band.HIGH{color:var(--high)}.band.MEDIUM{color:var(--medium)}
      .band.LOW{color:var(--low)}
      .scale{display:grid;grid-template-columns:repeat(4,1fr);gap:3px;margin-top:10px}
      .scale span{height:6px;border-radius:3px;background:var(--chip)}
      .scale span.on.ELITE{background:var(--elite)}.scale span.on.HIGH{background:var(--high)}
      .scale span.on.MEDIUM{background:var(--medium)}.scale span.on.LOW{background:var(--low)}
      .scroll{overflow-x:auto;border:1px solid var(--line);border-radius:12px;background:var(--card);
      box-shadow:var(--shadow)}
      table{border-collapse:collapse;width:100%;font-size:14px}
      th,td{padding:8px 12px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top;
      white-space:nowrap}
      td.t{white-space:normal;min-width:220px}
      th{font-weight:600;color:var(--muted);background:var(--card);position:sticky;top:0;font-size:13px}
      tbody tr:nth-child(even) td{background:color-mix(in srgb,var(--chip) 45%,transparent)}
      tr:last-child td{border-bottom:0}
      td.n{text-align:right;font-variant-numeric:tabular-nums}
      .SUCCESS{color:var(--ok)}.FAILURE{color:var(--ko)}.CANCELLED,.PENDING{color:var(--warn)}
      td.SUCCESS,td.FAILURE,td.CANCELLED,td.PENDING{font-weight:600}
      .empty{padding:12px 16px;color:var(--muted)}
      .rules td:first-child{font-weight:600;white-space:nowrap}.rules td{white-space:normal}
      """;

  private final Snapshot s;
  private final Metrics m;
  private final ZoneId zone;
  private final Markup out = new Markup();

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
    out.line("<!doctype html>");
    out.line("<html lang=\"fr\"><head><meta charset=\"utf-8\">");
    out.line("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
    out.line("<title>DORA " + html(path) + "</title>");
    out.line("<style>" + STYLE + "</style></head><body><main>");
    out.line("<header><h1><small>Mesures DORA</small>");
    out.line("<a href=\"" + html(s.project().webUrl()) + "\">" + html(path) + "</a></h1>");
    out.line(
        "<p class=\"sub\">Du "
            + Show.day(s.from(), zone)
            + " au "
            + Show.day(s.to(), zone)
            + " ("
            + m.days()
            + " jours), extrait le "
            + Show.dateTime(s.to(), zone)
            + ".</p></header>");
    tiles();
    new RulesSection(s, m, zone, out).write();
    deployments();
    mergeRequests();
    incidents();
    out.line(
        "<script type=\"application/json\" id=\"donnees\">"
            + json.replace("</", "<\\/")
            + "</script>");
    out.line("</main></body></html>");
    return out.toString();
  }

  private void tiles() {
    out.line("<section class=\"tiles\">");
    String unit = leadUnit();
    tile(
        "Fréquence de déploiement",
        Show.tenths(m.perWeek()) + " / sem.",
        Bands.frequency(m.perWeek()),
        m.successes().size() + " déploiements réussis en " + m.days() + " jours");
    tile(
        "Délai de mise en production",
        Show.duration(m.leadTime()),
        Bands.leadTime(m.leadTime()),
        "médiane sur " + m.leads().size() + " " + unit);
    if (m.incidentsRead()) {
      tile(
          "Taux d'échec des changements",
          Show.percent(m.changeFailureRate()),
          Bands.failureRate(m.changeFailureRate()),
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
          Bands.restore(m.timeToRestore()),
          "médiane sur "
              + m.recoveries().size()
              + " incident(s) résolu(s) sur "
              + s.incidents().size());
    } else {
      tile(
          "Taux d'échec des changements",
          Show.NONE,
          null,
          "pas de projet Jira : aucun incident lu");
      tile("Temps de rétablissement", Show.NONE, null, "pas de projet Jira : aucun incident lu");
    }
    out.line("</section>");
    out.line(
        "<p class=\"note\">Niveaux du rapport DORA 2023, aux seuils du tableau de bord DORA"
            + " d'Apache DevLake : élite, haut, moyen, bas.</p>");
  }

  private String leadUnit() {
    if (s.project().deploySource() == Snapshot.DeploySource.MERGE) return "MR";
    return s.project().reachable() ? "fonctionnalités" : "commits";
  }

  private void tile(String title, String value, Bands.@Nullable Band band, String detail) {
    String cls = band == null ? "" : " " + band.name();
    StringBuilder scale = new StringBuilder("<div class=\"scale\">");
    for (Bands.Band b : Bands.Band.values()) {
      scale.append(b == band ? "<span class=\"on" + cls + "\"></span>" : "<span></span>");
    }
    scale.append("</div>");
    out.line(
        "<div class=\"tile"
            + cls
            + "\"><h3>"
            + html(title)
            + "</h3><div class=\"v\">"
            + html(value)
            + "</div><span class=\"band"
            + cls
            + "\">"
            + (band == null ? "sans niveau" : "niveau " + html(band.label()))
            + "</span>"
            + scale
            + "<p>"
            + html(detail)
            + "</p></div>");
  }

  private void deployments() {
    List<Snapshot.Deployment> all = s.deployments();
    out.line("<h2>Déploiements (" + all.size() + ")</h2>");
    if (all.isEmpty()) {
      out.empty("Aucun déploiement sur la période.");
      return;
    }
    out.table("Date", "Run", "Résultat", "Embarqués", "Livrés", "Incidents qui suivent");
    for (Snapshot.Deployment d : all.reversed()) {
      List<Snapshot.Incident> after = m.links().incidentsAfter().getOrDefault(d, List.of());
      out.row(
          Markup.td(Show.dateTime(d.at(), zone)),
          "<td><a href=\""
              + html(d.url())
              + "\">"
              + html(d.pipeline() + " #" + d.counter())
              + "</a>"
              + (d.forced() ? " (forcé)" : "")
              + "</td>",
          "<td class=\"" + d.outcome().name() + "\">" + html(d.outcome().label()) + "</td>",
          Markup.num(d.commits().size()),
          Markup.num(m.links().delivered().getOrDefault(d, List.of()).size()),
          Markup.td(String.join(", ", after.stream().map(Snapshot.Incident::key).toList())));
    }
    out.endTable();
    out.line(
        "<p class=\"note\">Embarqués : les commits du projet que GoCD dit avoir embarqués."
            + " Livrés : ceux que nos règles lui attribuent, mis en production pour la première"
            + " fois.</p>");
  }

  private void mergeRequests() {
    List<Snapshot.MergeRequest> all = s.mergeRequests();
    out.line("<h2>Merge requests fusionnées (" + all.size() + ")</h2>");
    if (all.isEmpty()) {
      out.empty("Aucune merge request fusionnée sur la branche par défaut pendant la période.");
      return;
    }
    out.table(
        "MR", "Titre", "Auteur", "Fusionnée", "En production", "Écriture", "Revue", "Attente");
    for (Snapshot.MergeRequest mr : all.reversed()) {
      Snapshot.Deployment d = m.links().deployedBy().get(mr.iid());
      Instant deployed = d == null ? null : d.at();
      out.row(
          "<td><a href=\"" + html(mr.url()) + "\">!" + mr.iid() + "</a></td>",
          "<td class=\"t\">" + html(mr.title()) + "</td>",
          Markup.td(mr.author()),
          Markup.td(Show.dateTime(mr.mergedAt(), zone)),
          Markup.td(Show.dateTime(deployed, zone)),
          Markup.td(Show.duration(Metrics.between(mr.firstCommitAt(), mr.createdAt()))),
          Markup.td(Show.duration(Metrics.between(mr.createdAt(), mr.mergedAt()))),
          Markup.td(Show.duration(Metrics.between(mr.mergedAt(), deployed))));
    }
    out.endTable();
    out.line(
        "<p class=\"note\">Écriture : du premier commit à l'ouverture. Revue : de l'ouverture à"
            + " la fusion. Attente : de la fusion à la mise en production.</p>");
  }

  private void incidents() {
    List<Snapshot.Incident> all = s.incidents();
    out.line("<h2>Incidents (" + all.size() + ")</h2>");
    if (all.isEmpty()) {
      out.empty(
          s.project().jiraProjects().isEmpty()
              ? "Pas de projet Jira donné : ni taux d'échec ni rétablissement."
              : "Aucun incident sur la période.");
      return;
    }
    out.table("Clé", "Titre", "Type", "Créé", "Résolu", "Durée", "Après le déploiement");
    for (Snapshot.Incident i : all.reversed()) {
      Snapshot.Deployment d = m.links().precededBy().get(i.key());
      out.row(
          "<td><a href=\"" + html(i.url()) + "\">" + html(i.key()) + "</a></td>",
          "<td class=\"t\">" + html(i.title()) + "</td>",
          Markup.td(i.type()),
          Markup.td(Show.dateTime(i.createdAt(), zone)),
          Markup.td(Show.dateTime(i.resolvedAt(), zone)),
          Markup.td(Show.duration(Metrics.between(i.createdAt(), i.resolvedAt()))),
          Markup.td(d == null ? Show.NONE : d.pipeline() + " #" + d.counter()));
    }
    out.endTable();
  }
}
