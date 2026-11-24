package ch.ge.edg.sonar.gitlab.audit.cli.activity.report;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.model.CellFormat.pct;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.model.CellFormat.plural;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.report.Screen.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.report.Screen.YELLOW;

import ch.ge.edg.sonar.gitlab.audit.cli.activity.commits.CommitActivity;
import ch.ge.edg.sonar.gitlab.audit.cli.activity.commits.GraphqlDates;
import ch.ge.edg.sonar.gitlab.audit.cli.activity.inventory.Inventory;
import ch.ge.edg.sonar.gitlab.audit.cli.activity.model.AuditedProject;
import ch.ge.edg.sonar.gitlab.audit.cli.activity.model.CommitMeasure;
import ch.ge.edg.sonar.gitlab.audit.cli.activity.model.Fate;
import ch.ge.edg.sonar.gitlab.audit.cli.activity.model.Funnel;
import ch.ge.edg.sonar.gitlab.audit.cli.activity.model.FunnelResult;
import ch.ge.edg.sonar.gitlab.audit.cli.activity.selection.ActivityFloor;
import ch.ge.edg.sonar.gitlab.audit.cli.activity.selection.Selection;
import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Ce que l'entonnoir affiche, étape par étape, de ce que chaque étape a établi. */
public final class FunnelReport {

  private final Screen screen;
  private final Console out;
  private final Funnel funnel;

  public FunnelReport(Screen screen, Funnel funnel) {
    this.screen = screen;
    this.out = screen.out();
    this.funnel = funnel;
  }

  public int sinceDays() {
    return funnel.sinceDays();
  }

  /** inventaire.csv vient d'être écrit. */
  public void inventoryWritten(int rows) {
    out.printf("%n  Inventaire : %d lignes.%n", rows);
  }

  public void beginInventory() {
    screen.title("1. Inventaire (étape 0)");
  }

  public void inventory(Inventory.Census census) {
    List<AuditedProject> all = census.all();
    for (Inventory.Ignored i : census.ignored()) {
      out.printf("    %-40s %s — ignoré%n", i.path(), i.status());
    }
    out.printf("  Projets retournés               : %d%n", all.size());
    if (!census.exclusions().isEmpty()) {
      out.printf(
          "  Liste d'exclusion               : %s (%d entrées)%n",
          census.exclusions().file(), census.exclusions().entries().size());
    }
    Map<String, Long> byReason = new TreeMap<>();
    for (AuditedProject p : all) {
      if (p.fate() instanceof Fate.Excluded e) byReason.merge(e.reason(), 1L, Long::sum);
    }
    byReason.forEach((r, n) -> out.printf("    exclu — %-22s : %d%n", r, n));
    long kept = CommitActivity.inPlay(all).size();
    out.printf(
        "  Retenus pour analyse            : %d (%s du parc)%n", kept, pct(kept, all.size()));
    if (census.forbidden() > 0) {
      screen.say(
          Text.format(
              "    %d %s 403 : le jeton ne voit pas tout le parc.",
              census.forbidden(), census.forbidden() == 1 ? "réponse" : "réponses"),
          YELLOW);
      screen.say("    Un trou de permission raccourcit la liste sans lever d'erreur.", DIM);
    }
  }

  public void recency(List<AuditedProject> all) {
    screen.title("2. Filtre de fraîcheur (étape 1) — 0 appel");
    long kept = all.stream().filter(p -> !p.fate().excludedAt(Fate.Stage.INVENTORY)).count();
    long fresh = CommitActivity.inPlay(all).size();
    out.printf(
        "  Actifs sur %d jours              : %d / %d (%s)%n",
        funnel.sinceDays(), fresh, kept, pct(fresh, kept));
    screen.say("    Coupe la plus rentable du pipeline : gratuite.", DIM);
  }

  public void validation(List<AuditedProject> all) {
    long size = all.stream().filter(p -> p.commits() instanceof CommitMeasure.Sampled).count();
    if (size == 0) return;
    long leaks = all.stream().filter(p -> p.commits().leaked()).count();
    out.printf(
        "  Échantillon de contrôle sous la coupe : %d projets, %d faux négatif%s (%s)%n",
        size, leaks, plural(leaks), pct(leaks, size));
    if (leaks > 0) {
      screen.say("    Le filtre laisse passer des projets actifs. Chiffre à publier", YELLOW);
      screen.say("    tel quel dans le rapport : la sélection n'est pas exhaustive.", YELLOW);
    } else {
      screen.say("    Aucune fuite mesurée : la porte tient, et elle reste gratuite.", DIM);
    }
  }

  public void beginActivity() {
    screen.title("3. Activité réelle en commits (étape 2)");
  }

  public void dating(CommitActivity.Dated dated, CommitActivity activity) {
    GraphqlDates.Dating dating = dated.dating();
    if (!dating.available()) {
      screen.say("  GraphQL indisponible sur cette instance — repli REST.", DIM);
      return;
    }
    out.printf(
        "  Dernier commit daté par GraphQL : %d projets en %d requêtes%n",
        dating.dated(), dating.calls());
    long stale =
        CommitActivity.inPlay(dated.all()).stream()
            .filter(activity::lastCommitBeforeWindow)
            .count();
    out.printf("    dont aucun commit dans la fenêtre : %d%n", stale);
    screen.say("    (last_activity_at récent sans commit récent : tickets, wiki, CI)", DIM);
  }

