package ch.ge.edg.sonar.gitlab.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A JBang script is an entry point and nothing else: its directives, one class, one main that calls
 * the real main in src/main/java. Code written in a script escapes every gate (format, Error Prone,
 * NullAway, Checkstyle, PMD, tests, coverage), so it is kept out of scripts rather than checked
 * there. Scripts that already break the rule are listed in config/baseline/jbang-scripts.txt; the
 * list may shrink, never grow (the ratchet checks it).
 */
class JbangScriptsTest {

  static final int MAX_SCRIPT_LINES = 20;

  private static final Path ROOT = Path.of("");
  private static final Path BASELINE = Path.of("config/baseline/jbang-scripts.txt");

  /**
   * Directories that hold no scripts of ours: build output, client data, tooling, Maven sources.
   */
  private static final Set<String> SKIPPED =
      Set.of("src", "tools", "target", "work", "audit", "dump", "extraction", "node_modules");

  private static final Pattern DIRECTIVE = Pattern.compile("^//[A-Z]+\\b.*");
  private static final Pattern SOURCES = Pattern.compile("^//SOURCES\\s+(.+)$");
  private static final Pattern DELEGATE =
      Pattern.compile(
          "public class \\w+ \\{ public static void main\\(String\\[\\] args\\)(?: throws"
              + " Exception)? \\{ ((?:\\w+\\.)+\\w+)\\.main\\(args\\); \\} \\}");

  @Test
  void every_jbang_script_only_delegates_to_src_main_java() throws IOException {
    Set<String> baseline = baseline();
    Map<String, List<String>> broken = new TreeMap<>();
    for (Path script : scripts()) {
      String name = relative(script);
      List<String> problems = problems(script, read(script));
      if (!problems.isEmpty() && !baseline.contains(name)) {
        broken.put(name, problems);
      }
    }
    assertThat(broken)
        .as(
            "Move the code to src/main/java and leave the script a %d-line entry point, like"
                + " CrossAudit.java at the root",
            MAX_SCRIPT_LINES)
        .isEmpty();
  }

  @Test
  void the_baseline_lists_only_scripts_that_still_break_the_rule() throws IOException {
    List<String> stale = new ArrayList<>();
    for (String name : baseline()) {
      Path script = ROOT.resolve(name);
      if (!Files.isRegularFile(script) || problems(script, read(script)).isEmpty()) {
        stale.add(name);
      }
    }
    assertThat(stale)
        .as("Fixed or removed: take these lines out of %s, the list only shrinks", BASELINE)
        .isEmpty();
  }

  @Test
  void a_thin_entry_point_passes() {
    assertThat(problems(Path.of("Thin.java"), thin("cli.cross.CrossAudit"))).isEmpty();
  }

  @Test
  void imports_or_extra_code_in_the_script_are_refused() {
    String fat =
        thin("cli.cross.CrossAudit").replace("public class", "import java.util.List;\npublic class")
            + "\nclass Helper {}\n";
    assertThat(problems(Path.of("Fat.java"), fat))
        .singleElement()
        .asString()
        .contains("not a single call");
  }

  @Test
  void a_long_script_and_sources_outside_src_main_java_are_refused() {
    String text =
        thin("cli.cross.CrossAudit")
                .replace("//SOURCES", "//SOURCES ../helpers/Helper.java\n//SOURCES")
            + "\n".repeat(MAX_SCRIPT_LINES);
    assertThat(problems(Path.of("Long.java"), text))
        .hasSize(2)
        .anyMatch(p -> p.contains("lines, at most"))
        .anyMatch(p -> p.contains("../helpers/Helper.java"));
  }

  @Test
  void the_delegate_must_exist_in_src_main_java() {
    assertThat(problems(Path.of("Ghost.java"), thin("cli.NoSuchTool")))
        .singleElement()
        .asString()
        .contains("no such source");
  }

