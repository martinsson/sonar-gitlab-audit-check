package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.dec;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.iso;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.num;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.orEmpty;

import ch.ge.edg.sonar.gitlab.audit.csv.Column;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

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
        practice("branche_protegee", r -> String.valueOf(r.protection().defaultProtected())),
        practice("push_verrouille", r -> String.valueOf(r.protection().pushLocked())),
        review("mr_fusionnees", r -> String.valueOf(r.mergedMrs())),
        review("ttm_median_j", r -> dec(r.medianTtmDays())),
        review("auto_merge", r -> String.valueOf(r.selfMerged())),
        review("notes_par_mr", r -> dec(r.notesPerMr())),
        approvals("echantillon_approbations", a -> a == null ? "" : String.valueOf(a.sample())),
        approvals("part_approuvee", a -> a == null ? "" : dec(a.share())),
        approvals("auto_approbation", a -> a == null ? "0" : String.valueOf(a.selfApproved())),
        builds("pipelines", b -> String.valueOf(b.pipelines())),
        builds("taux_succes", b -> dec(b.success())),
        recovery("incidents_rouges", r -> r == null ? "" : String.valueOf(r.incidents())),
        recovery("retour_au_vert_h", r -> r == null ? "" : dec(r.medianHours())),
        recovery("rouge_non_resolu", r -> r == null ? "" : String.valueOf(r.unresolved())),
        deployment("environnements", d -> num(d.environments())),
        deployment("deploiements", d -> dec(d.deployments())),
        deployment("dora_indispo", d -> String.valueOf(d.doraUnavailable())),
        facts("ci_sonar", f -> String.valueOf(f.ciSonar)),
        facts("ci_securite", f -> String.valueOf(f.ciSecurity)),
        facts("cle_sonar", f -> orEmpty(f.sonarKey)),
        facts("source_cle_sonar", f -> orEmpty(f.shownKeySource())),
        practice("fichiers", r -> String.join(" ", r.sonar().files())),
        audited("id", a -> String.valueOf(a.project().id())),
        new Column<>("fenetre_j", a -> String.valueOf(windowDays)),
        lines("lignes_modifiees", l -> num(l.changed())),
        lines("commits_lignes", l -> num(l.commits())),
        lines("lignes_tronquees", l -> String.valueOf(l.truncated())),
        facts("cle_pom", f -> orEmpty(f.pomKey)),
        facts("nom_pom", f -> orEmpty(f.pomName)));
  }

  private static Column<Assessed> audited(String name, Function<AuditedProject, String> f) {
    return new Column<>(name, a -> f.apply(a.audited()));
  }

  private static Column<Assessed> practice(String name, Function<Practices, String> f) {
    return new Column<>(name, a -> f.apply(a.practices()));
  }

  private static Column<Assessed> review(String name, Function<Practices.Review, String> f) {
    return practice(name, r -> f.apply(r.review()));
  }

  private static Column<Assessed> approvals(
      String name, Function<Practices.@Nullable Approvals, String> f) {
    return practice(name, r -> f.apply(r.review().approvals()));
  }

  private static Column<Assessed> builds(String name, Function<Practices.Builds, String> f) {
    return practice(name, r -> f.apply(r.builds()));
  }

  private static Column<Assessed> recovery(
      String name, Function<Practices.@Nullable Recovery, String> f) {
    return practice(name, r -> f.apply(r.builds().recovery()));
  }

  private static Column<Assessed> deployment(
      String name, Function<Practices.Deployment, String> f) {
    return practice(name, r -> f.apply(r.deployment()));
  }

  private static Column<Assessed> facts(String name, Function<CiFacts, String> f) {
    return practice(name, r -> f.apply(r.sonar().facts()));
  }

  private static Column<Assessed> lines(String name, Function<Practices.Lines, String> f) {
    return practice(name, r -> f.apply(r.lines()));
  }
}
