package ch.ge.edg.sonar.gitlab.audit.cli.rank;

import ch.ge.edg.sonar.gitlab.audit.exchange.SonarInventory;
import org.jspecify.annotations.Nullable;

/**
 * Quatre signaux, tous déjà dans le CSV. La sévérité va toujours dans le même sens — plus c'est
 * haut, pire c'est — la couverture est donc inversée.
 *
 * <p>Un seul écart au poids uniforme : {@code sqale_debt_ratio} mesure le niveau de dette, pas sa
 * vitesse d'accumulation, et à poids plein il ramène le classement vers les gros projets anciens —
 * exactement ce que cette démarche cherche à éviter. Les trois autres restent à égalité tant que
 * personne n'a lu une shortlist et dit lequel l'a trompé.
 */
enum Signal {
  INJECTION("issues_neuves_par_kloc", 1.0),
  NEW_COVERAGE("couverture_code_neuf", 1.0),
  NEW_DUPLICATION("duplication_code_neuf", 1.0),
  DEBT_RATIO("ratio_dette", 0.5);

  public final String column;
  public final double weight;

  Signal(String column, double weight) {
    this.column = column;
    this.weight = weight;
  }

  /** {@code null} quand la métrique manque : le signal est alors absent, pas nul. */
  public @Nullable Double severity(InventoryRow r) {
    return switch (this) {
      case INJECTION -> injection(r);
      case NEW_COVERAGE -> {
        Double v = r.num(SonarInventory.NEW_COVERAGE);
        yield v == null ? null : -v; // moins couvert = pire
      }
      case NEW_DUPLICATION -> r.num(SonarInventory.NEW_DUPLICATED_LINES_DENSITY);
      case DEBT_RATIO -> r.num(SonarInventory.SQALE_DEBT_RATIO);
    };
  }

  private static @Nullable Double injection(InventoryRow r) {
    Double v = r.num(SonarInventory.NEW_VIOLATIONS);
    Double l = r.num(SonarInventory.NEW_LINES);
    if (v == null || l == null || l <= 0) return null;
    return v / l * 1000;
  }
}
