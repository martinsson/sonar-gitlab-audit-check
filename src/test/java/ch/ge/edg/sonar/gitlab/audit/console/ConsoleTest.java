package ch.ge.edg.sonar.gitlab.audit.console;

import static org.assertj.core.api.Assertions.assertThat;

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
  void the_option_has_the_last_word_whatever_its_case() {
    assertThat(Console.colors("never", true)).isFalse();
    assertThat(Console.colors("ALWAYS", false)).isTrue();
    assertThat(Console.colors("Never", true)).isFalse();
  }

  @Test
  void auto_absent_or_unknown_falls_back_on_what_the_terminal_can_do() {
    for (boolean auto : new boolean[] {true, false}) {
      assertThat(Console.colors(null, auto)).isEqualTo(auto);
      assertThat(Console.colors("auto", auto)).isEqualTo(auto);
      assertThat(Console.colors("whatever", auto)).isEqualTo(auto);
    }
  }

  @Test
  void color_wraps_only_when_colours_are_on() {
    assertThat(console(true).color("x", "\033[1m")).isEqualTo("\033[1mx\033[0m");
    assertThat(console(false).color("x", "\033[1m")).isEqualTo("x");
    assertThat(console(true).colors()).isTrue();
  }

  @Test
  void lines_go_to_the_output_and_err_to_the_error() {
    Console c = console(false);
    c.println("a");
    c.print("b");
    c.println();
    c.out().printf("%s%n", "c");
    c.err().println("e");
    c.flush();
    assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("a\nb\nc\n");
    assertThat(err.toString(StandardCharsets.UTF_8)).isEqualTo("e\n");
  }
}