  /** {@code all} après la coupe : les écartés y portent leur motif. */
  public void cut(List<AuditedProject> all) {
    int activeWithin = funnel.options().activeWithinDays;
    if (activeWithin <= 0) return;
    List<AuditedProject> fresh = fresh(all);
    long tooQuiet = all.stream().filter(p -> p.fate().excludedAt(Fate.Stage.ACTIVITY_CUT)).count();
    out.printf(
        "  Coupe --active-within %-3d       : %d projets écartés sur %d%n",
        activeWithin, tooQuiet, fresh.size());
    screen.say("    Écartés sans être comptés : « pas regardé » n'est pas « rien vu ».", DIM);
    boolean byCommit = fresh.stream().anyMatch(x -> x.head().lastCommit() != null);
    screen.say(
        byCommit
            ? "    Gratuit — la date vient de la passe GraphQL, pas d'un appel de plus."
            : "    Gratuit — GraphQL indisponible, la coupe porte sur last_activity_at,\n"
                + "    qui majore l'activité : elle écarte donc moins, jamais à tort.",
        DIM);
  }

  /** {@code all} après le comptage REST de {@code counted} projets. */
  public void counts(List<AuditedProject> all, int counted) {
    List<AuditedProject> fresh = fresh(all);
    long active = fresh.stream().filter(p -> p.commitsOrZero() > 0).count();
    out.printf(
        "  Comptés par pagination REST     : %d %s%n",
        counted, counted == 1 ? "projet" : "projets");
    out.printf("  Avec au moins un commit humain  : %d%n", active);
    long botOnly = fresh.stream().filter(p -> p.commits().botOnly()).count();
    if (botOnly > 0) {
      out.printf("  Activité exclusivement robotique : %d%n", botOnly);
      screen.say("    Renovate seul sur un dépôt est un constat, pas une ligne vide.", DIM);
    }
  }

  /** Les projets passés par le filtre de fraîcheur : en lice, ou écartés depuis. */
  private static List<AuditedProject> fresh(List<AuditedProject> all) {
    return all.stream()
        .filter(
            p ->
                !p.fate().excludedAt(Fate.Stage.INVENTORY)
                    && !p.fate().excludedAt(Fate.Stage.RECENCY))
        .toList();
  }

  public void floor(ActivityFloor.Floor floor) {
    screen.title("4. Plancher d'activité (étape 3)");
    List<Integer> volumes = floor.volumes();
    out.print("  Distribution des commits (déciles) :");
    for (int d = 1; d <= 9; d += 2) {
      int idx = Math.min(volumes.size() - 1, volumes.size() * d / 10);
      out.printf(" D%d=%d", d, volumes.isEmpty() ? 0 : volumes.get(idx));
    }
    out.println();
    if (floor.imposed()) {
      out.printf("  Plancher imposé (--floor)       : %d commits%n", floor.threshold());
    } else {
      out.printf(
          "  Plancher déduit des données     : %d commits sur %d jours%n",
          floor.threshold(), funnel.sinceDays());
    }
    floorCounts(floor.all());
  }

  private void floorCounts(List<AuditedProject> all) {
    List<AuditedProject> below =
        all.stream().filter(p -> p.fate().excludedAt(Fate.Stage.FLOOR)).toList();
    long unmeasurable = below.stream().filter(p -> p.commits().human() == null).count();
    long eligible = all.stream().filter(p -> p.fate() instanceof Fate.Eligible).count();
    out.printf("  Éligibles au tirage             : %d%n", eligible);
    out.printf("  Cohorte faible activité         : %d%n", below.size() - unmeasurable);
    if (unmeasurable > 0) {
      out.printf("  Activité non mesurable          : %d%n", unmeasurable);
      screen.say("    403 ou erreur sur les commits. Écartés, pas classés à zéro :", YELLOW);
      screen.say("    « je n'ai pas pu voir » n'est pas « il ne se passe rien ».", YELLOW);
    }
    screen.say("    Cette cohorte n'est pas perdue : elle se rapporte en effectifs,", DIM);
    screen.say("    comme les « jamais analysés » côté Sonar. C'est un constat.", DIM);
  }

  public void selection(FunnelResult result) {
    screen.title("5. Sélection par quotas (étape 4)");
    if (result.eligible() == 0) {
      screen.say("  Aucun projet au-dessus du plancher.", YELLOW);
      return;
    }
    List<AuditedProject> selected = result.selected();
    int top = funnel.options().top;
    out.printf("  Budget                          : %d%n", top);
    out.printf("  Sélectionnés                    : %d%n", selected.size());
    selected.stream()
        .collect(
            Collectors.groupingBy(
                p -> {
                  String reason = p.fate().selectionReason();
                  return reason.startsWith("tête") ? "tête de strate" : reason;
                },
                TreeMap::new,
                Collectors.counting()))
        .forEach((r, n) -> out.printf("    %-32s : %d%n", r, n));
    long namespaces = selected.stream().map(p -> p.project().namespace()).distinct().count();
    out.printf(
        "  Namespaces représentés          : %d (plafond %d par namespace)%n",
        namespaces, Selection.namespaceCap(top));
    screen.say("    Le témoin permettra d'écrire « couverture X sur les sélectionnés,", DIM);
    screen.say("    Y sur un tirage du reste » — et si X et Y divergent, la règle", DIM);
    screen.say("    de sélection est elle-même le sujet.", DIM);
  }
}
