package ch.ge.edg.sonar.gitlab.audit.cli.rank;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Ce que le classement dit à la console : gouvernance, shortlist, listes annexes, fichier. */
final class RankReport {

  public static final String ESC = String.valueOf((char) 27);
  public static final String BOLD = ESC + "[1m";
  public static final String DIM = ESC + "[2m";
  public static final String RED = ESC + "[31m";
  public static final String YELLOW = ESC + "[33m";
  public static final String RESET = ESC + "[0m";

  private final Console out;
  private final RankSettings settings;

  RankReport(Console out, RankSettings settings) {
    this.out = out;
    this.settings = settings;
  }

  // ----------------------------------------------------------------------
  // 1. Gouvernance
  // ----------------------------------------------------------------------

  void governance(List<InventoryRow> all, Cohorts cohorts) {
    title("1. Gouvernance (constats qui ne demandent aucun score)");

    int total = all.size();
    int never = cohorts.never().size();
    int stale = cohorts.stale().size();
    int active = cohorts.active().size();
    int staleDays = settings.staleDays();

    out.out().printf("  Projets dans l'inventaire       : %d%n", total);
    out.out().printf("  Jamais analysés                 : %s%n", pct(never, total, YELLOW));
    out.out()
        .printf("  Analysés, obsolètes (> %d j)    : %s%n", staleDays, pct(stale, total, YELLOW));
    out.out().printf("  Actifs (< %d j)                 : %s%n", staleDays, pct(active, total, ""));

    coverageBlindSpot(all, cohorts.active());

    out.out().printf("%n  → Population classable          : %d projets actifs.%n", active);
  }

  /** Le point aveugle de tout tri par couverture : ces projets n'y figurent pas. */
  private void coverageBlindSpot(List<InventoryRow> all, List<InventoryRow> active) {
    long noCoverageAll = all.stream().filter(r -> r.num("coverage") == null).count();
    long noCoverageActive = active.stream().filter(r -> r.num("coverage") == null).count();
    out.println();
    out.out().printf("  Sans donnée de couverture       : %d sur %d%n", noCoverageAll, all.size());
    out.out()
        .printf(
            "    dont projets actifs           : %s%n",
            pct((int) noCoverageActive, active.size(), RED));
    if (noCoverageActive > 0) {
      // Le bloc texte retire l'indentation commune : le décalage de la
      // fermeture est ce qui donne les quatre espaces d'alignement.
      out.println(
          c(
              """
                    Un projet analysé obtient coverage=0.0 dès que son analyseur
                    rapporte des lignes couvrables. Une couverture *absente* sur un
                    projet actif signale donc un langage sans instrumentation, ou un
                    rapport jamais branché sur le scanner — pas du code non testé.
                    C'est un constat d'outillage, et il ne se classe pas.\
                """,
              DIM));
    }
  }

  // ----------------------------------------------------------------------
  // 2. Classement vélocité
  // ----------------------------------------------------------------------

  void velocity(List<Scored> scored) {
    title("2. Créent de la dette (classement vélocité, projets actifs)");

    List<Scored> ranked = scored.stream().filter(Scored::ranked).toList();
    List<Scored> shortlist = ranked.stream().filter(s -> s.shortlisted).toList();

    if (ranked.isEmpty()) {
      out.println(
          c(
              scored.isEmpty()
                  ? "  Aucun projet actif dans cet inventaire : rien à classer."
                  : "  Aucun projet actif ne réunit assez de signaux pour être classé.",
              YELLOW));
      exclusions(scored);
      return;
    }

    out.out().printf("  Projets classés                 : %d%n", ranked.size());
    out.out()
        .printf(
            "  Retenus (décile haut / strate)  : %s%n%n",
            c(String.valueOf(shortlist.size()), BOLD));
    shortlistTable(shortlist);
    partialNote(ranked);
    exclusions(scored);
  }

  private void shortlistTable(List<Scored> shortlist) {
    out.out()
        .printf(
            "  %-4s %-44s %-7s %8s %7s %6s%n", "#", "projet", "strate", "ncloc", "score", "sig.");
    int rank = 0;
    for (Scored s : shortlist) {
      out.out()
          .printf(
              "  %-4d %-44s %-7s %8.0f %7.1f %6s%n",
              ++rank,
              Text.ellipsize(s.row.label(), 44),
              s.bucket.name(),
              s.row.ncloc(),
              s.score,
              s.signalsPresent() + "/4");
    }
  }

  private void partialNote(List<Scored> ranked) {
    long partial = ranked.stream().filter(s -> s.signalsPresent() < Signal.values().length).count();
    if (partial == 0) return;
    out.out()
        .printf(
            "%n  %s%n",
            c(
                ("%d des %d projets classés le sont sur un " + "sous-ensemble de signaux.")
                    .formatted(partial, ranked.size()),
                DIM));
    out.println(
        c("  Une métrique absente est retirée de la moyenne, jamais " + "comptée comme 0 :", DIM));
    out.println(c("  lire signaux_presents avant de comparer deux scores.", DIM));
  }

  /** Un classement qui ne dit pas ce qu'il a écarté se lit comme complet. */
  private void exclusions(List<Scored> scored) {
    Map<Exclusion, Long> counts = new EnumMap<>(Exclusion.class);
    for (Scored s : scored) {
      if (!s.ranked()) counts.merge(s.exclusion, 1L, Long::sum);
    }
    if (counts.isEmpty()) return;
    out.println("\n  Écartés du classement :");
    counts.forEach((e, n) -> out.out().printf("    %-38s %d%n", e.label, n));
  }

  // ----------------------------------------------------------------------
  // 3. Listes annexes, et le fichier
  // ----------------------------------------------------------------------

  void sideLists(List<Scored> carrying, List<Scored> abandoned) {
    title("3. Listes annexes");
    out.out()
        .printf(
            "  Portent la dette sans en créer  : %d " + "(actifs, aucune ligne neuve)%n",
            carrying.size());
    out.out()
        .printf(
            "  Abandons récents (%d-%d j)     : %d%n",
            settings.staleDays(), settings.abandonedDays(), abandoned.size());
    out.println(
        c(
            "    Les deux se classent sur sqale_debt_ratio seul : " + "un niveau, pas une vitesse.",
            DIM));
  }

  void written(Path file, boolean comma) {
    out.out().printf("%n  Classement écrit : %s%n", file.toAbsolutePath());
    out.println(c("  Filtrer la colonne 'liste', puis 'retenu' = O " + "pour la shortlist.", DIM));
    if (!comma) {
      out.println(
          c(
              "  Format Excel (point-virgule + BOM UTF-8) : "
                  + "double-clic, ou `start "
                  + file
                  + "`.",
              DIM));
      out.println(c("  Pour un autre outil : relancer avec --comma.", DIM));
    }
  }

  // ----------------------------------------------------------------------
  // Présentation
  // ----------------------------------------------------------------------

  private void title(String text) {
    out.println("\n" + c(text, BOLD));
    out.println("-".repeat(Math.min(78, text.length() + 4)));
  }

  String c(String s, String color) {
    return color.isEmpty() ? s : out.color(s, color);
  }

  String pct(int n, int total, String color) {
    String s = total == 0 ? String.valueOf(n) : "%d (%.0f%%)".formatted(n, 100.0 * n / total);
    return c(s, color);
  }
}
