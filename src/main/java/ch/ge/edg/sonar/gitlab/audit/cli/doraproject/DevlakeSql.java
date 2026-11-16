package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.dora.JiraKeys;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * devlake-import.sql : un instantané écrit dans la couche « domaine » d'Apache DevLake (v1.0.x,
 * MySQL), là où son plugin dora lit ce qu'il calcule. Nos règles y entrent toutes faites :
 *
 * <ul>
 *   <li>un pipeline GoCD de production devient un « cicd_scope », chacun de ses runs un déploiement
 *       en PRODUCTION (cicd_deployments) et un commit déployé (cicd_deployment_commits) ;
 *   <li>les commits qu'un déploiement réussi a mis en production, d'après nos règles (matériaux
 *       GoCD avec report des runs en échec, ou « atteint main »), deviennent ses lignes de
 *       commits_diffs : c'est là que DevLake cherche le commit d'une merge request pour dater sa
 *       mise en production ;
 *   <li>une merge request devient une pull_request, son commit de fusion étant l'un de ses commits
 *       que nos règles rattachent à son déploiement (les fusions en avance rapide n'en laissent
 *       pas) ;
 *   <li>un incident Jira devient un ticket de type INCIDENT sur un tableau rattaché au projet, que
 *       le plugin dora transforme en incident.
 * </ul>
 *
 * <p>Le projet DevLake doit exister (config UI ou API) ; le script s'arrête sinon. Il se rejoue :
 * il efface d'abord les lignes qu'il a écrites pour ce projet GitLab (identifiants « dora-import:
 * »), puis les réécrit. Le calcul DORA se lance ensuite dans DevLake (devlake/README.md).
 */
final class DevlakeSql {

