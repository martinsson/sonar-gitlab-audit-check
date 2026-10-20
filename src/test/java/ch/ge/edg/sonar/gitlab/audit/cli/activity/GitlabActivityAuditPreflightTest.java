package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;

class GitlabActivityAuditPreflightTest extends GitlabActivityAuditTestBase {

  @Test
  void helpListsEveryOption() {
    Cli.Result r = Cli.run(GitlabActivityAudit::new, "--help");
    // Not a golden master: usageHelpAutoWidth makes the layout depend on the terminal.
    assertThat(r.exitCode()).isZero();
    assertThat(r.out())
        .startsWith("Usage : GitlabActivityAudit [--group <chemin>] [--deep] [--out-dir <rép>]")
        .contains("GITLAB_URL et GITLAB_TOKEN peuvent remplacer --url et --token.")
        .contains("(défaut : 90)", "(défaut : 200)", "(défaut : 30)")
        .contains("(défaut : auto)", "(défaut : true)");
    for (String option :
        new String[] {
          "--url",
          "--token",
          "--group",
          "--include-subgroups",
          "--projects",
          "--exclusions",
          "--since",
          "--top",
          "--active-within",
          "--floor",
          "--deep",
          "--out-dir",
          "--include-archived",
          "--validation-sample",
          "--graphql",
          "--seed",
          "--csv",
          "--pratiques",
          "--bot-pattern",
          "--comma",
          "--max-commit-pages",
          "--concurrency",
          "--cache",
          "--no-cache",
          "--timeout",
          "--insecure",
          "--dump-dir",
          "--color",
          "--help",
          "--version"
        }) {
      assertThat(r.out()).contains(option);
    }
  }

  @Test
  void urlAndTokenAreRequired() {
    Cli.Result noUrl = Cli.run(GitlabActivityAudit::new, "--url", "", "--token", "t");
    Cli.Result noToken = Cli.run(GitlabActivityAudit::new, "--url", "http://x", "--token", " ");
    for (Cli.Result r : new Cli.Result[] {noUrl, noToken}) {
      assertThat(r.exitCode()).isEqualTo(2);
      assertThat(r.out()).isEmpty();
      assertThat(r.err())
          .isEqualTo(
              "GITLAB_URL et GITLAB_TOKEN sont requis (variables d'env ou --url/--token).\n");
    }
  }

  @Test
  void anUnreachableInstanceStopsWithExitOne() throws Exception {
    int port;
    try (ServerSocket s = new ServerSocket(0)) {
      port = s.getLocalPort();
    }
    Cli.Result r =
        Cli.run(
            GitlabActivityAudit::new,
            "--url",
            "http://127.0.0.1:" + port,
            "--token",
            "t",
            "--color",
            "never");
    assertThat(r.exitCode()).isEqualTo(1);
    expectSelfie(r.out().replace(String.valueOf(port), "<PORT>")).toMatchDisk();
  }

  @Test
  void aRefusedTokenStopsWithExitOne() {
    fake.user(req -> Response.status(401, "{\"message\":\"401 Unauthorized\"}"));
    Cli.Result r = fake.run();
    assertThat(r.exitCode()).isEqualTo(1);
    expectSelfie(out(r)).toMatchDisk();
    assertThat(fake.server.requests("/api/v4/user").getFirst().header("PRIVATE-TOKEN"))
        .isEqualTo("glpat-test");
    assertThat(fake.server.requests("/api/v4/metadata")).isEmpty();
  }

  @Test
  void anEmptyGroupStopsWithExitOneAfterSayingSo() {
    fake.enterprise(false).group("team-a");
    Cli.Result r = fake.run("--group", "team-a");
    assertThat(r.exitCode()).isEqualTo(1);
    expectSelfie(out(r)).toMatchDisk();
  }

  @Test
  void anEnterpriseInstanceIsAnnounced() {
    fake.enterprise(true).group("team-a");
    Cli.Result r = fake.run("--group", "team-a");
    assertThat(fake.normalize(r.out(), tmp))
        .contains("api/v4/metadata                  [OK]  GitLab 17.0.0 · Enterprise\n");
  }

  @Test
  void anUnknownPlanIsAssumedCommunity() {
    fake.group("team-a");
    Cli.Result r = fake.run("--group", "team-a");
    assertThat(fake.normalize(r.out(), tmp))
        .contains(
            "api/v4/metadata                  [ABSENT]  HTTP 404 — plan inconnu, on suppose"
                + " Community\n");
  }

  @Test
  void colorsCanBeForced() {
    fake.user(req -> Response.status(403, "{}"));
    Cli.Result r =
        Cli.run(
            GitlabActivityAudit::new,
            "--url",
            fake.server.url(),
            "--token",
            "t",
            "--color",
            "always");
    assertThat(r.out())
        .contains("\033[1m\nInstance : ")
        .contains("\033[31m[REFUSÉ]\033[0m  HTTP 403 — jeton refusé")
        .contains("\033[2m-------");
  }
}
