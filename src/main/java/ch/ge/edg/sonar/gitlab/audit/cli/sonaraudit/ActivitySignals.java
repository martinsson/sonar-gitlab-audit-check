package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.BOLD;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.YELLOW;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.orEmptyList;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.orZero;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.parseDate;
import static ch.ge.edg.sonar.gitlab.audit.rest.Query.params;
import static ch.ge.edg.sonar.gitlab.audit.text.Text.orEmpty;
import static ch.ge.edg.sonar.gitlab.audit.text.Text.orNa;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Analyses;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Analysis;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.ComponentMeasures;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Event;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.FacetValue;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.IssuesSearch;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Measure;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.SearchHistory;
import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** 4. Les signaux d'activité qu'on lit sans passer par Git, sur le projet témoin. */
final class ActivitySignals {

  private final Session session;
  private final Console out;
  private final String sample;
  private final int days;

  ActivitySignals(Session session, String sample, int days) {
    this.session = session;
    this.out = session.out();
    this.sample = sample;
    this.days = days;
  }

  void report() {
    Presentation.title(
        out, Text.format("4. Signaux d'activité disponibles sans Git (sur %s)", sample));
    String since =
        LocalDateTime.now(session.clock()).minusDays(days).format(DateTimeFormatter.ISO_LOCAL_DATE);

    reportAnalysisCadence(since);
    reportAuthorConcentration(since);
    reportNewCodeVolume();
    reportDebtPerLine(since);
    new Blame(session).report(sample);
  }

  /** Cadence d'analyses = cadence de livraison (biais : les builds nocturnes la gonflent). */
  private void reportAnalysisCadence(String since) {
    RestResponse r =
        session
            .sq()
            .get(
                "api/project_analyses/search",
                params("project", sample, "ps", "500", "from", since));
    Optional<Analyses> a = r.as(Analyses.class);
    if (a.isEmpty()) {
      Presentation.line(out, "api/project_analyses/search", CallVerdict.of(r), r.statusLine());
      return;
    }
    List<Analysis> analyses = orEmptyList(a.get().analyses());
    long versions =
        analyses.stream()
            .flatMap(x -> orEmptyList(x.events()).stream())
            .filter(e -> "VERSION".equals(e.category()))
            .map(Event::name)
            .distinct()
            .count();

    out.print(
        Text.format(
            "  Analyses sur %d j            : %s%n",
            days, out.color(String.valueOf(analyses.size()), BOLD)));
    out.print(Text.format("  Versions livrées                : %d%n", versions));

    List<LocalDateTime> dates =
        analyses.stream()
            .map(x -> parseDate(x.date(), session.zone()))
            .filter(Objects::nonNull)
            .sorted()
            .toList();
    if (dates.size() > 1) {
      long span = Math.max(1, ChronoUnit.DAYS.between(dates.get(0), dates.get(dates.size() - 1)));
      out.print(
          Text.format(
              "  Cadence moyenne                 : 1 analyse / %.1f j%n",
              (double) span / (dates.size() - 1)));
    }
  }

  /** La facette 'author' donne le nombre de contributeurs sans toucher à Git. */
  private void reportAuthorConcentration(String since) {
    RestResponse r =
        session
            .sq()
            .get(
                "api/issues/search",
                params(
                    "componentKeys", sample, "createdAfter", since, "facets", "author", "ps", "1"));
    Optional<IssuesSearch> is = r.as(IssuesSearch.class);
    if (is.isEmpty()) {
      Presentation.line(out, "Facette 'author' sur issues", CallVerdict.of(r), r.statusLine());
      return;
    }
    List<FacetValue> authors = is.get().facetValues("author");
    int total = Math.max(1, authors.stream().mapToInt(f -> orZero(f.count())).sum());

    out.println();
    out.print(
        Text.format(
            "  Auteurs distincts (%d j)      : %s%n",
            days, out.color(String.valueOf(authors.size()), BOLD)));
    if (authors.isEmpty()) return;

    FacetValue top = authors.get(0);
    double share = (double) orZero(top.count()) / total;
    out.print(
        Text.format(
            "  Auteur principal                : %s (%.0f%% des issues récentes)%n",
            top.val(), share * 100));
    if (share > 0.7 && authors.size() > 1) {
      out.println(out.color("    → forte concentration : proxy de bus factor faible", YELLOW));
    }
  }

  private void reportNewCodeVolume() {
    ComponentMeasures cm =
        session.read(
            "api/measures/component",
            params(
                "component", sample, "metricKeys", "new_lines,new_violations,new_coverage,ncloc"),
            ComponentMeasures.class);
    if (cm == null || cm.component() == null) return;

    Map<String, String> ms =
        cm.measures().stream()
            .collect(
                Collectors.toMap(Measure::metric, m -> orEmpty(m.effectiveValue()), (a, b) -> a));
    out.println();
    out.print(Text.format("  Lignes neuves (new code)        : %s%n", orNa(ms.get("new_lines"))));
    out.print(
        Text.format("  Violations neuves               : %s%n", orNa(ms.get("new_violations"))));
    out.print(
        Text.format("  Couverture du code neuf         : %s%n", orNa(ms.get("new_coverage"))));
  }

  /** Δdette / Δlignes : la dette ajoutée par ligne écrite, le vrai signal de vélocité. */
  private void reportDebtPerLine(String since) {
    SearchHistory h =
        session.read(
            "api/measures/search_history",
            params(
                "component", sample, "metrics", "ncloc,sqale_index", "ps", "1000", "from", since),
            SearchHistory.class);
    if (h == null) return;

    Velocity v = Velocity.of(h.pointsFor("ncloc"), h.pointsFor("sqale_index"), session.zone());
    out.println();
    if (v == null) {
      out.println(
          out.color(
              "  Vélocité de dette indisponible : moins de deux analyses chiffrées sur la fenêtre.",
              DIM));
      return;
    }
    // La fenêtre demandée et la fenêtre mesurée ne coïncident jamais : les
    // points sont ceux des analyses, pas ceux du calendrier. On affiche
    // l'écart réellement mesuré, sinon le lecteur rapporte à 90 jours une
    // variation qui en couvre 12.
    out.print(
        Text.format(
            "  Δ lignes sur %d j (%d j mesurés) : %+d%n", days, v.spanDays(), v.deltaNcloc()));
    out.print(Text.format("  Δ dette sur %d j (min)       : %+d%n", days, v.deltaDebt()));
    if (v.deltaNcloc() > 0) {
      out.println(
          "  Dette ajoutée par ligne écrite  : "
              + out.color(Text.format("%+.2f min/LOC", v.debtPerLine()), BOLD));
      out.println(out.color("    (négatif = l'équipe rembourse ; > 10 = signal fort)", DIM));
    }
  }
}
