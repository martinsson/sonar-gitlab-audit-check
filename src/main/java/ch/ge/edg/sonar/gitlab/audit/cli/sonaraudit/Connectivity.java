package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.RED;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.YELLOW;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.line;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.orEmpty;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.orEmptyList;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.params;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.CurrentUser;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.SystemStatus;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Validation;
import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.sonar.Sonar;
import java.util.List;

/** 1. L'API répond-elle, le jeton est-il valide, et qui est-il ? */
final class Connectivity {

  private final Session session;
  private final Console out;

  Connectivity(Session session) {
    this.session = session;
    this.out = session.out();
  }

  /** false quand l'audit ne peut pas continuer : instance injoignable ou jeton refusé. */
  boolean check() {
    Presentation.title(out, "1. Connectivité et identité");
    if (!reachable() || !tokenValid()) return false;
    identity();
    return true;
  }

  private boolean reachable() {
    Sonar.Response status = session.sq().get("api/system/status", params());
    if (status.unreachable()) {
      line(out, "api/system/status", Verdict.ERROR, status.errorMessage());
      out.println();
      out.println(
          out.color(
              "  Impossible de joindre l'instance. " + "Vérifie l'URL, le proxy, le TLS.", RED));
      return false;
    }
    SystemStatus st = status.as(SystemStatus.class);
    if (st != null) {
      line(
          out,
          "api/system/status",
          Verdict.OK,
          "%s · v%s · %s".formatted(st.status(), st.version(), orEmpty(st.id())));
    } else {
      line(out, "api/system/status", Verdict.of(status), "HTTP " + status.status());
    }
    return true;
  }

  private boolean tokenValid() {
    Validation v = session.read("api/authentication/validate", params(), Validation.class);
    boolean valid = v != null && Boolean.TRUE.equals(v.valid());
    line(
        out,
        "api/authentication/validate",
        valid ? Verdict.OK : Verdict.DENIED,
        valid ? "token valide" : "token invalide ou expiré");
    if (!valid) {
      out.println();
      out.println(out.color("  Le token n'est pas accepté. Vérifie qu'il s'agit bien d'un", RED));
      out.println(out.color("  token de type 'User' (squ_...) et non d'un token d'analyse.", RED));
    }
    return valid;
  }

  private void identity() {
    Sonar.Response r = session.sq().get("api/users/current", params());
    CurrentUser me = r.as(CurrentUser.class);
    if (me == null) {
      line(out, "api/users/current", Verdict.of(r), r.errorMessage());
      return;
    }
    List<String> perms = me.globalPermissions();
    line(out, "api/users/current", Verdict.OK, "%s (%s)".formatted(me.login(), orEmpty(me.name())));
    out.println();
    out.println(
        "  Permissions globales : "
            + out.color(perms.isEmpty() ? "aucune" : String.join(", ", perms), DIM));
    List<String> groups = orEmptyList(me.groups());
    if (!groups.isEmpty()) {
      out.println(
          "  Groupes              : "
              + out.color(String.join(", ", groups.subList(0, Math.min(8, groups.size()))), DIM));
    }
    if (perms.contains("admin")) adminNote();
  }

  private void adminNote() {
    out.println();
    out.println(
        out.color("  Note : ce token a 'Administer System'. Il verra tout, donc ce", YELLOW));
    out.println(
        out.color("  diagnostic ne reflétera pas le périmètre d'un compte d'audit", YELLOW));
    out.println(
        out.color("  restreint. Pour un audit récurrent, préfère un compte dédié.", YELLOW));
  }
}
