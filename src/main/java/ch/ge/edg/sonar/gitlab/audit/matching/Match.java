package ch.ge.edg.sonar.gitlab.audit.matching;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Une ligne de gauche et son sort.
 *
 * <p>{@code choice} dit ce qui a été retenu. {@code found} donne, pour chaque méthode par clé, les
 * clés de droite qu'elle désigne — qu'elle ait été retenue ou non. {@code guess} est la suggestion
 * de noms faite sur ce qui restait ; {@code free} la même ressemblance, calculée contre toute la
 * droite, pour l'évaluation.
 */
public record Match(
    int index,
    Csv.Row left,
    Choice choice,
    Map<String, List<String>> found,
    @Nullable NameGuess guess,
    @Nullable NameGuess free) {

  public Csv.@Nullable Row right() {
    return choice.right();
  }

  public @Nullable String method() {
    return choice.method();
  }

  public String label() {
    return choice.label();
  }

  public Confidence confidence() {
    return choice.confidence();
  }

  /** Une jointure sur laquelle on peut compter : ni suggestion, ni absence. */
  public boolean joined() {
    Confidence c = confidence();
    return right() != null
        && (c == Confidence.EXACT || c == Confidence.DERIVED || c == Confidence.MANUEL);
  }

  public String rightKey(Spec spec) {
    Csv.Row r = right();
    return r == null ? "" : r.str(spec.rightKey());
  }

  Match with(Choice c, @Nullable NameGuess g, @Nullable NameGuess f) {
    return new Match(index, left, c, found, g, f);
  }
}
