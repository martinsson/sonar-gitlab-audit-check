package ch.ge.edg.sonar.gitlab.audit.cli;

import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Confidence;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Fmt;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Keys;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Match;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.NameSpec;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Spec;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.naming.NameMatcher;
import com.opencsv.CSVWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chaque projet Sonar contre TOUT GitLab, pas seulement la sélection.
 *
 * <p>Le croisement apparie les ~200 projets que l'audit GitLab a retenus : une ligne par projet
 * GitLab sélectionné. Vu depuis Sonar, c'est un dénominateur faux — un projet Sonar dont le dépôt
 * n'a pas été tiré y reste sans homologue, indiscernable d'un projet Sonar sans dépôt du tout. Ici,
 * la question part de l'autre côté : pour chaque projet Sonar, quel dépôt de l'inventaire GitLab
 * complet ?
 *
 * <p>Mêmes principes que le croisement, et mêmes outils :
 *
 * <p>1. les paires sûres du croisement (clé de la CI, clé du pom, saisies à la main) sont reprises
 * telles quelles, et un « rejete » n'est jamais reproposé ; 2. la liaison DevOps de Sonar, cette
 * fois contre tout l'inventaire ; 3. des méthodes dérivées, dans l'ordre, qui n'apparient que si
 * elles ne désignent qu'un dépôt : clé d'import GitLab, clé = chemin, clé = fin du chemin, nom
 * court de la clé = dernier segment ou nom GitLab, nom Sonar = dernier segment. Plusieurs dépôts du
 * même nom (copies, forks, espaces personnels) ne donnent qu'une suggestion, départagée par la
 * ressemblance des noms ou, à défaut, par l'activité ; 4. la ressemblance de noms de {@link
 * NameMatcher} sur ce qui reste, un pour un, avec le seuil et la marge du croisement : en
 * suggestion.
 *
 * <p>Les méthodes dérivées sont aussi jouées sur les paires exactes, pour dire combien de fois
 * elles auraient trouvé la même chose : c'est ce qui permet de les compter comme sûres.
 */
final class SonarToGitlab {

  /** Ce que SonarQube fabrique en important un dépôt GitLab : chemin, puis un identifiant. */
  private static final Pattern IMPORT_KEY =
      Pattern.compile(
          "^(.+?)_(?:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|A[A-Za-z0-9_-]{19})$");

  private static final Pattern PAREN_NAME = Pattern.compile("^\\(([^)]+)\\)");

  static final String NONE = "aucune";

  /** Le sort d'un projet Sonar. {@code gitlab} est null quand rien n'a été trouvé. */
  record Pair(
      Csv.Row sonar,
      Csv.Row gitlab,
      String method,
      Confidence confidence,
      Double score,
      String candidates,
      String reason) {

    boolean joined() {
      return gitlab != null
          && (confidence == Confidence.EXACT
              || confidence == Confidence.DERIVED
              || confidence == Confidence.MANUEL);
    }

    String key() {
      return sonar.str("key");
    }

    String path() {
      return gitlab == null ? "" : gitlab.str("path");
    }
  }

  /** Une méthode dérivée jouée sur une paire exacte : d'accord ou non. */
  record Check(String method, boolean agrees) {}

  record Result(List<Pair> pairs, List<Check> checks) {

    /** Les dépôts GitLab qu'un projet Sonar désigne sûrement. */
    Set<String> joinedPaths() {
      Set<String> s = new HashSet<>();
      for (Pair p : pairs) if (p.joined()) s.add(p.path());
      return s;
    }
  }

  private record Step(String label, Set<Integer> candidates) {}

  private final Csv.Table sonar;
  private final List<Csv.Row> gitlab;
  private final double threshold;
  private final double margin;
  private final Map<String, Integer> byPath = new HashMap<>();
  private final Map<String, Integer> byId = new HashMap<>();
  private final Map<String, Set<Integer>> idxPath = new HashMap<>();
  private final Map<String, Set<Integer>> idxSuffix = new HashMap<>();
  private final Map<String, Set<Integer>> idxLast = new HashMap<>();
  private final NameMatcher matcher;

