package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.text;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Lignes ajoutées + supprimées sur la fenêtre : le dénominateur que CrossAudit rapporte à la pente
 * des issues Sonar.
 *
 * <p>Une passe séparée du comptage des commits, et seulement ici : with_stats fait calculer à
 * GitLab le diff de chaque commit, ce qui ne se paie pas sur tout le parc. Les merges sont écartés
 * — leur diff contre le premier parent recompte la branche fusionnée, déjà comptée commit par
 * commit — et les bots aussi, comme dans commits_window.
 *
 * <p>Le chiffre compte tout ce que le dépôt contient : lockfiles, code généré, documentation. Sonar
 * ne compte que le code qu'il analyse. L'écart est un biais connu, pas corrigible sans lire chaque
 * diff.
 */
final class Churn {

  private final AuditContext ctx;
  private long lines;
  private int counted;

  private Churn(AuditContext ctx) {
    this.ctx = ctx;
  }

  static void measure(AuditContext ctx, Proj p, Prat r) {
    new Churn(ctx).run(p, r);
  }

  private void run(Proj p, Prat r) {
    CommitPager.Walk w = new CommitPager(ctx).walk(p, true, CommitPager.each(this::add));
    // Illisible n'est pas « aucune ligne » : la colonne reste vide.
    if (!w.ok()) return;
    r.churnTruncated = w.truncated();
    r.linesChanged = lines;
    r.churnCommits = counted;
  }

  private void add(JsonNode c) {
    if (Gitlab.isBot(ctx.bots(), text(c, "author_email"), text(c, "author_name"))) return;
    if (Gitlab.isMerge(c)) return;
    JsonNode st = c.path("stats");
    if (st.isMissingNode()) return;
    lines += st.path("additions").asLong(0) + st.path("deletions").asLong(0);
    counted++;
  }
}
