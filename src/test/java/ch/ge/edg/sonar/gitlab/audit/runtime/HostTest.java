package ch.ge.edg.sonar.gitlab.audit.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * The edge: charset choice, colour detection and what {@link Host#system()} installs. A forked test
 * JVM never has a terminal, so there the system host always means no colours and UTF-8.
 */
class HostTest {

  private static final Charset CP850 = Charset.forName("IBM850");

  @Test
  void aForcedCharsetWinsAndIsTrimmed() {
    assertThat(Host.charset(" IBM850 ", null)).isEqualTo(CP850);
    assertThat(Host.charset("windows-1252", StandardCharsets.UTF_8))
        .isEqualTo(Charset.forName("windows-1252"));
  }

  @Test
  void anUnknownOrIllegalForcedCharsetFallsBackToUtf8() {
    assertThat(Host.charset("no-such-charset", CP850)).isEqualTo(StandardCharsets.UTF_8);
    assertThat(Host.charset("bad charset name!", CP850)).isEqualTo(StandardCharsets.UTF_8);
  }

  @Test
  void withoutAForcedCharsetTheTerminalDecidesAndUtf8WithoutOne() {
    assertThat(Host.charset("   ", CP850)).isEqualTo(CP850);
    assertThat(Host.charset(null, CP850)).isEqualTo(CP850);
    assertThat(Host.charset(null, null)).isEqualTo(StandardCharsets.UTF_8);
  }

  @Test
  void coloursNeedATerminalAndNoNOCOLOR() {
    assertThat(new Terminal(true, false).colors(Map.of())).isTrue();
    assertThat(new Terminal(false, false).colors(Map.of())).isFalse();
    assertThat(new Terminal(true, false).colors(Map.of("NO_COLOR", ""))).isFalse();
    assertThat(Terminal.NONE.colors(Map.of("TERM", "xterm"))).isFalse();
  }

  @Test
  void onWindowsColoursNeedAnEmulatorThatSaysSo() {
    Terminal windows = new Terminal(true, true);
    assertThat(windows.colors(Map.of())).isFalse();
    for (String v : new String[] {"WT_SESSION", "ConEmuANSI", "ANSICON", "TERM"}) {
      assertThat(windows.colors(Map.of(v, "1"))).as(v).isTrue();
    }
    assertThat(windows.colors(Map.of("NO_COLOR", "", "WT_SESSION", "1"))).isFalse();
  }

  @Test
  void theConsoleFollowsTheOptionOverTheTerminal() {
    Host host = host(new Terminal(true, false));
    assertThat(host.console(null).colors()).isTrue();
    assertThat(host.console("never").colors()).isFalse();
    assertThat(host(Terminal.NONE).console("always").colors()).isTrue();
    assertThat(host(Terminal.NONE).console("auto").colors()).isFalse();
  }

  @Test
  void theCommandLineWritesHelpAndErrorsOnTheHostStreams() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Host host =
        new Host(
            new PrintStream(out, true, StandardCharsets.UTF_8),
            System.err,
            Map.of(),
            Clock.systemUTC(),
            pause -> {},
            Path.of(""),
            Terminal.NONE);
    PrintWriter writer = host.commandLine(new Nothing()).getOut();
    writer.print("é");
    writer.flush();
    assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("é");
  }

  @Test
  void theHelpIsColouredByTheSameRuleAsTheConsole() {
    assertThat(help(new Terminal(true, false), Map.of())).contains("\033[1mnothing");
    assertThat(help(Terminal.NONE, Map.of("TERM", "xterm"))).doesNotContain("\033[");
    assertThat(help(new Terminal(true, true), Map.of())).doesNotContain("\033[");
  }

  @Test
  void theSystemHostReplacesBothStreamsAndHasNoTerminal() {
    for (String cs : new String[] {"UTF-8", "IBM850"}) {
      @Nullable String saved = System.getProperty(Host.FORCED_CHARSET);
      PrintStream out = System.out;
      PrintStream err = System.err;
      System.setProperty(Host.FORCED_CHARSET, cs);
      try {
        Host host = Host.system();
        assertThat(System.out).isSameAs(host.out()).isNotSameAs(out);
        assertThat(System.err).isSameAs(host.err()).isNotSameAs(err).isNotSameAs(System.out);
        assertThat(host.terminal().attached()).isFalse();
        assertThat(host.console("auto").colors()).isFalse();
      } finally {
        System.setOut(out);
        System.setErr(err);
        if (saved == null) System.clearProperty(Host.FORCED_CHARSET);
        else System.setProperty(Host.FORCED_CHARSET, saved);
      }
    }
  }

  @Command(name = "nothing")
  static final class Nothing {}

  private static String help(Terminal terminal, Map<String, String> env) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Host host =
        new Host(
            new PrintStream(out, true, StandardCharsets.UTF_8),
            System.err,
            env,
            Clock.systemUTC(),
            pause -> {},
            Path.of(""),
            terminal);
    CommandLine commandLine = host.commandLine(new Nothing());
    commandLine.usage(commandLine.getOut());
    return out.toString(StandardCharsets.UTF_8);
  }

  private static Host host(Terminal terminal) {
    PrintStream nowhere =
        new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);
    return new Host(
        nowhere, nowhere, Map.of(), Clock.systemUTC(), pause -> {}, Path.of(""), terminal);
  }
}
