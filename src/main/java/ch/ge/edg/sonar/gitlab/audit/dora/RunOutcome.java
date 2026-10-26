package ch.ge.edg.sonar.gitlab.audit.dora;

import ch.ge.edg.sonar.gitlab.audit.gocd.Run;
import ch.ge.edg.sonar.gitlab.audit.gocd.Stage;
import java.util.HashSet;
import java.util.Set;

/**
 * L'issue d'un run GoCD, d'après ses stages lancés : un stage en échec l'emporte sur un stage
 * annulé, qui l'emporte sur un stage pas encore fini. Un stage jamais lancé (approbation manuelle
 * en attente) ne compte pas.
 */
public enum RunOutcome {
  SUCCESS("succès"),
  FAILURE("échec"),
  CANCELLED("annulé"),
  PENDING("en cours");

  private final String label;

  RunOutcome(String label) {
    this.label = label;
  }

  /** Le mot écrit dans les CSV et les pages, en français. */
  public String label() {
    return label;
  }

  public static RunOutcome of(Run run) {
    Set<String> results = new HashSet<>();
    for (Stage s : run.stages()) {
      if (s.launched()) results.add(s.result());
    }
    if (results.contains("Failed")) return FAILURE;
    if (results.contains("Cancelled")) return CANCELLED;
    results.remove("Passed");
    return results.isEmpty() ? SUCCESS : PENDING;
  }
}
