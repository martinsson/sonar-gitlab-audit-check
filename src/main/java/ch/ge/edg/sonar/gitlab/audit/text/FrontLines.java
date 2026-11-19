package ch.ge.edg.sonar.gitlab.audit.text;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

/**
 * Lines of front-end source in a tar.gz snapshot of a repository, as GitLab serves it from {@code
 * GET /projects/:id/repository/archive.tar.gz}: one tree, no history.
 *
 * <p>Counted: files whose extension is in {@link #EXTENSIONS}. Left out: dependency and build
 * directories ({@link #SKIPPED_DIRECTORIES}), minified or bundled files by name, files over {@link
 * #MAX_FILE_BYTES}, files holding a NUL byte (binary), and files whose lines average more than
 * {@link #MAX_AVERAGE_LINE} bytes (generated). A line is non-blank when it holds anything but
 * whitespace; comments count, as they do not in cloc's "code".
 *
 * <p>Pure JDK on purpose: {@code frontend-inventory/FrontendInventory.java} compiles this file with
 * {@code //SOURCES} and nothing else from this repository.
 */
public final class FrontLines {

  /** Extensions of front-end source files, lower case, without the dot. */
  public static final Set<String> EXTENSIONS =
      Set.of(
          "js", "jsx", "mjs", "cjs", "ts", "tsx", "vue", "svelte", "css", "scss", "sass", "less",
          "styl", "html", "htm");

  /** Directories whose files are dependencies, build output or test reports, never written. */
  public static final Set<String> SKIPPED_DIRECTORIES =
      Set.of(
          "node_modules",
          "bower_components",
          "jspm_packages",
          "vendor",
          "dist",
          "build",
          "target",
          "coverage",
          ".nuxt",
          ".next",
          ".output",
          ".svelte-kit",
          ".angular",
          ".cache",
          ".git");

  /** Above this size a source file is generated or vendored, not written by hand. */
  public static final int MAX_FILE_BYTES = 1_000_000;

  /** Bytes per line above which a file is minified or generated, whatever its name. */
  public static final int MAX_AVERAGE_LINE = 300;

  private static final List<String> GENERATED_SUFFIXES =
      List.of(".min.js", ".min.css", ".bundle.js", ".chunk.js", "-bundle.js");

  private static final int BLOCK = 512;

  private FrontLines() {}

  /** Why a file with a front extension was not counted. */
  public enum Skipped {
    MINIFIED("minifiés"),
    TOO_LARGE("> 1 Mo"),
    BINARY("binaires"),
    GENERATED("lignes trop longues");

    private final String label;

    Skipped(String label) {
      this.label = label;
    }

    public String label() {
      return label;
    }
  }

  /**
   * What one archive holds.
   *
   * @param files front files counted
   * @param lines their non-blank lines
   * @param byExtension non-blank lines per extension
   * @param skipped files with a front extension left out, by reason
   * @param truncated the download budget ran out: the counts cover only the start of the archive
   */
  public record Count(
      int files,
      long lines,
      Map<String, Long> byExtension,
      Map<Skipped, Integer> skipped,
      boolean truncated) {

    public static final Count EMPTY = new Count(0, 0, Map.of(), Map.of(), false);

    public Count {
      byExtension = Map.copyOf(byExtension);
      skipped = Map.copyOf(skipped);
    }

    /** Both counts together, for a project measured in several directories. */
    public Count plus(Count other) {
      Map<String, Long> ext = new TreeMap<>(byExtension);
      other.byExtension.forEach((k, v) -> ext.merge(k, v, Long::sum));
      Map<Skipped, Integer> skip = new TreeMap<>(skipped);
      other.skipped.forEach((k, v) -> skip.merge(k, v, Integer::sum));
      return new Count(
          files + other.files, lines + other.lines, ext, skip, truncated || other.truncated);
    }

    /** {@code vue 5308, js 641, html 36}: most lines first. */
    public String byExtensionText() {
      return byExtension.entrySet().stream()
          .sorted(
              Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                  .thenComparing(Map.Entry.comparingByKey()))
          .map(e -> e.getKey() + " " + e.getValue())
          .collect(Collectors.joining(", "));
    }

    /** {@code minifiés 2, binaires 1}, in the order of {@link Skipped}. */
    public String skippedText() {
      return new TreeMap<>(skipped)
          .entrySet().stream()
              .map(e -> e.getKey().label() + " " + e.getValue())
              .collect(Collectors.joining(", "));
    }
  }

