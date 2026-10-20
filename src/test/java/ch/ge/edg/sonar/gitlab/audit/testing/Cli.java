package ch.ge.edg.sonar.gitlab.audit.testing;

import ch.ge.edg.sonar.gitlab.audit.runtime.Host;
import ch.ge.edg.sonar.gitlab.audit.runtime.Terminal;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.function.Function;

/**
 * Runs a picocli command in-process, as {@code main} would minus {@code System.exit}, on a {@link
 * Host} of fixed values: captured streams, no environment, no terminal, no sleep, the given clock
 * (UTC by default) and a working directory with an empty namespace-mapping.yaml and no
 * exclusions.txt. Nothing global is swapped: runs may overlap.
 */
public final class Cli {

  /** What a run produced. */
  public record Result(int exitCode, String out, String err) {}

  /**
   * The working directory of the runs: an empty namespace mapping, no exclusion list. Made under
   * target/ when first asked for, since namespace-mapping.yaml is a name git ignores.
   */
  public static Path workDir() {
    Path dir = Path.of("target", "test-workdir");
    Path mapping = dir.resolve("namespace-mapping.yaml");
    try {
      Files.createDirectories(dir);
      if (!Files.exists(mapping)) Files.writeString(mapping, "# empty: no namespace is mapped\n");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return dir;
  }

  private Cli() {}

  public static Result run(Function<Host, ?> command, String... args) {
    return run(Clock.systemUTC(), command, args);
  }

  public static Result run(Clock clock, Function<Host, ?> command, String... args) {
    return run(clock, workDir(), command, args);
  }

  public static Result run(Clock clock, Path workDir, Function<Host, ?> command, String... args) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    Host host = host(stream(out), stream(err), clock, workDir);
    int code = host.commandLine(command.apply(host)).execute(args);
    return new Result(
        code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
  }

  /** A host of fixed values that prints nowhere: for a command that is only parsed. */
  public static Host host() {
    PrintStream nowhere =
        new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);
    return host(nowhere, nowhere, Clock.systemUTC(), workDir());
  }

  /** A host of fixed values around the given streams. */
  public static Host host(PrintStream out, PrintStream err, Clock clock, Path workDir) {
    return new Host(out, err, Map.of(), clock, pause -> {}, workDir, Terminal.NONE);
  }

  private static PrintStream stream(ByteArrayOutputStream bytes) {
    return new PrintStream(bytes, true, StandardCharsets.UTF_8);
  }
}
