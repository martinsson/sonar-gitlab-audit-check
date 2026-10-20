package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Les signaux de pratique d'un projet sélectionné. Absent reste null, comme dans CommitMeasure. */
final class Prat {
  boolean defaultProtected, pushLocked, ciSonar, ciSecurity, doraUnavailable;
  @Nullable String sonarKey, sonarKeySource, pomKey, pomName, pomUnresolved;
  int mergedMrs, selfMerged, selfApproved, pipelines;
  @Nullable Integer approvalSample, environments, redIncidents, unresolvedRed, churnCommits;
  @Nullable Long linesChanged;
  boolean churnTruncated;
  @Nullable
  Double medianTtmDays,
      notesPerMr,
      approvedShare,
      pipelineSuccess,
      deployments,
      recoveryMedianHours;
  final List<String> files = new ArrayList<>();
}
