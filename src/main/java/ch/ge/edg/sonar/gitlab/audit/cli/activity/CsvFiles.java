package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.dec;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.iso;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.num;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.orEmpty;

import ch.ge.edg.sonar.gitlab.audit.csv.Column;
import java.util.List;
import java.util.function.Function;

/**
 * Les deux CSV de l'audit, à lire ensemble : inventaire.csv et pratiques.csv. Leurs colonnes sont
 * décrites dans COLUMNS.md.
 */
final class CsvFiles {

  private CsvFiles() {}

  /** Tout le périmètre, exclus compris, sur une fenêtre de {@code windowDays} jours. */
  static List<Column<AuditedProject>> inventory(int windowDays) {
    return List.of(
        project("id", p -> String.valueOf(p.id())),
        project("path", p -> orEmpty(p.path())),
        project("name", p -> orEmpty(p.name())),
        project("namespace", p -> orEmpty(p.namespace())),
        project("default_branch", p -> orEmpty(p.defaultBranch())),
        project("visibility", p -> orEmpty(p.visibility())),
        project("created_at", p -> iso(p.dates().created())),
        project("last_activity_at", p -> iso(p.lastActivity())),
        repo("archived", r -> String.valueOf(r.archived())),
        repo("fork", r -> r.forkedFrom() == null ? "false" : "true"),
        repo("mirror", r -> String.valueOf(r.mirror())),
        repo("total_commits", r -> num(r.commitCount())),
        repo("repo_size", r -> num(r.size())),
        new Column<>("bucket", a -> a.fate().bucket()),
        new Column<>("last_commit", a -> iso(a.lastCommit())),
        commits("commits_window", m -> num(m.human())),
        commits("commits_bots", m -> num(m.bots())),
        commits("authors_window", m -> num(m.authors())),
        commits("merge_commits", m -> num(m.merges())),
        commits("reverts", m -> num(m.reverts())),
        commits("active_days", m -> num(m.activeDays())),
        new Column<>(
            "commits_per_week",
            a -> a.commits().human() == null ? "" : "%.2f".formatted(a.perWeek(windowDays))),
        commits("tronque", m -> String.valueOf(m.truncated())),
        new Column<>("exclu", a -> a.fate().exclusion()),
        commits("fuite_filtre", m -> String.valueOf(m.leaked())),
        new Column<>("selectionne", a -> String.valueOf(a.fate().isSelected())),
        new Column<>("motif_selection", a -> a.fate().selectionReason()));
  }

  private static Column<AuditedProject> project(String name, Function<GitlabProject, String> f) {
    return new Column<>(name, a -> f.apply(a.project()));
  }

  private static Column<AuditedProject> repo(
      String name, Function<GitlabProject.Repository, String> f) {
    return new Column<>(name, a -> f.apply(a.project().repository()));
  }

  private static Column<AuditedProject> commits(String name, Function<CommitMeasure, String> f) {
    return new Column<>(name, a -> f.apply(a.commits()));
  }

  /** Les seuls sélectionnés, sur une fenêtre de {@code windowDays} jours. */
  static List<Column<Assessed>> practices(int windowDays) {
    return List.of(
        audited("path", a -> orEmpty(a.project().path())),
        audited("motif_selection", a -> a.fate().selectionReason()),
        audited("bucket", a -> a.fate().bucket()),
        audited("commits_window", a -> num(a.commits().human())),
        audited("authors_window", a -> num(a.commits().authors())),
        prat("branche_protegee", r -> String.valueOf(r.defaultProtected)),
        prat("push_verrouille", r -> String.valueOf(r.pushLocked)),
        prat("mr_fusionnees", r -> String.valueOf(r.mergedMrs)),
        prat("ttm_median_j", r -> dec(r.medianTtmDays)),
        prat("auto_merge", r -> String.valueOf(r.selfMerged)),
        prat("notes_par_mr", r -> dec(r.notesPerMr)),
        prat("echantillon_approbations", r -> num(r.approvalSample)),
        prat("part_approuvee", r -> dec(r.approvedShare)),
        prat("auto_approbation", r -> String.valueOf(r.selfApproved)),
        prat("pipelines", r -> String.valueOf(r.pipelines)),
        prat("taux_succes", r -> dec(r.pipelineSuccess)),
        prat("incidents_rouges", r -> num(r.redIncidents)),
        prat("retour_au_vert_h", r -> dec(r.recoveryMedianHours)),
        prat("rouge_non_resolu", r -> num(r.unresolvedRed)),
        prat("environnements", r -> num(r.environments)),
        prat("deploiements", r -> dec(r.deployments)),
        prat("dora_indispo", r -> String.valueOf(r.doraUnavailable)),
        prat("ci_sonar", r -> String.valueOf(r.ciSonar)),
        prat("ci_securite", r -> String.valueOf(r.ciSecurity)),
        prat("cle_sonar", r -> orEmpty(r.sonarKey)),
        prat("source_cle_sonar", r -> orEmpty(r.sonarKeySource)),
        prat("fichiers", r -> String.join(" ", r.files)),
        audited("id", a -> String.valueOf(a.project().id())),
        new Column<>("fenetre_j", a -> String.valueOf(windowDays)),
        prat("lignes_modifiees", r -> num(r.linesChanged)),
        prat("commits_lignes", r -> num(r.churnCommits)),
        prat("lignes_tronquees", r -> String.valueOf(r.churnTruncated)),
        prat("cle_pom", r -> orEmpty(r.pomKey)),
        prat("nom_pom", r -> orEmpty(r.pomName)));
  }

  private static Column<Assessed> audited(String name, Function<AuditedProject, String> f) {
    return new Column<>(name, a -> f.apply(a.audited()));
  }

  private static Column<Assessed> prat(String name, Function<Prat, String> f) {
    return new Column<>(name, a -> f.apply(a.practices()));
  }
}