  /** What is wrong with one script; empty when it is a thin entry point. */
  static List<String> problems(Path script, String text) {
    List<String> problems = new ArrayList<>();
    List<String> lines = text.lines().toList();
    if (lines.size() > MAX_SCRIPT_LINES) {
      problems.add(lines.size() + " lines, at most " + MAX_SCRIPT_LINES);
    }
    Path dir = script.toAbsolutePath().resolve("..").normalize();
    Path main = ROOT.toAbsolutePath().resolve("src/main/java").normalize();
    for (String line : lines) {
      Matcher m = SOURCES.matcher(line.trim());
      // Only the part before the first glob: a '*' is not a legal path character everywhere.
      if (m.matches()
          && !dir.resolve(m.group(1).trim().replaceFirst("\\*.*$", ""))
              .normalize()
              .startsWith(main)) {
        problems.add("//SOURCES outside src/main/java: " + m.group(1).trim());
      }
    }
    String body =
        lines.stream()
            .map(String::trim)
            .filter(l -> !l.isEmpty() && !l.startsWith("//") && !DIRECTIVE.matcher(l).matches())
            .collect(Collectors.joining(" "))
            .replaceAll("/\\*.*?\\*/", " ")
            .replaceAll("\\s+", " ")
            .trim();
    Matcher delegate = DELEGATE.matcher(body);
    if (!delegate.matches()) {
      problems.add("not a single call to a main in src/main/java");
    } else if (!Files.isRegularFile(main.resolve(source(delegate.group(1))))) {
      problems.add("no such source for " + delegate.group(1));
    }
    return problems;
  }

  private static String source(String qualifiedClass) {
    return qualifiedClass.replace('.', '/') + ".java";
  }

  /** A script shaped like the ones at the root, delegating to {@code tool} under our package. */
  private static String thin(String tool) {
    String name = tool.substring(tool.lastIndexOf('.') + 1);
    return """
        ///usr/bin/env jbang "$0" "$@" ; exit $?
        //JAVA 26
        //SOURCES src/main/java/ch/ge/edg/sonar/gitlab/audit/**/*.java

        // Entry point.
        public class %s {
            public static void main(String[] args) {
                ch.ge.edg.sonar.gitlab.audit.%s.main(args);
            }
        }
        """
        .formatted(name, tool);
  }

  /** Every .java file outside the Maven trees that carries a JBang shebang or directive. */
  private static List<Path> scripts() throws IOException {
    try (Stream<Path> files =
        Files.walk(ROOT.toAbsolutePath())
            .filter(p -> p.toString().endsWith(".java"))
            .filter(p -> !skipped(ROOT.toAbsolutePath().relativize(p)))) {
      List<Path> scripts = new ArrayList<>();
      for (Path p : files.toList()) {
        if (read(p).lines().limit(5).anyMatch(l -> l.startsWith("///usr/bin/env jbang"))
            || read(p).lines().anyMatch(l -> l.startsWith("//DEPS") || l.startsWith("//SOURCES"))) {
          scripts.add(p);
        }
      }
      return scripts;
    }
  }

  private static boolean skipped(Path relative) {
    for (Path part : relative) {
      String name = part.toString();
      if (name.startsWith(".") || SKIPPED.contains(name)) {
        return true;
      }
    }
    return false;
  }

  private static Set<String> baseline() throws IOException {
    if (!Files.exists(BASELINE)) {
      return Set.of();
    }
    return Files.readAllLines(BASELINE, StandardCharsets.UTF_8).stream()
        .map(String::trim)
        .filter(l -> !l.isEmpty() && !l.startsWith("#"))
        .collect(Collectors.toCollection(java.util.TreeSet::new));
  }

  private static String relative(Path script) {
    return ROOT.toAbsolutePath().relativize(script).toString().replace('\\', '/');
  }

  private static String read(Path p) throws IOException {
    return Files.readString(p, StandardCharsets.UTF_8);
  }
}
