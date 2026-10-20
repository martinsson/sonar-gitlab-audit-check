package ch.ge.edg.sonar.gitlab.audit.cli.cross;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.matching.MappingFile;
import ch.ge.edg.sonar.gitlab.audit.matching.Spec;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The mapping file as the matcher writes it, then a hand-made rejection read back by the cross. */
class MappingContractTest {

  @Test
  void aPairRejectedByHandInTheWrittenFileIsReadBackAsRejected(@TempDir Path dir) throws Exception {
    Csv.Table gitlab = CrossAuditFixtures.table(dir, "pratiques.csv", "path,cle_sonar", "g/app,k1");
    Csv.Table sonar = SonarToGitlabFixtures.sonar(dir, "k1,App,,,2026-01-01,100");
    Path file = dir.resolve("appariement.csv");

    MappingFile.mapAndKeep(
        Spec.sonarGitlab(0.5, 0.1), gitlab, sonar, new MappingFile(file, true, false));
    assertThat(Files.readString(file)).contains("\"exact\"");
    Files.writeString(file, Files.readString(file).replace("\"exact\"", "\"rejete\""));

    SonarToGitlab.Rejections rejections = SonarToGitlab.Rejections.read(file);
    assertThat(rejections.pair("k1", "g/app")).isTrue();
  }
}
