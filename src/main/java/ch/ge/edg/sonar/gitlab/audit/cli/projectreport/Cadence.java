package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/** Ce que la section « Cadence » a mesuré. */
record Cadence(
    int commits,
    long activeDays,
    double bigCommitShare,
    double weekendShare,
    @Nullable LocalDateTime lastCommit) {
  static final Cadence EMPTY = new Cadence(0, 0, 0, 0, null);
}
