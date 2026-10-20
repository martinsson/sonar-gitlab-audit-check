package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.time.OffsetDateTime;
import java.util.IdentityHashMap;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Étape 2 : activité réelle, en commits. */
final class CommitActivity {

  private final Gitlab gl;
  private final Funnel funnel;

  CommitActivity(Gitlab gl, Funnel funnel) {
    this.gl = gl;
    this.funnel = funnel;
  }

  /** Les projets en lice, datés par GraphQL ; et la datation, pour le rapport. */
  record Dated(List<AuditedProject> all, GraphqlDates.Dating dating) {}

  /** La date du dernier commit des projets en lice, à cinquante projets la requête. */
  Dated date(List<AuditedProject> all) {
    List<AuditedProject> fresh = inPlay(all);
    GraphqlDates.Dating dating = new GraphqlDates(gl).date(fresh);
    IdentityHashMap<AuditedProject, CommitMeasure.Head> heads = new IdentityHashMap<>();
    for (int i = 0; i < fresh.size(); i++) heads.put(fresh.get(i), dating.heads().get(i));
    List<AuditedProject> dated =
        all.stream()
            .map(
                p -> {
                  CommitMeasure.Head h = heads.get(p);
                  return h == null ? p : p.with(h);
                })
            .toList();
    return new Dated(dated, dating);
  }

  /**
   * La date du dernier commit vient de coûter un appel pour cinquante projets. Elle décide donc
   * gratuitement qui mérite la pagination REST, qui est le seul poste de l'étape 2 à coûter un
   * appel par projet.
   */
  List<AuditedProject> cut(List<AuditedProject> all) {
    return all.stream().map(p -> p.fate().inPlay() ? cut(p) : p).toList();
  }

  private AuditedProject cut(AuditedProject p) {
    // Si GraphQL a daté le dernier commit hors fenêtre, le compte est zéro
    // par construction : inutile de le redemander en REST.
    if (lastCommitBeforeWindow(p)) return p.with(new CommitMeasure.NoneInWindow());
    return belowCut(p) ? p.with(cutReason(p)) : p;
  }

  /**
   * Un appel par projet, sans lien entre eux : c'est le poste le plus lourd de l'outil sur un gros
   * parc, et il n'avait aucune raison d'être séquentiel.
   */
  List<AuditedProject> count(List<AuditedProject> all) {
    List<AuditedProject> toCount = toCount(all);
    CommitPager pager = new CommitPager(gl, funnel);
    List<CommitMeasure> measures = gl.map(toCount, funnel.concurrency(), pager::measure);
    IdentityHashMap<AuditedProject, CommitMeasure> measured = new IdentityHashMap<>();
    for (int i = 0; i < toCount.size(); i++) measured.put(toCount.get(i), measures.get(i));
    return all.stream()
        .map(
            p -> {
              CommitMeasure m = measured.get(p);
              return m == null ? p : p.with(m);
            })
        .toList();
  }

  /** En lice, et pas encore compté : ce que la pagination REST doit lire. */
  static List<AuditedProject> toCount(List<AuditedProject> all) {
    return all.stream()
        .filter(p -> p.fate().inPlay() && p.commits() instanceof CommitMeasure.NotCounted)
        .toList();
  }

  static List<AuditedProject> inPlay(List<AuditedProject> all) {
    return all.stream().filter(p -> p.fate().inPlay()).toList();
  }

  boolean lastCommitBeforeWindow(AuditedProject p) {
    OffsetDateTime last = p.head().lastCommit();
    return last != null && last.isBefore(funnel.windowStart());
  }

  /**
   * Et s'il est daté avant la coupe demandée, le projet n'est pas de ceux qu'on veut regarder : on
   * ne le compte pas, et surtout on ne le note pas zéro — il a des commits, on a choisi de ne pas
   * les compter. La nuance tient tout le reste du rapport.
   *
   * <p>À défaut de date de commit — GraphQL indisponible — on retombe sur last_activity_at, qui se
   * trompe dans le sens sûr : il est gonflé par les événements non-commit, donc un projet sous la
   * coupe selon lui l'est forcément selon ses commits. La coupe reste gratuite.
   */
  private boolean belowCut(AuditedProject p) {
    int activeWithin = funnel.options().activeWithinDays;
    OffsetDateTime seen = seen(p);
    return activeWithin > 0 && seen != null && seen.isBefore(funnel.now().minusDays(activeWithin));
  }

  private static @Nullable OffsetDateTime seen(AuditedProject p) {
    OffsetDateTime dated = p.head().lastCommit();
    return dated != null ? dated : p.project().lastActivity();
  }

  private Fate cutReason(AuditedProject p) {
    return new Fate.Excluded(
        Fate.Stage.ACTIVITY_CUT,
        "hors coupe d'activité (%s > %dj)"
            .formatted(
                p.head().lastCommit() != null ? "dernier commit" : "dernière activité",
                funnel.options().activeWithinDays));
  }
}
