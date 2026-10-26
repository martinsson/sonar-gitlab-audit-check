package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.dora.RunOutcome;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * middleware-import.sql : les déploiements et les incidents d'un instantané, écrits dans la base de
 * Middleware HQ (schéma de la version open source, 2024-05 et suivantes).
 *
 * <p>Middleware ne lit les déploiements que dans GitHub Actions et les incidents que dans les merge
 * requests de revert : il n'a ni import ni webhook publiés. Son modèle a pourtant la place pour
 * d'autres sources, et c'est par là qu'on entre :
 *
 * <ul>
 *   <li>un pipeline GoCD devient un « workflow » de déploiement du dépôt (RepoWorkflow, fournisseur
 *       « gocd »), chacun de ses runs un RepoWorkflowRuns ; les équipes du dépôt passent en
 *       déploiement par workflow ;
 *   <li>un projet Jira devient un service d'incidents (OrgIncidentService, fournisseur « jira »)
 *       rattaché aux équipes du dépôt, chaque incident un Incident.
 * </ul>
 *
 * <p>Le dépôt GitLab lui-même, ses merge requests et leurs commits restent lus par l'intégration
 * GitLab de Middleware : c'est sa façon standard, et le script exige qu'elle ait été faite. Le
 * script se rejoue : chaque ligne est insérée ou mise à jour, rien n'est supprimé. Aucune
 * synchronisation de Middleware ne touche ces lignes, aucun de ses fournisseurs ne s'appelant «
 * gocd » ou « jira ».
 */
final class MiddlewareSql {

  static final String NAME = "middleware-import.sql";

  static final String GOCD = "gocd";
  static final String JIRA = "jira";

  /** Le fournisseur de workflow que Middleware relit sans jamais le synchroniser. */
  static final String WORKFLOW_PROVIDER = "CIRCLE_CI";

  private final Snapshot s;
  private final StringBuilder out = new StringBuilder();

  private MiddlewareSql(Snapshot s) {
    this.s = s;
  }

  static String render(Snapshot s) {
    return new MiddlewareSql(s).script();
  }

  private String script() {
    Snapshot.Project p = s.project();
    line("-- Import Middleware HQ : " + p.path() + ", extrait le " + s.to() + ".");
    line("-- psql \"postgresql://postgres:postgres@localhost:5434/mhq-oss\" -f " + NAME);
    line(
        "-- Le dépôt doit déjà être lié dans Middleware (intégration GitLab, ajouté à une équipe).");
    line("\\set ON_ERROR_STOP on");
    line("BEGIN;");
    line("");
    line("CREATE TEMP TABLE dora_repo ON COMMIT DROP AS");
    line(
        "  SELECT id, org_id FROM \"OrgRepo\" WHERE provider = 'gitlab' AND idempotency_key = "
            + text(p.gitlabId())
            + ";");
    line("DO $$ BEGIN");
    line("  IF NOT EXISTS (SELECT 1 FROM dora_repo) THEN");
    line(
        "    RAISE EXCEPTION 'Dépôt GitLab "
            + p.gitlabId()
            + " ("
            + escape(p.path())
            + ") absent de Middleware : liez-le d''abord.';");
    line("  END IF;");
    line("END $$;");
    if (p.deploySource() == Snapshot.DeploySource.GOCD) {
      workflows();
      runs();
      teamRepos();
    } else {
      line("\n-- Pas de GoCD : Middleware compte les merge requests fusionnées (PR_MERGE).");
    }
    if (!p.jiraProjects().isEmpty()) {
      services();
      incidentRows();
    }
    line("");
    line("COMMIT;");
    return out.toString();
  }

  private void workflows() {
    Snapshot.Project p = s.project();
    line("");
    line("-- Déploiements : un workflow par pipeline GoCD de production.");
    line(
        "INSERT INTO \"RepoWorkflow\" (org_repo_id, type, provider, provider_workflow_id, name,"
            + " is_active, meta)");
    line(
        "SELECT r.id, 'DEPLOYMENT', "
            + text(WORKFLOW_PROVIDER)
            + ", v.workflow, v.name, true, '{\"source\": \"gocd\"}'::jsonb");
    line("FROM dora_repo r, (VALUES");
    List<String> pipelines = p.prodPipelines();
    for (int i = 0; i < pipelines.size(); i++) {
      String name = pipelines.get(i);
      line("  (" + text(workflowId(name)) + ", " + text(name) + ")" + comma(i, pipelines.size()));
    }
    line(") AS v (workflow, name)");
    line("ON CONFLICT (org_repo_id, provider_workflow_id)");
    line(
        "DO UPDATE SET name = EXCLUDED.name, provider = EXCLUDED.provider, is_active = true,"
            + " meta = EXCLUDED.meta, updated_at = now();");
  }

