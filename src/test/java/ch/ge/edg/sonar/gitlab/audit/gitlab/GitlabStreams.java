package ch.ge.edg.sonar.gitlab.audit.gitlab;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Captures what a piece of code prints on System.out and System.err. Tests must not overlap. */
final class GitlabStreams {

  /** What was printed. */
  record Printed(String out, String err) {}

  private GitlabStreams() {}

  static Printed capture(Runnable action) {
    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    try {
      System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
      action.run();
    } finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
    return new Printed(out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
  }
}
