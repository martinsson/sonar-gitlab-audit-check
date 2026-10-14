package ratchet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Element;

/**
 * Numeric limits, keyed so that two versions can be compared. A key ends in {@code .minimum} (may
 * not drop), {@code .maximum} (may not rise) or {@code .module} (may not disappear).
 */
final class Limits {

  private static final Pattern POM_LIMIT =
      Pattern.compile("<([\\w.-]+\\.(?:minimum|maximum))>([^<]*)</\\1>");

  /** Written without \w or \d so that git grep -E understands it too. */
  static final String MAX_CONSTANT = "static final int (MAX_[A-Z0-9_]+) = ([0-9]+);";

  private static final Pattern MAX_CONSTANT_PATTERN = Pattern.compile(MAX_CONSTANT);

  private Limits() {}

  /** pom properties named *.minimum or *.maximum. */
  static Map<String, String> fromPom(String pom) {
    Map<String, String> limits = new TreeMap<>();
    Matcher m = POM_LIMIT.matcher(pom);
    while (m.find()) {
      limits.put(m.group(1), m.group(2).trim());
    }
    return limits;
  }

  /** Every Checkstyle module, and every module property whose name contains "max". */
  static Map<String, String> fromCheckstyle(String config) throws IOException {
    Map<String, String> limits = new TreeMap<>();
    if (!config.isBlank()) {
      collectModule(Xml.parse(config).getDocumentElement(), "", limits);
    }
    return limits;
  }

  private static void collectModule(Element module, String parent, Map<String, String> limits) {
    String path = parent + "/" + module.getAttribute("name");
    limits.put(path + ".module", "present");
    for (Element property : Xml.children(module, "property")) {
      String name = property.getAttribute("name");
      if (name.toLowerCase(Locale.ROOT).contains("max")) {
        limits.put(path + "." + name + ".maximum", property.getAttribute("value"));
      }
    }
    for (Element child : Xml.children(module, "module")) {
      collectModule(child, path, limits);
    }
  }

  /** Constants such as ArchitectureTest.MAX_CLASSES_PER_PACKAGE. */
  static Map<String, String> fromConstants(String file, List<String> lines) {
    Map<String, String> limits = new TreeMap<>();
    for (String line : lines) {
      Matcher m = MAX_CONSTANT_PATTERN.matcher(line);
      if (m.find()) {
        limits.put(file + " " + m.group(1) + ".maximum", m.group(2));
      }
    }
    return limits;
  }

  /** What got looser from {@code before} to {@code now}, one sentence per problem. */
  static List<String> loosened(String where, Map<String, String> before, Map<String, String> now) {
    List<String> problems = new ArrayList<>();
    for (Map.Entry<String, String> limit : before.entrySet()) {
      String key = limit.getKey();
      String old = limit.getValue();
      String current = now.get(key);
      String label = where + ": " + label(key);
      if (current == null) {
        problems.add(label + " was removed");
      } else if (key.endsWith(".minimum") && number(current) < number(old)) {
        problems.add(label + " lowered from " + old + " to " + current);
      } else if (key.endsWith(".maximum") && number(current) > number(old)) {
        problems.add(label + " raised from " + old + " to " + current);
      }
    }
    return problems;
  }

  /** pom property names keep their suffix; other keys read better without it. */
  static String label(String key) {
    boolean pomProperty = !key.contains("/") && !key.contains(" ");
    return pomProperty ? key : key.replaceAll("\\.(minimum|maximum|module)$", "");
  }

  private static double number(String value) {
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return Double.NaN; // not a number: never reported as looser
    }
  }
}
