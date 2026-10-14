package ch.ge.edg.sonar.gitlab.audit.cli;

import static ch.ge.edg.sonar.gitlab.audit.cli.GitlabAuditFake.FUTURE;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitlabAuditFake.PAST;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitlabAuditFake.daily;
import static ch.ge.edg.sonar.gitlab.audit.cli.GitlabAuditFake.project;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Request;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GitlabActivityAuditInventoryTest extends GitlabActivityAuditTestBase {

  private static ObjectNode fork(long id, String path, String lastActivity) {
    ObjectNode p = project(id, path);
    p.putObject("forked_from_project").put("path_with_namespace", "upstream/" + id);
    p.put("created_at", "2020-01-01T00:00:00Z");
    p.put("last_activity_at", lastActivity);
    return p;
  }

  private ObjectNode[] everyKindOfProject() {
    ObjectNode noBranch = project(7, "team-a/no-branch");
    noBranch.remove("default_branch");
    ObjectNode listedAndArchived = project(10, "team-a/excluded-old").put("archived", true);
    return new ObjectNode[] {
      project(1, "team-a/alpha"),
      project(2, "team-a/legacy/archived-one").put("archived", true),
      project(3, "team-a/empty-one").put("empty_repo", true),
      project(4, "team-a/mirror-one").put("mirror", true),
      fork(5, "team-a/fork-same", "2020-01-01T12:00:00Z"),
      fork(6, "team-a/fork-diverged", FUTURE),
      noBranch,
      project(8, "team-a/excluded-new"),
      project(9, "team-a/sleepy").put("last_activity_at", PAST),
      listedAndArchived,
      fork(11, "team-a/fork-day-later", "2020-01-02T00:00:00Z"),
    };
  }

  @Test
  void everyExclusionReasonIsCountedAndWritten() {
    Path exclusions = write("exclusions.txt", "# synthetic\nteam-a/excluded-*  # list\n\n");
    fake.enterprise(false)
        .group("team-a", everyKindOfProject())
        .commits(1, daily("a", "alice", 6))
        .commits(6, daily("f", "bob", 3))
        .commits(11, daily("g", "carol", 1));
    Path outDir = tmp.resolve("audit");

    Cli.Result r =
        fake.run(
            "--group",
            "team-a",
            "--exclusions",
            exclusions.toString(),
            "--csv",
            tmp.resolve("not/yet/inv.csv").toString());

    assertOk(r);
    expectSelfie(out(r)).toMatchDisk("stdout");
    expectSelfie(file(tmp.resolve("not/yet/inv.csv"))).toMatchDisk("csv");
    assertThat(outDir).doesNotExist();
  }

  @Test
  void outDirGetsTheInventory() {
    Path exclusions = write("exclusions.txt", "team-a/excluded-*\n");
    fake.enterprise(false)
        .group("team-a", everyKindOfProject())
        .commits(1, daily("a", "alice", 6))
        .commits(6, daily("f", "bob", 3));
    Path outDir = tmp.resolve("audit");

    Cli.Result r =
        fake.run(
            "--group",
            "team-a",
            "--exclusions",
            exclusions.toString(),
            "--out-dir",
            outDir.toString());

    assertOk(r);
    expectSelfie(file(outDir.resolve("inventaire.csv"))).toMatchDisk("inventaire");
    assertThat(outDir.resolve("pratiques.csv")).doesNotExist();
    assertThat(outDir.resolve(".sonar-keys.json")).doesNotExist();
  }

  @Test
  void groupRequestCarriesTheScopeParameters() {
    fake.enterprise(false).group("team-a/sub", project(1, "team-a/sub/alpha"));
    fake.run("--group", "team-a/sub");
    Request req = fake.server.requests("/api/v4/groups/team-a%2Fsub/projects").getFirst();
    assertThat(req.target())
        .isEqualTo(
            "/api/v4/groups/team-a%2Fsub/projects?include_subgroups=true&with_shared=false"
                + "&statistics=true&archived=false&per_page=100&page=1");
  }

  @Test
  void subgroupsAndArchivedCanBeIncluded() {
    fake.enterprise(false)
        .group(
            "team-a", project(1, "team-a/alpha"), project(2, "team-a/old").put("archived", true));
    Cli.Result r = fake.run("--group", "team-a", "--include-subgroups=false", "--include-archived");
    Request req = fake.server.requests("/api/v4/groups/team-a/projects").getFirst();
    assertThat(req.target())
        .isEqualTo(
            "/api/v4/groups/team-a/projects?include_subgroups=false&with_shared=false"
                + "&statistics=true&per_page=100&page=1");
    assertThat(r.out()).contains("Retenus pour analyse            : 2 (100 % du parc)");
  }

  @Test
  void withoutGroupTheWholeInstanceIsListed() {
    fake.enterprise(false).instance(project(1, "team-a/alpha"), project(2, "team-b/beta"));
    Cli.Result r = fake.run();
    assertOk(r);
    assertThat(fake.server.requests("/api/v4/projects").getFirst().target())
        .isEqualTo(
            "/api/v4/projects?statistics=true&archived=false&order_by=id&sort=asc"
                + "&per_page=100&page=1");
    assertThat(r.out()).contains("Projets retournés               : 2\n");
    fake.run("--include-archived");
    assertThat(fake.server.requests("/api/v4/projects").getLast().target())
        .isEqualTo("/api/v4/projects?statistics=true&order_by=id&sort=asc&per_page=100&page=1");
  }

  @Test
  void aBlankGroupMeansTheWholeInstance() {
    fake.enterprise(false).instance(project(1, "team-a/alpha"));
    fake.run("--group", " ");
    assertThat(fake.server.requests("/api/v4/projects")).hasSize(1);
  }

  @Test
  void theInventoryFollowsPagination() {
    fake.enterprise(false)
        .route(
            "/api/v4/groups/team-a/projects",
            req ->
                "1".equals(req.param("page"))
                    ? Response.json(GitlabAuditFake.array(project(1, "team-a/alpha")))
                        .withHeader("X-Next-Page", "2")
                    : Response.json(GitlabAuditFake.array(project(2, "team-a/beta"))));
    Cli.Result r = fake.run("--group", "team-a");
    assertThat(r.out()).contains("Projets retournés               : 2\n");
  }

  @Test
  void aProjectsFileIsReadOneProjectAtATime() {
    Path list =
        write(
            "projects.txt",
            "# synthetic list\n  team-a/alpha  \n\nteam-a/missing\n#team-a/beta\nteam-a/secret\n");
    fake.enterprise(false)
        .byPath(project(1, "team-a/alpha"))
        .route("/api/v4/projects/team-a%2Fsecret", req -> Response.status(403, "{}"))
        .commits(1, daily("a", "alice", 7));
    Cli.Result r = fake.run("--projects", list.toString(), "--concurrency", "1");
    assertOk(r);
    expectSelfie(out(r)).toMatchDisk();
    assertThat(fake.server.requests("/api/v4/projects/team-a%2Falpha").getFirst().target())
        .isEqualTo("/api/v4/projects/team-a%2Falpha?statistics=true");
  }

  @Test
  void aProjectsFileWinsOverAGroup() {
    Path list = write("projects.txt", "team-a/alpha\n");
    fake.enterprise(false).byPath(project(1, "team-a/alpha"));
    fake.run("--projects", list.toString(), "--group", "team-b", "--concurrency", "4");
    assertThat(fake.server.requests("/api/v4/groups/team-b/projects")).isEmpty();
  }
}
