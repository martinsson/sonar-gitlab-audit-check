package ch.ge.edg.sonar.gitlab.audit.gocd;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Un stage d'un run, tel que le run le décrit ou que {@code /go/api/stages/...} le détaille (ce
 * dernier seul donne les transitions des jobs).
 *
 * @param counter 1, ou plus pour une relance ; 1 si GoCD ne le dit pas
 * @param scheduled faux pour un stage qui attend son approbation ; absent, il est lancé
 * @param approvalType « success » (automatique) ou « manual »
 */
public record Stage(
    String name,
    int counter,
    @Nullable Boolean scheduled,
    String result,
    String approvalType,
    List<Job> jobs) {

  public Stage {
    counter = Math.max(counter, 1);
    jobs = List.copyOf(jobs);
  }

  public record Job(@Nullable Instant scheduledDate, List<Transition> jobStateTransitions) {

    public Job {
      jobStateTransitions = List.copyOf(jobStateTransitions);
    }
  }

  /**
   * Un changement d'état d'un job.
   *
   * @param state « Scheduled », « Building », « Completed »...
   */
  public record Transition(String state, @Nullable Instant stateChangeTime) {}

  public static Stage of(JsonNode json) {
    return GocdJson.read(json, Stage.class);
  }

  /** Lancé : faux seulement pour un stage qui attend son approbation. */
  public boolean launched() {
    return !Boolean.FALSE.equals(scheduled);
  }

  /** La planification du premier job. */
  public @Nullable Instant start() {
    Instant start = null;
    for (Job j : jobs) {
      Instant at = j.scheduledDate();
      if (at != null && (start == null || at.isBefore(start))) start = at;
    }
    return start;
  }

  /** La dernière fois qu'un job est passé à « Completed » ; null sans transitions. */
  public @Nullable Instant completed() {
    Instant end = null;
    for (Job j : jobs) {
      for (Transition t : j.jobStateTransitions()) {
        Instant at = t.stateChangeTime();
        if (at == null || !"Completed".equals(t.state())) continue;
        if (end == null || at.isAfter(end)) end = at;
      }
    }
    return end;
  }
}
