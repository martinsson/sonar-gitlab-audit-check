package ratchet;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares the working tree with a base commit and lists everything that got looser. A commit in
 * base..HEAD whose message has a {@code Ratchet-Override:} line turns failures into warnings: a
 * human decision, visible in history and review.
 */
final class RatchetCheck {

  static final String POM = "pom.xml";
  static final String CHECKSTYLE_CONFIG = "config/checkstyle/checkstyle.xml";
  static final String PMD_RULESET = "config/pmd/ruleset.xml";

  /** Markers that switch a check off in source code. Their count may not grow. */
  static final List<String> SUPPRESSION_MARKERS =
      List.of("@SuppressWarnings", "NOPMD", "CHECKSTYLE:OFF", "@Disabled", "@ArchIgnore");

  private static final Pattern PMD_RULE_REF = Pattern.compile("ref=\"([^\"]+)\"");
  private static final Pattern ERROR_PRONE_OFF =
      Pattern.compile("(-Xep:\\w+:(?:OFF|WARN)|-XepDisableAllChecks|-XepDisableAllWarnings)");
  private static final int MAX_PRINTED = 40;

  private final String baseRef;
  private final GitTree base;
  private final GitTree now = GitTree.workingTree();
  private final PrintStream out;

  RatchetCheck(String baseRef, PrintStream out) {
    this.baseRef = baseRef;
    this.base = GitTree.commit(baseRef);
    this.out = out;
  }

  int run() throws IOException {
    List<String> problems = new ArrayList<>();
    problems.addAll(baselineGrowth());
    problems.addAll(
        Limits.loosened(POM, Limits.fromPom(base.text(POM)), Limits.fromPom(now.text(POM))));
    problems.addAll(
        Limits.loosened(
            CHECKSTYLE_CONFIG,
            Limits.fromCheckstyle(base.text(CHECKSTYLE_CONFIG)),
            Limits.fromCheckstyle(now.text(CHECKSTYLE_CONFIG))));
    problems.addAll(constants());
    removed(PMD_RULESET, PMD_RULE_REF)
        .forEach(r -> problems.add(PMD_RULESET + ": rule " + r + " was removed"));
    added(POM, ERROR_PRONE_OFF)
        .forEach(f -> problems.add("pom.xml: Error Prone check switched off with " + f));
    problems.addAll(suppressionMarkers());
    return report(problems);
  }

  private int report(List<String> problems) throws IOException {
    if (problems.isEmpty()) {
      return 0;
    }
    boolean overridden =
        GitTree.run("git", "log", "--format=%B", baseRef + "..HEAD").stream()
            .anyMatch(line -> line.startsWith("Ratchet-Override:"));
    String prefix = overridden ? "RATCHET (overridden): " : "RATCHET: ";
    problems.stream().limit(MAX_PRINTED).forEach(p -> out.println(prefix + p));
    if (problems.size() > MAX_PRINTED) {
      out.println(prefix + "... and " + (problems.size() - MAX_PRINTED) + " more");
    }
    if (overridden) {
      return 0;
    }
    out.println(
        "HINT: a gate got looser than on "
            + baseRef
            + ". Fix the new problem instead of recording it; never raise a limit or lower a"
            + " minimum. If a human decided otherwise, add a 'Ratchet-Override: <reason>' line"
            + " to the commit message.");
    return 1;
  }

  private List<String> baselineGrowth() throws IOException {
    if (base.files(BaselineFiles.DIR).isEmpty()) {
      return List.of(); // adopting the ratchet: the first baseline is the starting point
    }
    Set<String> added = new TreeSet<>(BaselineEntries.of(now));
    added.removeAll(BaselineEntries.of(base));
    return added.stream().map(entry -> "new baseline entry " + entry).toList();
  }

  private List<String> constants() throws IOException {
    Map<String, String> before = new TreeMap<>();
    Map<String, String> current = new TreeMap<>();
    for (String file :
        GitTree.run("git", "grep", "-l", "--untracked", "-E", Limits.MAX_CONSTANT, "--", "src")) {
      before.putAll(Limits.fromConstants(file, base.lines(file)));
      current.putAll(Limits.fromConstants(file, now.lines(file)));
    }
    before.keySet().retainAll(current.keySet());
    return Limits.loosened("tests", before, current);
  }

  private Set<String> removed(String file, Pattern pattern) throws IOException {
    Set<String> gone = matches(base.text(file), pattern);
    gone.removeAll(matches(now.text(file), pattern));
    return gone;
  }

  private Set<String> added(String file, Pattern pattern) throws IOException {
    Set<String> fresh = matches(now.text(file), pattern);
    fresh.removeAll(matches(base.text(file), pattern));
    return fresh;
  }

  private List<String> suppressionMarkers() throws IOException {
    List<String> problems = new ArrayList<>();
    for (String marker : SUPPRESSION_MARKERS) {
      int before = base.count(marker);
      int current = now.count(marker);
      if (current > before) {
        problems.add(
            "'%s' in src/ and tools/ went from %d to %d (grep -rn '%s' src tools)"
                .formatted(marker, before, current, marker));
      }
    }
    return problems;
  }

  private static Set<String> matches(String text, Pattern pattern) {
    Set<String> found = new TreeSet<>();
    Matcher m = pattern.matcher(text);
    while (m.find()) {
      found.add(m.group(1));
    }
    return found;
  }
}
