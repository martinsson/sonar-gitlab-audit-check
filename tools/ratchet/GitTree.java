package ratchet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** The files of the project, either as they are on disk or as they were in a git commit. */
sealed interface GitTree {

  static GitTree workingTree() {
    return new WorkingTree();
  }

  static GitTree commit(String ref) {
    return new Commit(ref);
  }

  /** The lines of a file, or none when it does not exist. */
  List<String> lines(String path) throws IOException;

  /** Every file under a directory, as a path relative to the project. */
  List<String> files(String dir) throws IOException;

  /** Lines under src/ and tools/ that contain the marker. */
  int count(String marker) throws IOException;

  default String text(String path) throws IOException {
    return String.join("\n", lines(path));
  }

  /** Output lines of a git command, or nothing when it fails (missing path, no match). */
  static List<String> run(String... command) throws IOException {
    Process process =
        new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    try {
      return process.waitFor() == 0 ? out.lines().toList() : List.of();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted while running " + String.join(" ", command), e);
    }
  }

  /** Sums the per-file counts printed by {@code git grep -c} ("path:3" or "ref:path:3"). */
  private static int sumCounts(List<String> grepOutput) {
    return grepOutput.stream()
        .mapToInt(line -> Integer.parseInt(line.substring(line.lastIndexOf(':') + 1)))
        .sum();
  }

  /** The files on disk, including untracked ones. */
  record WorkingTree() implements GitTree {
    @Override
    public List<String> lines(String path) throws IOException {
      Path file = Path.of(path);
      return Files.exists(file) ? Files.readAllLines(file) : List.of();
    }

    @Override
    public List<String> files(String dir) throws IOException {
      if (!Files.isDirectory(Path.of(dir))) {
        return List.of();
      }
      try (Stream<Path> walk = Files.walk(Path.of(dir))) {
        return walk.filter(Files::isRegularFile)
            .map(p -> p.toString().replace('\\', '/'))
            .sorted()
            .toList();
      }
    }

    @Override
    public int count(String marker) throws IOException {
      return sumCounts(
          run("git", "grep", "-c", "-F", "--untracked", "-e", marker, "--", "src", "tools"));
    }
  }

  /** The files of a commit, read with git. */
  record Commit(String ref) implements GitTree {
    @Override
    public List<String> lines(String path) throws IOException {
      List<String> listed = run("git", "ls-tree", "--name-only", ref, "--", path);
      return listed.isEmpty() ? List.of() : run("git", "show", ref + ":" + path);
    }

    @Override
    public List<String> files(String dir) throws IOException {
      return run("git", "ls-tree", "-r", "--name-only", ref, "--", dir);
    }

    @Override
    public int count(String marker) throws IOException {
      return sumCounts(run("git", "grep", "-c", "-F", "-e", marker, ref, "--", "src", "tools"));
    }
  }
}
