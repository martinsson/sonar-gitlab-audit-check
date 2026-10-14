package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.BOLD;
import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.YELLOW;
import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.c;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** 3. Contributeurs : combien de gens, et quelle concentration ? */
final class PeopleSection {

  private final Report report;
  private final ProjectApi api;
  private final Settings settings;
  private final LocalDateTime now;

  PeopleSection(Report report, ProjectApi api, Settings settings, LocalDateTime now) {
    this.report = report;
    this.api = api;
    this.settings = settings;
    this.now = now;
  }

  People measure(Project p, Commits commits) {
    report.title("3. Contributeurs et concentration");

    // Hors commits de fusion, ici encore : le mainteneur qui clique « fusionner »
    // n'a pas écrit le code, et compter ses fusions le sacre premier contributeur.
    List<Commit> cs = commits.humans().stream().filter(x -> !x.isMerge()).toList();
    if (cs.isEmpty()) {
      report.println("  Pas de commit sur la fenêtre : rien à mesurer.", DIM);
      return People.EMPTY;
    }

    Authors authors = Authors.of(cs);
    report.println(
        "  Auteurs distincts               : %s"
            .formatted(c(String.valueOf(authors.count()), BOLD)));
    regrouped(cs, authors.count());

    int busFactor = authors.busFactor();
    Map.Entry<String, Long> top = authors.ranked().get(0);
    double topShare = authors.share(top);
    report.println(
        "  Auteur principal                : %s (%.0f %% des commits)"
            .formatted(authors.label(top.getKey()), topShare));
    report.println(
        "  Personnes couvrant 80 %% du code : %s"
            .formatted(c(String.valueOf(busFactor), busFactor <= 1 ? YELLOW : BOLD)));
    leaders(authors);

    long activeRecently = activeRecently(cs, authors.count());
    bots(commits);
    allTime(p);
    return new People(authors.count(), busFactor, topShare, activeRecently);
  }

  /** Ce que la normalisation a regroupé, dit plutôt que subi. */
  private void regrouped(List<Commit> cs, int persons) {
    // Le compte brut d'adresses est celui qu'on lirait sans la normalisation.
    long distinctEmails = distinct(cs, Commit::authorEmail);
    if (distinctEmails > persons) {
      report.println(
          "  Adresses distinctes             : %d %s"
              .formatted(distinctEmails, c("— ramenées à %d personnes".formatted(persons), DIM)));
    }
    // Une identité qui porte plusieurs noms, c'est soit un poste partagé, soit
    // deux homonymes de partie locale sur des domaines différents — auquel cas
    // la normalisation a fusionné deux personnes, et le bus factor est optimiste.
    long distinctNames = distinct(cs, Commit::authorName);
    if (distinctNames > persons) {
      report.println(
          "  Noms distincts                  : %d %s"
              .formatted(distinctNames, c("— une identité porte plusieurs noms", DIM)));
    }
  }

  private static long distinct(List<Commit> cs, Function<Commit, @Nullable String> field) {
    return cs.stream()
        .map(x -> Format.orEmpty(field.apply(x)).trim().toLowerCase(Locale.ROOT))
        .filter(s -> !s.isBlank())
        .distinct()
        .count();
  }

  private void leaders(Authors authors) {
    int shown = Math.min(5, authors.count());
    report.println();
    for (Map.Entry<String, Long> e : authors.ranked().subList(0, shown)) {
      report.println(
          "    %-34s %4d  %s"
              .formatted(authors.label(e.getKey()), e.getValue(), Format.bar(authors.share(e))));
    }
    if (authors.count() > shown) {
      report.println("    … et %d autres".formatted(authors.count() - shown), DIM);
    }
  }

  /**
   * Actifs récemment vs actifs sur la fenêtre : l'écart, ce sont les gens partis. Un fichier dont
   * tous les auteurs ont quitté le projet n'a plus de propriétaire, quel que soit son état de
   * santé.
   */
  private long activeRecently(List<Commit> cs, int persons) {
    int recentDays = Math.min(90, settings.days());
    LocalDateTime recent = now.minusDays(recentDays);
    long activeRecently =
        cs.stream()
            .filter(x -> isAfter(Dates.parseLocal(x.committedDate()), recent))
            .map(Authors::identity)
            .distinct()
            .count();
    report.println();
    report.println(
        "  %-32s: %d sur %d".formatted("Actifs sur " + recentDays + " j", activeRecently, persons));
    return activeRecently;
  }

  private static boolean isAfter(@Nullable LocalDateTime d, LocalDateTime t) {
    return d != null && d.isAfter(t);
  }

  private void bots(Commits commits) {
    if (commits.bots().isEmpty()) return;
    Set<String> botNames =
        commits.bots().stream()
            .map(Commit::authorName)
            .filter(s -> !Format.isBlank(s))
            .collect(Collectors.toCollection(TreeSet::new));
    report.println("  Écartés comme bots              : " + String.join(", ", botNames), DIM);
  }

  /**
   * Vue « toute l'histoire », en un appel : elle situe la fenêtre dans la durée de vie du dépôt.
   * Ses comptes incluent les commits de fusion — ils ne sont donc pas comparables terme à terme
   * avec ceux du dessus.
   */
  private void allTime(Project p) {
    Gitlab.Page<Contributor> f = api.contributors(p);
    if (f.error() == null && !f.items().isEmpty()) {
      report.println(
          "  Contributeurs de tout temps     : %d %s"
              .formatted(f.items().size(), c("(commits de fusion inclus)", DIM)));
    }
  }
}
