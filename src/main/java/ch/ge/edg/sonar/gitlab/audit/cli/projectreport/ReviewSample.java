package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.YELLOW;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Le délai jusqu'à la première relecture demande un appel par MR : on échantillonne, et on affiche
 * la taille de l'échantillon plutôt que de laisser croire à une mesure exhaustive.
 */
final class ReviewSample {

  private final Report report;
  private final ProjectApi api;
  private final int sample;
  private final Dates dates;

  private final List<Long> latencies = new ArrayList<>();
  private int unreviewed;
  private int measured;
  private @Nullable String error;

  ReviewSample(Report report, ProjectApi api, int sample, Dates dates) {
    this.report = report;
    this.api = api;
    this.sample = sample;
    this.dates = dates;
  }

  void measure(Project p, List<MergeRequest> merged) {
    if (merged.isEmpty() || sample <= 0) return;
    for (MergeRequest m : merged.subList(0, Math.min(sample, merged.size()))) {
      if (!read(p, m)) break;
    }
    report.println();
    print();
  }

  /** Lit les notes d'une MR ; faux sur un refus, qui arrête l'échantillon. */
  private boolean read(Project p, MergeRequest m) {
    Gitlab.Page<Note> f = api.notes(p, m);
    // Un refus se répète : on s'arrête au premier plutôt que de brûler
    // l'échantillon entier en 401, et surtout on le dit. Une section qui
    // disparaît sans un mot ressemble à une absence de sujet.
    if (f.error() != null) {
      error = f.error();
      return false;
    }
    measured++;
    Optional<LocalDateTime> first =
        f.items().stream()
            .filter(n -> n.isThirdPartyReview(m.authorUsername()))
            .map(n -> dates.parseLocal(n.createdAt()))
            .filter(Objects::nonNull)
            .min(Comparator.naturalOrder());
    LocalDateTime created = dates.parseLocal(m.createdAt());
    if (first.isEmpty()) unreviewed++;
    else if (created != null)
      latencies.add(Math.max(0, ChronoUnit.HOURS.between(created, first.get())));
    return true;
  }

  private void print() {
    String refused = error;
    if (refused != null) {
      report.line("merge_requests/:iid/notes", EndpointVerdict.DENIED, refused);
      report.println("    Délai de revue et part de MR non relues : non mesurés.", YELLOW);
      report.println("    Les notes exigent un jeton, même sur un projet public.", YELLOW);
      return;
    }
    if (measured == 0) return;

    report.println("  Échantillon de revue            : %d MR fusionnées".formatted(measured));
    report.println(
        "  Fusionnées sans relecture       : %d (%.0f %%) %s"
            .formatted(
                unreviewed,
                100.0 * unreviewed / measured,
                report.paint("— aucun commentaire d'un tiers", DIM)));
    if (latencies.isEmpty()) return;
    Collections.sort(latencies);
    report.println(
        "  Délai avant première relecture  : médiane %s, 9e décile %s"
            .formatted(
                ReportFormat.hours(ReportFormat.percentile(latencies, 50)),
                ReportFormat.hours(ReportFormat.percentile(latencies, 90))));
  }
}
