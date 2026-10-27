package ch.ge.edg.sonar.gitlab.audit.testing;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** A console that writes output and error into one buffer, for a printer tested on its own. */
public final class Printed {

  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
  private final Console console;

  private Printed(boolean colors) {
    PrintStream stream = new PrintStream(bytes, true, StandardCharsets.UTF_8);
    this.console = new Console(stream, stream, colors);
  }

  /** Without colours. */
  public static Printed plain() {
    return new Printed(false);
  }

  /** With ANSI colours. */
  public static Printed colored() {
    return new Printed(true);
  }

  public Console console() {
    return console;
  }

  /** What was written so far, with \n line ends. */
  public String text() {
    return Native.lines(bytes.toString(StandardCharsets.UTF_8));
  }
}
