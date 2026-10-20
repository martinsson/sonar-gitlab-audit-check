package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
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

  private final Funnel funnel;
  private final int top;
  private final int nsCap;
  private final Map<String, Integer> perNamespace = new HashMap<>();
  private final IdentityHashMap<AuditedProject, String> taken = new IdentityHashMap<>();

  private Selection(Funnel funnel) {
    this.funnel = funnel;
    this.top = funnel.options().top;
    this.nsCap = namespaceCap(top);
  }

  /** Le plafond par namespace : 15 % du budget, et jamais moins de trois. */
  static int namespaceCap(int top) {
    return Math.max(3, (int) Math.ceil(top * 0.15));
  }

  /** Chaque éligible tiré devient sélectionné, avec le motif de son entrée. */
  static List<AuditedProject> run(Funnel funnel, List<AuditedProject> all) {
    return new Selection(funnel).select(all);
  }

  private List<AuditedProject> select(List<AuditedProject> all) {
    List<AuditedProject> eligible =
        all.stream().filter(p -> p.fate() instanceof Fate.Eligible).toList();
    if (eligible.isEmpty()) return all;

    strataHeads(eligible, Math.max(1, top * 70 / 100));
    singleAuthors(eligible, Math.max(1, top * 10 / 100));
    witness(eligible, Math.max(1, top * 15 / 100));
    remainder(eligible);

    return all.stream()
        .map(
            p -> {
              String reason = taken.get(p);
              return reason == null ? p : p.with(new Fate.Selected(p.fate().bucket(), reason));
            })
        .toList();
  }

  /**
   * 1. Tête de chaque strate, en tourniquet pour que les petites strates ne soient pas noyées par
   * les grosses.
   */
  private void strataHeads(List<AuditedProject> eligible, int coreQuota) {
    Map<String, List<AuditedProject>> byBucket =
        eligible.stream()
            .sorted(byWeeklyCommits())
            .collect(
                Collectors.groupingBy(p -> p.fate().bucket(), TreeMap::new, Collectors.toList()));
    // Un curseur par strate : ce qu'il a dépassé — pris, ou namespace plein — ne revient pas.
    List<Iterator<AuditedProject>> strata = byBucket.values().stream().map(List::iterator).toList();
    boolean progressed = true;
    while (taken.size() < coreQuota && progressed) {
      progressed = false;
      for (Iterator<AuditedProject> stratum : strata) {
        if (taken.size() >= coreQuota) break;
        AuditedProject next = nextCandidate(stratum);
        if (next != null) {
          take(next, "tête de strate " + next.fate().bucket());
          progressed = true;
        }
      }
    }
  }

  private @Nullable AuditedProject nextCandidate(Iterator<AuditedProject> stratum) {
    while (stratum.hasNext()) {
      AuditedProject p = stratum.next();
      if (!isTaken(p) && !namespaceFull(p)) return p;
    }
    return null;
  }

  /**
   * 2. Mono-auteur à forte activité : un bus factor qui compte vraiment, et que le classement par
   * volume ne fait jamais remonter.
   */
  private void singleAuthors(List<AuditedProject> eligible, int busQuota) {
    eligible.stream()
        .filter(p -> !isTaken(p) && isSingleAuthor(p))
        .sorted(Comparator.comparingInt(AuditedProject::commitsOrZero).reversed())
        .limit(busQuota)
        .forEach(p -> take(p, "mono-auteur, forte activité"));
  }

  /**
   * 3. Témoin aléatoire. C'est la tranche qui a l'air d'un gâchis et qui n'en est pas une : sans
   * elle, chaque affirmation du rapport final est conditionnée à une règle de sélection que
   * personne n'a validée.
   */
  private void witness(List<AuditedProject> eligible, int controlQuota) {
    List<AuditedProject> rest =
        eligible.stream().filter(p -> !isTaken(p)).collect(Collectors.toCollection(ArrayList::new));
    Collections.shuffle(rest, new Random(funnel.options().seed));
    rest.stream().limit(controlQuota).forEach(p -> take(p, "témoin aléatoire"));
  }

  /**
   * Une tranche qui ne trouve pas preneur ne doit pas laisser du budget sur la table : le reliquat
   * retourne au classement, plafond de namespace inclus.
   */
  private void remainder(List<AuditedProject> eligible) {
    if (taken.size() >= top) return;
    for (AuditedProject p : eligible.stream().sorted(byWeeklyCommits()).toList()) {
      if (taken.size() >= top) break;
      if (isTaken(p) || namespaceFull(p)) continue;
      take(p, "reliquat de budget");
    }
  }

  private static boolean isSingleAuthor(AuditedProject p) {
    Integer authors = p.commits().authors();
    return authors != null && authors <= 1;
  }

  private Comparator<AuditedProject> byWeeklyCommits() {
    int days = funnel.sinceDays();
    return Comparator.comparingDouble((AuditedProject p) -> p.perWeek(days)).reversed();
  }

  private boolean isTaken(AuditedProject p) {
    return taken.containsKey(p);
  }

  private boolean namespaceFull(AuditedProject p) {
    return perNamespace.getOrDefault(p.project().namespace(), 0) >= nsCap;
  }

  private void take(AuditedProject p, String reason) {
    taken.put(p, reason);
    perNamespace.merge(p.project().namespace(), 1, Integer::sum);
  }

  /** Strates sur le volume historique : une même mesure ne se compare qu'à taille comparable. */
  static String bucketOf(GitlabProject p) {
    Long total = p.repository().commitCount();
    Long size = p.repository().size();
    long c = total != null ? total : size != null ? size / 20_000 : 0;
    if (c < 100) return "A(<100)";
    if (c < 1_000) return "B(<1k)";
    if (c < 10_000) return "C(<10k)";
    return "D(>10k)";
  }
}
