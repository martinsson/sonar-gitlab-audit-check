package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import ch.ge.edg.sonar.gitlab.audit.gocd.Run;
import ch.ge.edg.sonar.gitlab.audit.gocd.Stage;
import java.util.HashSet;
import java.util.Set;

/**
 * L'issue d'un run, d'après ses stages lancés : un stage en échec l'emporte sur un stage annulé,
 * qui l'emporte sur un stage pas encore fini. Un stage jamais lancé (approbation manuelle en
 * attente) ne compte pas.
 */
final class Outcome {

  private Outcome() {}

  static String of(Run run) {
    Set<String> results = new HashSet<>();
    for (Stage s : run.stages()) {
      if (s.launched()) results.add(s.result());
    }
    if (results.contains("Failed")) return "échec";
    if (results.contains("Cancelled")) return "annulé";
    results.remove("Passed");
    return results.isEmpty() ? "succès" : "en cours";
  }
}
