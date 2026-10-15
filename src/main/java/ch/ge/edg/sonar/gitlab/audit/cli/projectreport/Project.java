package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

// Records des réponses GitLab
//
// Tout champ numérique est boxé, pour la même raison que côté Sonar :
// une donnée absente doit rester null et ne pas se lire comme un zéro.
// Les noms JSON de GitLab sont en snake_case et déclarés explicitement —
// une stratégie de nommage globale se contenterait de rendre null les
// champs mal devinés, sans la moindre erreur. Seuls les champs que le
// rapport lit sont déclarés.

/** Le projet, tel que le décrit GET projects/:id?statistics=true. */
@JsonIgnoreProperties(ignoreUnknown = true)
record Project(
    @Nullable Long id,
    @JsonProperty("path_with_namespace") @Nullable String pathWithNamespace,
    @JsonProperty("default_branch") @Nullable String defaultBranch,
    @Nullable String visibility,
    @Nullable Boolean archived,
    @JsonProperty("created_at") @Nullable String createdAt,
    @JsonProperty("last_activity_at") @Nullable String lastActivityAt,
    @Nullable Statistics statistics) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Statistics(
      @JsonProperty("commit_count") @Nullable Long commitCount,
      @JsonProperty("repository_size") @Nullable Long repositorySize) {}
}
