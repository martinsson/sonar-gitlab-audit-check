package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;

/**
 * Une MR de la liste merge_requests. GitLab la rend à plat ; on la lit en deux parties — ce qui
 * s'est passé, et qui y a pris part — chacune lue sur le même objet JSON.
 */
record MergeRequest(Facts facts, Participants participants) {

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  static MergeRequest of(JsonNode json) throws JsonProcessingException {
    return new MergeRequest(
        Gitlab.MAPPER.treeToValue(json, Facts.class),
        Gitlab.MAPPER.treeToValue(json, Participants.class));
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Facts(
      @Nullable Long iid,
      @Nullable String state,
      @JsonProperty("created_at") @Nullable String createdAt,
      @JsonProperty("merged_at") @Nullable String mergedAt,
      @Nullable Boolean squash,
      @JsonProperty("user_notes_count") @Nullable Integer userNotesCount) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Participants(
      @Nullable User author,
      @JsonProperty("merge_user") @Nullable User mergeUser,
      @JsonProperty("merged_by") @Nullable User mergedBy) {}

  @Nullable Long iid() {
    return facts.iid();
  }

  @Nullable String state() {
    return facts.state();
  }

  @Nullable String createdAt() {
    return facts.createdAt();
  }

  @Nullable String mergedAt() {
    return facts.mergedAt();
  }

  @Nullable String authorUsername() {
    return User.usernameOf(participants.author());
  }

  /** merged_by est déprécié au profit de merge_user : on lit les deux. */
  @Nullable String mergedByUsername() {
    String mergeUser = User.usernameOf(participants.mergeUser());
    if (mergeUser != null) return mergeUser;
    return User.usernameOf(participants.mergedBy());
  }

  /** Fusionnée par son auteur : la revue n'a pas eu lieu, ou n'a pas pesé. */
  boolean isSelfMerged() {
    String by = mergedByUsername();
    return by != null && by.equals(authorUsername());
  }
}