  /**
   * Counts the front lines of a tar.gz, reading at most {@code maxCompressedBytes} of it. The first
   * path component, the directory GitLab wraps the snapshot in, is dropped.
   */
  public static Count count(InputStream tarGz, long maxCompressedBytes) throws IOException {
    Budget budget = new Budget(tarGz, maxCompressedBytes);
    Tally tally = new Tally();
    try (Tar tar = new Tar(new GZIPInputStream(budget, 64 * 1024))) {
      for (Optional<String> name = tar.nextFile(); name.isPresent(); name = tar.nextFile()) {
        tally.add(withoutFirstComponent(name.get()), tar);
      }
    } catch (IOException e) {
      if (!budget.exhausted()) throw e;
    }
    return tally.count(budget.exhausted());
  }

  /** Non-blank lines: lines holding at least one character other than whitespace. */
  public static long nonBlankLines(byte[] content) {
    long n = 0;
    boolean ink = false;
    for (byte b : content) {
      if (b == '\n') {
        if (ink) n++;
        ink = false;
      } else if (!blank(b)) {
        ink = true;
      }
    }
    return ink ? n + 1 : n;
  }

  private static boolean blank(byte b) {
    return switch (b) {
      case ' ', '\t', '\r', '\f', 0x0B -> true;
      default -> false;
    };
  }

  /** The counts of one archive while it is read. */
  private static final class Tally {
    private final Map<String, Long> byExtension = new TreeMap<>();
    private final Map<Skipped, Integer> skipped = new TreeMap<>();
    private int files;
    private long lines;

    /** One regular file; its content is read only when its name says it may be front source. */
    void add(String path, Tar tar) throws IOException {
      String extension = extension(path);
      if (!EXTENSIONS.contains(extension) || inSkippedDirectory(path)) return;
      Optional<Skipped> why = beforeReading(path, tar.size());
      if (why.isEmpty()) {
        byte[] content = tar.content();
        why = whyNotSource(content);
        if (why.isEmpty()) {
          long n = nonBlankLines(content);
          files++;
          lines += n;
          byExtension.merge(extension, n, Long::sum);
        }
      }
      why.ifPresent(w -> skipped.merge(w, 1, Integer::sum));
    }

    Count count(boolean truncated) {
      return new Count(files, lines, byExtension, skipped, truncated);
    }
  }

  private static Optional<Skipped> beforeReading(String path, long size) {
    if (generatedByName(path)) return Optional.of(Skipped.MINIFIED);
    if (size > MAX_FILE_BYTES) return Optional.of(Skipped.TOO_LARGE);
    return Optional.empty();
  }

  /** Why a file that looks like source by its name is not; empty when it is source. */
  private static Optional<Skipped> whyNotSource(byte[] content) {
    long newlines = 0;
    for (byte b : content) {
      if (b == 0) return Optional.of(Skipped.BINARY);
      if (b == '\n') newlines++;
    }
    return content.length / (newlines + 1) > MAX_AVERAGE_LINE
        ? Optional.of(Skipped.GENERATED)
        : Optional.empty();
  }

