package ch.ge.edg.sonar.gitlab.audit.cli.mapper;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.matching.similarity.NameMatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** La ressemblance de noms : une colonne à gauche, une ou plusieurs à droite. */
public record NameSpec(String leftColumn, List<String> rightColumns, boolean pathLike) {

  private static final Pattern COMMA = Pattern.compile("\\s*,\\s*");

  /**
   * Lit {@code gauche=droite1,droite2}.
   *
   * @throws IllegalArgumentException si la spécification ne s'écrit pas ainsi
   */
  public static NameSpec parse(String s, boolean pathLike) {
    int eq = s.indexOf('=');
    if (eq <= 0) {
      throw new IllegalArgumentException(
          "--names illisible : « " + s + " » (attendu gauche=droite1,droite2)");
    }
    return new NameSpec(
        s.substring(0, eq).trim(), List.of(COMMA.split(s.substring(eq + 1).trim())), pathLike);
  }

  /** Les documents TF-IDF des deux côtés : une ligne de gauche, une ligne de droite chacune. */
  public NameMatcher matcher(Csv.Table left, Csv.Table right) {
    List<Map<String, Double>> srcTf = new ArrayList<>();
    List<Set<String>> srcNum = new ArrayList<>();
    for (Csv.Row l : left.rows()) {
      Map<String, Double> tf = new HashMap<>();
      srcNum.add(leftDoc(l.str(leftColumn), tf));
      srcTf.add(tf);
    }
    List<Map<String, Double>> dstTf = new ArrayList<>();
    List<Set<String>> dstNum = new ArrayList<>();
    for (Csv.Row r : right.rows()) {
      Map<String, Double> tf = new HashMap<>();
      dstNum.add(rightDoc(r, tf));
      dstTf.add(tf);
    }
    return new NameMatcher(srcTf, srcNum, dstTf, dstNum);
  }

  /** Remplit les termes d'un nom de gauche ; rend les nombres qu'il porte. */
  private Set<String> leftDoc(String v, Map<String, Double> tf) {
    if (!pathLike) {
      NameMatcher.addTokens(tf, v, 1.0);
      return NameMatcher.numbers(v);
    }
    // Le dernier segment compte double : le namespace aide à
    // départager, il ne doit pas suffire à rapprocher.
    // On retire le numéro de ticket du dernier segment pour
    // améliorer la similarité avec la clé Sonar.
    int cut = v.lastIndexOf('/');
    String lastSeg = Keys.stripTicketNumber(cut >= 0 ? v.substring(cut + 1) : v);
    NameMatcher.addTokens(tf, lastSeg, 2.0);
    if (cut > 0) NameMatcher.addTokens(tf, v.substring(0, cut), 1.0);
    return NameMatcher.numbers(lastSeg);
  }

  /** Remplit les termes d'une ligne de droite, toutes colonnes ; rend les nombres retenus. */
  private Set<String> rightDoc(Csv.Row r, Map<String, Double> tf) {
    String numbered = "";
    for (String col : rightColumns) {
      String v = r.str(col);
      NameMatcher.addTokens(tf, v, 1.0);
      // Les nombres de la dernière colonne non vide — le nom affiché
      // dans le préréglage : la clé porte souvent un préfixe numéroté
      // qui ne dit rien du projet.
      if (!v.isEmpty()) numbered = v;
    }
    return NameMatcher.numbers(numbered);
  }
}
