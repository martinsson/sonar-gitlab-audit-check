package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.GitlabAuditFake.daily;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.GitlabAuditFake.project;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import ch.ge.edg.sonar.gitlab.audit.testing.SuspectedBug;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** GitlabActivityAudit messages that put a plural after a count of one. */
class CosmeticSuspectedBugsTest extends GitlabActivityAuditTestBase {

  private Cli.Result oneVisibleProjectAndOneForbidden() {
    Path list = write("projects.txt", "team-a/alpha\nteam-a/secret\n");
    fake.enterprise(false)
        .byPath(project(1, "team-a/alpha"))
        .route("/api/v4/projects/team-a%2Fsecret", req -> Response.status(403, "{}"))
        .commits(1, daily("a", "alice", 7));
    Cli.Result r = fake.run("--projects", list.toString(), "--concurrency", "1");
    assertOk(r);
    return r;
  }

  @Test
  @SuspectedBug("a single 403 should read « 1 réponse 403 », in the singular")
  void oneForbiddenProjectIsReportedAsOneReponsesInThePlural() {
    assertThat(oneVisibleProjectAndOneForbidden().out())
        .contains("    1 réponses 403 : le jeton ne voit pas tout le parc.\n");
  }

  @Test
  @SuspectedBug("a single project counted by REST should read « 1 projet », in the singular")
  void oneProjectCountedByRestIsReportedAsOneProjetsInThePlural() {
    assertThat(oneVisibleProjectAndOneForbidden().out())
        .contains("  Comptés par pagination REST     : 1 projets\n");
  }
}