  private SonarToGitlab(Csv.Table sonar, Csv.Table inventory, double threshold, double margin) {
    this.sonar = sonar;
    this.gitlab = inventory.rows();
    this.threshold = threshold;
    this.margin = margin;
    for (int i = 0; i < gitlab.size(); i++) {
      Csv.Row g = gitlab.get(i);
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
    this.matcher = new NameSpec("path", List.of("key", "name"), true).matcher(inventory, sonar);
  }

  private static void put(Map<String, Set<Integer>> idx, String k, int i) {
    if (!k.isEmpty()) idx.computeIfAbsent(k, x -> new LinkedHashSet<>()).add(i);
  }

  /**
   * @param fromCross les paires du croisement (gauche = GitLab, droite = Sonar)
   * @param rejected les paires refusées à la main, « clé\nchemin » : jamais reproposées
   * @param rejectedPaths les dépôts refusés sans clé connue : plus de suggestion de noms vers eux
   */
  static Result map(
      Csv.Table sonar,
      Csv.Table inventory,
      List<Match> fromCross,
      Set<String> rejected,
      Set<String> rejectedPaths,
      double threshold,
      double margin) {
    return new SonarToGitlab(sonar, inventory, threshold, margin)
        .run(fromCross, rejected, rejectedPaths);
  }

  private Result run(List<Match> fromCross, Set<String> rejected, Set<String> rejectedPaths) {
    List<Csv.Row> sq = sonar.rows();
    Pair[] out = new Pair[sq.size()];
    Map<String, Integer> sonarIndex = new HashMap<>();
    for (int s = 0; s < sq.size(); s++) sonarIndex.putIfAbsent(sq.get(s).str("key"), s);

    // 1. Ce que le croisement tient déjà pour sûr.
    Map<String, List<String>> crossPaths = new LinkedHashMap<>();
    Map<String, Match> crossFirst = new HashMap<>();
    for (Match m : fromCross) {
      if (!m.joined()) continue;
      String key = m.right().str("key");
      crossPaths.computeIfAbsent(key, k -> new ArrayList<>()).add(m.left().str("path"));
      crossFirst.putIfAbsent(key, m);
    }
    crossPaths.forEach(
        (key, paths) -> {
          Integer s = sonarIndex.get(key);
          if (s == null) return;
          Match m = crossFirst.get(key);
          Integer g = byPath.get(paths.get(0));
          Csv.Row row = g != null ? gitlab.get(g) : m.left();
          out[s] =
              new Pair(
                  sq.get(s),
                  row,
                  "croisement : " + m.label(),
                  m.confidence(),
                  null,
                  paths.size() > 1 ? "clé partagée par " + String.join(", ", paths) : "",
                  "");
        });

    // 2. La liaison DevOps, contre tout l'inventaire.
    for (int s = 0; s < sq.size(); s++) {
      Csv.Row r = sq.get(s);
      if (out[s] != null || !"gitlab".equals(r.str("alm"))) continue;
      Integer g = byId.get(r.str("alm_repository"));
      if (g != null && !rejected.contains(r.str("key") + "\n" + gitlab.get(g).str("path"))) {
        out[s] =
            new Pair(
                r, gitlab.get(g), "liaison DevOps Sonar → GitLab", Confidence.EXACT, null, "", "");
      }
    }

    // 3. Les méthodes dérivées : appliquées à ce qui reste, contrôlées sur les paires exactes.
    List<Check> checks = new ArrayList<>();
    Map<Integer, String> ambiguous = new HashMap<>();
    for (int s = 0; s < sq.size(); s++) {
      Csv.Row r = sq.get(s);
      boolean exact = out[s] != null && out[s].confidence() == Confidence.EXACT;
      if (out[s] != null && !exact) continue;
      final int si = s;
      for (Step step : steps(r)) {
        Set<Integer> cands = new LinkedHashSet<>(step.candidates());
        cands.removeIf(g -> rejected.contains(r.str("key") + "\n" + gitlab.get(g).str("path")));
        if (cands.isEmpty()) continue;
        if (cands.size() == 1) {
          int g = cands.iterator().next();
          if (exact)
            checks.add(new Check(step.label(), gitlab.get(g).str("path").equals(out[s].path())));
          else
            out[s] =
                new Pair(
                    r,
                    gitlab.get(g),
                    step.label(),
                    Confidence.DERIVED,
                    matcher.score(g, si),
                    "",
                    "");
          break;
        }
        List<Integer> ranked = new ArrayList<>(cands);
        ranked.sort(Comparator.comparingDouble((Integer g) -> matcher.score(g, si)).reversed());
        if (exact) {
          checks.add(
              new Check(
                  step.label() + " (homonymes)",
                  gitlab.get(ranked.get(0)).str("path").equals(out[s].path())));
          break;
        }
        String names =
            String.join(
                ", ",
                ranked.stream()
                    .limit(5)
                    .map(
                        g ->
                            "%s (%s)"
                                .formatted(
                                    gitlab.get(g).str("path"), Fmt.score(matcher.score(g, si))))
                    .toList());
        Integer pick = null;
        String how = null;
        if (matcher.score(ranked.get(0), si) - matcher.score(ranked.get(1), si) >= margin) {
          pick = ranked.get(0);
          how = "ressemblance des noms";
        } else {
          // Le même nom à plusieurs endroits : la copie sur laquelle on
          // travaille vraiment est la plus probable. Deux copies mortes
          // ne se départagent pas.
          List<Integer> byActivity = new ArrayList<>(cands);
          byActivity.sort(activityOrder().reversed());
          Csv.Row a = gitlab.get(byActivity.get(0)), b = gitlab.get(byActivity.get(1));
          boolean tie = original(a) == original(b) && commits(a) == commits(b) && commits(a) == 0;
          if (!tie) {
            pick = byActivity.get(0);
            how = "activité GitLab";
          }
        }
        if (pick != null && rejectedPaths.contains(gitlab.get(pick).str("path"))) pick = null;
        if (pick == null)
          ambiguous.put(s, "homonymes non départagés (" + step.label() + ")\n" + names);
        else
          out[s] =
              new Pair(
                  r,
                  gitlab.get(pick),
                  "%s (%d homonymes, départagés par %s)".formatted(step.label(), cands.size(), how),
                  Confidence.SUGGESTION,
                  matcher.score(pick, si),
                  names,
                  "");
        break;
      }
    }

    // 4. La ressemblance de noms, un pour un, sur ce qui reste.
    Set<Integer> taken = new HashSet<>();
    for (Pair p : out)
      if (p != null && p.gitlab() != null) {
        Integer g = byPath.get(p.path());
        if (g != null) taken.add(g);
      }
    record Proposal(int sonar, int gitlab, double score, String second) {}
    List<Proposal> proposals = new ArrayList<>();
    Map<Integer, String> refused = new HashMap<>();
    for (int s = 0; s < sq.size(); s++) {
      if (out[s] != null || ambiguous.containsKey(s)) continue;
      String key = sq.get(s).str("key");
      List<NameMatcher.Scored> ranked =
          matcher.rankSources(
              s,
              g ->
                  !taken.contains(g)
                      && !rejectedPaths.contains(gitlab.get(g).str("path"))
                      && !rejected.contains(key + "\n" + gitlab.get(g).str("path")));
      if (ranked.isEmpty()) {
        refused.put(s, "aucun candidat");
        continue;
      }
      NameMatcher.Scored best = ranked.get(0);
      NameMatcher.Scored next = ranked.size() > 1 ? ranked.get(1) : null;
      String second =
          next == null
              ? ""
              : "%s (%s)".formatted(gitlab.get(next.index()).str("path"), Fmt.score(next.score()));
      String why =
          best.score() < threshold
              ? "sous le seuil"
              : next != null && best.score() - next.score() < margin ? "ambigu" : null;
      String bestText =
          "%s (%s)".formatted(gitlab.get(best.index()).str("path"), Fmt.score(best.score()));
      if (why != null)
        refused.put(s, why + "\n" + bestText + (second.isEmpty() ? "" : ", " + second));
      else proposals.add(new Proposal(s, best.index(), best.score(), second));
    }
    proposals.sort(Comparator.comparingDouble(Proposal::score).reversed());
    Map<Integer, Integer> claimedBy = new HashMap<>();
    for (Proposal p : proposals) {
      Integer winner = claimedBy.putIfAbsent(p.gitlab(), p.sonar());
      if (winner == null) {
        out[p.sonar()] =
            new Pair(
                sq.get(p.sonar()),
                gitlab.get(p.gitlab()),
                Spec.NAMES_LABEL,
                Confidence.SUGGESTION,
                p.score(),
                p.second(),
                "");
      } else {
        refused.put(
            p.sonar(),
            "déjà proposé à "
                + sq.get(winner).str("key")
                + "\n"
                + "%s (%s)".formatted(gitlab.get(p.gitlab()).str("path"), Fmt.score(p.score())));
      }
    }

    List<Pair> pairs = new ArrayList<>();
    for (int s = 0; s < sq.size(); s++) {
      if (out[s] != null) {
        pairs.add(out[s]);
        continue;
      }
      String why = ambiguous.getOrDefault(s, refused.getOrDefault(s, "aucun candidat"));
      int nl = why.indexOf('\n');
      pairs.add(
          new Pair(
              sq.get(s),
              null,
              NONE,
              Confidence.NONE,
              null,
              nl < 0 ? "" : why.substring(nl + 1),
              nl < 0 ? why : why.substring(0, nl)));
    }
    return new Result(pairs, checks);
  }

  /** Les méthodes dérivées, dans l'ordre : chacune avec les dépôts qu'elle désigne. */
  private List<Step> steps(Csv.Row r) {
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

    List<String> variants =
        key.contains(":")
            ? List.of(key.substring(key.indexOf(':') + 1), Keys.extractShortName(key))
            : List.of(key, Keys.stripTicketNumber(key));
    Set<Integer> c = new LinkedHashSet<>();
    for (String v : variants) {
      c.addAll(get(idxLast, Keys.normalise(v)));
      c.addAll(get(idxSuffix, Keys.normalise(v)));
    }
    steps.add(new Step("nom court de la clé = dernier segment / nom GitLab", c));

    Matcher paren = PAREN_NAME.matcher(name);
    c = new LinkedHashSet<>();
    for (String v :
        List.of(paren.find() ? paren.group(1) : "", name, Keys.stripTicketNumber(name))) {
      String n = Keys.normalise(v);
      if (n.isEmpty()) continue;
      c.addAll(get(idxLast, n));
      c.addAll(get(idxSuffix, n));
      c.addAll(get(idxPath, n));
    }
    steps.add(new Step("nom Sonar = dernier segment / nom GitLab", c));
    return steps;
  }

  private static Set<Integer> get(Map<String, Set<Integer>> idx, String k) {
    return k.isEmpty() ? Set.of() : idx.getOrDefault(k, Set.of());
  }

  private static boolean original(Csv.Row g) {
    return !g.flag("fork") && !g.flag("mirror");
  }

  private static long commits(Csv.Row g) {
    Double d = g.num("commits_window");
    return d == null ? 0 : d.longValue();
  }

  /** Une copie d'origine avant un fork ou un miroir, puis la plus active, puis la plus récente. */
  private Comparator<Integer> activityOrder() {
    return Comparator.comparing((Integer g) -> original(gitlab.get(g)))
        .thenComparingLong(g -> commits(gitlab.get(g)))
        .thenComparing(g -> gitlab.get(g).str("last_activity_at"));
  }

  // ----------------------------------------------------------------------
  // Sortie
  // ----------------------------------------------------------------------

  static final List<String> HEADER =
      List.of(
          "sonar_key",
          "sonar_name",
          "gitlab_path",
          "gitlab_id",
          "methode",
          "confiance",
          "score",
          "autres_candidats",
          "raison",
          "cles_sonar_sur_ce_projet",
          "dans_selection",
          "gl_commits_window",
          "gl_last_activity_at",
          "gl_exclu",
          "sq_analysisDate",
          "sq_ncloc");

  static void write(Result r, Path file, boolean comma) throws IOException {
    Path dir = file.toAbsolutePath().getParent();
    if (dir != null) Files.createDirectories(dir);
    Map<String, Integer> perPath = new HashMap<>();
    for (Pair p : r.pairs()) if (p.gitlab() != null) perPath.merge(p.path(), 1, Integer::sum);
    try (CSVWriter w = Csv.writer(file, comma)) {
      w.writeNext(HEADER.toArray(String[]::new));
      for (Pair p : r.pairs()) {
        Csv.Row g = p.gitlab();
        w.writeNext(
            new String[] {
              p.key(),
              p.sonar().str("name"),
              p.path(),
              g == null ? "" : g.str("id"),
              p.method(),
              p.confidence().code(),
              p.score() == null ? "" : Fmt.score(p.score()),
              p.candidates(),
              p.reason(),
              g == null ? "" : String.valueOf(perPath.get(p.path())),
              g == null ? "" : g.flag("selectionne") ? "oui" : "non",
              g == null ? "" : g.str("commits_window"),
              g == null ? "" : g.str("last_activity_at"),
              g == null ? "" : g.str("exclu"),
              p.sonar().str("analysisDate"),
              p.sonar().str("ncloc")
            });
      }
    }
  }

  /** Ce que la passe a donné, en quelques lignes. */
  static void print(Result r, Path file) {
    Map<String, Integer> byConf = new TreeMap<>();
    int outside = 0, outsideActive = 0;
    for (Pair p : r.pairs()) {
      byConf.merge(p.confidence().code(), 1, Integer::sum);
      if (p.joined() && !p.gitlab().flag("selectionne")) {
        outside++;
        if (commits(p.gitlab()) >= 10) outsideActive++;
      }
    }
    int n = r.pairs().size();
    long joined = r.pairs().stream().filter(Pair::joined).count();
    System.out.printf(
        "  Projets Sonar : %d, dont %d appariés sûrement (%s)%n", n, joined, Fmt.pct(joined, n));
    byConf.forEach((c, k) -> System.out.printf("    %-10s %d%n", c, k));
    System.out.printf(
        "  Appariés à un dépôt hors de la sélection GitLab : %d, dont %d actifs "
            + "(10 commits ou plus)%n",
        outside, outsideActive);
    Map<String, Integer> reasons = new TreeMap<>();
    for (Pair p : r.pairs()) {
      if (p.gitlab() == null)
        reasons.merge(
            p.reason()
                .replaceFirst(" \\(.*", "")
                .replaceFirst("^déjà proposé à .*", "déjà proposé à un autre"),
            1,
            Integer::sum);
    }
    if (!reasons.isEmpty()) {
      System.out.println("  Sans dépôt GitLab, par raison :");
      reasons.forEach((why, k) -> System.out.printf("    %-36s %d%n", why, k));
    }
    if (!r.checks().isEmpty()) {
      long ok = r.checks().stream().filter(Check::agrees).count();
      System.out.printf(
          "  Méthodes dérivées rejouées sur les paires exactes : %d sur %d d'accord%n",
          ok, r.checks().size());
      Map<String, int[]> perMethod = new TreeMap<>();
      for (Check c : r.checks()) {
        int[] a = perMethod.computeIfAbsent(c.method(), x -> new int[2]);
        a[c.agrees() ? 0 : 1]++;
      }
      perMethod.forEach(
          (m, a) ->
              System.out.println(
                  CrossAudit.c(
                      "    %s : %d d'accord, %d non".formatted(m, a[0], a[1]), CrossAudit.DIM)));
    }
    System.out.printf("  Sonar × tout GitLab : %d lignes → %s%n", n, file.toAbsolutePath());
  }
}
