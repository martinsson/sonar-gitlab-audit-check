package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/**
 * Un projet du périmètre et ce que l'entonnoir en a appris. Chaque étape en rend une copie, avec ce
 * qu'elle a établi.
 *
 * @param head la tête de la branche par défaut telle que GraphQL l'a datée
 * @param commits ce que l'on sait de ses commits sur la fenêtre
 * @param fate ce que l'entonnoir a décidé de lui
 */
record AuditedProject(
    GitlabProject project, CommitMeasure.Head head, CommitMeasure commits, Fate fate) {

  /** Entré dans l'entonnoir, rien encore de mesuré ni de décidé. */
  static AuditedProject entering(GitlabProject project) {
    return new AuditedProject(
        project, CommitMeasure.Head.unknown(), new CommitMeasure.NotCounted(), new Fate.InPlay());
  }

  AuditedProject with(CommitMeasure.Head dated) {
    return new AuditedProject(project, dated, commits, fate);
  }

  AuditedProject with(CommitMeasure measure) {
    return new AuditedProject(project, head, measure, fate);
  }

  AuditedProject with(Fate decided) {
    return new AuditedProject(project, head, commits, decided);
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
}
