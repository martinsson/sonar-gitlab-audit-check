package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import org.jspecify.annotations.Nullable;

/**
 * Ce qu'on a appris de la CI d'un projet, et qui ne changera pas tant que son dépôt ne bouge pas.
 */
final class CiFacts {
  boolean ciSonar, ciSecurity;
  @Nullable String sonarKey, keySource, ciRoute, unresolvedKey;
  @Nullable String pomKey, pomName, pomUnresolved;

  void applyTo(Prat r) {
    r.ciSonar = ciSonar;
    r.ciSecurity = ciSecurity;
    r.sonarKey = sonarKey;
    r.pomKey = pomKey;
    r.pomName = pomName;
    r.pomUnresolved = pomUnresolved;
    r.sonarKeySource =
        unresolvedKey != null ? "variable non résolue : " + unresolvedKey : keySource;
  }
}
