package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/** Une note de merge_requests/:iid/notes. */
@JsonIgnoreProperties(ignoreUnknown = true)
record Note(
    @Nullable Long id,
    @Nullable Boolean system,
    @JsonProperty("created_at") @Nullable String createdAt,
    @Nullable User author) {

  @Nullable String authorUsername() {
    return User.usernameOf(author);
  }

  /**
   * Les notes système (« a assigné », « a poussé ») ne sont pas des relectures : sans ce filtre,
   * toute MR paraît relue dans la seconde.
   */
  boolean isThirdPartyReview(@Nullable String mrAuthor) {
    String by = authorUsername();
    return !Boolean.TRUE.equals(system) && by != null && !by.equals(mrAuthor);
  }
}
