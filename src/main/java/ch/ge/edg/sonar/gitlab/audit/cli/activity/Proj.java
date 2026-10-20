package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.parse;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.text;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.iso;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.num;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.orEmpty;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/**
 * État par projet.
 *
 * <p>Tous les compteurs sont boxés : une mesure absente doit rester null et ne surtout pas se lire
 * comme un zéro. « Pas de commit compté » et « zéro commit » sont deux constats différents, et
 * c'est exactement l'erreur que ce dépôt a déjà payée une fois côté Sonar.
 */
final class Proj {
  final long id;
  final String path;
  final String name;
  final String namespace;
  final String defaultBranch;
  final String visibility;
  final @Nullable OffsetDateTime created;
  final @Nullable OffsetDateTime lastActivity;
  final boolean archived;
  final boolean emptyRepo;
  final boolean mirror;
  final @Nullable String forkedFrom;
  final @Nullable Long totalCommits;
  final @Nullable Long repoSize;

  /** La tête de la branche par défaut telle que GraphQL l'a datée. */
  CommitMeasure.Head head = CommitMeasure.Head.unknown();

  CommitMeasure commits = new CommitMeasure.NotCounted();
  Fate fate = new Fate.InPlay();

  /** Les pratiques, mesurées par la passe profonde sur les seuls sélectionnés. */
  final Prat prat = new Prat();

  private Proj(JsonNode n) {
    id = n.path("id").asLong();
    path = text(n, "path_with_namespace");
    name = text(n, "name");
    namespace = n.path("namespace").path("full_path").asText("");
    defaultBranch = text(n, "default_branch");
    visibility = text(n, "visibility");
    created = parse(text(n, "created_at"));
    lastActivity = parse(text(n, "last_activity_at"));
    archived = n.path("archived").asBoolean(false);
    emptyRepo = n.path("empty_repo").asBoolean(false);
    mirror = n.path("mirror").asBoolean(false);
    forkedFrom =
        n.hasNonNull("forked_from_project")
            ? n.path("forked_from_project").path("path_with_namespace").asText(null)
            : null;
    JsonNode st = n.path("statistics");
    totalCommits = st.hasNonNull("commit_count") ? st.get("commit_count").asLong() : null;
    repoSize = st.hasNonNull("repository_size") ? st.get("repository_size").asLong() : null;
  }

  static Proj of(JsonNode n) {
    return new Proj(n);
  }

  /** Le dernier commit : celui de la pagination quand elle a eu lieu, sinon celui de GraphQL. */
  @Nullable OffsetDateTime lastCommit() {
    return commits instanceof CommitMeasure.Counted c ? c.head().lastCommit() : head.lastCommit();
  }

  /** Le SHA de tête, qui indexe le cache des clés Sonar. */
  @Nullable String headSha() {
    return commits instanceof CommitMeasure.Counted c ? c.head().sha() : head.sha();
  }

  double perWeek(int windowDays) {
    Integer n = commits.human();
    if (n == null || windowDays <= 0) return 0;
    return n * 7.0 / windowDays;
  }

  /** Commits humains comptés, zéro quand ils ne l'ont pas été — pour trier, pas pour publier. */
  int commitsOrZero() {
    return commits.humanOrZero();
  }

  /** Une ligne d'inventaire.csv. */
  String[] row(int windowDays) {
    return new String[] {
      String.valueOf(id),
      orEmpty(path),
      orEmpty(name),
      orEmpty(namespace),
      orEmpty(defaultBranch),
      orEmpty(visibility),
      iso(created),
      iso(lastActivity),
      String.valueOf(archived),
      forkedFrom == null ? "false" : "true",
      String.valueOf(mirror),
      num(totalCommits),
      num(repoSize),
      fate.bucket(),
      iso(lastCommit()),
      num(commits.human()),
      num(commits.bots()),
      num(commits.authors()),
      num(commits.merges()),
      num(commits.reverts()),
      num(commits.activeDays()),
      commits.human() == null ? "" : "%.2f".formatted(perWeek(windowDays)),
      String.valueOf(commits.truncated()),
      fate.exclusion(),
      String.valueOf(commits.leaked()),
      String.valueOf(fate.isSelected()),
      fate.selectionReason()
    };
  }

  /** Une ligne de pratiques.csv. */
  String[] pratRow(int windowDays) {
    return prat.row(this, windowDays);
  }
}
