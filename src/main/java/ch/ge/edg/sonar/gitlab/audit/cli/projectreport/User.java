package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/** Un utilisateur tel que GitLab l'imbrique dans une MR ou une note. */
@JsonIgnoreProperties(ignoreUnknown = true)
record User(@Nullable Long id, @Nullable String username, @Nullable String name) {

  static @Nullable String usernameOf(@Nullable User u) {
    return u == null ? null : u.username();
  }
}
