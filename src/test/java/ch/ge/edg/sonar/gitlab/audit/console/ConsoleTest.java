package ch.ge.edg.sonar.gitlab.audit.console;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Native;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The colour switch and what a console writes where. */
class ConsoleTest {

  private final ByteArrayOutputStream out = new ByteArrayOutputStream();
  private final ByteArrayOutputStream err = new ByteArrayOutputStream();

  private Console console(boolean colors) {
    return new Console(
        new PrintStream(out, true, StandardCharsets.UTF_8),
        new PrintStream(err, true, StandardCharsets.UTF_8),
        colors);
  }

  @Test
  void theOptionHasTheLastWordWhateverItsCase() {
    assertThat(Console.colors("never", true)).isFalse();
    assertThat(Console.colors("ALWAYS", false)).isTrue();
    assertThat(Console.colors("Never", true)).isFalse();
  }

  @Test
  void autoAbsentOrUnknownFallsBackOnWhatTheTerminalCanDo() {
    for (boolean auto : new boolean[] {true, false}) {
      assertThat(Console.colors(null, auto)).isEqualTo(auto);
      assertThat(Console.colors("auto", auto)).isEqualTo(auto);
      assertThat(Console.colors("whatever", auto)).isEqualTo(auto);
    }
  }

  @Test
  void colorWrapsOnlyWhenColoursAreOn() {
    assertThat(console(true).color("x", "\033[1m")).isEqualTo("\033[1mx\033[0m");
    assertThat(console(false).color("x", "\033[1m")).isEqualTo("x");
    assertThat(console(true).colors()).isTrue();
  }

  @Test
  void linesGoToTheOutputAndErrToTheError() {
    Console c = console(false);
    c.println("a");
    c.print("b");
    c.println();
    c.out().printf("%s%n", "c");
    c.err().println("e");
    c.flush();
    assertThat(Native.lines(out.toString(StandardCharsets.UTF_8))).isEqualTo("a\nb\nc\n");
    assertThat(Native.lines(err.toString(StandardCharsets.UTF_8))).isEqualTo("e\n");
  }
}
