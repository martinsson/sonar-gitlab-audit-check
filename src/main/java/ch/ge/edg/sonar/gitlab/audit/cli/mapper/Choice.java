package ch.ge.edg.sonar.gitlab.audit.cli.mapper;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import org.jspecify.annotations.Nullable;

/**
 * Le sort d'une ligne de gauche : la ligne de droite retenue (aucune si null), l'id de la méthode
 * qui l'a retenue ({@link Spec#NAMES_ID} pour une suggestion, null sinon), son libellé et la
 * confiance qu'on lui accorde.
 */
public record Choice(
    Csv.@Nullable Row right, @Nullable String method, String label, Confidence confidence) {

  /** Rien de retenu. */
  static Choice none() {
    return new Choice(null, null, Spec.NONE_LABEL, Confidence.NONE);
  }
}
