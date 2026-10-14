package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/** Un contributeur de repository/contributors : toute l'histoire, commits de fusion inclus. */
@JsonIgnoreProperties(ignoreUnknown = true)
record Contributor(
    @Nullable String name,
    @Nullable String email,
    @Nullable Integer commits,
    @Nullable Integer additions,
    @Nullable Integer deletions) {}
