package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** dora-donnees.json written by an older version still reads, as it was read then. */
class SnapshotFileTest {

  @Test
  void aSnapshotWrittenBeforeTheRuleReadsAsMaterialsWithNothingOnMain(@TempDir Path dir)
      throws IOException {
    Path file = dir.resolve(SnapshotFile.NAME);
    Files.writeString(
        file,
        """
        {"project":{"gitlabId":"1","path":"g/p","webUrl":"w","defaultBranch":"main",
          "deploySource":"GOCD","prodPipelines":[],"jiraProjects":[],"incidentRule":""},
         "from":"2026-01-01T00:00:00Z","to":"2026-01-02T00:00:00Z",
         "mergeRequests":[],"deployments":[],"incidents":[]}
        """);
    Snapshot s = SnapshotFile.read(file);
    assertThat(s.mainCommits()).isEmpty();
    assertThat(s.pushes()).isEmpty();
    assertThat(s.project().reachable()).isFalse();
    assertThat(Metrics.of(s).links().features()).isEmpty();
  }

  @Test
  void aSnapshotWrittenWithTheMainBranchAtItsRootStillReadsIt(@TempDir Path dir)
      throws IOException {
    Path file = dir.resolve(SnapshotFile.NAME);
    Files.writeString(
        file,
        """
        {"project":{"gitlabId":"1","path":"g/p","webUrl":"w","defaultBranch":"main",
          "deploySource":"REACHABLE","prodPipelines":["prod"],"jiraProjects":[],"incidentRule":""},
         "from":"2026-01-01T00:00:00Z","to":"2026-01-02T00:00:00Z",
         "mergeRequests":[],"deployments":[],"incidents":[],
         "mainCommits":[{"sha":"c1","title":"fix: x","authoredAt":"2026-01-01T08:00:00Z",
           "committedAt":"2026-01-01T09:00:00Z"}],
         "pushes":[{"at":"2026-01-01T10:00:00Z","commitTo":"c1","commitCount":1}]}
        """);
    Snapshot s = SnapshotFile.read(file);
    assertThat(s.mainCommits()).extracting(Snapshot.MainCommit::sha).containsExactly("c1");
    assertThat(s.pushes()).extracting(Snapshot.Push::commitTo).containsExactly("c1");
    assertThat(s.releases()).isNull();
    assertThat(SnapshotFile.json(s)).contains("\"mainBranch\"").doesNotContain("\"mainCommits\"");
  }
}
