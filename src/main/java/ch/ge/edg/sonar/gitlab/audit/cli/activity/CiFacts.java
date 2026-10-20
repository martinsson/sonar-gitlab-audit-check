package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import org.jspecify.annotations.Nullable;

/**
 * Ce qu'on a appris de la CI d'un projet, et qui ne changera pas tant que son dépôt ne bouge pas :
 * l'entrée du cache des clés Sonar, remplie par la sonde, puis seulement lue.
 */
final class CiFacts {
  boolean ciSonar;
  boolean ciSecurity;
  @Nullable String sonarKey;
  @Nullable String keySource;
  @Nullable String ciRoute;
  @Nullable String unresolvedKey;
  @Nullable String pomKey;
  @Nullable String pomName;
  @Nullable String pomUnresolved;

  /** La source de la clé telle qu'on la publie : une variable non résolue se dit en clair. */
  @Nullable String shownKeySource() {
    return unresolvedKey != null ? "variable non résolue : " + unresolvedKey : keySource;
  }
}
