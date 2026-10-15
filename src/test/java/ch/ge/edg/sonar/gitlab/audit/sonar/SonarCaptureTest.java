package ch.ge.edg.sonar.gitlab.audit.sonar;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import ch.ge.edg.sonar.gitlab.audit.testing.SuspectedBug;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** --dump-dir writes one file per request plus index.txt; --replay-dir reads them back. */
class SonarCaptureTest {

  private final FakeHttpServer server = new FakeHttpServer();

  @TempDir Path tmp;

  @AfterEach
  void stop() {
    server.close();
  }

  @Test
  void captureNameIsSlugPlusHashOfSortedParams() {
    Map<String, String> ab = new LinkedHashMap<>();
    ab.put("b", "2");
    ab.put("a", "1");
    String name = Captures.name("api/measures/search", ab);
    assertThat(name).matches("measures_search-[0-9a-f]{8}\\.json");
    assertThat(Captures.name("api/measures/search", Map.of("a", "1", "b", "2"))).isEqualTo(name);
    assertThat(Captures.name("api/measures/search", Map.of("a", "1"))).isNotEqualTo(name);
  }

  @Test
  void captureNameIsAKnownSha1Prefix() {
    // SHA-1("api/system/status") starts with these four bytes.
    assertThat(Captures.name("api/system/status", Map.of()))
        .isEqualTo("system_status-" + sha1Prefix("api/system/status") + ".json");
  }

  @Test
  void captureNameKeepsNonApiPrefixAndTruncatesTheSlugTo60() {
    String longPath = "x/" + "a".repeat(80);
    String name = Captures.name(longPath, Map.of());
    assertThat(name).startsWith("x_" + "a".repeat(58) + "-").hasSize(60 + 1 + 8 + 5);
  }

  @Test
  void dumpWritesTheBodyAndAppendsTheIndex() throws Exception {
    server.json("/api/system/status", "{\"status\":\"UP\"}");
    server.on("/api/err", r -> Response.status(404, "{\"errors\":[]}"));
    Path dump = tmp.resolve("new/dir");
    Sonar sq =
        new Sonar(
            new SonarConnection(server.url(), "t", null, 5, false),
            new Captures(dump, null, System.err));
    assertThat(dump).isDirectory();

    sq.get("api/system/status", Map.of());
    sq.get("api/err", Map.of("k", "v w"));

    String status = Captures.name("api/system/status", Map.of());
    String err = Captures.name("api/err", Map.of("k", "v w"));
    assertThat(dump.resolve(status)).hasContent("{\"status\":\"UP\"}");
    assertThat(dump.resolve(err)).hasContent("{\"errors\":[]}");
    String nl = System.lineSeparator();
    assertThat(Files.readString(dump.resolve("index.txt")))
        .isEqualTo(status + "  api/system/status" + nl + err + "  api/err?k=v+w" + nl);
  }

  @Test
  void dumpOfAnUnreachableCallStoresItsErrorText() throws Exception {
    Path dump = tmp.resolve("d");
    Sonar sq =
        new Sonar(
            new SonarConnection("not a url", "t", null, 5, false),
            new Captures(dump, null, System.err));
    sq.get("api/x", Map.of());
    assertThat(Files.readString(dump.resolve(Captures.name("api/x", Map.of()))))
        .startsWith("IllegalArgumentException");
  }

  @Test
  void dumpFailureIsReportedOnStderrAndTheResponseStillReturned() throws Exception {
    server.json("/api/x", "{}");
    Path dump = tmp.resolve("d");
    // The warnings stream is the one the caller hands over: the CLI passes System.err.
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    PrintStream warnings = new PrintStream(err, true, StandardCharsets.UTF_8);
    Sonar sq =
        new Sonar(
            new SonarConnection(server.url(), "t", null, 5, false),
            new Captures(dump, null, warnings));
    Files.createDirectories(dump.resolve(Captures.name("api/x", Map.of())));

    Sonar.Response r = sq.get("api/x", Map.of());
    assertThat(r.body()).isEqualTo("{}");
    assertThat(err.toString(StandardCharsets.UTF_8)).startsWith("  (capture non écrite : ");
    assertThat(dump.resolve("index.txt")).doesNotExist();
  }

