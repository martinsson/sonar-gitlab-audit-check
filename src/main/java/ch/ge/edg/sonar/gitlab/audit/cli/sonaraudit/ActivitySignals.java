package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.BOLD;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.YELLOW;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.orEmptyList;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.orZero;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.params;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.parseDate;
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
import ch.ge.edg.sonar.gitlab.audit.sonar.Sonar;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        out, "4. Signaux d'activité disponibles sans Git (sur %s)".formatted(sample));
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
    Sonar.Response r =
        session
            .sq()
            .get(
                "api/project_analyses/search",
                params("project", sample, "ps", "500", "from", since));
    Analyses a = r.as(Analyses.class);
    if (a == null) {
      Presentation.line(
          out, "api/project_analyses/search", CallVerdict.of(r), "HTTP " + r.status());
      return;
    }
    List<Analysis> analyses = orEmptyList(a.analyses());
    long versions =
        analyses.stream()
            .flatMap(x -> orEmptyList(x.events()).stream())
            .filter(e -> "VERSION".equals(e.category()))
            .map(Event::name)
            .distinct()
            .count();

    out.out()
        .printf(
            "  Analyses sur %d j            : %s%n",
            days, out.color(String.valueOf(analyses.size()), BOLD));
    out.out().printf("  Versions livrées                : %d%n", versions);

    List<LocalDateTime> dates =
        analyses.stream()
            .map(x -> parseDate(x.date(), session.zone()))
            .filter(Objects::nonNull)
            .sorted()
            .toList();
    if (dates.size() > 1) {
      long span = Math.max(1, ChronoUnit.DAYS.between(dates.get(0), dates.get(dates.size() - 1)));
      out.out()
          .printf(
              "  Cadence moyenne                 : 1 analyse / %.1f j%n",
              (double) span / dates.size());
    }
  }

  /** La facette 'author' donne le nombre de contributeurs sans toucher à Git. */
  private void reportAuthorConcentration(String since) {
    Sonar.Response r =
        session
            .sq()
            .get(
                "api/issues/search",
                params(
                    "componentKeys", sample, "createdAfter", since, "facets", "author", "ps", "1"));
    IssuesSearch is = r.as(IssuesSearch.class);
    if (is == null) {
      Presentation.line(
          out, "Facette 'author' sur issues", CallVerdict.of(r), "HTTP " + r.status());
      return;
    }
    List<FacetValue> authors = is.facetValues("author");
    int total = Math.max(1, authors.stream().mapToInt(f -> orZero(f.count())).sum());

    out.println();
    out.out()
        .printf(
            "  Auteurs distincts (%d j)      : %s%n",
            days, out.color(String.valueOf(authors.size()), BOLD));
    if (authors.isEmpty()) return;

    FacetValue top = authors.get(0);
    double share = (double) orZero(top.count()) / total;
    out.out()
        .printf(
            "  Auteur principal                : %s (%.0f%% des issues récentes)%n",
            top.val(), share * 100);
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
    out.out().printf("  Lignes neuves (new code)        : %s%n", orNa(ms.get("new_lines")));
    out.out().printf("  Violations neuves               : %s%n", orNa(ms.get("new_violations")));
    out.out().printf("  Couverture du code neuf         : %s%n", orNa(ms.get("new_coverage")));
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
              "  Vélocité de dette indisponible : moins de deux analyses "
                  + "chiffrées sur la fenêtre.",
              DIM));
      return;
    }
    // La fenêtre demandée et la fenêtre mesurée ne coïncident jamais : les
    // points sont ceux des analyses, pas ceux du calendrier. On affiche
    // l'écart réellement mesuré, sinon le lecteur rapporte à 90 jours une
    // variation qui en couvre 12.
    out.out()
        .printf("  Δ lignes sur %d j (%d j mesurés) : %+d%n", days, v.spanDays(), v.deltaNcloc());
    out.out().printf("  Δ dette sur %d j (min)       : %+d%n", days, v.deltaDebt());
    if (v.deltaNcloc() > 0) {
      out.println(
          "  Dette ajoutée par ligne écrite  : "
              + out.color("%+.2f min/LOC".formatted(v.debtPerLine()), BOLD));
      out.println(out.color("    (négatif = l'équipe rembourse ; > 10 = signal fort)", DIM));
    }
  }
}