  private void runs() {
    List<Snapshot.Deployment> runs = s.deployments();
    if (runs.isEmpty()) return;
    String branch = s.project().defaultBranch();
    line("");
    line(
        "INSERT INTO \"RepoWorkflowRuns\" (repo_workflow_id, provider_workflow_run_id, status,"
            + " head_branch, event_actor, conducted_at, html_url, meta)");
    line(
        "SELECT w.id, v.run, v.status, v.branch, v.actor, v.at::timestamptz, v.url,"
            + " v.meta::jsonb");
    line("FROM dora_repo r JOIN \"RepoWorkflow\" w ON w.org_repo_id = r.id, (VALUES");
    for (int i = 0; i < runs.size(); i++) {
      Snapshot.Deployment d = runs.get(i);
      line(
          "  ("
              + String.join(
                  ", ",
                  text(workflowId(d.pipeline())),
                  text(String.valueOf(d.counter())),
                  text(status(d.outcome())),
                  text(branch),
                  text(d.forced() ? "gocd (forcé)" : "gocd"),
                  time(d.at()),
                  text(d.url()),
                  text("{\"commits\": " + d.commits().size() + "}"))
              + ")"
              + comma(i, runs.size()));
    }
    line(") AS v (workflow, run, status, branch, actor, at, url, meta)");
    line("WHERE w.provider_workflow_id = v.workflow");
    line("ON CONFLICT (repo_workflow_id, provider_workflow_run_id)");
    line(
        "DO UPDATE SET status = EXCLUDED.status, head_branch = EXCLUDED.head_branch,"
            + " event_actor = EXCLUDED.event_actor, conducted_at = EXCLUDED.conducted_at,");
    line("  html_url = EXCLUDED.html_url, meta = EXCLUDED.meta, updated_at = now();");
  }

  private void teamRepos() {
    line("");
    line("-- Les équipes du dépôt comptent ses déploiements par workflow, plus par fusion.");
    line(
        "UPDATE \"TeamRepos\" SET deployment_type = 'WORKFLOW', updated_at = now()"
            + " WHERE org_repo_id IN (SELECT id FROM dora_repo);");
  }

  private void services() {
    List<String> projects = s.project().jiraProjects();
    line("");
    line("-- Incidents : un service par projet Jira, rattaché aux équipes du dépôt.");
    line(
        "INSERT INTO \"OrgIncidentService\" (provider, key, name, org_id, source_type, status,"
            + " meta)");
    line("SELECT 'jira', v.key, v.name, r.org_id, 'INCIDENT_SERVICE', 'active', '{}'::jsonb");
    line("FROM dora_repo r, (VALUES");
    for (int i = 0; i < projects.size(); i++) {
      String key = projects.get(i);
      line("  (" + text(key) + ", " + text("Jira " + key) + ")" + comma(i, projects.size()));
    }
    line(") AS v (key, name)");
    line("ON CONFLICT (provider, org_id, key)");
    line("DO UPDATE SET name = EXCLUDED.name, is_deleted = false, updated_at = now();");
    line("");
    line(
        "INSERT INTO \"TeamIncidentService\" (team_id, service_id)"
            + " SELECT DISTINCT t.team_id, o.id");
    line("FROM \"TeamRepos\" t JOIN dora_repo r ON t.org_repo_id = r.id");
    line("JOIN \"OrgIncidentService\" o ON o.org_id = r.org_id AND o.provider = 'jira'");
    line("WHERE t.is_active AND o.key IN (" + list(projects) + ")");
    line("ON CONFLICT (team_id, service_id) DO UPDATE SET is_deleted = false, updated_at = now();");
  }

  private void incidentRows() {
    List<Snapshot.Incident> all = s.incidents();
    if (all.isEmpty()) return;
    line("");
    line(
        "INSERT INTO \"Incident\" (provider, key, title, status, creation_date, resolved_date,"
            + " incident_type, url, meta)");
    line("VALUES");
    for (int i = 0; i < all.size(); i++) {
      Snapshot.Incident n = all.get(i);
      line(
          "  ("
              + String.join(
                  ", ",
                  text(JIRA),
                  text(n.key()),
                  text(n.title()),
                  text(n.resolvedAt() == null ? "triggered" : "resolved"),
                  time(n.createdAt()),
                  time(n.resolvedAt()),
                  "'INCIDENT'",
                  text(n.url()),
                  text("{\"type\": " + json(n.type()) + "}") + "::jsonb")
              + ")"
              + comma(i, all.size()));
    }
    line("ON CONFLICT (provider, key)");
    line(
        "DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, creation_date ="
            + " EXCLUDED.creation_date,");
    line(
        "  resolved_date = EXCLUDED.resolved_date, url = EXCLUDED.url, meta = EXCLUDED.meta,"
            + " updated_at = now();");
    incidentMap(all);
  }

  private void incidentMap(List<Snapshot.Incident> all) {
    line("");
    line("INSERT INTO \"IncidentOrgIncidentServiceMap\" (incident_id, service_id)");
    line("SELECT i.id, o.id FROM \"Incident\" i");
    line("JOIN dora_repo r ON true");
    line(
        "JOIN \"OrgIncidentService\" o ON o.org_id = r.org_id AND o.provider = 'jira'"
            + " AND i.key LIKE o.key || '-%'");
    line(
        "WHERE i.provider = 'jira' AND i.key IN ("
            + list(all.stream().map(Snapshot.Incident::key).toList())
            + ")");
    line("ON CONFLICT DO NOTHING;");
  }

  /** L'id du workflow dans Middleware : unique par dépôt. */
  static String workflowId(String pipeline) {
    return GOCD + ":" + pipeline;
  }

  /** Les statuts de run que Middleware connaît. */
  static String status(RunOutcome outcome) {
    return outcome.name();
  }

  private static String comma(int i, int size) {
    return i + 1 < size ? "," : "";
  }

  private static String list(List<String> values) {
    return String.join(", ", values.stream().map(MiddlewareSql::text).toList());
  }

  /** Une chaîne SQL : apostrophes doublées. */
  static String text(String value) {
    return "'" + escape(value) + "'";
  }

  private static String escape(String value) {
    return value.replace("'", "''");
  }

  private static String time(@Nullable Instant at) {
    return at == null ? "NULL::timestamptz" : "'" + at + "'::timestamptz";
  }

  /** Une chaîne JSON, guillemets et barres obliques inverses échappés. */
  static String json(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private void line(String s) {
    out.append(s).append('\n');
  }
}
