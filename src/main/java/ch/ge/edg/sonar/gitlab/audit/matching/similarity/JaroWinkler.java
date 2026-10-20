package ch.ge.edg.sonar.gitlab.audit.matching.similarity;

/** Jaro-Winkler : ressemblance de deux mots, 0–1, avec un bonus pour un début commun. */
final class JaroWinkler {

  private JaroWinkler() {}

  static double similarity(String a, String b) {
    if (a.equals(b)) return 1.0;
    boolean[] ma = new boolean[a.length()];
    boolean[] mb = new boolean[b.length()];
    int matches = match(a, b, ma, mb);
    if (matches == 0) return 0;
    int transpositions = transpositions(a, b, ma, mb);
    double m = matches;
    double jaro = (m / a.length() + m / b.length() + (m - transpositions / 2.0) / m) / 3.0;
    return jaro + commonPrefix(a, b) * 0.1 * (1 - jaro);
  }

  /** Marque les lettres communes à moins d'une fenêtre l'une de l'autre, et les compte. */
  private static int match(String a, String b, boolean[] ma, boolean[] mb) {
    int window = Math.max(0, Math.max(a.length(), b.length()) / 2 - 1);
    int matches = 0;
    for (int i = 0; i < a.length(); i++) {
      int j =
          freeMatch(
              a.charAt(i), b, mb, Math.max(0, i - window), Math.min(b.length() - 1, i + window));
      if (j >= 0) {
        ma[i] = true;
        mb[j] = true;
        matches++;
      }
    }
    return matches;
  }

  /**
   * La première lettre {@code ch} de {@code b} entre {@code from} et {@code to}, pas encore prise.
   */
  private static int freeMatch(char ch, String b, boolean[] mb, int from, int to) {
    for (int j = from; j <= to; j++) {
      if (!mb[j] && ch == b.charAt(j)) return j;
    }
    return -1;
  }

  private static int transpositions(String a, String b, boolean[] ma, boolean[] mb) {
    int transpositions = 0;
    for (int i = 0, j = 0; i < a.length(); i++) {
      if (!ma[i]) continue;
      while (!mb[j]) j++;
      if (a.charAt(i) != b.charAt(j)) transpositions++;
      j++;
    }
    return transpositions;
  }

  /** Longueur du début commun, plafonnée à quatre lettres. */
  private static int commonPrefix(String a, String b) {
    int max = Math.min(4, Math.min(a.length(), b.length()));
    int prefix = 0;
    while (prefix < max && a.charAt(prefix) == b.charAt(prefix)) prefix++;
    return prefix;
  }
}
