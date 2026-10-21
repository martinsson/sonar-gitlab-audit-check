package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.util.Map;
import java.util.function.Function;

/**
 * Un probe = un appel, un verdict OK/REFUSE/ABSENT, et un détail si ça a marché.
 *
 * @param hint ce qu'on dit d'une réponse lue ; doit supporter les champs absents
 */
record Probe<T>(
    String label,
    String path,
    Class<T> type,
    Function<T, String> hint,
    Map<String, String> params) {

  void run(Session session) {
    RestResponse r = session.sq().get(path, params);
    CallVerdict v = CallVerdict.of(r);
    String detail;
    if (v == CallVerdict.OK) {
      detail = r.as(type).map(t -> Text.orEmpty(hint.apply(t))).orElse("réponse illisible");
    } else {
      detail = r.statusLine() + " " + r.message();
    }
    Presentation.line(session.out(), label, v, detail);
  }
}
