package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.text;
import static ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab.parse;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/**
 * Un projet tel que l'inventaire GitLab le décrit, avant toute mesure. Rien ici ne change pendant
 * la course.
 */
record GitlabProject(
    long id,
    String path,
    String name,
    String namespace,
    String visibility,
    GitlabProject.Dates dates,
    GitlabProject.Repository repository) {

  /** Création, et dernier événement quel qu'il soit — ticket, wiki, CI aussi bien que commit. */
  record Dates(@Nullable OffsetDateTime created, @Nullable OffsetDateTime lastActivity) {}

  /**
   * Le dépôt : sa branche par défaut, ce qui l'exclut d'office, et sa taille historique.
   *
   * @param commitCount les commits de tout l'historique, quand GitLab les a comptés
   * @param size la taille du dépôt en octets, quand GitLab l'a mesurée
   */
  record Repository(
      String defaultBranch,
      boolean archived,
      boolean empty,
      boolean mirror,
      @Nullable String forkedFrom,
      @Nullable Long commitCount,
      @Nullable Long size) {}

  /**
   * Lu depuis un projet de l'API ; un champ absent reste vide, ou null quand vide aurait un sens.
   */
  static GitlabProject of(JsonNode n) {
    JsonNode st = n.path("statistics");
    return new GitlabProject(
        n.path("id").asLong(),
        text(n, "path_with_namespace"),
        text(n, "name"),
        n.path("namespace").path("full_path").asText(""),
        text(n, "visibility"),
        new Dates(parse(text(n, "created_at")), parse(text(n, "last_activity_at"))),
        new Repository(
            text(n, "default_branch"),
            n.path("archived").asBoolean(false),
            n.path("empty_repo").asBoolean(false),
            n.path("mirror").asBoolean(false),
            n.hasNonNull("forked_from_project")
                ? n.path("forked_from_project").path("path_with_namespace").asText(null)
                : null,
            st.hasNonNull("commit_count") ? st.get("commit_count").asLong() : null,
            st.hasNonNull("repository_size") ? st.get("repository_size").asLong() : null));
  }

  String defaultBranch() {
    return repository.defaultBranch();
  }

  @Nullable OffsetDateTime lastActivity() {
    return dates.lastActivity();
  }
}
