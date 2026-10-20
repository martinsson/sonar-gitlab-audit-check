package ch.ge.edg.sonar.gitlab.audit.matching;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Une méthode par clé. {@code whereColumn}/{@code whereValue} restreignent les lignes de droite
 * qu'elle lit : une liaison GitLab et une liaison GitHub portent toutes deux un « dépôt », pas du
 * même système.
 */
public record KeyMethod(
    String id,
    String label,
    Kind kind,
    String leftColumn,
    String rightColumn,
    @Nullable String whereColumn,
    @Nullable String whereValue) {

  private static final Pattern SPEC =
      Pattern.compile("([\\w-]+):([^=?]+)=([^=?]+)(?:\\?([^=]+)=(.*))?");

  /**
   * Lit {@code id:gauche=droite[?colonne=valeur]}.
   *
   * @throws IllegalArgumentException si la méthode ne s'écrit pas ainsi ; le message dit ce qui est
   *     attendu
   */
  public static KeyMethod parse(String s, Kind kind) {
    Matcher m = SPEC.matcher(s.trim());
    if (!m.matches()) {
      throw new IllegalArgumentException(
          "méthode illisible : « " + s + " » (attendu id:gauche=droite[?colonne=valeur])");
    }
    String where = m.group(4);
    String value = m.group(5);
    return new KeyMethod(
        m.group(1),
        m.group(1),
        kind,
        m.group(2).trim(),
        m.group(3).trim(),
        where == null ? null : where.trim(),
        value == null ? null : value.trim());
  }

  public Confidence confidence() {
    return switch (kind) {
      case EXACT -> Confidence.EXACT;
      case DERIVED -> Confidence.DERIVED;
      case MAPPED, NAME -> Confidence.DERIVED;
      case LINK -> Confidence.NONE;
    };
  }

  public boolean joins() {
    return kind != Kind.LINK;
  }

  public boolean accepts(Csv.Row right) {
    return whereColumn == null || Objects.equals(whereValue, right.str(whereColumn));
  }

  /** La valeur qu'une ligne de gauche cherche, telle que la méthode la compare. */
  String leftValue(String v) {
    return switch (kind) {
      case EXACT -> v.trim();
      case DERIVED, NAME -> Keys.normalise(v);
      case MAPPED -> Keys.mapPathToSonarKey(v);
      case LINK -> v.trim().toLowerCase(Locale.ROOT);
    };
  }

  /** Les valeurs sous lesquelles une ligne de droite se laisse trouver. */
  List<String> rightValues(String v) {
    if (v.isBlank()) return List.of();
    return switch (kind) {
      case EXACT -> List.of(v.trim());
      case DERIVED, NAME -> List.of(Keys.normalise(v));
      case MAPPED -> Keys.mappedSonarKey(v);
      case LINK -> Keys.linkedPaths(v);
    };
  }
}
