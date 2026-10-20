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
    Verdict v = Verdict.of(r);
    String detail;
    if (v == Verdict.OK) {
      T parsed = r.as(type);
      detail = (parsed == null) ? "réponse illisible" : Text.orEmpty(hint.apply(parsed));
    } else {
      detail = Text.format("HTTP %d %s", r.status(), r.errorMessage());
    }
    Presentation.line(session.out(), label, v, detail);
  }
}
