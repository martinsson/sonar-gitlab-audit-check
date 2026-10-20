package ch.ge.edg.sonar.gitlab.audit.matching;

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
}
