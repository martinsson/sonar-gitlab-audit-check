package ch.ge.edg.sonar.gitlab.audit.cli;

import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Fmt;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Keys;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * L'inventaire GitLab complet de {@link SonarToGitlab}, indexé par chemin, identifiant et morceaux
 * de chemin : de quoi jouer les méthodes dérivées sans parcourir tout l'inventaire.
 */
final class GitlabInventoryIndex {

  /** Ce que SonarQube fabrique en important un dépôt GitLab : chemin, puis un identifiant. */
  private static final Pattern IMPORT_KEY =
      Pattern.compile(
          "^(.+?)_(?:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|A[A-Za-z0-9_-]{19})$");

  private static final Pattern PAREN_NAME = Pattern.compile("^\\(([^)]+)\\)");

  /** Une méthode dérivée et les dépôts qu'elle désigne pour un projet Sonar. */
  record Step(String label, Set<Integer> candidates) {}

  private final List<Csv.Row> rows;
  private final Map<String, Integer> byPath = new HashMap<>();
  private final Map<String, Integer> byId = new HashMap<>();
  private final Map<String, Set<Integer>> idxPath = new HashMap<>();
  private final Map<String, Set<Integer>> idxSuffix = new HashMap<>();
  private final Map<String, Set<Integer>> idxLast = new HashMap<>();

  GitlabInventoryIndex(List<Csv.Row> rows) {
    this.rows = rows;
    for (int i = 0; i < rows.size(); i++) index(i, rows.get(i));
  }

  private void index(int i, Csv.Row g) {
    String path = g.str("path");
    byPath.putIfAbsent(path, i);
    if (!g.str("id").isEmpty()) byId.putIfAbsent(g.str("id"), i);
    String[] segs = path.split("/");
    put(idxPath, Keys.normalise(path), i);
    // Deux segments ou plus, sans le namespace de tête : la clé reprend
    // souvent le sous-groupe et le dépôt, rarement le département.
    for (int k = 2; k < segs.length; k++) {
      List<String> tail = List.of(segs).subList(segs.length - k, segs.length);
      put(idxSuffix, Keys.normalise(String.join("-", tail)), i);
      put(
          idxSuffix,
          Keys.normalise(String.join("-", tail.stream().map(Keys::stripTicketNumber).toList())),
          i);
    }
    String last = segs[segs.length - 1];
    for (String v :
        List.of(
            last,
            Keys.stripTicketNumber(last),
            g.str("name"),
            Keys.stripTicketNumber(g.str("name")))) {
      put(idxLast, Keys.normalise(v), i);
    }
  }

  private static void put(Map<String, Set<Integer>> idx, String k, int i) {
    if (!k.isEmpty()) idx.computeIfAbsent(k, x -> new LinkedHashSet<>()).add(i);
  }

  private static Set<Integer> get(Map<String, Set<Integer>> idx, String k) {
    return k.isEmpty() ? Set.of() : idx.getOrDefault(k, Set.of());
  }

  Csv.Row row(int g) {
    return rows.get(g);
  }

  String path(int g) {
    return rows.get(g).str("path");
  }

  /** « chemin (score) » : un candidat tel que le CSV le montre. */
  String described(int g, double score) {
    return "%s (%s)".formatted(path(g), Fmt.score(score));
  }

  @Nullable Integer byPath(String path) {
    return byPath.get(path);
  }

  @Nullable Integer byId(String id) {
    return byId.get(id);
  }

  /** Les méthodes dérivées, dans l'ordre : chacune avec les dépôts qu'elle désigne. */
  List<Step> steps(Csv.Row r) {
    String key = r.str("key"), name = r.str("name");
    String nk = Keys.normalise(key);
    List<Step> steps = new ArrayList<>();
    Matcher m = IMPORT_KEY.matcher(key);
    steps.add(
        new Step(
            "clé d'import GitLab (chemin_id) = chemin GitLab",
            m.matches() ? get(idxPath, Keys.normalise(m.group(1))) : Set.of()));
    steps.add(new Step("clé normalisée = chemin GitLab", get(idxPath, nk)));
    steps.add(new Step("clé normalisée = fin du chemin GitLab", get(idxSuffix, nk)));
    steps.add(new Step("nom court de la clé = dernier segment / nom GitLab", byShortKey(key)));
    steps.add(new Step("nom Sonar = dernier segment / nom GitLab", bySonarName(name)));
    return steps;
  }

  private Set<Integer> byShortKey(String key) {
    List<String> variants =
        key.contains(":")
            ? List.of(key.substring(key.indexOf(':') + 1), Keys.extractShortName(key))
            : List.of(key, Keys.stripTicketNumber(key));
    Set<Integer> c = new LinkedHashSet<>();
    for (String v : variants) {
      c.addAll(get(idxLast, Keys.normalise(v)));
      c.addAll(get(idxSuffix, Keys.normalise(v)));
    }
    return c;
  }

  private Set<Integer> bySonarName(String name) {
    Matcher paren = PAREN_NAME.matcher(name);
    Set<Integer> c = new LinkedHashSet<>();
    for (String v :
        List.of(paren.find() ? paren.group(1) : "", name, Keys.stripTicketNumber(name))) {
      String n = Keys.normalise(v);
      if (n.isEmpty()) continue;
      c.addAll(get(idxLast, n));
      c.addAll(get(idxSuffix, n));
      c.addAll(get(idxPath, n));
    }
    return c;
  }

  static boolean original(Csv.Row g) {
    return !g.flag("fork") && !g.flag("mirror");
  }

  static long commits(Csv.Row g) {
    Double d = g.num("commits_window");
    return d == null ? 0 : d.longValue();
  }

  /** Une copie d'origine avant un fork ou un miroir, puis la plus active, puis la plus récente. */
  Comparator<Integer> activityOrder() {
    return Comparator.comparing((Integer g) -> original(rows.get(g)))
        .thenComparingLong(g -> commits(rows.get(g)))
        .thenComparing(g -> rows.get(g).str("last_activity_at"));
  }
}
