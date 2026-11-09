package ch.ge.edg.sonar.gitlab.audit.matching;

import java.util.ArrayList;
import java.util.List;

/**
 * Ce que la reprise a fait. {@code stale} : paires dont la clé de droite n'existe plus — projet
 * Sonar renommé ou supprimé —, recalculées.
 */
public record Reuse(
    int kept,
    int manual,
    int rejected,
    int recomputed,
    int added,
    List<String> stale,
    List<String[]> orphans) {

  public static final Reuse NONE = new Reuse(0, 0, 0, 0, 0, List.of(), List.of());

  /** Le bilan compté dans les lignes rejouées, dans leur ordre. */
  static Reuse of(List<Replay.Replayed> rows, List<String[]> orphans) {
    List<String> stale = new ArrayList<>();
    for (Replay.Replayed r : rows) {
      if (r.stale() != null) stale.add(r.stale());
    }
    return new Reuse(
        count(rows, Replay.Replayed.Step.KEPT),
        count(rows, Replay.Replayed.Step.MANUAL),
        count(rows, Replay.Replayed.Step.REJECTED),
        count(rows, Replay.Replayed.Step.RECOMPUTED),
        count(rows, Replay.Replayed.Step.ADDED),
        stale,
        orphans);
  }

  private static int count(List<Replay.Replayed> rows, Replay.Replayed.Step step) {
    return (int) rows.stream().filter(r -> r.step() == step).count();
  }
}
