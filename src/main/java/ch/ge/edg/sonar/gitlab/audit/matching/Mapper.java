package ch.ge.edg.sonar.gitlab.audit.matching;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.matching.similarity.NameMatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * L'appariement. Les méthodes sûres dans l'ordre donné — égalité exacte, puis égalité après
 * normalisation — puis la ressemblance de noms sur ce qui reste, en suggestion seulement. Les liens
 * sont lus et jamais utilisés pour apparier.
 */
final class Mapper {

  private final Spec spec;
  private final List<KeyMethod> available;
  private final List<Csv.Row> rights;

  /** Par id de méthode : valeur → lignes de droite qui la portent, dans l'ordre du fichier. */
  private final Map<String, Map<String, List<Integer>>> indexes = new HashMap<>();

  private final Set<Integer> taken = new HashSet<>();
  private final List<Integer> unresolved = new ArrayList<>();

  private Mapper(Spec spec, List<KeyMethod> available, List<Csv.Row> rights) {
    this.spec = spec;
    this.available = available;
    this.rights = rights;
  }

  /** Les méthodes applicables, les autres avec leur raison, et la spécification de noms retenue. */
  private record Columns(
      List<KeyMethod> available, List<String> unavailable, @Nullable NameSpec names) {

    static Columns check(Spec spec, Csv.Table left, Csv.Table right) {
      List<KeyMethod> available = new ArrayList<>();
      List<String> unavailable = new ArrayList<>();
      for (KeyMethod m : spec.methods()) {
        List<String> missing = new ArrayList<>();
        if (!left.has(m.leftColumn())) missing.add("gauche : " + m.leftColumn());
        if (!right.has(m.rightColumn())) missing.add("droite : " + m.rightColumn());
        String where = m.whereColumn();
        if (where != null && !right.has(where)) missing.add("droite : " + where);
        if (missing.isEmpty()) available.add(m);
        else unavailable.add(m.label() + " (colonne absente, " + String.join(", ", missing) + ")");
      }
      NameSpec names = spec.names();
      if (names != null
          && (!left.has(names.leftColumn())
              || names.rightColumns().stream().noneMatch(right::has))) {
        unavailable.add(Spec.NAMES_LABEL + " (colonne absente)");
        names = null;
      }
      return new Columns(available, unavailable, names);
    }
  }

  public static Result map(Spec spec, Csv.Table left, Csv.Table right) {
    Columns columns = Columns.check(spec, left, right);
    List<Csv.Row> rights = right.rows();
    Map<String, Integer> rightIndex = new HashMap<>();
    for (int j = 0; j < rights.size(); j++) {
      String k = rights.get(j).str(spec.rightKey());
      if (!k.isEmpty()) rightIndex.putIfAbsent(k, j);
    }

    Mapper mapper = new Mapper(spec, columns.available(), rights);
    mapper.index();
    List<Match> matches = new ArrayList<>();
    for (int i = 0; i < left.rows().size(); i++) {
      matches.add(mapper.byKeys(i, left.rows().get(i)));
    }

    NameSpec names = columns.names();
    NameMatcher matcher = names == null ? null : names.matcher(left, right);
    if (matcher != null) {
      new NameSuggestions(spec, matcher, rights, rightIndex)
          .suggest(matches, mapper.unresolved, mapper.taken);
    }
    return new Result(
        spec,
        left,
        right,
        columns.available(),
        columns.unavailable(),
        matches,
        matcher,
        rightIndex);
  }

  /**
   * Un index par méthode. Plusieurs lignes de droite peuvent partager une valeur — un monodépôt lié
   * plusieurs fois, un lien recopié : on les garde toutes, dans l'ordre du fichier.
   */
  private void index() {
    for (KeyMethod m : available) {
      Map<String, List<Integer>> idx = new HashMap<>();
      for (int j = 0; j < rights.size(); j++) {
        Csv.Row r = rights.get(j);
        if (r.str(spec.rightKey()).isEmpty() || !m.accepts(r)) continue;
        for (String v : m.rightValues(r.str(m.rightColumn()))) {
          List<Integer> l = idx.computeIfAbsent(v, k -> new ArrayList<>());
          if (!l.contains(j)) l.add(j);
        }
      }
      indexes.put(m.id(), idx);
    }
  }

  private record Hit(int row, KeyMethod by) {}

  /** Chaque méthode dit ce qu'elle désigne ; la première qui joint, sans ambiguïté, l'emporte. */
  private Match byKeys(int i, Csv.Row l) {
    Map<String, List<String>> found = new LinkedHashMap<>();
    Hit hit = null;
    for (KeyMethod m : available) {
      List<Integer> js = lookup(m, m.leftValue(l.str(m.leftColumn())));
      found.put(m.id(), js.stream().map(j -> rights.get(j).str(spec.rightKey())).toList());
      if (hit == null && decides(m, js)) hit = new Hit(js.get(0), m);
    }
    if (hit == null) {
      unresolved.add(i);
      return new Match(i, l, Choice.none(), found, null, null);
    }
    taken.add(hit.row());
    KeyMethod by = hit.by();
    return new Match(
        i,
        l,
        new Choice(rights.get(hit.row()), by.id(), by.label(), by.confidence()),
        found,
        null,
        null);
  }

  /** Une méthode qui joint et désigne quelque chose ; un nom, seulement s'il est unique. */
  private static boolean decides(KeyMethod m, List<Integer> js) {
    return m.joins() && !js.isEmpty() && (m.kind() != Kind.NAME || js.size() == 1);
  }

  private List<Integer> lookup(KeyMethod m, String v) {
    if (v.isEmpty()) return List.of();
    return indexes.getOrDefault(m.id(), Map.of()).getOrDefault(v, List.of());
  }
}
