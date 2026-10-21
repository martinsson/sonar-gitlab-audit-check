package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.CellFormat.iso;
import static ch.ge.edg.sonar.gitlab.audit.rest.Query.params;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import java.util.function.Consumer;

/** Les commits de la branche par défaut sur la fenêtre, lus en REST. */
final class CommitPager {

  private final Gitlab gitlab;
  private final Funnel funnel;

  CommitPager(Gitlab gitlab, Funnel funnel) {
    this.gitlab = gitlab;
    this.funnel = funnel;
  }

  /**
   * L'issue d'une pagination : lue jusqu'au bout, plafonnée, ou interrompue sur un statut.
   *
   * @param status « HTTP 200 », ou le statut qui l'a interrompue (« HTTP 403 », « injoignable »)
   */
  record Walk(boolean ok, String status, boolean truncated) {}

  /** Compte sans pagination quand X-Total est là ; sinon pagine. */
  int count(GitlabProject p) {
    String since = iso(funnel.windowStart());
    RestResponse r =
        gitlab.get(path(p), params("ref_name", p.defaultBranch(), "since", since, "per_page", "1"));
    if (!r.is(200)) return 0;
    Optional<Integer> total = r.intHeader("x-total");
    if (total.isPresent()) return total.get();
    // GitLab omet X-Total quand le comptage serait coûteux. Absent ne veut pas
    // dire zéro : on redemande une page pleine plutôt que de lire un en-tête
    // manquant comme un 0 — c'est exactement la confusion « absent = zéro »
    // qui a déjà coûté cher côté Sonar.
    RestResponse full =
        gitlab.get(
            path(p), params("ref_name", p.defaultBranch(), "since", since, "per_page", "100"));
    return full.is(200) && full.tree().isArray() ? full.tree().size() : 0;
  }

  /**
   * Une seule passe de pagination donne quatre choses : le volume, les auteurs distincts, le taux
   * de commits de merge et l'étalement dans le temps. On la fait une fois et on garde les commits
   * bruts.
   */
  CommitMeasure measure(AuditedProject p) {
    CommitTally tally = new CommitTally(funnel.windowStart(), funnel.bots());
    Walk w = walk(p.project(), false, each(tally::add));
    // Refus de permission ou erreur : on ne sait pas, et « on ne sait
    // pas » ne doit surtout pas se lire comme « aucune activité ».
    return w.ok()
        ? tally.result(p.head(), w.truncated())
        : new CommitMeasure.Unmeasurable(w.status());
  }

  /** Ce que reçoit chaque page non vide : son numéro et ses commits. */
  interface PageVisitor {
    void visit(int page, JsonNode commits);
  }

  /**
   * Parcourt les pages de cent jusqu'à une page incomplète ou au plafond --max-commit-pages, qui
   * marque le résultat tronqué. Le premier statut autre que 200 arrête tout.
   */
  Walk walk(GitlabProject p, boolean withStats, PageVisitor visitor) {
    int max = funnel.options().maxCommitPages;
    boolean truncated = false;
    for (int page = 1; page <= max; page++) {
      RestResponse r =
          gitlab.get(path(p), Api.commitPage(p, funnel.windowStart(), page, withStats));
      if (!r.is(200)) return new Walk(false, r.statusLine(), truncated);
      JsonNode arr = r.tree();
      if (!arr.isArray() || arr.isEmpty()) break;
      visitor.visit(page, arr);
      if (arr.size() < 100) break;
      if (page == max) truncated = true;
    }
    return new Walk(true, "HTTP 200", truncated);
  }

  /** Pour qui n'a qu'un commit à la fois à regarder. */
  static PageVisitor each(Consumer<JsonNode> perCommit) {
    return (page, arr) -> arr.forEach(perCommit);
  }

  private static String path(GitlabProject p) {
    return "projects/" + p.id() + "/repository/commits";
  }
}
