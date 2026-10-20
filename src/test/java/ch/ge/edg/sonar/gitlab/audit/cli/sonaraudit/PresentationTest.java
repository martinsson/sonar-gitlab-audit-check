package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.testing.Printed;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Console helpers: progress counter, titles, verdict lines, colors. */
class PresentationTest {

  private static String capture(Consumer<Console> r) {
    return capture(false, r);
  }

  private static String capture(boolean colors, Consumer<Console> r) {
    Printed printed = colors ? Printed.colored() : Printed.plain();
    r.accept(printed.console());
    return printed.text();
  }

  private static void tick(Progress p, int times) {
    for (int i = 0; i < times; i++) p.tick();
  }

  @Test
  void liveProgressRewritesEveryTwentyFiveAndAtTheEnd() {
    String out =
        capture(
            true,
            c -> {
              Progress p = new Progress(c, "x", 30);
              tick(p, 30);
              p.done();
            });
    assertThat(out).isEqualTo("x 0/30\rx 25/30\rx 30/30\rx 30/30\n");
  }

  @Test
  void liveProgressOfNothingIsSilent() {
    String out =
        capture(
            true,
            c -> {
              Progress p = new Progress(c, "x", 0);
              p.done();
            });
    assertThat(out).isEmpty();
  }

  @Test
  void progressIsSilentWithoutColors() {
    String out =
        capture(
            c -> {
              Progress p = new Progress(c, "x", 25);
              tick(p, 25);
              p.done();
            });
    assertThat(out).isEmpty();
  }

  @Test
  void titlePadsToSixtySixWithEquals() {
    String out = capture(c -> Presentation.title(c, "abc"));
    assertThat(out).isEqualTo("\n=== abc " + "=".repeat(63) + "\n");
    String longTitle = "t".repeat(70);
    assertThat(capture(c -> Presentation.title(c, longTitle)))
        .isEqualTo("\n=== " + longTitle + " \n");
  }

  @Test
  void lineAlignsLabelAndDropsBlankDetail() {
    assertThat(capture(c -> Presentation.line(c, "lbl", CallVerdict.OK, "d")))
        .isEqualTo("  OK       lbl" + " ".repeat(39) + " d\n");
    assertThat(capture(c -> Presentation.line(c, "lbl", CallVerdict.ERROR, " ")))
        .isEqualTo("  ERREUR   lbl" + " ".repeat(39) + " \n");
  }

  @Test
  void colorsWrapWhenEnabled() {
    assertThat(Printed.colored().console().color("t", Presentation.RED))
        .isEqualTo("\033[31mt\033[0m");
    assertThat(capture(true, c -> Presentation.line(c, "l", CallVerdict.DENIED, "d")))
        .isEqualTo("  \033[31mREFUSE  \033[0m l" + " ".repeat(41) + " \033[2md\033[0m\n");
    assertThat(Printed.plain().console().color("t", Presentation.RED)).isEqualTo("t");
  }
}
