package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.GitlabAuditFake.daily;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.GitlabAuditFake.project;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.rest.Capture;
import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GitlabActivityAuditOutputTest extends GitlabActivityAuditTestBase {

  @BeforeEach
  void oneSelectedProject() {
    fake.enterprise(false)
        .group("team-a", project(1, "team-a/alpha"))
        .commits(1, daily("a", "alice", 6))
        .tree(1, "README.md");
  }

  private Cli.Result run(String... extra) {
    String[] base = {"--group", "team-a", "--graphql=false"};
    return fake.run(Stream.concat(Stream.of(base), Stream.of(extra)).toArray(String[]::new));
  }

  @Test
  void outDirWithDeepWritesBothFilesAndSaysHowToReadThem() throws Exception {
    Path dir = tmp.resolve("audit");
    Cli.Result r = run("--deep", "--out-dir", dir.toString());
    assertOk(r);
    expectSelfie(out(r)).toMatchDisk("stdout");
    expectSelfie(file(dir.resolve("pratiques.csv"))).toMatchDisk("pratiques");
    String raw = Files.readString(dir.resolve("inventaire.csv"), StandardCharsets.UTF_8);
    assertThat(raw).startsWith("﻿\"id\";\"path\";").contains("\r\n");
    assertThat(dir.resolve(".sonar-keys.json")).exists();
  }

  @Test
  void commaFilesHaveNoBomAndUseCommas() throws Exception {
    Path dir = tmp.resolve("audit");
    Cli.Result r = run("--deep", "--out-dir", dir.toString(), "--comma");
    assertOk(r);
    String inv = Files.readString(dir.resolve("inventaire.csv"), StandardCharsets.UTF_8);
    assertThat(inv).startsWith("\"id\",\"path\",\"name\",").doesNotContain("\r\n");
    assertThat(Files.readString(dir.resolve("pratiques.csv"), StandardCharsets.UTF_8))
        .startsWith("\"path\",\"motif_selection\",");
    assertThat(r.out()).contains("    Format machine (virgule, sans BOM).\n");
  }

  @Test
  void pratiquesAloneImpliesDeepAndPutsTheInventoryNextToIt() {
    Path prat = tmp.resolve("sub/prat.csv");
    Cli.Result r = run("--pratiques", prat.toString());
    assertOk(r);
    assertThat(prat).exists();
    assertThat(tmp.resolve("sub/inventaire.csv")).exists();
    assertThat(tmp.resolve("sub/.sonar-keys.json")).doesNotExist();
    assertThat(fake.normalize(r.out(), tmp))
        .contains("6. Signaux de pratique sur les 1 sélectionnés")
        .contains("    inventaire : <TMP>/sub/inventaire.csv\n")
        .contains("    pratiques  : <TMP>/sub/prat.csv\n");
  }

  @Test
  void explicitPathsWinOverTheOutDir() {
    Path dir = tmp.resolve("audit");
    Path inv = tmp.resolve("a/b/inv.csv");
    Path prat = tmp.resolve("c/prat.csv");
    Cli.Result r =
        run(
            "--deep",
            "--out-dir",
            dir.toString(),
            "--csv",
            inv.toString(),
            "--pratiques",
            prat.toString(),
            "--cache",
            tmp.resolve("d/keys.json").toString());
    assertOk(r);
    assertThat(inv).exists();
    assertThat(prat).exists();
    assertThat(tmp.resolve("d/keys.json")).exists();
    assertThat(dir).isEmptyDirectory();
  }

  @Test
  void csvAndPratiquesWithoutOutDir() {
    Path inv = tmp.resolve("inv.csv");
    Path prat = tmp.resolve("prat.csv");
    Cli.Result r = run("--csv", inv.toString(), "--pratiques", prat.toString());
    assertOk(r);
    assertThat(inv).exists();
    assertThat(prat).exists();
    assertThat(tmp.resolve(".sonar-keys.json")).doesNotExist();
  }

  @Test
  void deepWithoutAnyFileStillMeasures() {
    Cli.Result r = run("--deep");
    assertOk(r);
    assertThat(r.out())
        .contains("6. Signaux de pratique sur les 1 sélectionnés")
        .contains("  Aucun fichier écrit : ajoute --out-dir <rép> pour conserver l'inventaire\n");
  }

  @Test
  void rawResponsesCanBeDumped() throws Exception {
    Path dump = tmp.resolve("dump");
    Cli.Result r = run("--dump-dir", dump.toString(), "--concurrency", "1");
    assertOk(r);
    // Named after the request, its parameters sorted: the commits query (whose "since" moves
    // with the day) and the paged group listing are only checked by their readable prefix.
    String user = Capture.name("api/v4/user");
    try (Stream<Path> files = Files.list(dump)) {
      assertThat(files.map(p -> p.getFileName().toString()).sorted().toList())
          .hasSize(5)
          .contains("index.txt", user, Capture.name("api/v4/metadata"))
          .anyMatch(n -> n.startsWith("v4_groups_team_a_projects-"))
          .anyMatch(n -> n.startsWith("v4_projects_1_repository_commits-"));
    }
    assertThat(dump.resolve(user)).hasContent("{\"username\":\"alice\",\"name\":\"Alice A\"}");
  }

  @Test
  void timeoutAndInsecureAreAccepted() {
    String key = "jdk.internal.httpclient.disableHostnameVerification";
    String before = System.getProperty(key);
    try {
      Cli.Result r = run("--timeout", "3", "--insecure");
      assertOk(r);
    } finally {
      if (before == null) System.clearProperty(key);
      else System.setProperty(key, before);
    }
  }
}
