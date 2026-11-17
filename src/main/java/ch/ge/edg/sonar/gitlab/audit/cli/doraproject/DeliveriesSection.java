package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import static ch.ge.edg.sonar.gitlab.audit.cli.doraproject.Show.html;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * La section « Tags de version » de dora.html : pour chaque tag, ce qu'il a livré depuis le
 * précédent, ses fonctionnalités avec leur dernier commit et leurs deux délais, et les commits
 * écartés ; puis les totaux.
 */
final class DeliveriesSection {

  private final Metrics m;
  private final ZoneId zone;
  private final Markup out;

  DeliveriesSection(Metrics m, ZoneId zone, Markup out) {
    this.m = m;
    this.zone = zone;
    this.out = out;
  }

  void write() {
    List<DeliveredFeatures.Shipment> all = m.links().shipments();
    out.line("<h2>Tags de version (" + all.size() + ")</h2>");
    if (all.isEmpty()) {
      out.empty("Aucun tag de version de la branche par défaut sur la période.");
      return;
    }
    out.table(
        "Tag",
        "Date",
        "Fonctionnalité",
        "Rattachée par",
        "Commits",
        "Dernier commit",
        "Délai (auteur)",
        "Délai (commit)",
        "Écartés");
    for (DeliveredFeatures.Shipment t : all.reversed()) shipment(t);
    out.endTable();
    note(all);
  }

  /** Les lignes d'un tag : une par fonctionnalité, ou une seule qui dit qu'il n'en livre aucune. */
  private void shipment(DeliveredFeatures.Shipment t) {
    Delivery d = t.delivery();
    Deploy previous = d.previous();
    int rows = t.features().size();
    String span = rows > 1 ? " rowspan=\"" + rows + "\"" : "";
    String head =
        "<td"
            + span
            + "><a href=\""
            + html(t.deployment().url())
            + "\">"
            + html(d.deploy().name())
            + "</a>"
            + (d.truncated() ? " (incomplet)" : "")
            + "<br><span class=\"note\">"
            + (previous == null ? "premier tag" : "depuis " + html(previous.name()))
            + "</span></td><td"
            + span
            + ">"
            + html(Show.dateTime(d.deploy().at(), zone))
            + "</td>";
    String excluded = "<td class=\"t\"" + span + ">" + html(excluded(t)) + "</td>";
    if (t.features().isEmpty()) {
      out.row(head, "<td class=\"t\" colspan=\"6\">aucun commit de travail</td>", excluded);
      return;
    }
    boolean first = true;
    for (DeliveredFeatures.Feature f : t.features()) {
      Delivery.Commit last = f.last();
      out.row(
          first ? head : "",
          Markup.td(f.key() + (f.story() ? "" : " (sans story)")),
          Markup.td(f.source()),
          Markup.num(f.commits().size()),
          "<td class=\"t\">"
              + html(last.shortSha() + " " + last.title())
              + " ("
              + html(Show.dateTime(last.authoredAt(), zone))
              + ")</td>",
          Markup.td(Show.duration(f.authorLead())),
          Markup.td(Show.duration(f.committerLead())),
          first ? excluded : "");
      first = false;
    }
  }

  /** « 1 chore, 1 fusion, 1 ci(release) » : ce qui ne compte pas, sans les zéros. */
  private static String excluded(DeliveredFeatures.Shipment t) {
    List<String> parts = new ArrayList<>();
    t.excluded().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(e -> parts.add(e.getValue() + " " + e.getKey()));
    if (t.merges() > 0) parts.add(t.merges() + " fusion(s)");
    if (t.ciReleases() > 0) parts.add(t.ciReleases() + " ci(release)");
    return String.join(", ", parts);
  }

  private void note(List<DeliveredFeatures.Shipment> all) {
    int merges = all.stream().mapToInt(DeliveredFeatures.Shipment::merges).sum();
    int ci = all.stream().mapToInt(DeliveredFeatures.Shipment::ciReleases).sum();
    List<String> parts = new ArrayList<>();
    DeliveredFeatures.excluded(all).forEach((type, n) -> parts.add(n + " " + type));
    List<String> idle =
        all.stream()
            .filter(t -> t.features().isEmpty())
            .map(t -> t.delivery().deploy().name())
            .toList();
    StringBuilder text =
        new StringBuilder("Écartés faute de story : ")
            .append(parts.isEmpty() ? "aucun" : String.join(", ", parts))
            .append(". Écartés toujours : ")
            .append(merges)
            .append(" commit(s) de fusion, ")
            .append(ci)
            .append(" commit(s) ci(release):.");
    if (!idle.isEmpty()) {
      text.append(" Tags sans commit de travail : ").append(String.join(", ", idle)).append('.');
    }
    if (all.getFirst().delivery().previous() == null) {
      text.append(" Le premier tag n'a pas de tag de version avant lui : ses commits sont ceux")
          .append(" qu'il atteint, commités pendant la période.");
    }
    if (all.stream().anyMatch(t -> t.delivery().truncated())) {
      text.append(" Incomplet : GitLab a interrompu ou refusé la comparaison.");
    }
    out.line("<p class=\"note\">" + html(text.toString()) + "</p>");
  }
}
