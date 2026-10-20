package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.BOLD;
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

/** 4. Revue : les MR sont-elles relues, par qui, en combien de temps ? */
final class ReviewSection {

  private final Report report;
  private final ProjectApi api;
  private final Settings settings;
  private final Dates dates;

  ReviewSection(Report report, ProjectApi api, Settings settings, Dates dates) {
    this.report = report;
    this.api = api;
    this.settings = settings;
    this.dates = dates;
  }

  Reviews measure(Project p, LocalDateTime since) {
    report.title("4. Merge requests et revue");

    Gitlab.Page<MergeRequest> f = api.mergeRequests(p, since);
    String error = f.error();
    if (error != null) {
      report.line("merge_requests", Verdict.ERROR, error);
      return Reviews.EMPTY;
    }
    List<MergeRequest> all = f.items();
    if (all.isEmpty()) {
      report.println("  Aucune MR créée sur la fenêtre.", YELLOW);
      report.println(
          """
              Le travail arrive donc directement sur la branche par défaut.
            Ce n'est pas une absence de données : c'est l'absence de revue.\
          """,
          DIM);
      return Reviews.NONE;
    }

    List<MergeRequest> merged = all.stream().filter(m -> "merged".equals(m.state())).toList();
    volume(all, merged.size(), f.truncated());
    double leadP90 = leadTimes(merged);

    // user_notes_count est fourni par la liste : la profondeur d'échange ne
    // coûte donc aucun appel supplémentaire. Zéro note = personne n'a rien dit.
    long silent = all.stream().filter(m -> Format.orZero(m.facts().userNotesCount()) == 0).count();
    double silentShare = 100.0 * silent / all.size();
    report.println(
        "  MR sans aucun commentaire       : %d (%.0f %%)".formatted(silent, silentShare));

    double selfShare = selfMerges(merged);

    long squashed = all.stream().filter(m -> Boolean.TRUE.equals(m.facts().squash())).count();
    double squashShare = 100.0 * squashed / all.size();
    report.println(
        "  Squash à la fusion              : %.0f %%%s"
            .formatted(
                squashShare,
                squashShare >= 50 ? report.c("   (écrase l'historique par commit)", DIM) : ""));

    new ReviewSample(report, api, settings.sample(), dates).measure(p, merged);
    return new Reviews(all.size(), leadP90, silentShare, selfShare, squashShare, false);
  }

  private void volume(List<MergeRequest> all, int merged, boolean truncated) {
    if (truncated) {
      report.println(
          "  Plafond --max-mrs atteint : chiffres portant sur les %d plus récentes."
              .formatted(settings.maxMrs()),
          YELLOW);
    }
    long closed = all.stream().filter(m -> "closed".equals(m.state())).count();
    long opened = all.stream().filter(m -> "opened".equals(m.state())).count();

    double observed = settings.days();
    if (truncated) {
      LocalDateTime oldest =
          all.stream()
              .map(m -> dates.parseLocal(m.createdAt()))
              .filter(Objects::nonNull)
              .min(Comparator.naturalOrder())
              .orElse(null);
      if (oldest != null) observed = Math.max(1, ChronoUnit.DAYS.between(oldest, dates.now()));
    }
    double weeks = Math.max(1.0, observed / 7.0);

    report.println(
        "  MR créées                       : %s (%.1f / semaine)%s"
            .formatted(
                report.c(String.valueOf(all.size()), BOLD),
                all.size() / weeks,
                truncated
                    ? report.c("   (sur les %.0f j couverts)".formatted(observed), DIM)
                    : ""));
    report.println(
        "  Fusionnées / fermées / ouvertes : %d / %d / %d".formatted(merged, closed, opened));
  }

  /**
   * Délai de bout en bout. La médiane décrit le quotidien, le 9e décile décrit ce qui coince — et
   * c'est là que se loge la dysfonction.
   *
   * @return le 9e décile, en heures ; 0 quand rien n'est mesurable
   */
  private double leadTimes(List<MergeRequest> merged) {
    List<Long> lead = new ArrayList<>();
    for (MergeRequest m : merged) {
      LocalDateTime a = dates.parseLocal(m.createdAt());
      LocalDateTime b = dates.parseLocal(m.mergedAt());
      if (a != null && b != null && !b.isBefore(a)) lead.add(ChronoUnit.HOURS.between(a, b));
    }
    if (lead.isEmpty()) return 0;
    Collections.sort(lead);
    double leadP90 = Format.percentile(lead, 90);
    report.println(
        "  Délai création → fusion         : médiane %s, 9e décile %s"
            .formatted(Format.hours(Format.percentile(lead, 50)), Format.hours(leadP90)));
    return leadP90;
  }

  /**
   * Fusionnée par son auteur : la revue n'a pas eu lieu, ou n'a pas pesé. merge_user est absent des
   * versions plus anciennes ; on ne compte alors que les MR où l'information existe, et on le dit.
   *
   * @return la part d'auto-fusions, en pourcents ; -1 quand elle n'est pas mesurable
   */
  private double selfMerges(List<MergeRequest> merged) {
    long withMerger = merged.stream().filter(m -> m.mergedByUsername() != null).count();
    long selfMerged = merged.stream().filter(MergeRequest::isSelfMerged).count();
    double selfShare = withMerger == 0 ? -1 : 100.0 * selfMerged / withMerger;
    if (merged.isEmpty()) {
      report.println("  Auto-fusion                     : aucune MR fusionnée sur la fenêtre", DIM);
    } else if (withMerger == 0) {
      report.println("  Auto-fusion                     : non mesurable (merge_user absent)", DIM);
    } else {
      report.println(
          "  Fusionnées par leur auteur      : %d sur %d (%.0f %%)"
              .formatted(selfMerged, withMerger, selfShare));
    }
    return selfShare;
  }
}
