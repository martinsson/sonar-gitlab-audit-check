package ratchet;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Turns the reports in target/ into config/baseline. The ArchUnit store is written by ArchUnit. */
final class BaselineWriter {

  private final PrintStream out;

  BaselineWriter(PrintStream out) {
    this.out = out;
  }

  void writeAll() throws IOException {
    Files.createDirectories(Path.of(BaselineFiles.DIR));
    writeCheckstyle();
    writePmd();
    writeCpd();
  }

  private void writeCheckstyle() throws IOException {
    Set<String> lines = new TreeSet<>();
    for (Element file : report("target/checkstyle-result.xml", "file")) {
      String regex = relative(file.getAttribute("name")).replace(".", "\\.");
      for (Element error : Xml.elements(file, "error")) {
        String check = checkName(error.getAttribute("source"));
        lines.add("  <suppress files=\"%s$\" checks=\"%s\"/>".formatted(regex, check));
      }
    }
    String body =
        """
        <?xml version="1.0"?>
        <!DOCTYPE suppressions PUBLIC
            "-//Checkstyle//DTD SuppressionFilter Configuration 1.2//EN"
            "https://checkstyle.org/dtds/suppressions_1_2.dtd">
        <!-- %s -->
        <suppressions>
        %s</suppressions>
        """
            .formatted(BaselineFiles.HEADER, joinLines(lines));
    write(BaselineFiles.CHECKSTYLE, body, lines.size());
  }

  private void writePmd() throws IOException {
    Map<String, Set<String>> rulesPerClass = new TreeMap<>();
    for (Element file : report("target/pmd.xml", "file")) {
      for (Element violation : Xml.elements(file, "violation")) {
        String type =
            violation.getAttribute("class").isEmpty()
                ? className(file.getAttribute("name"))
                : violation.getAttribute("package") + "." + violation.getAttribute("class");
        rulesPerClass
            .computeIfAbsent(type, k -> new TreeSet<>())
            .add(violation.getAttribute("rule"));
      }
    }
    Set<String> lines = new TreeSet<>();
    rulesPerClass.forEach((type, rules) -> lines.add(type + "=" + String.join(",", rules)));
    int entries = rulesPerClass.values().stream().mapToInt(Set::size).sum();
    write(BaselineFiles.PMD, "# " + BaselineFiles.HEADER + "\n" + joinLines(lines), entries);
  }

  private void writeCpd() throws IOException {
    Set<String> lines = new TreeSet<>();
    for (Element duplication : report("target/cpd.xml", "duplication")) {
      Set<String> types = new TreeSet<>();
      for (Element file : Xml.elements(duplication, "file")) {
        types.add(className(file.getAttribute("path")));
      }
      lines.add(String.join(",", types));
    }
    write(BaselineFiles.CPD, "# " + BaselineFiles.HEADER + "\n" + joinLines(lines), lines.size());
  }

  private static Iterable<Element> report(String path, String tag) throws IOException {
    Path report = Path.of(path);
    if (!Files.exists(report)) {
      return Set.of();
    }
    Document doc = Xml.parse(Files.readString(report));
    return Xml.elements(doc, tag);
  }

  private void write(String path, String content, int entries) throws IOException {
    Files.writeString(Path.of(path), content);
    out.println("  " + path + ": " + entries + " entries");
  }

  private static String joinLines(Set<String> lines) {
    StringBuilder text = new StringBuilder();
    lines.forEach(line -> text.append(line).append('\n'));
    return text.toString();
  }

  static String relative(String absolute) {
    String cwd = Path.of("").toAbsolutePath().toString().replace('\\', '/') + "/";
    String path = absolute.replace('\\', '/');
    return path.startsWith(cwd) ? path.substring(cwd.length()) : path;
  }

  /** src/main/java/com/example/Foo.java -> com.example.Foo */
  static String className(String path) {
    String p = relative(path);
    int root = p.indexOf("/java/");
    String inRoot = root < 0 ? p : p.substring(root + "/java/".length());
    return inRoot.replaceAll("\\.java$", "").replace('/', '.');
  }

  /** com.puppycrawl.tools.checkstyle.checks.sizes.MethodLengthCheck -> MethodLength */
  static String checkName(String source) {
    return source.substring(source.lastIndexOf('.') + 1).replaceAll("Check$", "");
  }
}
