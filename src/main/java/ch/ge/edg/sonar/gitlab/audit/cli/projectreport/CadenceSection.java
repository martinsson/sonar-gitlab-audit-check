package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.BOLD;
import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.YELLOW;

import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 2. Cadence : à quel rythme, par à-coups ou en continu, quand ? */
final class CadenceSection {

  static final int BIG_COMMIT = 2000;

  private final Report report;
  private final Settings settings;
  private final Dates dates;

  CadenceSection(Report report, Settings settings, Dates dates) {
    this.report = report;
    this.settings = settings;
    this.dates = dates;
  }

  Cadence measure(Commits commits) {
    report.title(
        Text.format("2. Cadence (branche par défaut, %d derniers jours)", settings.days()));

    List<Commit> real = ordinaryCommits(commits);
    if (real.isEmpty()) return Cadence.EMPTY;

    List<LocalDateTime> committed =
        real.stream()
            .map(x -> dates.parseLocal(x.committedDate()))
            .filter(Objects::nonNull)
            .sorted()
            .toList();
    long activeDays = committed.stream().map(LocalDateTime::toLocalDate).distinct().count();

    rhythm(commits, real.size(), committed, activeDays);
    gaps(committed);
    double weekendShare = weekly(real);
    double bigShare = volumes(real);
    return new Cadence(
        real.size(),
        activeDays,
        bigShare,
        weekendShare,
        committed.isEmpty() ? null : committed.get(committed.size() - 1));
  }

  /** Les commits humains hors fusions ; vide, avec la raison imprimée, quand il n'y a rien. */
  private List<Commit> ordinaryCommits(Commits commits) {
    String error = commits.error();
    if (error != null) {
      report.line("repository/commits", Verdict.ERROR, error);
      return List.of();
    }
    List<Commit> cs = commits.humans();
    if (cs.isEmpty()) {
      report.println("  Aucun commit humain sur la fenêtre.", YELLOW);
      if (!commits.bots().isEmpty()) {
        report.println(
            Text.format("  (%d commits de bots sur la même période)", commits.bots().size()));
      }
      return List.of();
    }
    if (commits.truncated()) {
      report.println(
          Text.format(
              "  Plafond --max-commits atteint : les chiffres portent sur les %d plus récents.",
              settings.maxCommits()),
          YELLOW);
    }

    // parent_ids > 1 = commit de fusion. Il ne porte aucun travail propre : ses
    // lignes sont celles des commits qu'il réunit, et sa date est celle du clic
    // sur « fusionner », par quelqu'un qui n'a pas écrit le code. Tout ce qui
    // suit se mesure donc sur les seuls commits ordinaires.
    List<Commit> real = cs.stream().filter(x -> !x.isMerge()).toList();
    if (real.isEmpty()) {
      report.println("  Que des commits de fusion : rien à mesurer sur cette branche.", YELLOW);
    }
    return real;
  }

  private void rhythm(Commits commits, int real, List<LocalDateTime> dates, long activeDays) {
    // Sous plafond, la fenêtre réellement couverte n'est plus --days mais celle
    // qui remonte au plus ancien commit rapatrié. Rapporter le débit aux --days
    // demandés donnerait un taux mécaniquement sous-estimé, et présenté comme
    // une mesure.
    double observed = settings.days();
    if (commits.truncated() && !dates.isEmpty()) {
      observed = Math.max(1, ChronoUnit.DAYS.between(dates.get(0), this.dates.now()));
    }
    double weeks = Math.max(1.0, observed / 7.0);
    int merges = commits.humans().size() - real;

    report.println(
        Text.format(
            "  Commits (hors bots)             : %s",
            report.c(String.valueOf(commits.humans().size()), BOLD)));
    if (!commits.bots().isEmpty()) {
      report.println(Text.format("  Commits de bots écartés         : %d", commits.bots().size()));
    }
    if (merges > 0) {
      report.println(
          Text.format(
              "  dont commits de fusion          : %d %s",
              merges, report.c("(écartés de toutes les mesures ci-dessous)", DIM)));
    }
    report.println(
        Text.format(
            "  Rythme                          : %.1f commits / semaine%s",
            real / weeks,
            commits.truncated()
                ? report.c(Text.format("   (sur les %.0f j couverts)", observed), DIM)
                : ""));
    report.println(
        Text.format("  Jours avec au moins un commit   : %d sur %.0f", activeDays, observed));
  }

