package ch.ge.edg.sonar.gitlab.audit.matching.similarity;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * La mesure elle-même, TF-IDF « souple » (Cohen et al. 2003) : deux noms déjà réduits en mots
 * pondérés, et leur ressemblance. {@link NameMatcher} s'occupe du corpus et du choix des candidats.
 */
final class SoftTfIdf {

  /** Deux mots distincts se rejoignent à partir de cette ressemblance. */
  static final double TOKEN_SIMILARITY = 0.9;

  /** En dessous, un mot est trop court pour qu'une ressemblance veuille dire quelque chose. */
  static final int MIN_FUZZY_LENGTH = 4;

  private SoftTfIdf() {}

  /** Un nom prêt à comparer : mots pondérés (norme 1) et nombres portés. */
  record Doc(Map<String, Double> weights, Set<String> numbers) {}

  /** Les mots comptés d'un nom, pondérés par leur rareté et ramenés à la norme 1. */
  static Doc doc(Map<String, Double> tf, Set<String> numbers, Map<String, Double> idf) {
    Map<String, Double> w = new HashMap<>();
    double norm = 0;
    for (var e : tf.entrySet()) {
      double v = e.getValue() * idf.getOrDefault(e.getKey(), 1.0);
      w.put(e.getKey(), v);
      norm += v * v;
    }
    double len = Math.sqrt(norm);
    if (len > 0) w.replaceAll((k, v) -> v / len);
    return new Doc(w, numbers);
  }

  /**
   * Pour chaque mot de la source, le mot le plus proche de la cible, s'il l'est assez. Borné à 1 :
   * deux mots proches d'un même mot ne font pas plus qu'une identité.
   */
  static double similarity(Doc a, Doc b) {
    double sum = 0;
    for (var ea : a.weights().entrySet()) {
      double best = 0;
      for (var eb : b.weights().entrySet()) {
        double s = tokenSimilarity(ea.getKey(), eb.getKey());
        if (s > 0) best = Math.max(best, eb.getValue() * s);
      }
      sum += ea.getValue() * best;
    }
    return Math.min(1.0, sum);
  }

  static double tokenSimilarity(String a, String b) {
    if (a.equals(b)) return 1.0;
    if (a.length() < MIN_FUZZY_LENGTH || b.length() < MIN_FUZZY_LENGTH) return 0;
    double jw = JaroWinkler.similarity(a, b);
    return jw >= TOKEN_SIMILARITY ? jw : 0;
  }

  static boolean numbersConflict(Set<String> a, Set<String> b) {
    return !a.isEmpty() && !b.isEmpty() && !a.equals(b);
  }
}