  @Test
  @SuspectedBug(
      "a captured 403 or 404 should replay with its status; today the body comes back as a 200")
  void aCaptured403ReplaysAsA200WithTheErrorBody() throws Exception {
    server.on("/api/denied", r -> Response.status(403, "{\"errors\":[{\"msg\":\"no\"}]}"));
    Path dir = tmp.resolve("d");
    Sonar live =
        new Sonar(
            new SonarConnection(server.url(), "t", null, 5, false),
            new Captures(dir, null, System.err));
    assertThat(live.get("api/denied", Map.of()).status()).isEqualTo(403);

    Sonar replay =
        new Sonar(
            new SonarConnection(server.url(), "t", null, 5, false),
            new Captures(null, dir, System.err));
    assertThat(replay.get("api/denied", Map.of()))
        .isEqualTo(new Sonar.Response(200, "{\"errors\":[{\"msg\":\"no\"}]}"));
  }

  @Test
  void replayReadsCapturesWithoutCallingTheInstance() throws Exception {
    Path replay = Files.createDirectories(tmp.resolve("r"));
    Files.writeString(replay.resolve(Captures.name("api/x", Map.of("p", "1"))), "{\"r\":1}");
    Sonar sq =
        new Sonar(
            new SonarConnection(server.url(), "t", null, 5, false),
            new Captures(null, replay, System.err));

    assertThat(sq.replaying()).isTrue();
    assertThat(sq.get("/api/x", Map.of("p", "1"))).isEqualTo(new Sonar.Response(200, "{\"r\":1}"));
    assertThat(sq.calls()).isEqualTo(1);
    assertThat(sq.missingCaptures()).isZero();
    assertThat(server.requests()).isEmpty();
  }

  @Test
  void replayCountsAndDescribesAMissingCapture() throws Exception {
    Path replay = Files.createDirectories(tmp.resolve("r"));
    Sonar sq =
        new Sonar(
            new SonarConnection(null, null, "org-a", 5, false),
            new Captures(null, replay, System.err));
    Sonar.Response r = sq.get("api/issues/search", Map.of("ps", "1"));
    Map<String, String> asked = new LinkedHashMap<>();
    asked.put("ps", "1");
    asked.put("organization", "org-a");
    String expected = Captures.name("api/issues/search", asked);
    assertThat(r)
        .isEqualTo(
            new Sonar.Response(
                0,
                "aucune capture pour api/issues/search?ps=1&organization=org-a (attendu : "
                    + expected
                    + ")"));
    assertThat(sq.missingCaptures()).isEqualTo(1);
    assertThat(sq.calls()).isEqualTo(1);
  }

  @Test
  void replayOfAnUnreadableCaptureIsCountedAsMissing() throws Exception {
    Path replay = Files.createDirectories(tmp.resolve("r"));
    Path f = replay.resolve(Captures.name("api/x", Map.of()));
    Files.writeString(f, "{}");
    Files.setPosixFilePermissions(f, PosixFilePermissions.fromString("---------"));
    Sonar sq =
        new Sonar(
            new SonarConnection(null, null, null, 5, false),
            new Captures(null, replay, System.err));
    Sonar.Response r = sq.get("api/x", Map.of());
    assertThat(r.status()).isZero();
    assertThat(r.body()).startsWith("capture illisible : ");
    assertThat(sq.missingCaptures()).isEqualTo(1);
  }

  @Test
  void aDirectoryWhereACaptureShouldBeIsMissing() throws Exception {
    Path replay = Files.createDirectories(tmp.resolve("r"));
    Files.createDirectories(replay.resolve(Captures.name("api/x", Map.of())));
    Sonar sq =
        new Sonar(
            new SonarConnection(null, null, null, 5, false),
            new Captures(null, replay, System.err));
    assertThat(sq.get("api/x", Map.of()).body()).startsWith("aucune capture pour api/x (attendu");
  }

  private static String sha1Prefix(String s) {
    try {
      byte[] d =
          java.security.MessageDigest.getInstance("SHA-1")
              .digest(s.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(d, 0, 4);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
