package ch.ge.edg.sonar.gitlab.audit.cli.cross;

import ch.ge.edg.sonar.gitlab.audit.cli.cross.SonarToGitlab.Check;
import ch.ge.edg.sonar.gitlab.audit.cli.cross.SonarToGitlab.Pair;
import ch.ge.edg.sonar.gitlab.audit.cli.cross.SonarToGitlab.Result;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.matching.Confidence;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Le sort de chaque projet Sonar pendant une passe de {@link SonarToGitlab} : décidé par la
 * première méthode qui trouve, ou laissé sans dépôt avec sa raison.
 */
final class SonarFates {

  private final List<Csv.Row> sonar;
  private final Pair[] pairs;
  private final Map<String, Integer> byKey = new HashMap<>();

  /** Homonymes qu'aucune méthode n'a départagés : la passe sur les noms ne les reprend pas. */
  final Map<Integer, String> ambiguous = new HashMap<>();

  /** Ce que la passe sur les noms a écarté, et pourquoi. */
  final Map<Integer, String> refused = new HashMap<>();

  /** Les méthodes dérivées rejouées sur les paires exactes. */
  final List<Check> checks = new ArrayList<>();

  SonarFates(List<Csv.Row> sonar) {
    this.sonar = sonar;
    this.pairs = new Pair[sonar.size()];
    for (int s = 0; s < sonar.size(); s++) byKey.putIfAbsent(sonar.get(s).str("key"), s);
  }

  int size() {
    return sonar.size();
  }

  Csv.Row sonar(int s) {
    return sonar.get(s);
  }

  @Nullable Integer indexOf(String key) {
    return byKey.get(key);
  }

  /** {@code null} tant qu'aucune méthode n'a décidé. */
  @Nullable Pair get(int s) {
    return pairs[s];
  }

  void set(int s, Pair p) {
    pairs[s] = p;
  }

  /** Les paires décidées jusqu'ici, dans l'ordre des projets Sonar. */
  List<Pair> decided() {
    List<Pair> out = new ArrayList<>();
    for (Pair p : pairs) if (p != null) out.add(p);
    return out;
  }

  /** Une ligne par projet Sonar : sa paire, ou l'absence et sa raison. */
  Result result() {
    List<Pair> out = new ArrayList<>();
    for (int s = 0; s < sonar.size(); s++) {
      Pair p = pairs[s];
      out.add(p != null ? p : unmatched(s));
    }
    return new Result(out, checks);
  }

  private Pair unmatched(int s) {
    String why = ambiguous.getOrDefault(s, refused.getOrDefault(s, "aucun candidat"));
    int nl = why.indexOf('\n');
    return new Pair(
        sonar.get(s),
        null,
        SonarToGitlab.NONE,
        Confidence.NONE,
        null,
        nl < 0 ? "" : why.substring(nl + 1),
        nl < 0 ? why : why.substring(0, nl));
  }
}
