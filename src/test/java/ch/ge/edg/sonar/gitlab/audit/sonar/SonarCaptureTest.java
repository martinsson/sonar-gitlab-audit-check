package ch.ge.edg.sonar.gitlab.audit.sonar;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.rest.Capture;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import ch.ge.edg.sonar.gitlab.audit.testing.Native;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    String name = Capture.name("api/measures/search?b=2&a=1");
    assertThat(name).matches("measures_search-[0-9a-f]{8}\\.json");
    assertThat(Capture.name("api/measures/search?a=1&b=2")).isEqualTo(name);
    assertThat(Capture.name("api/measures/search?a=1")).isNotEqualTo(name);
  }

  @Test
  void captureNameIsAKnownSha1Prefix() {
    // SHA-1("api/system/status") starts with these four bytes.
    assertThat(Capture.name("api/system/status"))
        .isEqualTo("system_status-" + sha1Prefix("api/system/status") + ".json");
  }

  @Test
  void captureNameKeepsNonApiPrefixAndTruncatesTheSlugTo60() {
    String longPath = "x/" + "a".repeat(80);
    String name = Capture.name(longPath);
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
            new Capture(dump, null, reason -> {}),
            pause -> {});
    assertThat(dump).isDirectory();

    sq.get("api/system/status", Map.of());
    sq.get("api/err", Map.of("k", "v w"));

    String status = Capture.name("api/system/status");
    String err = Capture.name("api/err?k=v+w");
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
            new Capture(dump, null, reason -> {}),
            pause -> {});
    sq.get("api/x", Map.of());
    assertThat(Files.readString(dump.resolve(Capture.name("api/x"))))
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
            new Capture(
                dump, null, reason -> warnings.println("  (capture non écrite : " + reason + ")")),
            pause -> {});
    Files.createDirectories(dump.resolve(Capture.name("api/x")));

    RestResponse r = sq.get("api/x", Map.of());
    assertThat(r.body()).isEqualTo("{}");
    assertThat(err.toString(StandardCharsets.UTF_8)).startsWith("  (capture non écrite : ");
    assertThat(dump.resolve("index.txt")).doesNotExist();
  }

  @Test
  void aCaptured403ReplaysWithItsStatus() throws Exception {
    server.on("/api/denied", r -> Response.status(403, "{\"errors\":[{\"msg\":\"no\"}]}"));
    Path dir = tmp.resolve("d");
    Sonar live =
        new Sonar(
            new SonarConnection(server.url(), "t", null, 5, false),
            new Capture(dir, null, reason -> {}),
            pause -> {});
    assertThat(live.get("api/denied", Map.of()).is(403)).isTrue();

    Sonar replay =
        new Sonar(
            new SonarConnection(server.url(), "t", null, 5, false),
            new Capture(null, dir, reason -> {}),
            pause -> {});
    assertAnswered(replay.get("api/denied", Map.of()), 403, "{\"errors\":[{\"msg\":\"no\"}]}");
  }

  @Test
  void aBodyOnlyCaptureFromBeforeTheStatusFileReplaysAsA200() throws Exception {
    Path replay = Files.createDirectories(tmp.resolve("old"));
    Files.writeString(replay.resolve(Capture.name("api/x")), "{\"r\":1}");
    Sonar sq =
        new Sonar(
            new SonarConnection(null, null, null, 5, false),
            new Capture(null, replay, reason -> {}),
            pause -> {});
    assertAnswered(sq.get("api/x", Map.of()), 200, "{\"r\":1}");
  }

  @Test
  void replayReadsCapturesWithoutCallingTheInstance() throws Exception {
    Path replay = Files.createDirectories(tmp.resolve("r"));
    Files.writeString(replay.resolve(Capture.name("api/x?p=1")), "{\"r\":1}");
    Sonar sq =
        new Sonar(
            new SonarConnection(server.url(), "t", null, 5, false),
            new Capture(null, replay, reason -> {}),
            pause -> {});

    assertThat(sq.replaying()).isTrue();
    assertAnswered(sq.get("/api/x", Map.of("p", "1")), 200, "{\"r\":1}");
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
            new Capture(null, replay, reason -> {}),
            pause -> {});
    RestResponse r = sq.get("api/issues/search", Map.of("ps", "1"));
    String expected = Capture.name("api/issues/search?ps=1&organization=org-a");
    assertThat(r)
        .isEqualTo(
            RestResponse.unreachable(
                "aucune capture pour api/issues/search?ps=1&organization=org-a (attendu : "
                    + expected
                    + ")"));
    assertThat(sq.missingCaptures()).isEqualTo(1);
    assertThat(sq.calls()).isEqualTo(1);
  }

  @Test
  void replayOfAnUnreadableCaptureIsCountedAsMissing() throws Exception {
    Path replay = Files.createDirectories(tmp.resolve("r"));
    Path f = replay.resolve(Capture.name("api/x"));
    Files.writeString(f, "{}");
    Native.unreadable(f);
    Sonar sq =
        new Sonar(
            new SonarConnection(null, null, null, 5, false),
            new Capture(null, replay, reason -> {}),
            pause -> {});
    RestResponse r = sq.get("api/x", Map.of());
    assertThat(r.unreachable()).isTrue();
    assertThat(r.why()).startsWith("capture illisible : ");
    assertThat(sq.missingCaptures()).isEqualTo(1);
  }

  @Test
  void aDirectoryWhereACaptureShouldBeIsMissing() throws Exception {
    Path replay = Files.createDirectories(tmp.resolve("r"));
    Files.createDirectories(replay.resolve(Capture.name("api/x")));
    Sonar sq =
        new Sonar(
            new SonarConnection(null, null, null, 5, false),
            new Capture(null, replay, reason -> {}),
            pause -> {});
    assertThat(sq.get("api/x", Map.of()).why()).startsWith("aucune capture pour api/x (attendu");
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

  private static void assertAnswered(RestResponse r, int status, String body) {
    assertThat(r.is(status)).as("status").isTrue();
    assertThat(r.body()).isEqualTo(body);
  }
}