  /**
   * L'écart médian dit le rythme ordinaire, le plus long silence dit s'il y a eu abandon puis
   * reprise. La moyenne, elle, ne dit ni l'un ni l'autre.
   */
  private void gaps(List<LocalDateTime> dates) {
    List<Long> gaps = new ArrayList<>();
    for (int i = 1; i < dates.size(); i++) {
      gaps.add(ChronoUnit.HOURS.between(dates.get(i - 1), dates.get(i)));
    }
    if (gaps.isEmpty()) return;
    Collections.sort(gaps);
    report.println(
        Text.format(
            "  Écart médian entre commits      : %s", Format.hours(Format.percentile(gaps, 50))));
    report.println(
        Text.format(
            "  Plus long silence               : %s", Format.hours(gaps.get(gaps.size() - 1))));
  }

  /**
   * L'heure est lue dans le décalage porté par la date elle-même, donc dans le fuseau de la machine
   * qui a commité : c'est l'heure locale de la personne, et non celle du serveur ou de l'auditeur.
   *
   * @return la part de commits du week-end, en pourcents
   */
  private double weekly(List<Commit> real) {
    int[] byDow = new int[7];
    int outsideHours = 0;
    int weekend = 0;
    int dated = 0;
    for (Commit x : real) {
      OffsetDateTime o = Dates.parseOffset(x.authoredDate());
      if (o == null) continue;
      dated++;
      DayOfWeek dow = o.getDayOfWeek();
      byDow[dow.getValue() - 1]++;
      if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) weekend++;
      int h = o.getHour();
      if (h < 8 || h >= 19) outsideHours++;
    }
    if (dated == 0) return 0;
    report.println();
    report.println(Text.format("  Répartition hebdomadaire        : %s", Format.dowBar(byDow)));
    report.println(
        Text.format("  Week-end                        : %.0f %%", 100.0 * weekend / dated));
    report.println(
        Text.format("  Hors 8h-19h (heure de l'auteur) : %.0f %%", 100.0 * outsideHours / dated));
    return 100.0 * weekend / dated;
  }

  /**
   * Volumes. stats est absent si le jeton ne peut pas lire le diff, et reste null plutôt que zéro :
   * « pas mesuré » n'est pas « rien changé ».
   *
   * @return la part de gros commits, en pourcents
   */
  private double volumes(List<Commit> real) {
    List<Long> sizes = new ArrayList<>();
    long added = 0;
    long deleted = 0;
    for (Commit x : real) {
      Commit.Stats stats = x.stats();
      if (stats == null) continue;
      long a = Format.orZero(stats.additions());
      long d = Format.orZero(stats.deletions());
      added += a;
      deleted += d;
      sizes.add(a + d);
    }
    if (sizes.isEmpty()) {
      report.println("  Volumes de lignes indisponibles (stats absentes de la réponse).", DIM);
      return 0;
    }
    Collections.sort(sizes);
    long big = sizes.stream().filter(s -> s > BIG_COMMIT).count();
    double bigShare = 100.0 * big / sizes.size();
    report.println();
    report.println(
        Text.format(
            "  Taille médiane d'un commit      : %.0f lignes", Format.percentile(sizes, 50)));
    report.println(
        Text.format(
            "  9e décile                       : %.0f lignes", Format.percentile(sizes, 90)));
    report.println(
        Text.format(
            "  Commits > %d lignes           : %d (%.0f %%) %s",
            BIG_COMMIT, big, bigShare, report.c("— imports, code généré, vendoring ?", DIM)));
    report.println(
        Text.format("  Lignes touchées / lignes nettes : %s", Format.rework(added, deleted)));
    report.println(
        "    (le net masque la réécriture : un fichier refait cinq fois ne bouge pas d'une ligne)",
        DIM);
    return bigShare;
  }
}
