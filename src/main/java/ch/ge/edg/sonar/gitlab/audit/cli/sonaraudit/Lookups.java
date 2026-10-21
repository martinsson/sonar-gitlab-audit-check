package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.orEmptyList;
import static ch.ge.edg.sonar.gitlab.audit.rest.Query.params;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Component;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.ProjectSearch;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Trouver de quoi tester : un projet témoin, et un fichier de ce projet. */
final class Lookups {

  private Lookups() {}

  static @Nullable String firstVisibleProject(Session s) {
    return firstKey(
        s.read("api/components/search_projects", params("ps", "1"), ProjectSearch.class));
  }

  static @Nullable String firstFileOf(Session s, String projectKey) {
    return firstKey(
        s.read(
            "api/components/tree",
            params("component", projectKey, "qualifiers", "FIL", "ps", "1"),
            ProjectSearch.class));
  }

  private static @Nullable String firstKey(@Nullable ProjectSearch p) {
    List<Component> components = p == null ? List.of() : orEmptyList(p.components());
    return components.isEmpty() ? null : components.get(0).key();
  }
}
