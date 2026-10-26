╔═ gitlabGocdAndJiraGiveTheFourMeasuresThePageAndTheImport/html ═╗
<!doctype html>
<html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>DORA octocat/Hello-World</title>
<style>:root{--bg:#fbfbfa;--fg:#1d1d1b;--muted:#6b6b66;--line:#e2e1dc;--card:#fff;--accent:#2d5fa8;
--ok:#2e7d4f;--ko:#b3412e;--warn:#9a6b00}
@media (prefers-color-scheme:dark){:root{--bg:#161615;--fg:#ecebe6;--muted:#a3a29b;
--line:#34332f;--card:#1f1f1d;--accent:#8db3ec;--ok:#7bc79a;--ko:#ec8f7d;--warn:#e0b45a}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.5 system-ui,-apple-system,
"Segoe UI",Roboto,sans-serif}
main{max-width:1180px;margin:0 auto;padding:24px 16px 48px}
h1{font-size:24px;margin:0 0 4px}h2{font-size:18px;margin:32px 0 8px}
a{color:var(--accent)}
.sub,.note{color:var(--muted)}.note{font-size:13px}
.tiles{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:12px;
margin-top:20px}
.tile{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:14px 16px}
.tile h3{font-size:13px;font-weight:600;color:var(--muted);margin:0;text-transform:uppercase;
letter-spacing:.03em}
.tile .v{font-size:30px;font-weight:650;margin:6px 0 2px;font-variant-numeric:tabular-nums}
.tile p{margin:0;font-size:13px;color:var(--muted)}
.scroll{overflow-x:auto;border:1px solid var(--line);border-radius:10px;background:var(--card)}
table{border-collapse:collapse;width:100%;font-size:14px}
th,td{padding:7px 10px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top;
white-space:nowrap}
td.t{white-space:normal;min-width:220px}
th{font-weight:600;color:var(--muted);background:var(--card);position:sticky;top:0}
tr:last-child td{border-bottom:0}
td.n{text-align:right;font-variant-numeric:tabular-nums}
.SUCCESS{color:var(--ok)}.FAILURE{color:var(--ko)}.CANCELLED,.PENDING{color:var(--warn)}
.empty{padding:12px 16px;color:var(--muted)}
dl{display:grid;grid-template-columns:max-content 1fr;gap:4px 16px;margin:0}
dt{color:var(--muted)}dd{margin:0}
</style></head><body><main>
<h1>DORA — <a href="https://gitlab.test/octocat/Hello-World">octocat/Hello-World</a></h1>
<p class="sub">Du 2026-07-23 au 2026-10-21 (90 jours), extrait le 2026-10-21 02:00.</p>
<section class="tiles">
<div class="tile"><h3>Fréquence de déploiement</h3><div class="v">0,2 / sem.</div><p>2 déploiements réussis en 90 jours</p></div>
<div class="tile"><h3>Délai de mise en production</h3><div class="v">26,5 h</div><p>médiane sur 4 commits</p></div>
<div class="tile"><h3>Taux d&#39;échec des changements</h3><div class="v">33 %</div><p>1 déploiement(s) sur 3 suivi(s) d&#39;un incident ; runs en échec : 33 %</p></div>
<div class="tile"><h3>Temps de rétablissement</h3><div class="v">4,0 h</div><p>médiane sur 1 incident(s) résolu(s) sur 2</p></div>
</section>
<h2>Règles appliquées</h2><dl>
<dt>Déploiement</dt><dd>un run réussi de prod (GoCD)</dd>
<dt>Délai</dt><dd>de la date d&#39;auteur d&#39;un commit à son premier déploiement réussi</dd>
<dt>Incident</dt><dd>un ticket de type Incident ou Bug créé dans ABC</dd>
<dt>Échec d&#39;un changement</dt><dd>un déploiement, réussi ou non, suivi d&#39;au moins un incident avant le déploiement suivant, rapporté à tous les déploiements (la règle de Middleware)</dd>
<dt>Rétablissement</dt><dd>de la création de l&#39;incident à sa date de résolution Jira</dd>
</dl>
<h2>Déploiements (3)</h2>
<div class="scroll"><table><thead><tr><th>Date</th><th>Run</th><th>Résultat</th><th>Commits</th><th>Incidents qui suivent</th></tr></thead><tbody>
<tr><td>2026-09-02 14:00</td><td><a href="https://gocd.test/go/pipelines/value_stream_map/prod/3">prod #3</a></td><td class="SUCCESS">succès</td><td class="n">0</td><td>ABC-5</td></tr>
<tr><td>2026-09-02 10:00</td><td><a href="https://gocd.test/go/pipelines/value_stream_map/prod/2">prod #2</a></td><td class="FAILURE">échec</td><td class="n">2</td><td></td></tr>
<tr><td>2026-08-03 12:00</td><td><a href="https://gocd.test/go/pipelines/value_stream_map/prod/1">prod #1</a></td><td class="SUCCESS">succès</td><td class="n">2</td><td></td></tr>
</tbody></table></div>
<h2>Merge requests fusionnées (3)</h2>
<div class="scroll"><table><thead><tr><th>MR</th><th>Titre</th><th>Auteur</th><th>Fusionnée</th><th>En production</th><th>Écriture</th><th>Revue</th><th>Attente</th></tr></thead><tbody>
<tr><td><a href="https://gitlab.test/octocat/Hello-World/-/merge_requests/3">!3</a></td><td class="t">Troisième</td><td>dev3</td><td>2026-10-10 10:00</td><td>—</td><td>1,0 h</td><td>24,0 h</td><td>—</td></tr>
<tr><td><a href="https://gitlab.test/octocat/Hello-World/-/merge_requests/2">!2</a></td><td class="t">Deuxième &lt;b&gt;</td><td>dev2</td><td>2026-09-02 09:00</td><td>2026-09-02 14:00</td><td>1,0 h</td><td>22,0 h</td><td>5,0 h</td></tr>
<tr><td><a href="https://gitlab.test/octocat/Hello-World/-/merge_requests/1">!1</a></td><td class="t">Premier l&#39;essai</td><td>dev1</td><td>2026-08-02 11:00</td><td>2026-08-03 12:00</td><td>2,0 h</td><td>23,0 h</td><td>25,0 h</td></tr>
</tbody></table></div>
<p class="note">Écriture : du premier commit à l'ouverture. Revue : de l'ouverture à la fusion. Attente : de la fusion à la mise en production.</p>
<h2>Incidents (2)</h2>
<div class="scroll"><table><thead><tr><th>Clé</th><th>Titre</th><th>Type</th><th>Créé</th><th>Résolu</th><th>Durée</th><th>Après le déploiement</th></tr></thead><tbody>
<tr><td><a href="https://jira.test/browse/ABC-5">ABC-5</a></td><td class="t">Paiement &quot;KO&quot;</td><td>Incident</td><td>2026-09-02 16:00</td><td>2026-09-02 20:00</td><td>4,0 h</td><td>prod #3</td></tr>
<tr><td><a href="https://jira.test/browse/ABC-7">ABC-7</a></td><td class="t">Ancien bug</td><td>Bug</td><td>2026-07-25 10:00</td><td>—</td><td>—</td><td>—</td></tr>
</tbody></table></div>
<script type="application/json" id="donnees">{
  "project" : {
    "gitlabId" : "42",
    "path" : "octocat/Hello-World",
    "webUrl" : "https://gitlab.test/octocat/Hello-World",
    "defaultBranch" : "main",
    "deploySource" : "GOCD",
    "prodPipelines" : [ "prod" ],
    "jiraProjects" : [ "ABC" ],
    "incidentRule" : "un ticket de type Incident ou Bug créé dans ABC"
  },
  "from" : "2026-07-23T00:00:00Z",
  "to" : "2026-10-21T00:00:00Z",
  "mergeRequests" : [ {
    "iid" : 1,
    "title" : "Premier l'essai",
    "author" : "dev1",
    "createdAt" : "2026-08-01T10:00:00Z",
    "mergedAt" : "2026-08-02T09:00:00Z",
    "firstCommitAt" : "2026-08-01T08:00:00Z",
    "shas" : [ "c1", "m1", "h1" ],
    "url" : "https://gitlab.test/octocat/Hello-World/-/merge_requests/1"
  }, {
    "iid" : 2,
    "title" : "Deuxième <b>",
    "author" : "dev2",
    "createdAt" : "2026-09-01T09:00:00Z",
    "mergedAt" : "2026-09-02T07:00:00Z",
    "firstCommitAt" : "2026-09-01T08:00:00Z",
    "shas" : [ "c2", "m2", "h2" ],
    "url" : "https://gitlab.test/octocat/Hello-World/-/merge_requests/2"
  }, {
    "iid" : 3,
    "title" : "Troisième",
    "author" : "dev3",
    "createdAt" : "2026-10-09T08:00:00Z",
    "mergedAt" : "2026-10-10T08:00:00Z",
    "firstCommitAt" : "2026-10-09T07:00:00Z",
    "shas" : [ "c3", "m3", "h3" ],
    "url" : "https://gitlab.test/octocat/Hello-World/-/merge_requests/3"
  } ],
  "deployments" : [ {
    "pipeline" : "prod",
    "counter" : 1,
    "at" : "2026-08-03T10:00:00Z",
    "outcome" : "SUCCESS",
    "forced" : false,
    "commits" : [ {
      "sha" : "c1",
      "at" : "2026-08-01T08:00:00Z"
    }, {
      "sha" : "m1",
      "at" : "2026-08-02T09:00:00Z"
    } ],
    "url" : "https://gocd.test/go/pipelines/value_stream_map/prod/1"
  }, {
    "pipeline" : "prod",
    "counter" : 2,
    "at" : "2026-09-02T08:00:00Z",
    "outcome" : "FAILURE",
    "forced" : false,
    "commits" : [ {
      "sha" : "c2",
      "at" : "2026-09-01T08:00:00Z"
    }, {
      "sha" : "m2",
      "at" : "2026-09-02T07:00:00Z"
    } ],
    "url" : "https://gocd.test/go/pipelines/value_stream_map/prod/2"
  }, {
    "pipeline" : "prod",
    "counter" : 3,
    "at" : "2026-09-02T12:00:00Z",
    "outcome" : "SUCCESS",
    "forced" : false,
    "commits" : [ ],
    "url" : "https://gocd.test/go/pipelines/value_stream_map/prod/3"
  } ],
  "incidents" : [ {
    "key" : "ABC-7",
    "title" : "Ancien bug",
    "type" : "Bug",
    "createdAt" : "2026-07-25T08:00:00Z",
    "resolvedAt" : null,
    "url" : "https://jira.test/browse/ABC-7"
  }, {
    "key" : "ABC-5",
    "title" : "Paiement \"KO\"",
    "type" : "Incident",
    "createdAt" : "2026-09-02T14:00:00Z",
    "resolvedAt" : "2026-09-02T18:00:00Z",
    "url" : "https://jira.test/browse/ABC-5"
  } ]
}</script>
</main></body></html>

╔═ gitlabGocdAndJiraGiveTheFourMeasuresThePageAndTheImport/json ═╗
{
  "project" : {
    "gitlabId" : "42",
    "path" : "octocat/Hello-World",
    "webUrl" : "https://gitlab.test/octocat/Hello-World",
    "defaultBranch" : "main",
    "deploySource" : "GOCD",
    "prodPipelines" : [ "prod" ],
    "jiraProjects" : [ "ABC" ],
    "incidentRule" : "un ticket de type Incident ou Bug créé dans ABC"
  },
  "from" : "2026-07-23T00:00:00Z",
  "to" : "2026-10-21T00:00:00Z",
  "mergeRequests" : [ {
    "iid" : 1,
    "title" : "Premier l'essai",
    "author" : "dev1",
    "createdAt" : "2026-08-01T10:00:00Z",
    "mergedAt" : "2026-08-02T09:00:00Z",
    "firstCommitAt" : "2026-08-01T08:00:00Z",
    "shas" : [ "c1", "m1", "h1" ],
    "url" : "https://gitlab.test/octocat/Hello-World/-/merge_requests/1"
  }, {
    "iid" : 2,
    "title" : "Deuxième <b>",
    "author" : "dev2",
    "createdAt" : "2026-09-01T09:00:00Z",
    "mergedAt" : "2026-09-02T07:00:00Z",
    "firstCommitAt" : "2026-09-01T08:00:00Z",
    "shas" : [ "c2", "m2", "h2" ],
    "url" : "https://gitlab.test/octocat/Hello-World/-/merge_requests/2"
  }, {
    "iid" : 3,
    "title" : "Troisième",
    "author" : "dev3",
    "createdAt" : "2026-10-09T08:00:00Z",
    "mergedAt" : "2026-10-10T08:00:00Z",
    "firstCommitAt" : "2026-10-09T07:00:00Z",
    "shas" : [ "c3", "m3", "h3" ],
    "url" : "https://gitlab.test/octocat/Hello-World/-/merge_requests/3"
  } ],
  "deployments" : [ {
    "pipeline" : "prod",
    "counter" : 1,
    "at" : "2026-08-03T10:00:00Z",
    "outcome" : "SUCCESS",
    "forced" : false,
    "commits" : [ {
      "sha" : "c1",
      "at" : "2026-08-01T08:00:00Z"
    }, {
      "sha" : "m1",
      "at" : "2026-08-02T09:00:00Z"
    } ],
    "url" : "https://gocd.test/go/pipelines/value_stream_map/prod/1"
  }, {
    "pipeline" : "prod",
    "counter" : 2,
    "at" : "2026-09-02T08:00:00Z",
    "outcome" : "FAILURE",
    "forced" : false,
    "commits" : [ {
      "sha" : "c2",
      "at" : "2026-09-01T08:00:00Z"
    }, {
      "sha" : "m2",
      "at" : "2026-09-02T07:00:00Z"
    } ],
    "url" : "https://gocd.test/go/pipelines/value_stream_map/prod/2"
  }, {
    "pipeline" : "prod",
    "counter" : 3,
    "at" : "2026-09-02T12:00:00Z",
    "outcome" : "SUCCESS",
    "forced" : false,
    "commits" : [ ],
    "url" : "https://gocd.test/go/pipelines/value_stream_map/prod/3"
  } ],
  "incidents" : [ {
    "key" : "ABC-7",
    "title" : "Ancien bug",
    "type" : "Bug",
    "createdAt" : "2026-07-25T08:00:00Z",
    "resolvedAt" : null,
    "url" : "https://jira.test/browse/ABC-7"
  }, {
    "key" : "ABC-5",
    "title" : "Paiement \"KO\"",
    "type" : "Incident",
    "createdAt" : "2026-09-02T14:00:00Z",
    "resolvedAt" : "2026-09-02T18:00:00Z",
    "url" : "https://jira.test/browse/ABC-5"
  } ]
}

╔═ gitlabGocdAndJiraGiveTheFourMeasuresThePageAndTheImport/sql ═╗
-- Import Middleware HQ : octocat/Hello-World, extrait le 2026-10-21T00:00:00Z.
-- psql "postgresql://postgres:postgres@localhost:5434/mhq-oss" -f middleware-import.sql
-- Le dépôt doit déjà être lié dans Middleware (intégration GitLab, ajouté à une équipe).
\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE dora_repo ON COMMIT DROP AS
  SELECT id, org_id FROM "OrgRepo" WHERE provider = 'gitlab' AND idempotency_key = '42';
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM dora_repo) THEN
    RAISE EXCEPTION 'Dépôt GitLab 42 (octocat/Hello-World) absent de Middleware : liez-le d''abord.';
  END IF;
END $$;

-- Déploiements : un workflow par pipeline GoCD de production.
INSERT INTO "RepoWorkflow" (org_repo_id, type, provider, provider_workflow_id, name, is_active, meta)
SELECT r.id, 'DEPLOYMENT', 'CIRCLE_CI', v.workflow, v.name, true, '{"source": "gocd"}'::jsonb
FROM dora_repo r, (VALUES
  ('gocd:prod', 'prod')
) AS v (workflow, name)
ON CONFLICT (org_repo_id, provider_workflow_id)
DO UPDATE SET name = EXCLUDED.name, provider = EXCLUDED.provider, is_active = true, meta = EXCLUDED.meta, updated_at = now();

INSERT INTO "RepoWorkflowRuns" (repo_workflow_id, provider_workflow_run_id, status, head_branch, event_actor, conducted_at, html_url, meta)
SELECT w.id, v.run, v.status, v.branch, v.actor, v.at::timestamptz, v.url, v.meta::jsonb
FROM dora_repo r JOIN "RepoWorkflow" w ON w.org_repo_id = r.id, (VALUES
  ('gocd:prod', '1', 'SUCCESS', 'main', 'gocd', '2026-08-03T10:00:00Z'::timestamptz, 'https://gocd.test/go/pipelines/value_stream_map/prod/1', '{"commits": 2}'),
  ('gocd:prod', '2', 'FAILURE', 'main', 'gocd', '2026-09-02T08:00:00Z'::timestamptz, 'https://gocd.test/go/pipelines/value_stream_map/prod/2', '{"commits": 2}'),
  ('gocd:prod', '3', 'SUCCESS', 'main', 'gocd', '2026-09-02T12:00:00Z'::timestamptz, 'https://gocd.test/go/pipelines/value_stream_map/prod/3', '{"commits": 0}')
) AS v (workflow, run, status, branch, actor, at, url, meta)
WHERE w.provider_workflow_id = v.workflow
ON CONFLICT (repo_workflow_id, provider_workflow_run_id)
DO UPDATE SET status = EXCLUDED.status, head_branch = EXCLUDED.head_branch, event_actor = EXCLUDED.event_actor, conducted_at = EXCLUDED.conducted_at,
  html_url = EXCLUDED.html_url, meta = EXCLUDED.meta, updated_at = now();

-- Les équipes du dépôt comptent ses déploiements par workflow, plus par fusion.
UPDATE "TeamRepos" SET deployment_type = 'WORKFLOW', updated_at = now() WHERE org_repo_id IN (SELECT id FROM dora_repo);

-- Incidents : un service par projet Jira, rattaché aux équipes du dépôt.
INSERT INTO "OrgIncidentService" (provider, key, name, org_id, source_type, status, meta)
SELECT 'jira', v.key, v.name, r.org_id, 'INCIDENT_SERVICE', 'active', '{}'::jsonb
FROM dora_repo r, (VALUES
  ('ABC', 'Jira ABC')
) AS v (key, name)
ON CONFLICT (provider, org_id, key)
DO UPDATE SET name = EXCLUDED.name, is_deleted = false, updated_at = now();

INSERT INTO "TeamIncidentService" (team_id, service_id) SELECT DISTINCT t.team_id, o.id
FROM "TeamRepos" t JOIN dora_repo r ON t.org_repo_id = r.id
JOIN "OrgIncidentService" o ON o.org_id = r.org_id AND o.provider = 'jira'
WHERE t.is_active AND o.key IN ('ABC')
ON CONFLICT (team_id, service_id) DO UPDATE SET is_deleted = false, updated_at = now();

INSERT INTO "Incident" (provider, key, title, status, creation_date, resolved_date, incident_type, url, meta)
VALUES
  ('jira', 'ABC-7', 'Ancien bug', 'triggered', '2026-07-25T08:00:00Z'::timestamptz, NULL::timestamptz, 'INCIDENT', 'https://jira.test/browse/ABC-7', '{"type": "Bug"}'::jsonb),
  ('jira', 'ABC-5', 'Paiement "KO"', 'resolved', '2026-09-02T14:00:00Z'::timestamptz, '2026-09-02T18:00:00Z'::timestamptz, 'INCIDENT', 'https://jira.test/browse/ABC-5', '{"type": "Incident"}'::jsonb)
ON CONFLICT (provider, key)
DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, creation_date = EXCLUDED.creation_date,
  resolved_date = EXCLUDED.resolved_date, url = EXCLUDED.url, meta = EXCLUDED.meta, updated_at = now();

INSERT INTO "IncidentOrgIncidentServiceMap" (incident_id, service_id)
SELECT i.id, o.id FROM "Incident" i
JOIN dora_repo r ON true
JOIN "OrgIncidentService" o ON o.org_id = r.org_id AND o.provider = 'jira' AND i.key LIKE o.key || '-%'
WHERE i.provider = 'jira' AND i.key IN ('ABC-7', 'ABC-5')
ON CONFLICT DO NOTHING;

COMMIT;

╔═ gitlabGocdAndJiraGiveTheFourMeasuresThePageAndTheImport/stdout ═╗
DORA octocat/Hello-World sur 90 jours
  Fréquence de déploiement      0,2 / sem. (2 déploiements réussis)
  Délai de mise en production   26,5 h (médiane sur 4)
  Taux d'échec des changements  33 % (1 sur 3)
  Temps de rétablissement       4,0 h (médiane sur 1 incident(s) résolu(s))
Écrit dans <dir> : dora-donnees.json, dora.html, middleware-import.sql

╔═ withoutGocdEachMergeIsADeployAndWithoutJiraThereIsNoIncident/stdout ═╗
DORA octocat/Hello-World sur 90 jours
  Fréquence de déploiement      0,2 / sem. (3 déploiements réussis)
  Délai de mise en production   25,0 h (médiane sur 3)
  Taux d'échec des changements  — (pas de projet Jira)
  Temps de rétablissement       — (pas de projet Jira)
Écrit dans <dir> : dora-donnees.json, dora.html, middleware-import.sql

╔═ [end of file] ═╗
