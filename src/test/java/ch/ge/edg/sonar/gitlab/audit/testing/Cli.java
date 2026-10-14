package ch.ge.edg.sonar.gitlab.audit.testing;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import picocli.CommandLine;

/**
 * Runs a picocli command in-process, as {@code main} would minus {@code System.exit}, capturing
 * what it prints. The tools print through {@code System.out}/{@code System.err}, so this swaps both
 * for the duration of the run: tests using it must not run in parallel.
 */
public final class Cli {

  /** What a run produced. */
  public record Result(int exitCode, String out, String err) {}

  private Cli() {}

  public static Result run(Object command, String... args) {
    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int code;
    try {
      System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
      code = new CommandLine(command).execute(args);
    } finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
    return new Result(
        code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
  }
}
