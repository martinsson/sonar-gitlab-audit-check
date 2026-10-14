package ch.ge.edg.sonar.gitlab.audit.cli.rank;

/** Les seuils sont arbitraires ; ce qui compte est de ne comparer qu'à taille comparable. */
public enum Bucket {
  XS("< 1k lignes", 0, 1_000),
  S("1k - 10k", 1_000, 10_000),
  M("10k - 100k", 10_000, 100_000),
  L("> 100k", 100_000, Integer.MAX_VALUE);

  public final String label;
  final int min;
  final int max;

  Bucket(String label, int min, int max) {
    this.label = label;
    this.min = min;
    this.max = max;
  }

  public static Bucket of(double ncloc) {
    for (Bucket b : values()) {
      if (ncloc >= b.min && ncloc < b.max) return b;
    }
    return L;
  }
}