  static boolean inSkippedDirectory(String path) {
    int start = 0;
    for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', start)) {
      if (SKIPPED_DIRECTORIES.contains(path.substring(start, slash))) return true;
      start = slash + 1;
    }
    return false;
  }

  static boolean generatedByName(String path) {
    String lower = path.toLowerCase(Locale.ROOT);
    return GENERATED_SUFFIXES.stream().anyMatch(lower::endsWith);
  }

  static String extension(String path) {
    String file = path.substring(path.lastIndexOf('/') + 1);
    int dot = file.lastIndexOf('.');
    return dot <= 0 ? "" : file.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  static String withoutFirstComponent(String name) {
    int slash = name.indexOf('/');
    return slash < 0 ? "" : name.substring(slash + 1);
  }

  /**
   * The regular files of a tar, one after the other: ustar names with their prefix, pax ('x') and
   * GNU ('L') long names; directories, links and global headers are passed over.
   */
  private static final class Tar implements AutoCloseable {
    private final InputStream in;
    private final byte[] header = new byte[BLOCK];
    private long size;
    private boolean read = true;

    Tar(InputStream in) {
      this.in = in;
    }

    /** The name of the next regular file, empty at the end of the archive. */
    Optional<String> nextFile() throws IOException {
      finishEntry();
      String longName = "";
      while (readFully(in, header) && !allZero(header)) {
        size = size(header);
        read = false;
        char type = (char) header[156];
        if (type == 'x' || type == 'L') {
          byte[] meta = content();
          longName = type == 'L' ? cString(meta, 0, meta.length) : paxPath(meta);
        } else if (type == '0' || type == '\0') {
          return Optional.of(longName.isEmpty() ? ustarName(header) : longName);
        } else {
          longName = "";
        }
        finishEntry();
      }
      return Optional.empty();
    }

    long size() {
      return size;
    }

    /** The current entry's bytes; call once, before nextFile. */
    byte[] content() throws IOException {
      if (size > Integer.MAX_VALUE) throw new IOException("entrée tar trop grande : " + size);
      byte[] b = in.readNBytes((int) size);
      if (b.length < size) throw new IOException("archive tar tronquée");
      read = true;
      return b;
    }

    /** Skips what is left of the current entry and its padding to the next block. */
    private void finishEntry() throws IOException {
      if (!read) in.skipNBytes(size);
      long rest = size % BLOCK;
      if (rest != 0) in.skipNBytes(BLOCK - rest);
      size = 0;
      read = true;
    }

    @Override
    public void close() throws IOException {
      in.close();
    }

    private static String ustarName(byte[] header) {
      String name = cString(header, 0, 100);
      boolean ustar = cString(header, 257, 5).equals("ustar");
      String prefix = ustar ? cString(header, 345, 155) : "";
      return prefix.isEmpty() ? name : prefix + "/" + name;
    }

    /**
     * Octal, NUL or space terminated; or base-256 when the top bit is set (GNU, files over 8 GB).
     */
    private static long size(byte[] header) {
      if ((header[124] & 0x80) != 0) {
        long v = 0;
        for (int i = 125; i < 136; i++) v = (v << 8) | (header[i] & 0xFF);
        return v;
      }
      String octal = cString(header, 124, 12).trim();
      return octal.isEmpty() ? 0 : Long.parseLong(octal, 8);
    }

    private static String cString(byte[] b, int offset, int length) {
      int end = offset;
      while (end < offset + length && b[end] != 0) end++;
      return new String(b, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static boolean allZero(byte[] block) {
      for (byte b : block) {
        if (b != 0) return false;
      }
      return true;
    }

    /** False at a clean end of stream before the block starts; an error if it stops halfway. */
    private static boolean readFully(InputStream in, byte[] block) throws IOException {
      int n = in.readNBytes(block, 0, block.length);
      if (n == 0) return false;
      if (n < block.length) throw new IOException("archive tar tronquée");
      return true;
    }
  }

  /** The {@code path} record of a pax extended header ("27 path=a/very/long/name\n"), or "". */
  static String paxPath(byte[] meta) {
    String text = new String(meta, StandardCharsets.UTF_8);
    for (String record : text.lines().toList()) {
      int space = record.indexOf(' ');
      if (space > 0 && record.startsWith("path=", space + 1)) {
        return record.substring(space + 1 + "path=".length());
      }
    }
    return "";
  }

  /** Stops the download once the budget is spent; the count then covers what came before. */
  private static final class Budget extends FilterInputStream {
    private final long max;
    private long read;

    Budget(InputStream in, long max) {
      super(in);
      this.max = max;
    }

    boolean exhausted() {
      return read >= max;
    }

    @Override
    public int read() throws IOException {
      if (exhausted()) throw new IOException("budget de téléchargement atteint");
      int b = super.read();
      if (b >= 0) read++;
      return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (exhausted()) throw new IOException("budget de téléchargement atteint");
      int n = super.read(b, off, (int) Math.min(len, max - read));
      if (n > 0) read += n;
      return n;
    }
  }
}
