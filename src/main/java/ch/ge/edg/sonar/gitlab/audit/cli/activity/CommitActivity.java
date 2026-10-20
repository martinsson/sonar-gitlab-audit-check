package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Screen.DIM;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Étape 2 : activité réelle, en commits. */
final class CommitActivity {

  private final Gitlab gl;
  private final Screen con;
  private final Counters counters;
  private final Funnel funnel;

  CommitActivity(Gitlab gl, Screen con, Counters counters, Funnel funnel) {
    this.gl = gl;
    this.con = con;
    this.counters = counters;
    this.funnel = funnel;
  }

  void run(List<Proj> fresh) {
    con.title("3. Activité réelle en commits (étape 2)");
    if (funnel.options().graphql) dateByGraphql(fresh);

    // La date du dernier commit vient de coûter un appel pour cinquante
    // projets. Elle décide donc gratuitement qui mérite la pagination REST,
    // qui est le seul poste de l'étape 2 à coûter un appel par projet.
    int activeWithin = funnel.options().activeWithinDays;
    OffsetDateTime cut =
        activeWithin > 0 ? funnel.now().minusDays(activeWithin) : funnel.windowStart();
    List<Proj> toCount = new ArrayList<>();
    int tooQuiet = 0;
    for (Proj p : fresh) {
      // Si GraphQL a daté le dernier commit hors fenêtre, le compte est zéro
      // par construction : inutile de le redemander en REST.
      if (lastCommitBeforeWindow(p)) {
        p.commits = 0;
        p.commitsBots = 0;
        p.authors = 0;
      } else if (belowCut(p, cut)) {
        tooQuiet++;
      } else {
        toCount.add(p);
      }
    }
    if (activeWithin > 0) reportCut(fresh, tooQuiet, activeWithin);

    // Un appel par projet, sans lien entre eux : c'est le poste le plus
    // lourd de l'outil sur un gros parc, et il n'avait aucune raison d'être
    // séquentiel.
    gl.parallel(toCount, funnel.concurrency(), new CommitPager(gl, funnel)::measure);
    counters.commitPassed = toCount.size();
    counters.notCounted = tooQuiet;
    reportCounts(fresh, toCount.size());
  }

  private void dateByGraphql(List<Proj> fresh) {
    int refined = new GraphqlDates(gl, con, counters).date(fresh);
    if (refined < 0) return;
    Console out = con.out();
    out.out()
        .printf(
            "  Dernier commit daté par GraphQL : %d projets en %d requêtes%n",
            refined, counters.graphqlCalls);
    long stale = fresh.stream().filter(this::lastCommitBeforeWindow).count();
    out.out().printf("    dont aucun commit dans la fenêtre : %d%n", stale);
    con.say("    (last_activity_at récent sans commit récent : tickets, wiki, CI)", DIM);
  }

  private boolean lastCommitBeforeWindow(Proj p) {
    OffsetDateTime last = p.lastCommit;
    return last != null && last.isBefore(funnel.windowStart());
  }

  /**
   * Et s'il est daté avant la coupe demandée, le projet n'est pas de ceux qu'on veut regarder : on
   * ne le compte pas, et surtout on ne le note pas zéro — il a des commits, on a choisi de ne pas
   * les compter. La nuance tient tout le reste du rapport.
   *
   * <p>À défaut de date de commit — GraphQL indisponible — on retombe sur last_activity_at, qui se
   * trompe dans le sens sûr : il est gonflé par les événements non-commit, donc un projet sous la
   * coupe selon lui l'est forcément selon ses commits. La coupe reste gratuite.
   */
  private boolean belowCut(Proj p, OffsetDateTime cut) {
    int activeWithin = funnel.options().activeWithinDays;
    OffsetDateTime seen = p.lastCommit != null ? p.lastCommit : p.lastActivity;
    if (activeWithin <= 0 || seen == null || !seen.isBefore(cut)) return false;
    p.excluded =
        "hors coupe d'activité (%s > %dj)"
            .formatted(p.lastCommit != null ? "dernier commit" : "dernière activité", activeWithin);
    return true;
  }

  private void reportCut(List<Proj> fresh, int tooQuiet, int activeWithin) {
    con.out()
        .out()
        .printf(
            "  Coupe --active-within %-3d       : %d projets écartés sur %d%n",
            activeWithin, tooQuiet, fresh.size());
    con.say("    Écartés sans être comptés : « pas regardé » n'est pas « rien vu ».", DIM);
    boolean byCommit = fresh.stream().anyMatch(x -> x.lastCommit != null);
    con.say(
        byCommit
            ? "    Gratuit — la date vient de la passe GraphQL, pas d'un appel de plus."
            : "    Gratuit — GraphQL indisponible, la coupe porte sur last_activity_at,\n"
                + "    qui majore l'activité : elle écarte donc moins, jamais à tort.",
        DIM);
  }

  private void reportCounts(List<Proj> fresh, int counted) {
    Console out = con.out();
    long active = fresh.stream().filter(p -> p.commits != null && p.commits > 0).count();
    out.out().printf("  Comptés par pagination REST     : %d projets%n", counted);
    out.out().printf("  Avec au moins un commit humain  : %d%n", active);
    long botOnly = fresh.stream().filter(CommitActivity::botOnly).count();
    if (botOnly > 0) {
      out.out().printf("  Activité exclusivement robotique : %d%n", botOnly);
      con.say("    Renovate seul sur un dépôt est un constat, pas une ligne vide.", DIM);
    }
  }

  static boolean botOnly(Proj p) {
    Integer human = p.commits;
    Integer bots = p.commitsBots;
    return human != null && human == 0 && bots != null && bots > 0;
  }
}
