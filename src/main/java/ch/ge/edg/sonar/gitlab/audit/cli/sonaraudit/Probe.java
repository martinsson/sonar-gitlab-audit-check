package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import ch.ge.edg.sonar.gitlab.audit.sonar.Sonar;
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
    Sonar.Response r = session.sq().get(path, params);
    CallVerdict v = CallVerdict.of(r);
    String detail;
    if (v == CallVerdict.OK) {
      T parsed = r.as(type);
      detail = (parsed == null) ? "réponse illisible" : Text.orEmpty(hint.apply(parsed));
    } else {
      detail = "HTTP %d %s".formatted(r.status(), r.errorMessage());
    }
    Presentation.line(session.out(), label, v, detail);
  }
}
