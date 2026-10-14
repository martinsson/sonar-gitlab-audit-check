package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Un commit de repository/commits?with_stats=true. */
@JsonIgnoreProperties(ignoreUnknown = true)
record Commit(
    @JsonProperty("author_name") @Nullable String authorName,
    @JsonProperty("author_email") @Nullable String authorEmail,
    @JsonProperty("authored_date") @Nullable String authoredDate,
    @JsonProperty("committed_date") @Nullable String committedDate,
    @JsonProperty("parent_ids") @Nullable List<String> parentIds,
    @Nullable Stats stats) {

  /** Deux parents ou plus : commit de fusion, dont les lignes appartiennent à d'autres. */
  boolean isMerge() {
    return Gitlab.isMerge(parentIds);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Stats(@Nullable Integer additions, @Nullable Integer deletions) {}
}
