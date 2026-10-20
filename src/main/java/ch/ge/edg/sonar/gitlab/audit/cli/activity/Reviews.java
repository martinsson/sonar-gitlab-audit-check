package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.params;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.parse;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.text;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.iso;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.median;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * La revue de code, telle que les MR fusionnées la laissent voir.
 *
 * <p>Le nombre qui compte serait la part des commits de la branche par défaut arrivés par MR
 * approuvée. Elle n'est pas calculable à coût raisonnable : il faudrait rattacher chaque commit à
 * sa MR. On publie donc les deux termes bruts — MR fusionnées et commits de merge — plutôt qu'un
 * ratio qui aurait l'air d'une mesure sans en être une.
 */
final class Reviews {

  /** Approbations lues par projet : un appel par MR. */
  private static final int APPROVAL_SAMPLE = 10;

  private final Gitlab gl;
  private final Counters counters;
  private final Funnel funnel;

  Reviews(Gitlab gl, Counters counters, Funnel funnel) {
    this.gl = gl;
    this.counters = counters;
    this.funnel = funnel;
  }

  void measure(GitlabProject p, Prat r) {
    List<JsonNode> mrs = mergedMrs(p);
    r.mergedMrs = mrs.size();
    List<Double> ttm = new ArrayList<>();
    int selfMerged = 0;
    int notes = 0;
    for (JsonNode mr : mrs) {
      OffsetDateTime a = parse(text(mr, "created_at"));
      OffsetDateTime b = parse(text(mr, "merged_at"));
      if (a != null && b != null) ttm.add(ChronoUnit.HOURS.between(a, b) / 24.0);
      int author = mr.path("author").path("id").asInt(-1);
      int merger =
          mr.path("merged_by").path("id").asInt(mr.path("merge_user").path("id").asInt(-2));
      if (author != -1 && author == merger) selfMerged++;
      notes += mr.path("user_notes_count").asInt(0);
    }
    r.medianTtmDays = median(ttm);
    r.selfMerged = selfMerged;
    r.notesPerMr = mrs.isEmpty() ? null : (double) notes / mrs.size();

    // Approbations : la mesure réelle, réservée aux plans Enterprise. On
    // échantillonne 10 MR par projet — 1 appel par MR sur 200 projets
    // coûterait plus cher que tout le reste de la passe réuni.
    if (counters.enterprise && !mrs.isEmpty()) approvals(p, r, mrs);
  }

  private List<JsonNode> mergedMrs(GitlabProject p) {
    List<JsonNode> mrs = new ArrayList<>();
    for (int page = 1; page <= 3; page++) {
      Gitlab.Response m =
          gl.get(
              "projects/" + p.id() + "/merge_requests",
              params(
                  "state",
                  "merged",
                  "target_branch",
                  p.defaultBranch(),
                  "updated_after",
                  iso(funnel.windowStart()),
                  "per_page",
                  "100",
                  "page",
                  String.valueOf(page)));
      JsonNode arr = m.json();
      if (m.status() != 200 || !arr.isArray() || arr.isEmpty()) break;
      arr.forEach(mrs::add);
      if (arr.size() < 100) break;
    }
    return mrs;
  }

  private void approvals(GitlabProject p, Prat r, List<JsonNode> mrs) {
    int sample = Math.min(APPROVAL_SAMPLE, mrs.size());
    int approved = 0;
    int selfApproved = 0;
    int seen = 0;
    for (int i = 0; i < sample; i++) {
      JsonNode mr = mrs.get(i);
      Gitlab.Response ap =
          gl.get(
              "projects/" + p.id() + "/merge_requests/" + mr.path("iid").asInt() + "/approvals",
              Map.of());
      if (ap.status() != 200) break;
      seen++;
      JsonNode by = ap.json().path("approved_by");
      if (by.size() > 0) approved++;
      selfApproved += selfApprovals(by, mr.path("author").path("id").asInt(-1));
    }
    if (seen > 0) {
      r.approvalSample = seen;
      r.approvedShare = (double) approved / seen;
      r.selfApproved = selfApproved;
    }
  }

  private static int selfApprovals(JsonNode approvedBy, int author) {
    int n = 0;
    for (JsonNode u : approvedBy) {
      if (u.path("user").path("id").asInt(-2) == author) n++;
    }
    return n;
  }
}
