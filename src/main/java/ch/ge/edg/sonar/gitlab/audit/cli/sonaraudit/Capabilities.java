package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.YELLOW;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.params;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.size;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.total;
import static ch.ge.edg.sonar.gitlab.audit.text.Text.orEmpty;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.AlmBinding;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Analyses;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.IssuesSearch;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.MeasuresSearch;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.ProjectLinks;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.ProjectSearch;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.QualityGateResponse;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.QualityProfiles;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.ScmResponse;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.SearchHistory;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.SettingsValues;
import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** 2. Quelles requêtes le jeton peut-il réellement faire, parmi celles qui comptent ? */
final class Capabilities {

  private final Session session;
  private final Console out;
  private final Metrics metrics;

  Capabilities(Session session, Metrics metrics) {
    this.session = session;
    this.out = session.out();
    this.metrics = metrics;
  }

  void check(@Nullable String sample) {
    Presentation.title(out, "2. Capacités du token sur les endpoints utiles");
    if (sample == null) {
      out.println(
          out.color(
              "  Aucun projet visible : " + "impossible de tester les endpoints projet.", YELLOW));
      return;
    }
    out.println("  Projet témoin : " + out.color(sample, DIM) + "\n");
    measureProbes(sample).forEach(p -> p.run(session));
    governanceProbes(sample).forEach(p -> p.run(session));
    repositoryProbes(sample).forEach(p -> p.run(session));
    probeBlame(sample);
  }

  /** Ce que l'inventaire et les tendances lisent. */
  private List<Probe<?>> measureProbes(String sample) {
    return List.of(
        new Probe<>(
            "Inventaire projets (search_projects)",
            "api/components/search_projects",
            ProjectSearch.class,
            p -> total(p.paging()) + " projets visibles",
            params("ps", "1")),
        new Probe<>(
            "Mesures en masse (measures/search)",
            "api/measures/search",
            MeasuresSearch.class,
            p -> size(p.measures()) + " mesures",
            params("projectKeys", sample, "metricKeys", metrics.keys())),
        new Probe<>(
            "Historique de mesures (search_history)",
            "api/measures/search_history",
            SearchHistory.class,
            p -> total(p.paging()) + " points",
            params("component", sample, "metrics", "ncloc,coverage,sqale_index", "ps", "5")),
        new Probe<>(
            "Issues + facettes (issues/search)",
            "api/issues/search",
            IssuesSearch.class,
            p -> p.issueCount() + " issues",
            params("componentKeys", sample, "ps", "1", "facets", "severities")),
        new Probe<>(
            "Contournements (WONTFIX / FALSE-POSITIVE)",
            "api/issues/search",
            IssuesSearch.class,
            p -> p.issueCount() + " issues neutralisées",
            params("componentKeys", sample, "resolutions", "WONTFIX,FALSE-POSITIVE", "ps", "1")));
  }

  /** Les règles du jeu : exclusions, quality gate, profils, analyses. */
  private static List<Probe<?>> governanceProbes(String sample) {
    return List.of(
        new Probe<>(
            "Exclusions (settings/values)",
            "api/settings/values",
            SettingsValues.class,
            p -> size(p.settings()) + " réglage(s) d'exclusion",
            params(
                "component",
                sample,
                "keys",
                "sonar.exclusions,sonar.coverage.exclusions,sonar.cpd.exclusions")),
        new Probe<>(
            "Quality gate du projet",
            "api/qualitygates/get_by_project",
            QualityGateResponse.class,
            QualityGateResponse::gateName,
            params("project", sample)),
        new Probe<>(
            "Quality profiles du projet",
            "api/qualityprofiles/search",
            QualityProfiles.class,
            p -> size(p.profiles()) + " profil(s)",
            params("project", sample)),
        new Probe<>(
            "Historique d'analyses (project_analyses)",
            "api/project_analyses/search",
            Analyses.class,
            p -> total(p.paging()) + " analyses",
            params("project", sample, "ps", "5")));
  }

  /** Le parc entier, et ce qui relie un projet à son dépôt. */
  private static List<Probe<?>> repositoryProbes(String sample) {
    return List.of(
        new Probe<>(
            "Arbre de composants (components/tree)",
            "api/components/tree",
            ProjectSearch.class,
            p -> total(p.paging()) + " fichiers",
            params("component", sample, "qualifiers", "FIL", "ps", "1")),
        new Probe<>(
            "Total réel des projets (admin uniquement)",
            "api/projects/search",
            ProjectSearch.class,
            p -> total(p.paging()) + " projets au total",
            params("ps", "1")),
        new Probe<>(
            "Liaison DevOps (alm_settings/get_binding)",
            "api/alm_settings/get_binding",
            AlmBinding.class,
            b ->
                b.alm() == null
                    ? "aucune liaison"
                    : "%s, dépôt %s".formatted(b.alm(), orEmpty(b.repository())),
            params("project", sample)),
        new Probe<>(
            "Liens du projet (project_links/search)",
            "api/project_links/search",
            ProjectLinks.class,
            l ->
                Values.orEmptyList(l.links()).stream().filter(k -> !Text.isBlank(k.url())).count()
                    + " lien(s)",
            params("projectKey", sample)));
  }

  /** Blame SCM : nécessite 'See Source Code' en plus de 'Browse'. */
  private void probeBlame(String sample) {
    String fileKey = Lookups.firstFileOf(session, sample);
    if (fileKey == null) {
      Presentation.line(
          out, "Blame SCM (sources/scm)", Verdict.EMPTY, "aucun fichier accessible pour tester");
      return;
    }
    new Probe<>(
            "Blame SCM (sources/scm)",
            "api/sources/scm",
            ScmResponse.class,
            ScmResponse::describe,
            params("key", fileKey, "from", "1", "to", "20"))
        .run(session);
  }
}
