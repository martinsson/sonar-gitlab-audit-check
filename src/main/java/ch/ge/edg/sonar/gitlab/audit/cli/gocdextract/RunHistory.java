package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import ch.ge.edg.sonar.gitlab.audit.gocd.GocdApi;
import ch.ge.edg.sonar.gitlab.audit.gocd.Run;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import java.io.PrintStream;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Les runs d'un pipeline planifiés depuis une date, du plus récent au plus ancien comme GoCD les
 * rend. La lecture s'arrête au premier run plus ancien : l'historique est trié, rien après ne
 * compterait.
 */
final class RunHistory {

  private RunHistory() {}

  static List<Run> since(Gocd gocd, String pipeline, Instant since, PrintStream err) throws Stop {
    AtomicBoolean older = new AtomicBoolean();
    GocdApi.History h =
        gocd.api()
            .history(
                pipeline,
                run -> {
                  Instant at = run.scheduledDate();
                  older.set(at != null && at.isBefore(since));
                  return older.get();
                },
                Integer.MAX_VALUE);
    gocd.checked(h.first());
    if (!h.first().ok()) {
      err.println(pipeline + " : historique illisible (" + h.first().why() + ")");
    } else if (!h.complete() && !older.get()) {
      err.println(pipeline + " : historique interrompu après " + h.pages() + " page(s)");
    }
    return h.runs();
  }
}
