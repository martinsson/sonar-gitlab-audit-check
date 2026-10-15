package ch.ge.edg.sonar.gitlab.audit.cli;

import static ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheckFake.error;
import static ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheckFake.ok;
import static ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheckFake.points;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Sections 2 and 4 on the sample project: probes that fail, and thin activity signals. */
class SonarAuditCheckActivityTest extends SonarAuditCheckTestBase {

  private static final String[] LEAN = {"--main-branch-only", "--no-bindings", "--no-trend"};

  private String out() {
    return stable(runCsv(LEAN).out());
  }

  @Test
  void failingProbesShowTheirVerdictAndHttpDetail() {
    fake.override("/api/settings/values", q -> error(403, "Insufficient privileges"));
    fake.override("/api/qualitygates/get_by_project", q -> error(404, "No gate"));
    fake.override("/api/qualityprofiles/search", q -> Response.status(400, "bad"));
    fake.override("/api/project_links/search", q -> Response.json("garbage"));
    assertThat(out())
        .contains(
            "  REFUSE   Exclusions (settings/values)               HTTP 403 Insufficient privileges\n")
        .contains("  ABSENT   Quality gate du projet                     HTTP 404 No gate\n")
        .contains("  ERREUR   Quality profiles du projet                 HTTP 400 bad\n")
        .contains("  OK       Liens du projet (project_links/search)     réponse illisible\n");
  }

  @Test
  void probesWithThinAnswersFallBackToPlaceholders() {
    fake.override("/api/issues/search", q -> ok(Map.of()));
    fake.override("/api/qualitygates/get_by_project", q -> ok(Map.of()));
    fake.override("/api/alm_settings/get_binding", q -> ok(Map.of("alm", "gitlab")));
    fake.override("/api/project_analyses/search", q -> ok(Map.of()));
    assertThat(out())
        .contains("  OK       Issues + facettes (issues/search)          ? issues\n")
        .contains("  OK       Quality gate du projet                     \n")
        .contains("  OK       Liaison DevOps (alm_settings/get_binding)  gitlab, dépôt \n")
        .contains("  OK       Historique d'analyses (project_analyses)   ? analyses\n")
        .contains("  Analyses sur 90 j            : 0\n  Versions livrées                : 0\n\n")
        .contains("  Auteurs distincts (90 j)      : 0\n\n  Lignes neuves");
  }

  @Test
  void bindingProbeWithoutAlmSaysNoBinding() {
    fake.override("/api/alm_settings/get_binding", q -> ok(Map.of("key", "k")));
    assertThat(out())
        .contains("  OK       Liaison DevOps (alm_settings/get_binding)  aucune liaison\n");
  }

  @Test
  void noFileInTheTreeMeansNoBlame() {
    fake.override("/api/components/tree", q -> ok(Map.of("components", List.of())));
    assertThat(out())
        .contains("  OK       Arbre de composants (components/tree)      ? fichiers\n")
        .contains(
            "  VIDE     Blame SCM (sources/scm)                    aucun fichier accessible pour tester\n")
        .contains(
            """


              Blame SCM inaccessible : il manque 'See Source Code' au token.
              Sans lui, ni le churn ni l'âge du code ne sont calculables ici.
            """);
  }

  @Test
  void refusedBlameIsInaccessible() {
    fake.override("/api/sources/scm", q -> error(403, "no"));
    assertThat(out())
        .contains("  REFUSE   Blame SCM (sources/scm)                    HTTP 403 no\n")
        .contains("\n\n  Blame SCM inaccessible : il manque 'See Source Code' au token.\n");
  }

  @Test
  void emptyBlameIsInaccessibleAndDescribedAsNoChangeset() {
    fake.override("/api/sources/scm", q -> ok(Map.of("scm", List.of())));
    assertThat(out())
        .contains("  OK       Blame SCM (sources/scm)                    aucun changeset\n")
        .contains(" Blame SCM inaccessible ");
  }

  @Test
  void blameWithoutAuthorsIsAShallowCloneFinding() {
    fake.override(
        "/api/sources/scm", q -> ok(Map.of("scm", List.of(List.of("1", " "), List.of("2")))));
    assertThat(out())
        .contains(
            "  OK       Blame SCM (sources/scm)                    2 changeset(s), aucun auteur (clone shallow ?)\n")
        .contains(
            """


              Blame SCM vide : le scanner tourne sans métadonnées SCM
              (clone shallow en CI, type 'fetch-depth: 1'). Sonar ne peut donc
              ni attribuer les issues à un auteur, ni calculer correctement le
              new code. C'est un constat d'audit à part entière.
            """);
  }

