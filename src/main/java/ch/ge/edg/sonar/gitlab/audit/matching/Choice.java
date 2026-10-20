package ch.ge.edg.sonar.gitlab.audit.matching;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import org.jspecify.annotations.Nullable;

/**
 * Le sort d'une ligne de gauche : la ligne de droite retenue (aucune si null), l'id de la méthode
 * qui l'a retenue ({@link Spec#NAMES_ID} pour une suggestion, null sinon), son libellé et la
 * confiance qu'on lui accorde. {@code rejectedKey} garde la clé d'un refus saisi à la main, pour
 * que l'appariement réécrit refuse encore la même paire.
 */
public record Choice(
    Csv.@Nullable Row right,
    @Nullable String method,
    String label,
    Confidence confidence,
    @Nullable String rejectedKey) {

  public Choice(
      Csv.@Nullable Row right, @Nullable String method, String label, Confidence confidence) {
    this(right, method, label, confidence, null);
  }

  /** Rien de retenu. */
  static Choice none() {
    return new Choice(null, null, Spec.NONE_LABEL, Confidence.NONE);
  }
}