  static final String NAME = "devlake-import.sql";

  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).withZone(ZoneOffset.UTC);

  private final Snapshot s;
  private final String project;
  private final DevlakeModel model;
  private final String prefix;
  private final StringBuilder out = new StringBuilder();

  private DevlakeSql(Snapshot s, String project) {
    this.s = s;
    this.project = project;
    this.model = new DevlakeModel(s);
    this.prefix = model.prefix();
  }

  static String render(Snapshot s, String project) {
    return new DevlakeSql(s, project).script();
  }

  /** Le dernier segment du chemin GitLab. */
  static String defaultProject(Snapshot s) {
    String path = s.project().path();
    return path.substring(path.lastIndexOf('/') + 1);
  }

  private String script() {
    Snapshot.Project p = s.project();
    line("-- Import Apache DevLake (v1.0.x) : " + p.path() + ", extrait le " + s.to() + ".");
    line("-- Projet DevLake : " + project + ". Il doit exister (config UI ou API).");
    line("-- mysql -h 127.0.0.1 -u merico -p lake < " + NAME);
    line("-- Puis lancer le plugin dora sur le projet : voir devlake/README.md.");
    line("SET NAMES utf8mb4;");
    line("START TRANSACTION;");
    deletes();
    line("");
    line("-- Le projet doit exister : sinon project_name vaut NULL et MySQL refuse la ligne.");
    line(
        "INSERT INTO project_mapping (project_name, `table`, row_id, created_at, updated_at)"
            + " VALUES ((SELECT name FROM projects WHERE name = "
            + text(project)
            + "), 'repos', "
            + text(prefix)
            + ", NOW(3), NOW(3));");
    line(
        "INSERT INTO repos (id, name, url, created_at, updated_at) VALUES ("
            + String.join(", ", text(prefix), text(p.path()), text(p.webUrl()))
            + ", NOW(3), NOW(3));");
    scopes();
    deployments();
    pullRequests();
    incidents();
    line("");
    line("COMMIT;");
    return out.toString();
  }

  private void deletes() {
    String like = text(prefix + ":%");
    line("");
    line("-- Ce que le script a écrit la dernière fois pour ce projet GitLab.");
    line(
        "DELETE cd FROM commits_diffs cd JOIN cicd_deployment_commits dc"
            + " ON dc.commit_sha = cd.new_commit_sha WHERE dc.id LIKE "
            + like
            + ";");
    for (String table :
        List.of(
            "cicd_deployment_commits",
            "cicd_deployments",
            "cicd_scopes",
            "pull_requests",
            "incidents",
            "issues",
            "boards")) {
      line("DELETE FROM " + table + " WHERE id LIKE " + like + ";");
    }
    line("DELETE FROM pull_request_commits WHERE pull_request_id LIKE " + like + ";");
    line("DELETE FROM board_issues WHERE board_id LIKE " + like + ";");
    line("DELETE FROM repos WHERE id = " + text(prefix) + ";");
    line(
        "DELETE FROM project_mapping WHERE project_name = "
            + text(project)
            + " AND (row_id = "
            + text(prefix)
            + " OR row_id LIKE "
            + like
            + ");");
  }

  private void scopes() {
    List<String> pipelines = model.pipelines();
    line("");
    line("-- Un scope CI/CD par pipeline de production, rattaché au projet.");
    line("INSERT INTO cicd_scopes (id, name, created_at, updated_at) VALUES");
    List<String> scopes = new ArrayList<>();
    for (String name : pipelines) {
      scopes.add("(" + text(model.scopeId(name)) + ", " + text(name) + ", NOW(3), NOW(3))");
    }
    rows(scopes, ";");
    List<String> rows = new ArrayList<>();
    for (String name : pipelines) rows.add(mapping("cicd_scopes", model.scopeId(name)));
    for (String key : s.project().jiraProjects()) rows.add(mapping("boards", model.boardId(key)));
    line("INSERT INTO project_mapping (project_name, `table`, row_id, created_at, updated_at)");
    values(rows);
  }

  private String mapping(String table, String rowId) {
    return "(" + text(project) + ", " + text(table) + ", " + text(rowId) + ", NOW(3), NOW(3))";
  }

  private void deployments() {
    List<DevlakeModel.Row> rows = model.rows();
    if (rows.isEmpty()) return;
    line("");
    line(
        "-- Déploiements : un run GoCD de production chacun, et un déploiement de référence par"
            + " pipeline (1970).");
    line(
        "INSERT INTO cicd_deployments (id, cicd_scope_id, name, display_title, url, result,"
            + " original_result, status, original_status, environment, original_environment,"
            + " created_date, started_date, finished_date, duration_sec, created_at, updated_at)"
            + " VALUES");
    List<String> values = new ArrayList<>();
    for (DevlakeModel.Row r : rows) {
      String id = text(model.deploymentId(r.run()));
      values.add(
          "(" + id + ", " + text(model.scopeId(r.run().pipeline())) + ", " + common(r) + ")");
    }
    rows(values, ";");
    line(
        "INSERT INTO cicd_deployment_commits (id, cicd_scope_id, cicd_deployment_id, name,"
            + " display_title, url, result, original_result, status, original_status,"
            + " environment, original_environment, created_date, started_date, finished_date,"
            + " duration_sec, created_at, updated_at, commit_sha, ref_name, repo_id, repo_url)"
            + " VALUES");
    values.clear();
    for (DevlakeModel.Row r : rows) {
      String id = text(model.deploymentId(r.run()));
      values.add(
          "("
              + String.join(
                  ", ",
                  id,
                  text(model.scopeId(r.run().pipeline())),
                  id,
                  common(r),
                  text(r.head()),
                  text(s.project().defaultBranch()),
                  text(prefix),
                  text(s.project().webUrl()))
              + ")");
    }
    rows(values, ";");
    diffs(rows);
  }

  /** Nom, titre, url, résultat, statut, environnement, dates et horodatage d'un déploiement. */
  private String common(DevlakeModel.Row r) {
    Snapshot.Deployment d = r.run();
    String[] result = DevlakeModel.result(d.outcome());
    String title = DevlakeModel.title(d);
    return String.join(
        ", ",
        text(title),
        text(title),
        text(d.url()),
        text(result[0]),
        text(d.outcome().name()),
        text(result[1]),
        text(d.outcome().name()),
        "'PRODUCTION'",
        "'PRODUCTION'",
        time(d.at()),
        time(d.at()),
        time(d.at()),
        "0",
        "NOW(3)",
        "NOW(3)");
  }

  /** Les commits qu'un déploiement réussi a mis en production, par rapport au précédent. */
  private void diffs(List<DevlakeModel.Row> rows) {
    List<String> values = new ArrayList<>();
    for (DevlakeModel.Row r : rows) {
      String previous = r.previous();
      if (previous == null) continue;
      List<String> shas = r.delivered();
      for (int i = 0; i < shas.size(); i++) {
        values.add(
            "("
                + String.join(", ", text(r.head()), text(previous), text(shas.get(i)), "" + i)
                + ")");
      }
    }
    if (values.isEmpty()) return;
    line("");
    line("-- Les commits que chaque déploiement réussi a mis en production, selon nos règles.");
    line(
        "INSERT IGNORE INTO commits_diffs (new_commit_sha, old_commit_sha, commit_sha,"
            + " sorting_index) VALUES");
    rows(values, ";");
  }

  private void pullRequests() {
    List<DevlakeModel.Pr> prs = model.prs();
    if (prs.isEmpty()) return;
    line("");
    line(
        "-- Merge requests (et, avec la règle « atteint main », les commits arrivés sans merge"
            + " request) : leur commit de fusion est celui que nos règles rattachent à leur"
            + " déploiement.");
    line(
        "INSERT INTO pull_requests (id, base_repo_id, head_repo_id, base_ref, merge_commit_sha,"
            + " status, original_status, title, url, author_name, pull_request_key, created_date,"
            + " merged_date, closed_date, created_at, updated_at) VALUES");
    List<String> values = new ArrayList<>();
    List<String> commits = new ArrayList<>();
    for (DevlakeModel.Pr pr : prs) {
      values.add(pullRequest(pr));
      for (String sha : pr.mr().shas()) {
        commits.add(
            "("
                + String.join(", ", text(sha), text(pr.id()), time(pr.mr().firstCommitAt()))
                + ", NOW(3), NOW(3))");
      }
    }
    rows(values, ";");
    if (commits.isEmpty()) return;
    line(
        "-- Leur date d'auteur à tous : celle du premier commit, la seule que DevLake lit (le"
            + " début du délai).");
    line(
        "INSERT IGNORE INTO pull_request_commits (commit_sha, pull_request_id,"
            + " commit_authored_date, created_at, updated_at) VALUES");
    rows(commits, ";");
  }

  private String pullRequest(DevlakeModel.Pr pr) {
    Snapshot.MergeRequest mr = pr.mr();
    return "("
        + String.join(
            ", ",
            text(pr.id()),
            text(prefix),
            text(prefix),
            text(s.project().defaultBranch()),
            text(pr.mergeSha()),
            "'MERGED'",
            "'merged'",
            text(mr.title()),
            text(mr.url()),
            text(mr.author()),
            pr.direct() ? "NULL" : String.valueOf(mr.iid()),
            time(mr.createdAt()),
            time(mr.mergedAt()),
            time(mr.mergedAt()))
        + ", NOW(3), NOW(3))";
  }

  private void incidents() {
    List<String> projects = s.project().jiraProjects();
    if (projects.isEmpty()) return;
    line("");
    line("-- Incidents : un tableau par projet Jira, un ticket de type INCIDENT par incident.");
    line("INSERT INTO boards (id, name, type, created_at, updated_at) VALUES");
    List<String> boards = new ArrayList<>();
    for (String key : projects) {
      boards.add(
          "("
              + text(model.boardId(key))
              + ", "
              + text("Jira " + key)
              + ", 'jira', NOW(3), NOW(3))");
    }
    rows(boards, ";");
    List<Snapshot.Incident> all = s.incidents();
    if (all.isEmpty()) return;
    line(
        "INSERT INTO issues (id, url, issue_key, title, type, original_type, status,"
            + " original_status, created_date, resolution_date, lead_time_minutes,"
            + " original_project, created_at, updated_at) VALUES");
    List<String> issues = new ArrayList<>();
    List<String> links = new ArrayList<>();
    for (Snapshot.Incident n : all) {
      String id = model.issueId(n);
      issues.add(issue(id, n));
      links.add(
          "(" + text(model.boardId(model.board(n.key()))) + ", " + text(id) + ", NOW(3), NOW(3))");
    }
    rows(issues, ";");
    line("INSERT INTO board_issues (board_id, issue_id, created_at, updated_at) VALUES");
    rows(links, ";");
  }

  /** Un incident, ticket DevLake de type INCIDENT : fait une fois résolu. */
  private static String issue(String id, Snapshot.Incident n) {
    Duration d = Metrics.between(n.createdAt(), n.resolvedAt());
    boolean resolved = n.resolvedAt() != null;
    return "("
        + String.join(
            ", ",
            text(id),
            text(n.url()),
            text(n.key()),
            text(n.title()),
            "'INCIDENT'",
            text(n.type()),
            resolved ? "'DONE'" : "'TODO'",
            resolved ? "'resolved'" : "'open'",
            time(n.createdAt()),
            time(n.resolvedAt()),
            d == null ? "NULL" : String.valueOf(d.toMinutes()),
            text(JiraKeys.prefix(n.key())))
        + ", NOW(3), NOW(3))";
  }

  private void values(List<String> rows) {
    line("VALUES");
    rows(rows, ";");
  }

  private void rows(List<String> rows, String end) {
    for (int i = 0; i < rows.size(); i++) {
      line("  " + rows.get(i) + (i + 1 < rows.size() ? "," : end));
    }
  }

  /** Une chaîne MySQL : apostrophes doublées, barres obliques inverses échappées. */
  static String text(String value) {
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'";
  }

  static String time(@Nullable Instant at) {
    return at == null ? "NULL" : "'" + TIME.format(at) + "'";
  }

  private void line(String text) {
    out.append(text).append('\n');
  }
}