  @Test
  void blameWithAuthorsButNoDatesSkipsTheDates() {
    fake.override(
        "/api/sources/scm", q -> ok(Map.of("scm", List.of(List.of("1", "alice", "bad-date")))));
    assertThat(out())
        .contains("    Auteurs distincts (1 changesets): 1\n    → churn et âge du code")
        .doesNotContain("Dernière modification");
  }

  @Test
  void blameRowThatBreaksTheDescriptionLeavesItEmpty() {
    fake.override("/api/sources/scm", q -> Response.json("{\"scm\":[null]}"));
    String out = out();
    assertThat(out).contains("  OK       Blame SCM (sources/scm)                    \n");
  }

  @Test
  void refusedAnalysesAndIssuesAreShownAsVerdicts() {
    fake.override("/api/project_analyses/search", q -> error(403, "no"));
    fake.override("/api/issues/search", q -> error(404, "no"));
    assertThat(out())
        .contains("  REFUSE   api/project_analyses/search                HTTP 403\n")
        .contains("  ABSENT   Facette 'author' sur issues                HTTP 404\n");
  }

  @Test
  void oneDatedAnalysisHasNoCadence() {
    String d = fake.ago(4);
    fake.override(
        "/api/project_analyses/search",
        q -> ok(Map.of("analyses", List.of(Map.of("date", d), Map.of("date", d)))));
    assertThat(out())
        .contains(
            "  Analyses sur 90 j            : 2\n  Versions livrées                : 0\n  Cadence moyenne                 : 1 analyse / 0.5 j\n");
  }

  @Test
  void singleDatedAnalysisPrintsNoCadence() {
    fake.override(
        "/api/project_analyses/search",
        q -> ok(Map.of("analyses", List.of(Map.of("date", fake.ago(4))))));
    assertThat(out()).doesNotContain("Cadence moyenne");
  }

  @Test
  void aSingleAuthorIsNotAConcentrationAlert() {
    fake.override("/api/issues/search", q -> issuesWithAuthors(5));
    assertThat(out())
        .contains("  Auteur principal                : a0 (100% des issues récentes)\n")
        .doesNotContain("forte concentration");
  }

  @Test
  void balancedAuthorsAreNotAConcentrationAlert() {
    fake.override("/api/issues/search", q -> issuesWithAuthors(7, 3));
    assertThat(out())
        .contains("  Auteur principal                : a0 (70% des issues récentes)\n")
        .doesNotContain("forte concentration");
  }

  @Test
  void authorsWithoutCountsDoNotDivideByZero() {
    fake.override(
        "/api/issues/search",
        q ->
            ok(
                Map.of(
                    "facets",
                    List.of(Map.of("property", "author", "values", List.of(Map.of("val", "x")))))));
    assertThat(out()).contains("  Auteur principal                : x (0% des issues récentes)\n");
  }

  @Test
  void newCodeMeasuresThatAreMissingAreNotAvailable() {
    fake.measures.put("alpha-service@new-code", Map.of("new_coverage", "period:12.5"));
    assertThat(out())
        .contains(
            "  Lignes neuves (new code)        : n/a\n  Violations neuves               : n/a\n  Couverture du code neuf         : 12.5\n");
  }

  @Test
  void refusedNewCodeMeasuresPrintNothing() {
    fake.measures.remove("alpha-service@new-code");
    assertThat(out()).doesNotContain("Lignes neuves");
  }

  @Test
  void historyTooThinForAVelocitySaysSo() {
    fake.historyOf("alpha-service").put("sqale_index", points(10, "650"));
    assertThat(out())
        .contains(
            "\n  Vélocité de dette indisponible : moins de deux analyses chiffrées sur la fenêtre.\n");
  }

  @Test
  void shrinkingCodeShowsDeltasButNoDebtPerLine() {
    fake.historyOf("alpha-service").put("ncloc", points(30, "1000", 10, "900"));
    fake.historyOf("alpha-service").put("sqale_index", points(30, "500", 10, "400"));
    assertThat(out())
        .contains(
            "  Δ lignes sur 90 j (20 j mesurés) : -100\n  Δ dette sur 90 j (min)       : -100\n\n")
        .doesNotContain("Dette ajoutée");
  }

  @Test
  void refusedSampleHistoryPrintsNoVelocity() {
    fake.history.remove("alpha-service");
    String out = out();
    assertThat(out).doesNotContain("Δ lignes").doesNotContain("Vélocité");
    assertThat(out)
        .contains(
            "  ABSENT   Historique de mesures (search_history)     HTTP 404 Component not found\n");
  }

  private static Response issuesWithAuthors(int... counts) {
    List<Map<String, Object>> values =
        IntStream.range(0, counts.length)
            .mapToObj(i -> Map.<String, Object>of("val", "a" + i, "count", counts[i]))
            .toList();
    return ok(Map.of("facets", List.of(Map.of("property", "author", "values", values))));
  }
}
