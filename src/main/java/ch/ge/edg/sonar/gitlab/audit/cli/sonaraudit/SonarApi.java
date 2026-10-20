package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.orEmptyList;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.size;
import static ch.ge.edg.sonar.gitlab.audit.text.Text.isBlank;
import static ch.ge.edg.sonar.gitlab.audit.text.Text.orEmpty;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Les réponses de l'API SonarQube que l'audit lit.
 *
 * <p>Tous les champs numériques sont boxés : une mesure absente doit rester null et ne surtout pas
 * se lire comme un zéro — « pas de couverture » et « 0 % de couverture » sont deux diagnostics
 * différents.
 */
final class SonarApi {

  private SonarApi() {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record SystemStatus(@Nullable String id, @Nullable String version, @Nullable String status) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Validation(@Nullable Boolean valid) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record CurrentUser(
      @Nullable String login,
      @Nullable String name,
      @Nullable Permissions permissions,
      @Nullable List<String> groups) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Permissions(@Nullable List<String> global) {}

    List<String> globalPermissions() {
      return (permissions == null) ? List.of() : orEmptyList(permissions.global());
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Paging(@Nullable Integer pageIndex, @Nullable Integer pageSize, @Nullable Integer total) {}

  /**
   * Les trois derniers champs ne viennent pas de l'API : resolveLatestBranch() les pose quand il
   * substitue la branche la plus récente à la principale. {@code analysisDate} porte alors la date
   * de cette branche, et {@code mainBranchAnalysisDate} ce que search_projects avait répondu.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Component(
      String key,
      @Nullable String name,
      @Nullable String qualifier,
      @Nullable String analysisDate,
      @Nullable String leakPeriodDate,
      @Nullable String branch,
      @Nullable Boolean isMain,
      @Nullable String mainBranchAnalysisDate) {

    Component onBranch(
        @Nullable String branch, boolean isMain, @Nullable String branchAnalysisDate) {
      return new Component(
          key, name, qualifier, branchAnalysisDate, leakPeriodDate, branch, isMain, analysisDate);
    }

    boolean onOtherBranch() {
      return branch != null && Boolean.FALSE.equals(isMain);
    }
  }

  /** Pour GitLab, {@code repository} est l'identifiant numérique du projet. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record AlmBinding(
      @Nullable String key,
      @Nullable String alm,
      @Nullable String repository,
      @Nullable String slug,
      @Nullable String url,
      @Nullable Boolean monorepo) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ProjectLinks(@Nullable List<ProjectLink> links) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ProjectLink(@Nullable String type, @Nullable String name, @Nullable String url) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Branch(
      @Nullable String name,
      @Nullable Boolean isMain,
      @Nullable String type,
      @Nullable String analysisDate) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record BranchList(@Nullable List<Branch> branches) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ProjectSearch(@Nullable Paging paging, @Nullable List<Component> components) {}

  /**
   * Attention : {@code value} est une chaîne, pas un nombre — l'API renvoie « 12.5 », pas 12.5. Et
   * les métriques new_* logent leur valeur sous {@code period} (ou {@code periods} sur les versions
   * plus anciennes).
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Measure(
      String component,
      String metric,
      @Nullable String value,
      @Nullable Period period,
      @Nullable List<Period> periods) {

    @Nullable String effectiveValue() {
      if (value != null) return value;
      if (period != null) return period.value();
      List<Period> all = orEmptyList(periods);
      return all.isEmpty() ? null : all.get(0).value();
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Period(@Nullable Integer index, @Nullable String value, @Nullable Boolean bestValue) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record MeasuresSearch(@Nullable List<Measure> measures) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record MetricsSearch(@Nullable Paging paging, @Nullable List<Metric> metrics) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Metric(String key, @Nullable String name, @Nullable String type) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ComponentMeasures(@Nullable ComponentWithMeasures component) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ComponentWithMeasures(@Nullable String key, @Nullable List<Measure> measures) {}

    /** Les mesures du composant ; vide quand la réponse n'en porte pas. */
    List<Measure> measures() {
      return component == null ? List.of() : orEmptyList(component.measures());
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record SearchHistory(@Nullable Paging paging, @Nullable List<HistoryMeasure> measures) {

    List<HistoryPoint> pointsFor(String metric) {
      return orEmptyList(measures).stream()
          .filter(m -> metric.equals(m.metric()))
          .findFirst()
          .map(m -> orEmptyList(m.history()).stream().filter(h -> !isBlank(h.value())).toList())
          .orElse(List.of());
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record HistoryMeasure(@Nullable String metric, @Nullable List<HistoryPoint> history) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record HistoryPoint(@Nullable String date, @Nullable String value) {}

  /** issues/search expose le total à la racine, pas dans paging comme les autres. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record IssuesSearch(
      @Nullable Integer total, @Nullable Paging paging, @Nullable List<Facet> facets) {

    String issueCount() {
      if (total != null) return String.valueOf(total);
      return Values.total(paging);
    }

    List<FacetValue> facetValues(String property) {
      return orEmptyList(facets).stream()
          .filter(f -> property.equals(f.property()))
          .findFirst()
          .map(f -> orEmptyList(f.values()))
          .orElse(List.of());
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Facet(@Nullable String property, @Nullable List<FacetValue> values) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record FacetValue(@Nullable String val, @Nullable Integer count) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record SettingsValues(@Nullable List<Setting> settings) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Setting(
      @Nullable String key,
      @Nullable String value,
      @Nullable List<String> values,
      @Nullable Boolean inherited) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record QualityGateResponse(@Nullable QualityGate qualityGate) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record QualityGate(@Nullable String id, @Nullable String name, @Nullable Boolean isDefault) {}

    String gateName() {
      return (qualityGate == null) ? "" : orEmpty(qualityGate.name());
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record QualityProfiles(@Nullable List<Profile> profiles) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Profile(
        @Nullable String key,
        @Nullable String name,
        @Nullable String language,
        @Nullable String languageName) {}
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Analyses(@Nullable Paging paging, @Nullable List<Analysis> analyses) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Analysis(
      @Nullable String key,
      @Nullable String date,
      @Nullable String projectVersion,
      @Nullable List<Event> events) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Event(@Nullable String key, @Nullable String category, @Nullable String name) {}

  /**
   * sources/scm renvoie des tableaux positionnels : [ligne, auteur, date, révision]. Une entrée par
   * changeset, pas par ligne : les lignes consécutives issues du même commit sont regroupées sous
   * la première.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record ScmResponse(@Nullable List<List<String>> scm) {

    /** Les changesets, vide quand la réponse n'en porte pas. */
    List<List<String>> rows() {
      return orEmptyList(scm);
    }

    /** Un changeset sans auteur = fichier indexé sans métadonnées SCM. */
    String describe() {
      int n = size(scm);
      if (n == 0) return "aucun changeset";
      for (List<String> row : rows()) {
        // Une entrée nulle (« [null] » dans le JSON) rend la réponse indescriptible.
        if (row == null) return "";
        if (hasAuthor(row)) return n + " changeset(s)";
      }
      return n + " changeset(s), aucun auteur (clone shallow ?)";
    }

    static boolean hasAuthor(List<String> row) {
      return row.size() > 1 && !isBlank(row.get(1));
    }
  }
}
