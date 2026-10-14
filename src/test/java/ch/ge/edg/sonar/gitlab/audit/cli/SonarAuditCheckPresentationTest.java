package ch.ge.edg.sonar.gitlab.audit.cli;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheck.Progress;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheck.Verdict;
import ch.ge.edg.sonar.gitlab.audit.console.ConsoleOut;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Console helpers: progress counter, titles, verdict lines, colors. */
class SonarAuditCheckPresentationTest {

  @AfterEach
  void noColors() {
    ConsoleOut.colorMode("never");
  }

  private static String capture(Runnable r) {
    PrintStream old = System.out;
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
    try {
      r.run();
    } finally {
      System.setOut(old);
    }
    return buf.toString(StandardCharsets.UTF_8);
  }

  private static void tick(Progress p, int times) {
    for (int i = 0; i < times; i++) p.tick();
  }

  @Test
  void liveProgressRewritesEveryTwentyFiveAndAtTheEnd() {
    ConsoleOut.colorMode("always");
    String out =
        capture(
            () -> {
              Progress p = new Progress("x", 30);
              tick(p, 30);
              p.done();
            });
    assertThat(out).isEqualTo("x 0/30\rx 25/30\rx 30/30\rx 30/30\n");
  }

  @Test
  void liveProgressOfNothingIsSilent() {
    ConsoleOut.colorMode("always");
    String out =
        capture(
            () -> {
              Progress p = new Progress("x", 0);
              p.done();
            });
    assertThat(out).isEmpty();
  }

  @Test
  void progressIsSilentWithoutColors() {
    ConsoleOut.colorMode("never");
    String out =
        capture(
            () -> {
              Progress p = new Progress("x", 25);
              tick(p, 25);
              p.done();
            });
    assertThat(out).isEmpty();
  }

  @Test
  void titlePadsToSixtySixWithEquals() {
    String out = capture(() -> SonarAuditCheck.title("abc"));
    assertThat(out).isEqualTo("\n=== abc " + "=".repeat(63) + "\n");
    String longTitle = "t".repeat(70);
    assertThat(capture(() -> SonarAuditCheck.title(longTitle)))
        .isEqualTo("\n=== " + longTitle + " \n");
  }

  @Test
  void lineAlignsLabelAndDropsBlankDetail() {
    assertThat(capture(() -> SonarAuditCheck.line("lbl", Verdict.OK, "d")))
        .isEqualTo("  OK       lbl" + " ".repeat(39) + " d\n");
    assertThat(capture(() -> SonarAuditCheck.line("lbl", Verdict.ERROR, " ")))
        .isEqualTo("  ERREUR   lbl" + " ".repeat(39) + " \n");
  }

  @Test
  void colorsWrapWhenEnabled() {
    ConsoleOut.colorMode("always");
    assertThat(SonarAuditCheck.c("t", SonarAuditCheck.RED)).isEqualTo("\033[31mt\033[0m");
    assertThat(capture(() -> SonarAuditCheck.line("l", Verdict.DENIED, "d")))
        .isEqualTo("  \033[31mREFUSE  \033[0m l" + " ".repeat(41) + " \033[2md\033[0m\n");
    ConsoleOut.colorMode("never");
    assertThat(SonarAuditCheck.c("t", SonarAuditCheck.RED)).isEqualTo("t");
  }
}
