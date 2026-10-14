package ch.ge.edg.sonar.gitlab.audit.cli.mapper;

import org.jspecify.annotations.Nullable;

/** Un rapprochement de noms : le candidat, son score, le suivant, et le refus éventuel. */
public record NameGuess(
    String candidate,
    double score,
    String runnerUp,
    double runnerUpScore,
    @Nullable String rejection) {

  public boolean accepted() {
    return rejection == null && !candidate.isEmpty();
  }

  NameGuess rejected(String reason) {
    return new NameGuess(candidate, score, runnerUp, runnerUpScore, reason);
  }
}
