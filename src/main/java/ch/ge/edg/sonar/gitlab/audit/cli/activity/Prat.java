package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.dec;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.num;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.orEmpty;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Les signaux de pratique d'un projet sélectionné. Absent reste null, comme dans Proj. */
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

  /** Une ligne de pratiques.csv. */
  String[] row(Proj p, int windowDays) {
    return new String[] {
      orEmpty(p.path),
      orEmpty(p.selectionReason),
      p.bucket,
      num(p.commits),
      num(p.authors),
      String.valueOf(defaultProtected),
      String.valueOf(pushLocked),
      String.valueOf(mergedMrs),
      dec(medianTtmDays),
      String.valueOf(selfMerged),
      dec(notesPerMr),
      num(approvalSample),
      dec(approvedShare),
      String.valueOf(selfApproved),
      String.valueOf(pipelines),
      dec(pipelineSuccess),
      num(redIncidents),
      dec(recoveryMedianHours),
      num(unresolvedRed),
      num(environments),
      dec(deployments),
      String.valueOf(doraUnavailable),
      String.valueOf(ciSonar),
      String.valueOf(ciSecurity),
      orEmpty(sonarKey),
      orEmpty(sonarKeySource),
      String.join(" ", files),
      String.valueOf(p.id),
      String.valueOf(windowDays),
      num(linesChanged),
      num(churnCommits),
      String.valueOf(churnTruncated),
      orEmpty(pomKey),
      orEmpty(pomName)
    };
  }
}
