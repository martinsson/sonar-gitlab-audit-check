package ch.ge.edg.sonar.gitlab.audit.console;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The global side of ConsoleOut: charset choice, color switch, install. What depends on a real
 * terminal or on environment variables (NO_COLOR, WT_SESSION, ConEmuANSI, ANSICON, TERM, os.name on
 * Windows) is not reachable from a forked test JVM, which never has a terminal: there, "auto"
 * always means no colors and no forced charset always means UTF-8.
 */
class ConsoleOutTest {

  private static final String FORCED = "audit.console.charset";

  private @Nullable String savedProperty;
  private boolean savedColors;

  @BeforeEach
  void save() {
    savedProperty = System.getProperty(FORCED);
    savedColors = ConsoleOut.colors();
  }

  @AfterEach
  void restore() {
    if (savedProperty == null) System.clearProperty(FORCED);
    else System.setProperty(FORCED, savedProperty);
    ConsoleOut.colorMode(savedColors ? "always" : "never");
  }

  @Test
  void a_forced_charset_wins_and_is_trimmed() {
    System.setProperty(FORCED, " IBM850 ");
    assertThat(ConsoleOut.target()).isEqualTo(Charset.forName("IBM850"));
    System.setProperty(FORCED, "windows-1252");
    assertThat(ConsoleOut.target()).isEqualTo(Charset.forName("windows-1252"));
  }

  @Test
  void an_unknown_or_illegal_forced_charset_falls_back_to_utf8() {
    System.setProperty(FORCED, "no-such-charset");
    assertThat(ConsoleOut.target()).isEqualTo(StandardCharsets.UTF_8);
    System.setProperty(FORCED, "bad charset name!");
    assertThat(ConsoleOut.target()).isEqualTo(StandardCharsets.UTF_8);
  }

  @Test
  void without_a_terminal_a_blank_or_absent_forced_charset_means_utf8() {
    System.setProperty(FORCED, "   ");
    assertThat(ConsoleOut.target()).isEqualTo(StandardCharsets.UTF_8);
    System.clearProperty(FORCED);
    assertThat(ConsoleOut.target()).isEqualTo(StandardCharsets.UTF_8);
  }

  @Test
  void color_mode_always_and_never_switch_and_the_option_is_case_insensitive() {
    ConsoleOut.colorMode("never");
    assertThat(ConsoleOut.colors()).isFalse();
    ConsoleOut.colorMode("ALWAYS");
    assertThat(ConsoleOut.colors()).isTrue();
    ConsoleOut.colorMode("Never");
    assertThat(ConsoleOut.colors()).isFalse();
  }

  @Test
  void a_null_color_mode_changes_nothing() {
    ConsoleOut.colorMode("always");
    ConsoleOut.colorMode(null);
    assertThat(ConsoleOut.colors()).isTrue();
    ConsoleOut.colorMode("never");
    ConsoleOut.colorMode(null);
    assertThat(ConsoleOut.colors()).isFalse();
  }

  @Test
  void auto_or_any_other_word_detects_and_without_a_terminal_turns_colors_off() {
    ConsoleOut.colorMode("always");
    ConsoleOut.colorMode("auto");
    assertThat(ConsoleOut.colors()).isFalse();
    ConsoleOut.colorMode("always");
    ConsoleOut.colorMode("whatever");
    assertThat(ConsoleOut.colors()).isFalse();
  }

  @Test
  void color_wraps_in_the_code_and_a_reset_only_when_colors_are_on() {
    ConsoleOut.colorMode("always");
    assertThat(ConsoleOut.color("x", "\033[1m")).isEqualTo("\033[1mx\033[0m");
    ConsoleOut.colorMode("never");
    assertThat(ConsoleOut.color("x", "\033[1m")).isEqualTo("x");
  }

  @Test
  void install_replaces_both_streams_and_detects_colors() {
    for (String cs : new String[] {"UTF-8", "IBM850"}) {
      System.setProperty(FORCED, cs);
      PrintStream out = System.out;
      PrintStream err = System.err;
      ConsoleOut.colorMode("always");
      try {
        ConsoleOut.install();
        assertThat(System.out).isNotSameAs(out);
        assertThat(System.err).isNotSameAs(err).isNotSameAs(System.out);
        assertThat(ConsoleOut.colors()).isFalse();
      } finally {
        System.setOut(out);
        System.setErr(err);
      }
    }
  }
}
