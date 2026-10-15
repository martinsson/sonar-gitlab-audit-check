package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Console.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Console.YELLOW;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Étape 4 : quotas, pas top-N.
 *
 * <p>Prendre les 200 premiers par volume de commits, c'est refaire l'erreur du classement Sonar par
 * sqale_index : on récupère les gros monolithes actifs de deux ou trois équipes et on apprend ce
 * qu'on savait déjà.
 *
 * <p>Quatre tranches, donc : le haut de chaque strate de taille, un plafond par namespace, les cas
 * mono-auteur que le classement brut enterre, et un témoin aléatoire.
 */
final class Selection {

  private final AuditContext ctx;
  private final int top;
  private final int nsCap;
  private final Map<String, Integer> perNamespace = new HashMap<>();
  private final List<Proj> selected = new ArrayList<>();

  Selection(AuditContext ctx) {
    this.ctx = ctx;
    this.top = ctx.funnel().top;
    this.nsCap = Math.max(3, (int) Math.ceil(top * 0.15));
  }

  void run(List<Proj> fresh) {
    ctx.con().title("5. Sélection par quotas (étape 4)");

    List<Proj> eligible =
        fresh.stream()
            .filter(p -> p.excluded == null)
            .collect(Collectors.toCollection(ArrayList::new));
    if (eligible.isEmpty()) {
      ctx.con().say("  Aucun projet au-dessus du plancher.", YELLOW);
      return;
    }
    for (Proj p : eligible) p.bucket = bucketOf(p);

    strataHeads(eligible, Math.max(1, top * 70 / 100));
    singleAuthors(eligible, Math.max(1, top * 10 / 100));
    witness(eligible, Math.max(1, top * 15 / 100));
    remainder(eligible);

    ctx.counters().selected = selected.size();
    report();
  }

  /**
   * 1. Tête de chaque strate, en tourniquet pour que les petites strates ne soient pas noyées par
   * les grosses.
   */
  private void strataHeads(List<Proj> eligible, int coreQuota) {
    Map<String, List<Proj>> byBucket =
        eligible.stream()
            .sorted(byWeeklyCommits())
            .collect(Collectors.groupingBy(p -> p.bucket, TreeMap::new, Collectors.toList()));
    // Un curseur par strate : ce qu'il a dépassé — pris, ou namespace plein — ne revient pas.
    List<Iterator<Proj>> strata = byBucket.values().stream().map(List::iterator).toList();
    boolean progressed = true;
    while (selected.size() < coreQuota && progressed) {
      progressed = false;
      for (Iterator<Proj> stratum : strata) {
        if (selected.size() >= coreQuota) break;
        Proj next = nextCandidate(stratum);
        if (next != null) {
          take(next, "tête de strate " + next.bucket);
          progressed = true;
        }
      }
    }
  }

  private @Nullable Proj nextCandidate(Iterator<Proj> stratum) {
    while (stratum.hasNext()) {
      Proj p = stratum.next();
      if (!p.selected && !namespaceFull(p)) return p;
    }
    return null;
  }

  /**
   * 2. Mono-auteur à forte activité : un bus factor qui compte vraiment, et que le classement par
   * volume ne fait jamais remonter.
   */
  private void singleAuthors(List<Proj> eligible, int busQuota) {
    eligible.stream()
        .filter(p -> !p.selected && p.authors != null && p.authors <= 1)
        .sorted(Comparator.comparingInt(Proj::commitsOrZero).reversed())
        .limit(busQuota)
        .forEach(p -> take(p, "mono-auteur, forte activité"));
  }

  /**
   * 3. Témoin aléatoire. C'est la tranche qui a l'air d'un gâchis et qui n'en est pas une : sans
   * elle, chaque affirmation du rapport final est conditionnée à une règle de sélection que
   * personne n'a validée.
   */
  private void witness(List<Proj> eligible, int controlQuota) {
    List<Proj> rest =
        eligible.stream().filter(p -> !p.selected).collect(Collectors.toCollection(ArrayList::new));
    Collections.shuffle(rest, new Random(ctx.funnel().seed));
    rest.stream().limit(controlQuota).forEach(p -> take(p, "témoin aléatoire"));
  }

  /**
   * Une tranche qui ne trouve pas preneur ne doit pas laisser du budget sur la table : le reliquat
   * retourne au classement, plafond de namespace inclus.
   */
  private void remainder(List<Proj> eligible) {
    if (selected.size() >= top) return;
    for (Proj p : eligible.stream().sorted(byWeeklyCommits()).toList()) {
      if (selected.size() >= top) break;
      if (p.selected || namespaceFull(p)) continue;
      take(p, "reliquat de budget");
    }
  }

  private Comparator<Proj> byWeeklyCommits() {
    int days = ctx.sinceDays();
    return Comparator.comparingDouble((Proj p) -> p.perWeek(days)).reversed();
  }

  private boolean namespaceFull(Proj p) {
    return perNamespace.getOrDefault(p.namespace, 0) >= nsCap;
  }

  private void take(Proj p, String reason) {
    p.selected = true;
    p.selectionReason = reason;
    selected.add(p);
    perNamespace.merge(p.namespace, 1, Integer::sum);
  }

  private void report() {
    PrintStream out = ctx.out();
    out.printf("  Budget                          : %d%n", top);
    out.printf("  Sélectionnés                    : %d%n", selected.size());
    selected.stream()
        .collect(
            Collectors.groupingBy(
                p -> p.selectionReason.startsWith("tête") ? "tête de strate" : p.selectionReason,
                TreeMap::new,
                Collectors.counting()))
        .forEach((r, n) -> out.printf("    %-32s : %d%n", r, n));
    out.printf(
        "  Namespaces représentés          : %d (plafond %d par namespace)%n",
        perNamespace.size(), nsCap);
    ctx.con().say("    Le témoin permettra d'écrire « couverture X sur les sélectionnés,", DIM);
    ctx.con().say("    Y sur un tirage du reste » — et si X et Y divergent, la règle", DIM);
    ctx.con().say("    de sélection est elle-même le sujet.", DIM);
  }

  /** Strates sur le volume historique : une même mesure ne se compare qu'à taille comparable. */
  static String bucketOf(Proj p) {
    Long total = p.totalCommits;
    Long size = p.repoSize;
    long c = total != null ? total : size != null ? size / 20_000 : 0;
    if (c < 100) return "A(<100)";
    if (c < 1_000) return "B(<1k)";
    if (c < 10_000) return "C(<10k)";
    return "D(>10k)";
  }
}
