package ch.ge.edg.sonar.gitlab.audit.cli.activity.model;

import org.jspecify.annotations.Nullable;

/**
 * Ce qu'on a appris de la CI d'un projet, et qui ne changera pas tant que son dépôt ne bouge pas :
 * l'entrée du cache des clés Sonar, remplie par la sonde, puis seulement lue.
 */
public final class CiFacts {
  public boolean ciSonar;
  public boolean ciSecurity;
  public @Nullable String sonarKey;
  public @Nullable String keySource;
  public @Nullable String ciRoute;
  public @Nullable String unresolvedKey;
  public @Nullable String pomKey;
  public @Nullable String pomName;
  public @Nullable String pomUnresolved;

  /** La source de la clé telle qu'on la publie : une variable non résolue se dit en clair. */
  public @Nullable String shownKeySource() {
    return unresolvedKey != null ? "variable non résolue : " + unresolvedKey : keySource;
  }
}
