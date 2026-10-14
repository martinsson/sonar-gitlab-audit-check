package ratchet;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Every violation recorded in config/baseline, one comparable string each. */
final class BaselineEntries {

  private static final Pattern COMMA = Pattern.compile(",");

  /** An ArchUnit violation starts on an unindented line; its continuation lines are indented. */
  private static final Pattern VIOLATION_START = Pattern.compile("\n(?! )");

  private BaselineEntries() {}

  static Set<String> of(GitTree tree) throws IOException {
    Set<String> entries = new TreeSet<>();
    for (String line : tree.lines(BaselineFiles.CHECKSTYLE)) {
      if (line.trim().startsWith("<suppress ")) {
        entries.add("(checkstyle) " + line.trim());
      }
    }
    Properties pmd = new Properties();
    pmd.load(new StringReader(tree.text(BaselineFiles.PMD)));
    for (String type : pmd.stringPropertyNames()) {
      COMMA
          .splitAsStream(pmd.getProperty(type, ""))
          .forEach(rule -> entries.add("(pmd) " + type + " " + rule.trim()));
    }
    for (String line : tree.lines(BaselineFiles.CPD)) {
      if (!line.isBlank() && !line.startsWith("#")) {
        entries.add("(cpd) " + line.trim());
      }
    }
    entries.addAll(archUnit(tree));
    return entries;
  }

  private static Set<String> archUnit(GitTree tree) throws IOException {
    Set<String> entries = new TreeSet<>();
    for (String file : tree.files(BaselineFiles.ARCHUNIT)) {
      if (file.endsWith("stored.rules")) {
        continue;
      }
      String text = tree.text(file).replace("\\\n", "\n");
      VIOLATION_START
          .splitAsStream(text)
          .filter(violation -> !violation.isBlank())
          .map(violation -> violation.lines().findFirst().orElse("").trim())
          .forEach(firstLine -> entries.add("(archunit) " + firstLine));
    }
    return entries;
  }
}
