package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** --dump-dir, --replay-dir, and the argument checks that come before any call. */
class SonarAuditCheckReplayTest extends SonarAuditCheckTestBase {

  private Cli.Result replay(Path dir, Path out) {
    return Cli.run(
        SonarAuditCheck::new,
        "--url",
        "",
        "--token",
        "",
        "--replay-dir",
        dir.toString(),
        "--csv",
        out.toString(),
        "--color",
        "never",
        "--exclusions",
        SonarAuditCheckFake.emptyExclusions(tmp).toString());
  }

  @Test
  void dumpThenReplayGivesTheSameInventoryWithoutTheInstance() throws IOException {
    Path dump = tmp.resolve("captures");
    Cli.Result live = runCsv("--dump-dir", dump.toString(), "--concurrency", "1");
    List<String> index = Files.readAllLines(dump.resolve("index.txt"), StandardCharsets.UTF_8);
    assertThat(index).hasSize(49);
    assertThat(index.getFirst()).matches("system_status-[0-9a-f]{8}\\.json  api/system/status");
    fake.close();

    Path again = tmp.resolve("rejoue.csv");
    Cli.Result r = replay(dump, again);

    assertThat(r.exitCode()).isZero();
    // A capture keeps the status: delta-legacy's refused binding (403) and missing history (404)
    // replay as what they were, and the inventory comes out identical.
    assertThat(Files.readString(again)).isEqualTo(Files.readString(csv()));
    assertThat(Files.readString(tmp.resolve("rejoue-historique.csv")))
        .isEqualTo(Files.readString(historyCsv()));
    assertThat(stable(r.out()))
        .startsWith("\nInstance : rejeu de <TMP>/captures\n")
        .contains("  49 appels API effectués.\n")
        .doesNotContain("capture(s) manquante(s)");
    expectSelfie(stable(live.out())).toMatchDisk("live");
    expectSelfie(stable(r.out())).toMatchDisk("replay");
  }

  @Test
  void aMissingCaptureIsCountedInTheSummary() throws IOException {
    Path dump = tmp.resolve("captures");
    runCsv("--dump-dir", dump.toString(), "--concurrency", "1");
    String links =
        Files.readAllLines(dump.resolve("index.txt")).stream()
            .filter(l -> l.endsWith("api/project_links/search?projectKey=delta-legacy"))
            .findFirst()
            .orElseThrow();
    Files.delete(dump.resolve(links.substring(0, links.indexOf(' '))));

    Cli.Result r = replay(dump, tmp.resolve("r.csv"));

    assertThat(r.exitCode()).isZero();
    assertThat(r.out()).contains("  1 capture(s) manquante(s) : le rejeu est incomplet.\n");
  }

  @Test
  void replayDirMustBeADirectory() {
    Cli.Result r = replay(tmp.resolve("nowhere"), tmp.resolve("r.csv"));
    assertThat(r.exitCode()).isEqualTo(2);
    assertThat(r.out()).isEmpty();
    assertThat(r.err())
        .isEqualTo("--replay-dir : " + tmp.resolve("nowhere") + " n'est pas un répertoire.\n");
  }

  @Test
  void urlIsRequiredOutsideReplay() {
    Cli.Result r = Cli.run(SonarAuditCheck::new, "--url", "", "--token", "t", "--color", "never");
    assertThat(r.exitCode()).isEqualTo(2);
    assertThat(r.out()).isEmpty();
    assertThat(r.err())
        .isEqualTo("SONAR_URL et SONAR_TOKEN sont requis (variables d'env ou --url/--token).\n");
  }

  @Test
  void tokenIsRequiredOutsideReplay() {
    Cli.Result r =
        Cli.run(
            SonarAuditCheck::new,
            "--url",
            "http://127.0.0.1:1",
            "--token",
            " ",
            "--color",
            "never");
    assertThat(r.exitCode()).isEqualTo(2);
    assertThat(r.err()).startsWith("SONAR_URL et SONAR_TOKEN sont requis");
  }

  @Test
  void anEmptyReplayDirIsAnUnreachableInstance() throws IOException {
    Path empty = Files.createDirectories(tmp.resolve("empty"));
    Cli.Result r = replay(empty, tmp.resolve("r.csv"));
    assertThat(r.exitCode()).isEqualTo(1);
    assertThat(stable(r.out()))
        .startsWith("\nInstance : rejeu de <TMP>/empty\n")
        .contains(
            "  ERREUR   api/system/status                          aucune capture pour api/system/status")
        .endsWith("  Impossible de joindre l'instance. Vérifie l'URL, le proxy, le TLS.\n");
  }
}
