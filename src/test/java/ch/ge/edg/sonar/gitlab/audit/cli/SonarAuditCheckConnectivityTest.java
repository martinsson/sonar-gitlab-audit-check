package ch.ge.edg.sonar.gitlab.audit.cli;

import static ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheckFake.error;
import static ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheckFake.ok;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Section 1: what stops the run, what does not, and how the identity is shown. */
class SonarAuditCheckConnectivityTest extends SonarAuditCheckTestBase {

  @Test
  void unreachableInstanceStopsWithExitOne() throws Exception {
    int port;
    try (ServerSocket s = new ServerSocket(0)) {
      port = s.getLocalPort();
    }
    String url = "http://127.0.0.1:" + port;
    Cli.Result r =
        Cli.run(new SonarAuditCheck(), "--url", url + "/", "--token", "t", "--color", "never");

    assertThat(r.exitCode()).isEqualTo(1);
    assertThat(r.out().replace(url, "<URL>"))
        .isEqualTo(
            """

            Instance : <URL>

            === 1. Connectivité et identité =======================================
              ERREUR   api/system/status                          ConnectException: Connection refused

              Impossible de joindre l'instance. Vérifie l'URL, le proxy, le TLS.
            """);
  }

  @Test
  void invalidTokenStopsWithExitOne() {
    fake.override("/api/authentication/validate", q -> ok(Map.of("valid", false)));
    Cli.Result r = runCsv();

    assertThat(r.exitCode()).isEqualTo(1);
    assertThat(csv()).doesNotExist();
    expectSelfie(stable(r.out())).toMatchDisk();
  }

  @Test
  void refusedValidationIsAnInvalidToken() {
    fake.override("/api/authentication/validate", q -> error(401, "Unauthorized"));
    Cli.Result r = runCsv();
    assertThat(r.exitCode()).isEqualTo(1);
    assertThat(r.out())
        .contains(
            "  REFUSE   api/authentication/validate                token invalide ou expiré\n");
  }

  @Test
  void statusThatIsNotJsonIsShownByItsHttpCodeAndTheRunGoesOn() {
    fake.override("/api/system/status", q -> Response.status(500, "<html>oops</html>"));
    Cli.Result r = runCsv();
    assertThat(r.exitCode()).isZero();
    assertThat(r.out())
        .contains("  ERREUR   api/system/status                          HTTP 500\n");
  }

  @Test
  void statusWithoutIdShowsAnEmptyId() {
    fake.override("/api/system/status", q -> ok(Map.of("version", "9.9", "status", "UP")));
    Cli.Result r = runCsv();
    assertThat(r.out())
        .contains("  OK       api/system/status                          UP · v9.9 · \n");
  }

  @Test
  void unreadableCurrentUserIsReportedAndTheRunGoesOn() {
    fake.override("/api/users/current", q -> error(403, "Insufficient privileges"));
    Cli.Result r = runCsv();
    assertThat(r.exitCode()).isZero();
    assertThat(r.out())
        .contains("  REFUSE   api/users/current                          Insufficient privileges\n")
        .doesNotContain("Permissions globales");
  }

  @Test
  void adminTokenWithoutNameAndManyGroupsGetsTheAdminNote() {
    List<String> groups = List.of("g1", "g2", "g3", "g4", "g5", "g6", "g7", "g8", "g9");
    fake.override(
        "/api/users/current",
        q ->
            ok(
                Map.of(
                    "login",
                    "root",
                    "permissions",
                    Map.of("global", List.of("admin")),
                    "groups",
                    groups)));
    Cli.Result r = runCsv();
    assertThat(r.out())
        .contains("  OK       api/users/current                          root ()\n")
        .contains("  Permissions globales : admin\n")
        .contains("  Groupes              : g1, g2, g3, g4, g5, g6, g7, g8\n")
        .contains(
            """


              Note : ce token a 'Administer System'. Il verra tout, donc ce
              diagnostic ne reflétera pas le périmètre d'un compte d'audit
              restreint. Pour un audit récurrent, préfère un compte dédié.
            """);
  }

  @Test
  void userWithoutPermissionsOrGroupsSaysNone() {
    fake.override("/api/users/current", q -> ok(Map.of("login", "bob", "groups", List.of())));
    Cli.Result r = runCsv();
    assertThat(r.out())
        .contains("  Permissions globales : aucune\n")
        .doesNotContain("Groupes   ")
        .doesNotContain("Administer System");
  }

  @Test
  void throttledAndRetriedCallsAreReportedInTheSummary() {
    AtomicInteger calls = new AtomicInteger();
    fake.override(
        "/api/settings/values",
        q ->
            calls.getAndIncrement() == 0
                ? Response.status(429, "").withHeader("Retry-After", "0")
                : ok(Map.of("settings", List.of())));
    AtomicInteger gw = new AtomicInteger();
    fake.override(
        "/api/qualityprofiles/search",
        q ->
            gw.getAndIncrement() == 0
                ? Response.status(503, "")
                : ok(Map.of("profiles", List.of())));
    Cli.Result r = runCsv();
    assertThat(r.out())
        .contains(
            "  51 appels API effectués (8 en vol).\n  dont 1 ralenti(s) (429) et 1 repris (5xx).\n");
  }
}
