package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.params;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.iso;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Consumer;

/** Les commits de la branche par défaut sur la fenêtre, lus en REST. */
final class CommitPager {

  private final Gitlab gl;
  private final Funnel funnel;

  CommitPager(Gitlab gl, Funnel funnel) {
    this.gl = gl;
    this.funnel = funnel;
  }

  /** L'issue d'une pagination : lue jusqu'au bout, plafonnée, ou interrompue sur un statut. */
  record Walk(boolean ok, int status, boolean truncated) {}

  /** Compte sans pagination quand X-Total est là ; sinon pagine. */
  int count(Proj p) {
    String since = iso(funnel.windowStart());
    Gitlab.Response r =
        gl.get(path(p), params("ref_name", p.defaultBranch, "since", since, "per_page", "1"));
    if (r.status() != 200) return 0;
    Integer total = r.intHeader("x-total");
    if (total != null) return total;
    // GitLab omet X-Total quand le comptage serait coûteux. Absent ne veut pas
    // dire zéro : on redemande une page pleine plutôt que de lire un en-tête
    // manquant comme un 0 — c'est exactement la confusion « absent = zéro »
    // qui a déjà coûté cher côté Sonar.
    Gitlab.Response full =
        gl.get(path(p), params("ref_name", p.defaultBranch, "since", since, "per_page", "100"));
    return full.status() == 200 && full.json().isArray() ? full.json().size() : 0;
  }

  /**
   * Une seule passe de pagination donne quatre choses : le volume, les auteurs distincts, le taux
   * de commits de merge et l'étalement dans le temps. On la fait une fois et on garde les commits
   * bruts.
   */
  void measure(Proj p) {
    CommitTally tally = new CommitTally(funnel.windowStart(), funnel.bots());
    Walk w = walk(p, false, each(tally::add));
    // Refus de permission ou erreur : on ne sait pas, et « on ne sait
    // pas » ne doit surtout pas se lire comme « aucune activité ».
    p.commits =
        w.ok()
            ? tally.result(p.head, w.truncated())
            : new CommitMeasure.Unmeasurable("HTTP " + w.status());
  }

  /** Ce que reçoit chaque page non vide : son numéro et ses commits. */
  interface PageVisitor {
    void visit(int page, JsonNode commits);
  }

  /**
   * Parcourt les pages de cent jusqu'à une page incomplète ou au plafond --max-commit-pages, qui
   * marque le résultat tronqué. Le premier statut autre que 200 arrête tout.
   */
  Walk walk(Proj p, boolean withStats, PageVisitor visitor) {
    int max = funnel.options().maxCommitPages;
    boolean truncated = false;
    for (int page = 1; page <= max; page++) {
      Gitlab.Response r = gl.get(path(p), Api.commitPage(p, funnel.windowStart(), page, withStats));
      if (r.status() != 200) return new Walk(false, r.status(), truncated);
      JsonNode arr = r.json();
      if (!arr.isArray() || arr.isEmpty()) break;
      visitor.visit(page, arr);
      if (arr.size() < 100) break;
      if (page == max) truncated = true;
    }
    return new Walk(true, 200, truncated);
  }

  /** Pour qui n'a qu'un commit à la fois à regarder. */
  static PageVisitor each(Consumer<JsonNode> perCommit) {
    return (page, arr) -> arr.forEach(perCommit);
  }

  private static String path(Proj p) {
    return "projects/" + p.id + "/repository/commits";
  }
}
