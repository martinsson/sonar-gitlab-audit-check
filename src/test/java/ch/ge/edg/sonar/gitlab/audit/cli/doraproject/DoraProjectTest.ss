╔═ gitlabGocdAndJiraGiveTheFourMeasuresThePageAndTheImport/devlake ═╗
-- Import Apache DevLake (v1.0.x) : octocat/Hello-World, extrait le 2026-10-21T00:00:00Z.
-- Projet DevLake : Hello-World. Il doit exister (config UI ou API).
-- mysql -h 127.0.0.1 -u merico -p lake < devlake-import.sql
-- Puis lancer le plugin dora sur le projet : voir devlake/README.md.
SET NAMES utf8mb4;
START TRANSACTION;

-- Ce que le script a écrit la dernière fois pour ce projet GitLab.
DELETE cd FROM commits_diffs cd JOIN cicd_deployment_commits dc ON dc.commit_sha = cd.new_commit_sha WHERE dc.id LIKE 'dora-import:42:%';
DELETE FROM cicd_deployment_commits WHERE id LIKE 'dora-import:42:%';
DELETE FROM cicd_deployments WHERE id LIKE 'dora-import:42:%';
DELETE FROM cicd_scopes WHERE id LIKE 'dora-import:42:%';
DELETE FROM pull_requests WHERE id LIKE 'dora-import:42:%';
DELETE FROM incidents WHERE id LIKE 'dora-import:42:%';
DELETE FROM issues WHERE id LIKE 'dora-import:42:%';
DELETE FROM boards WHERE id LIKE 'dora-import:42:%';
DELETE FROM pull_request_commits WHERE pull_request_id LIKE 'dora-import:42:%';
DELETE FROM board_issues WHERE board_id LIKE 'dora-import:42:%';
DELETE FROM repos WHERE id = 'dora-import:42';
DELETE FROM project_mapping WHERE project_name = 'Hello-World' AND (row_id = 'dora-import:42' OR row_id LIKE 'dora-import:42:%');

-- Le projet doit exister : sinon project_name vaut NULL et MySQL refuse la ligne.
INSERT INTO project_mapping (project_name, `table`, row_id, created_at, updated_at) VALUES ((SELECT name FROM projects WHERE name = 'Hello-World'), 'repos', 'dora-import:42', NOW(3), NOW(3));
INSERT INTO repos (id, name, url, created_at, updated_at) VALUES ('dora-import:42', 'octocat/Hello-World', 'https://gitlab.test/octocat/Hello-World', NOW(3), NOW(3));

-- Un scope CI/CD par pipeline de production, rattaché au projet.
INSERT INTO cicd_scopes (id, name, created_at, updated_at) VALUES
  ('dora-import:42:gocd:prod', 'prod', NOW(3), NOW(3));
INSERT INTO project_mapping (project_name, `table`, row_id, created_at, updated_at)
VALUES
  ('Hello-World', 'cicd_scopes', 'dora-import:42:gocd:prod', NOW(3), NOW(3)),
  ('Hello-World', 'boards', 'dora-import:42:jira:ABC', NOW(3), NOW(3));

-- Déploiements : un run GoCD de production chacun, et un déploiement de référence par pipeline (1970).
INSERT INTO cicd_deployments (id, cicd_scope_id, name, display_title, url, result, original_result, status, original_status, environment, original_environment, created_date, started_date, finished_date, duration_sec, created_at, updated_at) VALUES
  ('dora-import:42:gocd:prod:reference', 'dora-import:42:gocd:prod', 'référence, pas un déploiement', 'référence, pas un déploiement', '', 'SUCCESS', 'SUCCESS', 'DONE', 'SUCCESS', 'PRODUCTION', 'PRODUCTION', '1970-01-01 00:00:00.000', '1970-01-01 00:00:00.000', '1970-01-01 00:00:00.000', 0, NOW(3), NOW(3)),
  ('dora-import:42:gocd:prod:1', 'dora-import:42:gocd:prod', 'prod #1', 'prod #1', 'https://gocd.test/go/pipelines/value_stream_map/prod/1', 'SUCCESS', 'SUCCESS', 'DONE', 'SUCCESS', 'PRODUCTION', 'PRODUCTION', '2026-08-03 10:00:00.000', '2026-08-03 10:00:00.000', '2026-08-03 10:00:00.000', 0, NOW(3), NOW(3)),
  ('dora-import:42:gocd:prod:2', 'dora-import:42:gocd:prod', 'prod #2', 'prod #2', 'https://gocd.test/go/pipelines/value_stream_map/prod/2', 'FAILURE', 'FAILURE', 'DONE', 'FAILURE', 'PRODUCTION', 'PRODUCTION', '2026-09-02 08:00:00.000', '2026-09-02 08:00:00.000', '2026-09-02 08:00:00.000', 0, NOW(3), NOW(3)),
  ('dora-import:42:gocd:prod:3', 'dora-import:42:gocd:prod', 'prod #3', 'prod #3', 'https://gocd.test/go/pipelines/value_stream_map/prod/3', 'SUCCESS', 'SUCCESS', 'DONE', 'SUCCESS', 'PRODUCTION', 'PRODUCTION', '2026-09-02 12:00:00.000', '2026-09-02 12:00:00.000', '2026-09-02 12:00:00.000', 0, NOW(3), NOW(3));
INSERT INTO cicd_deployment_commits (id, cicd_scope_id, cicd_deployment_id, name, display_title, url, result, original_result, status, original_status, environment, original_environment, created_date, started_date, finished_date, duration_sec, created_at, updated_at, commit_sha, ref_name, repo_id, repo_url) VALUES
  ('dora-import:42:gocd:prod:reference', 'dora-import:42:gocd:prod', 'dora-import:42:gocd:prod:reference', 'référence, pas un déploiement', 'référence, pas un déploiement', '', 'SUCCESS', 'SUCCESS', 'DONE', 'SUCCESS', 'PRODUCTION', 'PRODUCTION', '1970-01-01 00:00:00.000', '1970-01-01 00:00:00.000', '1970-01-01 00:00:00.000', 0, NOW(3), NOW(3), '0000000000000000000000000000000000000000', 'main', 'dora-import:42', 'https://gitlab.test/octocat/Hello-World'),
  ('dora-import:42:gocd:prod:1', 'dora-import:42:gocd:prod', 'dora-import:42:gocd:prod:1', 'prod #1', 'prod #1', 'https://gocd.test/go/pipelines/value_stream_map/prod/1', 'SUCCESS', 'SUCCESS', 'DONE', 'SUCCESS', 'PRODUCTION', 'PRODUCTION', '2026-08-03 10:00:00.000', '2026-08-03 10:00:00.000', '2026-08-03 10:00:00.000', 0, NOW(3), NOW(3), 'm1', 'main', 'dora-import:42', 'https://gitlab.test/octocat/Hello-World'),
  ('dora-import:42:gocd:prod:2', 'dora-import:42:gocd:prod', 'dora-import:42:gocd:prod:2', 'prod #2', 'prod #2', 'https://gocd.test/go/pipelines/value_stream_map/prod/2', 'FAILURE', 'FAILURE', 'DONE', 'FAILURE', 'PRODUCTION', 'PRODUCTION', '2026-09-02 08:00:00.000', '2026-09-02 08:00:00.000', '2026-09-02 08:00:00.000', 0, NOW(3), NOW(3), 'm2', 'main', 'dora-import:42', 'https://gitlab.test/octocat/Hello-World'),
  ('dora-import:42:gocd:prod:3', 'dora-import:42:gocd:prod', 'dora-import:42:gocd:prod:3', 'prod #3', 'prod #3', 'https://gocd.test/go/pipelines/value_stream_map/prod/3', 'SUCCESS', 'SUCCESS', 'DONE', 'SUCCESS', 'PRODUCTION', 'PRODUCTION', '2026-09-02 12:00:00.000', '2026-09-02 12:00:00.000', '2026-09-02 12:00:00.000', 0, NOW(3), NOW(3), 'm2', 'main', 'dora-import:42', 'https://gitlab.test/octocat/Hello-World');

-- Les commits que chaque déploiement réussi a mis en production, selon nos règles.
INSERT IGNORE INTO commits_diffs (new_commit_sha, old_commit_sha, commit_sha, sorting_index) VALUES
  ('m1', '0000000000000000000000000000000000000000', 'c1', 0),
  ('m1', '0000000000000000000000000000000000000000', 'm1', 1),
  ('m2', 'm1', 'c2', 0),
  ('m2', 'm1', 'm2', 1);

-- Merge requests (et, avec la règle « atteint main », les commits arrivés sans merge request) : leur commit de fusion est celui que nos règles rattachent à leur déploiement.
INSERT INTO pull_requests (id, base_repo_id, head_repo_id, base_ref, merge_commit_sha, status, original_status, title, url, author_name, pull_request_key, created_date, merged_date, closed_date, created_at, updated_at) VALUES
  ('dora-import:42:mr:1', 'dora-import:42', 'dora-import:42', 'main', 'c1', 'MERGED', 'merged', 'Premier l''essai', 'https://gitlab.test/octocat/Hello-World/-/merge_requests/1', 'dev1', 1, '2026-08-01 10:00:00.000', '2026-08-02 09:00:00.000', '2026-08-02 09:00:00.000', NOW(3), NOW(3)),
  ('dora-import:42:mr:2', 'dora-import:42', 'dora-import:42', 'main', 'c2', 'MERGED', 'merged', 'Deuxième <b>', 'https://gitlab.test/octocat/Hello-World/-/merge_requests/2', 'dev2', 2, '2026-09-01 09:00:00.000', '2026-09-02 07:00:00.000', '2026-09-02 07:00:00.000', NOW(3), NOW(3)),
  ('dora-import:42:mr:3', 'dora-import:42', 'dora-import:42', 'main', 'h3', 'MERGED', 'merged', 'Troisième', 'https://gitlab.test/octocat/Hello-World/-/merge_requests/3', 'dev3', 3, '2026-10-09 08:00:00.000', '2026-10-10 08:00:00.000', '2026-10-10 08:00:00.000', NOW(3), NOW(3));
-- Leur date d'auteur à tous : celle du premier commit, la seule que DevLake lit (le début du délai).
INSERT IGNORE INTO pull_request_commits (commit_sha, pull_request_id, commit_authored_date, created_at, updated_at) VALUES
  ('c1', 'dora-import:42:mr:1', '2026-08-01 08:00:00.000', NOW(3), NOW(3)),
  ('m1', 'dora-import:42:mr:1', '2026-08-01 08:00:00.000', NOW(3), NOW(3)),
  ('h1', 'dora-import:42:mr:1', '2026-08-01 08:00:00.000', NOW(3), NOW(3)),
  ('c2', 'dora-import:42:mr:2', '2026-09-01 08:00:00.000', NOW(3), NOW(3)),
  ('m2', 'dora-import:42:mr:2', '2026-09-01 08:00:00.000', NOW(3), NOW(3)),
  ('h2', 'dora-import:42:mr:2', '2026-09-01 08:00:00.000', NOW(3), NOW(3)),
  ('c3', 'dora-import:42:mr:3', '2026-10-09 07:00:00.000', NOW(3), NOW(3)),
  ('m3', 'dora-import:42:mr:3', '2026-10-09 07:00:00.000', NOW(3), NOW(3)),
  ('h3', 'dora-import:42:mr:3', '2026-10-09 07:00:00.000', NOW(3), NOW(3));

-- Incidents : un tableau par projet Jira, un ticket de type INCIDENT par incident.
INSERT INTO boards (id, name, type, created_at, updated_at) VALUES
  ('dora-import:42:jira:ABC', 'Jira ABC', 'jira', NOW(3), NOW(3));
INSERT INTO issues (id, url, issue_key, title, type, original_type, status, original_status, created_date, resolution_date, lead_time_minutes, original_project, created_at, updated_at) VALUES
  ('dora-import:42:jira:ABC-7', 'https://jira.test/browse/ABC-7', 'ABC-7', 'Ancien bug', 'INCIDENT', 'Bug', 'TODO', 'open', '2026-07-25 08:00:00.000', NULL, NULL, 'ABC', NOW(3), NOW(3)),
  ('dora-import:42:jira:ABC-5', 'https://jira.test/browse/ABC-5', 'ABC-5', 'Paiement "KO"', 'INCIDENT', 'Incident', 'DONE', 'resolved', '2026-09-02 14:00:00.000', '2026-09-02 18:00:00.000', 240, 'ABC', NOW(3), NOW(3));
INSERT INTO board_issues (board_id, issue_id, created_at, updated_at) VALUES
  ('dora-import:42:jira:ABC', 'dora-import:42:jira:ABC-7', NOW(3), NOW(3)),
  ('dora-import:42:jira:ABC', 'dora-import:42:jira:ABC-5', NOW(3), NOW(3));

COMMIT;

╔═ gitlabGocdAndJiraGiveTheFourMeasuresThePageAndTheImport/html ═╗
<!doctype html>
<html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>DORA octocat/Hello-World</title>
<style>:root{--bg:#f6f7f9;--fg:#16181d;--muted:#5f6672;--line:#e3e6eb;--card:#fff;--accent:#2457c5;
--ok:#1f7a4a;--ko:#b3392b;--warn:#946200;--elite:#1f7a4a;--high:#3d8b2f;--medium:#b07800;
--low:#b3392b;--chip:#eef1f5;--shadow:0 1px 2px rgba(20,24,32,.06),0 4px 16px rgba(20,24,32,.04)}
@media (prefers-color-scheme:dark){:root{--bg:#121417;--fg:#e9ebef;--muted:#9aa2ae;
--line:#2a2e35;--card:#1a1d22;--accent:#8fb2f5;--ok:#6fcf97;--ko:#f08a7c;--warn:#e7b65a;
--elite:#6fcf97;--high:#a5d96a;--medium:#e7b65a;--low:#f08a7c;--chip:#23272e;--shadow:none}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.5 system-ui,-apple-system,
"Segoe UI",Roboto,sans-serif}
main{max-width:1180px;margin:0 auto;padding:28px 16px 56px}
header{display:flex;flex-wrap:wrap;align-items:baseline;justify-content:space-between;gap:8px}
h1{font-size:26px;margin:0;letter-spacing:-.01em}h1 small{color:var(--muted);font-weight:500;
font-size:15px;display:block;text-transform:uppercase;letter-spacing:.08em}
h2{font-size:18px;margin:36px 0 10px}
a{color:var(--accent);text-decoration:none}a:hover{text-decoration:underline}
.sub,.note{color:var(--muted)}.note{font-size:13px;margin:8px 2px 0}
.tiles{display:grid;grid-template-columns:repeat(auto-fit,minmax(250px,1fr));gap:14px;
margin-top:22px}
.tile{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:16px 18px;
box-shadow:var(--shadow);border-top:4px solid var(--line)}
.tile.ELITE{border-top-color:var(--elite)}.tile.HIGH{border-top-color:var(--high)}
.tile.MEDIUM{border-top-color:var(--medium)}.tile.LOW{border-top-color:var(--low)}
.tile h3{font-size:12px;font-weight:650;color:var(--muted);margin:0;text-transform:uppercase;
letter-spacing:.06em}
.tile .v{font-size:34px;font-weight:700;margin:6px 0 4px;font-variant-numeric:tabular-nums;
letter-spacing:-.02em}
.tile p{margin:8px 0 0;font-size:13px;color:var(--muted)}
.band{display:inline-block;font-size:12px;font-weight:650;padding:2px 9px;border-radius:99px;
background:var(--chip);color:var(--muted)}
.band.ELITE{color:var(--elite)}.band.HIGH{color:var(--high)}.band.MEDIUM{color:var(--medium)}
.band.LOW{color:var(--low)}
.scale{display:grid;grid-template-columns:repeat(4,1fr);gap:3px;margin-top:10px}
.scale span{height:6px;border-radius:3px;background:var(--chip)}
.scale span.on.ELITE{background:var(--elite)}.scale span.on.HIGH{background:var(--high)}
.scale span.on.MEDIUM{background:var(--medium)}.scale span.on.LOW{background:var(--low)}
.scroll{overflow-x:auto;border:1px solid var(--line);border-radius:12px;background:var(--card);
box-shadow:var(--shadow)}
table{border-collapse:collapse;width:100%;font-size:14px}
th,td{padding:8px 12px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top;
white-space:nowrap}
td.t{white-space:normal;min-width:220px}
th{font-weight:600;color:var(--muted);background:var(--card);position:sticky;top:0;font-size:13px}
tbody tr:nth-child(even) td{background:color-mix(in srgb,var(--chip) 45%,transparent)}
tr:last-child td{border-bottom:0}
td.n{text-align:right;font-variant-numeric:tabular-nums}
.SUCCESS{color:var(--ok)}.FAILURE{color:var(--ko)}.CANCELLED,.PENDING{color:var(--warn)}
td.SUCCESS,td.FAILURE,td.CANCELLED,td.PENDING{font-weight:600}
.empty{padding:12px 16px;color:var(--muted)}
.rules td:first-child{font-weight:600;white-space:nowrap}.rules td{white-space:normal}
</style></head><body><main>
<header><h1><small>Mesures DORA</small>
<a href="https://gitlab.test/octocat/Hello-World">octocat/Hello-World</a></h1>
<p class="sub">Du 2026-07-23 au 2026-10-21 (90 jours), extrait le 2026-10-21 02:00.</p></header>
<section class="tiles">
<div class="tile LOW"><h3>Fréquence de déploiement</h3><div class="v">0,2 / sem.</div><span class="band LOW">niveau bas</span><div class="scale"><span></span><span></span><span></span><span class="on LOW"></span></div><p>2 déploiements réussis en 90 jours</p></div>
<div class="tile HIGH"><h3>Délai de mise en production</h3><div class="v">26,5 h</div><span class="band HIGH">niveau haut</span><div class="scale"><span></span><span class="on HIGH"></span><span></span><span></span></div><p>médiane sur 4 commits</p></div>
<div class="tile LOW"><h3>Taux d&#39;échec des changements</h3><div class="v">33 %</div><span class="band LOW">niveau bas</span><div class="scale"><span></span><span></span><span></span><span class="on LOW"></span></div><p>1 déploiement(s) sur 3 suivi(s) d&#39;un incident ; runs en échec : 33 %</p></div>
<div class="tile HIGH"><h3>Temps de rétablissement</h3><div class="v">4,0 h</div><span class="band HIGH">niveau haut</span><div class="scale"><span></span><span class="on HIGH"></span><span></span><span></span></div><p>médiane sur 1 incident(s) résolu(s) sur 2</p></div>
</section>
<p class="note">Niveaux du rapport DORA 2023, aux seuils du tableau de bord DORA d'Apache DevLake : élite, haut, moyen, bas.</p>
<h2>Règles appliquées</h2>
<div class="scroll"><table class="rules"><thead><tr><th>Mesure</th><th>Notre règle</th><th>Dans DevLake (devlake-import.sql)</th></tr></thead><tbody>
<tr><td>Déploiement</td><td>un run réussi de prod (GoCD)</td><td>un cicd_deployment en PRODUCTION par run ; fréquence : médiane des jours de déploiement par semaine</td></tr>
<tr><td>Rattachement d&#39;un commit</td><td>les commits que le run a embarqués, pipelines amont compris ; ceux d&#39;un run en échec relancé sans nouveau matériau vont au run réussi suivant du même pipeline</td><td>les lignes commits_diffs de son déploiement</td></tr>
<tr><td>Délai</td><td>de la date d&#39;auteur d&#39;un commit à son premier déploiement réussi</td><td>par merge request : de son premier commit au déploiement qui porte son commit de fusion ; médiane basse</td></tr>
<tr><td>Incident</td><td>un ticket de type Incident ou Bug créé dans ABC</td><td>un ticket de type INCIDENT</td></tr>
<tr><td>Échec d&#39;un changement</td><td>un déploiement, réussi ou non, suivi d&#39;au moins un incident avant le déploiement suivant, rapporté à tous les déploiements (la règle de Middleware)</td><td>un déploiement réussi suivi d&#39;un incident, rapporté aux seuls déploiements réussis</td></tr>
<tr><td>Rétablissement</td><td>de la création de l&#39;incident à sa date de résolution Jira</td><td>rapport 2021 : la même durée ; rapport 2023 : du déploiement à la résolution ; médiane basse</td></tr>
</tbody></table></div>
<h2>Déploiements (3)</h2>
<div class="scroll"><table><thead><tr><th>Date</th><th>Run</th><th>Résultat</th><th>Embarqués</th><th>Livrés</th><th>Incidents qui suivent</th></tr></thead><tbody>
<tr><td>2026-09-02 14:00</td><td><a href="https://gocd.test/go/pipelines/value_stream_map/prod/3">prod #3</a></td><td class="SUCCESS">succès</td><td class="n">0</td><td class="n">2</td><td>ABC-5</td></tr>
<tr><td>2026-09-02 10:00</td><td><a href="https://gocd.test/go/pipelines/value_stream_map/prod/2">prod #2</a></td><td class="FAILURE">échec</td><td class="n">2</td><td class="n">0</td><td></td></tr>
<tr><td>2026-08-03 12:00</td><td><a href="https://gocd.test/go/pipelines/value_stream_map/prod/1">prod #1</a></td><td class="SUCCESS">succès</td><td class="n">2</td><td class="n">2</td><td></td></tr>
</tbody></table></div>
<p class="note">Embarqués : les commits du projet que GoCD dit avoir embarqués. Livrés : ceux que nos règles lui attribuent, mis en production pour la première fois.</p>
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
Écrit dans <dir> : dora-donnees.json, dora.html, middleware-import.sql, devlake-import.sql

╔═ gocdRunsWithoutProjectCommitsDeliverWhatReachedMainBeforeThem/devlake ═╗
-- Import Apache DevLake (v1.0.x) : octocat/Hello-World, extrait le 2026-10-21T00:00:00Z.
-- Projet DevLake : Hello-World. Il doit exister (config UI ou API).
-- mysql -h 127.0.0.1 -u merico -p lake < devlake-import.sql
-- Puis lancer le plugin dora sur le projet : voir devlake/README.md.
SET NAMES utf8mb4;
START TRANSACTION;

-- Ce que le script a écrit la dernière fois pour ce projet GitLab.
DELETE cd FROM commits_diffs cd JOIN cicd_deployment_commits dc ON dc.commit_sha = cd.new_commit_sha WHERE dc.id LIKE 'dora-import:42:%';
DELETE FROM cicd_deployment_commits WHERE id LIKE 'dora-import:42:%';
DELETE FROM cicd_deployments WHERE id LIKE 'dora-import:42:%';
DELETE FROM cicd_scopes WHERE id LIKE 'dora-import:42:%';
DELETE FROM pull_requests WHERE id LIKE 'dora-import:42:%';
DELETE FROM incidents WHERE id LIKE 'dora-import:42:%';
DELETE FROM issues WHERE id LIKE 'dora-import:42:%';
DELETE FROM boards WHERE id LIKE 'dora-import:42:%';
DELETE FROM pull_request_commits WHERE pull_request_id LIKE 'dora-import:42:%';
DELETE FROM board_issues WHERE board_id LIKE 'dora-import:42:%';
DELETE FROM repos WHERE id = 'dora-import:42';
DELETE FROM project_mapping WHERE project_name = 'Hello-World' AND (row_id = 'dora-import:42' OR row_id LIKE 'dora-import:42:%');

-- Le projet doit exister : sinon project_name vaut NULL et MySQL refuse la ligne.
INSERT INTO project_mapping (project_name, `table`, row_id, created_at, updated_at) VALUES ((SELECT name FROM projects WHERE name = 'Hello-World'), 'repos', 'dora-import:42', NOW(3), NOW(3));
INSERT INTO repos (id, name, url, created_at, updated_at) VALUES ('dora-import:42', 'octocat/Hello-World', 'https://gitlab.test/octocat/Hello-World', NOW(3), NOW(3));

-- Un scope CI/CD par pipeline de production, rattaché au projet.
INSERT INTO cicd_scopes (id, name, created_at, updated_at) VALUES
  ('dora-import:42:gocd:prod-ff', 'prod-ff', NOW(3), NOW(3));
INSERT INTO project_mapping (project_name, `table`, row_id, created_at, updated_at)
VALUES
  ('Hello-World', 'cicd_scopes', 'dora-import:42:gocd:prod-ff', NOW(3), NOW(3)),
  ('Hello-World', 'boards', 'dora-import:42:jira:ABC', NOW(3), NOW(3));

-- Déploiements : un run GoCD de production chacun, et un déploiement de référence par pipeline (1970).
INSERT INTO cicd_deployments (id, cicd_scope_id, name, display_title, url, result, original_result, status, original_status, environment, original_environment, created_date, started_date, finished_date, duration_sec, created_at, updated_at) VALUES
  ('dora-import:42:gocd:prod-ff:reference', 'dora-import:42:gocd:prod-ff', 'référence, pas un déploiement', 'référence, pas un déploiement', '', 'SUCCESS', 'SUCCESS', 'DONE', 'SUCCESS', 'PRODUCTION', 'PRODUCTION', '1970-01-01 00:00:00.000', '1970-01-01 00:00:00.000', '1970-01-01 00:00:00.000', 0, NOW(3), NOW(3)),
  ('dora-import:42:gocd:prod-ff:1', 'dora-import:42:gocd:prod-ff', 'prod-ff #1', 'prod-ff #1', 'https://gocd.test/go/pipelines/value_stream_map/prod-ff/1', 'SUCCESS', 'SUCCESS', 'DONE', 'SUCCESS', 'PRODUCTION', 'PRODUCTION', '2026-08-03 10:00:00.000', '2026-08-03 10:00:00.000', '2026-08-03 10:00:00.000', 0, NOW(3), NOW(3)),
  ('dora-import:42:gocd:prod-ff:2', 'dora-import:42:gocd:prod-ff', 'prod-ff #2', 'prod-ff #2', 'https://gocd.test/go/pipelines/value_stream_map/prod-ff/2', 'FAILURE', 'FAILURE', 'DONE', 'FAILURE', 'PRODUCTION', 'PRODUCTION', '2026-09-02 08:00:00.000', '2026-09-02 08:00:00.000', '2026-09-02 08:00:00.000', 0, NOW(3), NOW(3)),
  ('dora-import:42:gocd:prod-ff:3', 'dora-import:42:gocd:prod-ff', 'prod-ff #3', 'prod-ff #3', 'https://gocd.test/go/pipelines/value_stream_map/prod-ff/3', 'SUCCESS', 'SUCCESS', 'DONE', 'SUCCESS', 'PRODUCTION', 'PRODUCTION', '2026-09-02 12:00:00.000', '2026-09-02 12:00:00.000', '2026-09-02 12:00:00.000', 0, NOW(3), NOW(3)),
  ('dora-import:42:gocd:prod-ff:4', 'dora-import:42:gocd:prod-ff', 'prod-ff #4', 'prod-ff #4', 'https://gocd.test/go/pipelines/value_stream_map/prod-ff/4', 'SUCCESS', 'SUCCESS', 'DONE', 'SUCCESS', 'PRODUCTION', 'PRODUCTION', '2026-09-21 10:00:00.000', '2026-09-21 10:00:00.000', '2026-09-21 10:00:00.000', 0, NOW(3), NOW(3));
INSERT INTO cicd_deployment_commits (id, cicd_scope_id, cicd_deployment_id, name, display_title, url, result, original_result, status, original_status, environment, original_environment, created_date, started_date, finished_date, duration_sec, created_at, updated_at, commit_sha, ref_name, repo_id, repo_url) VALUES
  ('dora-import:42:gocd:prod-ff:reference', 'dora-import:42:gocd:prod-ff', 'dora-import:42:gocd:prod-ff:reference', 'référence, pas un déploiement', 'référence, pas un déploiement', '', 'SUCCESS', 'SUCCESS', 'DONE', 'SUCCESS', 'PRODUCTION', 'PRODUCTION', '1970-01-01 00:00:00.000', '1970-01-01 00:00:00.000', '1970-01-01 00:00:00.000', 0, NOW(3), NOW(3), '0000000000000000000000000000000000000000', 'main', 'dora-import:42', 'https://gitlab.test/octocat/Hello-World'),
  ('dora-import:42:gocd:prod-ff:1', 'dora-import:42:gocd:prod-ff', 'dora-import:42:gocd:prod-ff:1', 'prod-ff #1', 'prod-ff #1', 'https://gocd.test/go/pipelines/value_stream_map/prod-ff/1', 'SUCCESS', 'SUCCESS', 'DONE', 'SUCCESS', 'PRODUCTION', 'PRODUCTION', '2026-08-03 10:00:00.000', '2026-08-03 10:00:00.000', '2026-08-03 10:00:00.000', 0, NOW(3), NOW(3), 'h1', 'main', 'dora-import:42', 'https://gitlab.test/octocat/Hello-World'),
  ('dora-import:42:gocd:prod-ff:2', 'dora-import:42:gocd:prod-ff', 'dora-import:42:gocd:prod-ff:2', 'prod-ff #2', 'prod-ff #2', 'https://gocd.test/go/pipelines/value_stream_map/prod-ff/2', 'FAILURE', 'FAILURE', 'DONE', 'FAILURE', 'PRODUCTION', 'PRODUCTION', '2026-09-02 08:00:00.000', '2026-09-02 08:00:00.000', '2026-09-02 08:00:00.000', 0, NOW(3), NOW(3), 'h1', 'main', 'dora-import:42', 'https://gitlab.test/octocat/Hello-World'),
  ('dora-import:42:gocd:prod-ff:3', 'dora-import:42:gocd:prod-ff', 'dora-import:42:gocd:prod-ff:3', 'prod-ff #3', 'prod-ff #3', 'https://gocd.test/go/pipelines/value_stream_map/prod-ff/3', 'SUCCESS', 'SUCCESS', 'DONE', 'SUCCESS', 'PRODUCTION', 'PRODUCTION', '2026-09-02 12:00:00.000', '2026-09-02 12:00:00.000', '2026-09-02 12:00:00.000', 0, NOW(3), NOW(3), 'h2', 'main', 'dora-import:42', 'https://gitlab.test/octocat/Hello-World'),
  ('dora-import:42:gocd:prod-ff:4', 'dora-import:42:gocd:prod-ff', 'dora-import:42:gocd:prod-ff:4', 'prod-ff #4', 'prod-ff #4', 'https://gocd.test/go/pipelines/value_stream_map/prod-ff/4', 'SUCCESS', 'SUCCESS', 'DONE', 'SUCCESS', 'PRODUCTION', 'PRODUCTION', '2026-09-21 10:00:00.000', '2026-09-21 10:00:00.000', '2026-09-21 10:00:00.000', 0, NOW(3), NOW(3), 'd2', 'main', 'dora-import:42', 'https://gitlab.test/octocat/Hello-World');

-- Les commits que chaque déploiement réussi a mis en production, selon nos règles.
INSERT IGNORE INTO commits_diffs (new_commit_sha, old_commit_sha, commit_sha, sorting_index) VALUES
  ('h1', '0000000000000000000000000000000000000000', 'c1', 0),
  ('h1', '0000000000000000000000000000000000000000', 'm1', 1),
  ('h1', '0000000000000000000000000000000000000000', 'h1', 2),
  ('h2', 'h1', 'c2', 0),
  ('h2', 'h1', 'm2', 1),
  ('h2', 'h1', 'h2', 2),
  ('d2', 'h2', 'd1', 0),
  ('d2', 'h2', 'd2', 1);

-- Merge requests (et, avec la règle « atteint main », les commits arrivés sans merge request) : leur commit de fusion est celui que nos règles rattachent à leur déploiement.
INSERT INTO pull_requests (id, base_repo_id, head_repo_id, base_ref, merge_commit_sha, status, original_status, title, url, author_name, pull_request_key, created_date, merged_date, closed_date, created_at, updated_at) VALUES
  ('dora-import:42:mr:1', 'dora-import:42', 'dora-import:42', 'main', 'c1', 'MERGED', 'merged', 'Premier l''essai', 'https://gitlab.test/octocat/Hello-World/-/merge_requests/1', 'dev1', 1, '2026-08-01 10:00:00.000', '2026-08-02 09:00:00.000', '2026-08-02 09:00:00.000', NOW(3), NOW(3)),
  ('dora-import:42:mr:2', 'dora-import:42', 'dora-import:42', 'main', 'c2', 'MERGED', 'merged', 'Deuxième <b>', 'https://gitlab.test/octocat/Hello-World/-/merge_requests/2', 'dev2', 2, '2026-09-01 09:00:00.000', '2026-09-02 07:00:00.000', '2026-09-02 07:00:00.000', NOW(3), NOW(3)),
  ('dora-import:42:mr:3', 'dora-import:42', 'dora-import:42', 'main', 'h3', 'MERGED', 'merged', 'Troisième', 'https://gitlab.test/octocat/Hello-World/-/merge_requests/3', 'dev3', 3, '2026-10-09 08:00:00.000', '2026-10-10 08:00:00.000', '2026-10-10 08:00:00.000', NOW(3), NOW(3)),
  ('dora-import:42:main:d1', 'dora-import:42', 'dora-import:42', 'main', 'd1', 'MERGED', 'merged', 'Commits sur main sans merge request : ABC-9', 'https://gitlab.test/octocat/Hello-World', '', NULL, '2026-09-10 10:00:00.000', '2026-09-10 10:10:00.000', '2026-09-10 10:10:00.000', NOW(3), NOW(3)),
  ('dora-import:42:main:d2', 'dora-import:42', 'dora-import:42', 'main', 'd2', 'MERGED', 'merged', 'Commits sur main sans merge request : d2', 'https://gitlab.test/octocat/Hello-World', '', NULL, '2026-09-20 09:00:00.000', '2026-09-20 09:00:00.000', '2026-09-20 09:00:00.000', NOW(3), NOW(3));
-- Leur date d'auteur à tous : celle du premier commit, la seule que DevLake lit (le début du délai).
INSERT IGNORE INTO pull_request_commits (commit_sha, pull_request_id, commit_authored_date, created_at, updated_at) VALUES
  ('c1', 'dora-import:42:mr:1', '2026-08-01 08:00:00.000', NOW(3), NOW(3)),
  ('m1', 'dora-import:42:mr:1', '2026-08-01 08:00:00.000', NOW(3), NOW(3)),
  ('h1', 'dora-import:42:mr:1', '2026-08-01 08:00:00.000', NOW(3), NOW(3)),
  ('c2', 'dora-import:42:mr:2', '2026-09-01 08:00:00.000', NOW(3), NOW(3)),
  ('m2', 'dora-import:42:mr:2', '2026-09-01 08:00:00.000', NOW(3), NOW(3)),
  ('h2', 'dora-import:42:mr:2', '2026-09-01 08:00:00.000', NOW(3), NOW(3)),
  ('c3', 'dora-import:42:mr:3', '2026-10-09 07:00:00.000', NOW(3), NOW(3)),
  ('m3', 'dora-import:42:mr:3', '2026-10-09 07:00:00.000', NOW(3), NOW(3)),
  ('h3', 'dora-import:42:mr:3', '2026-10-09 07:00:00.000', NOW(3), NOW(3)),
  ('d1', 'dora-import:42:main:d1', '2026-09-10 10:00:00.000', NOW(3), NOW(3)),
  ('d2', 'dora-import:42:main:d2', '2026-09-20 09:00:00.000', NOW(3), NOW(3));

-- Incidents : un tableau par projet Jira, un ticket de type INCIDENT par incident.
INSERT INTO boards (id, name, type, created_at, updated_at) VALUES
  ('dora-import:42:jira:ABC', 'Jira ABC', 'jira', NOW(3), NOW(3));
INSERT INTO issues (id, url, issue_key, title, type, original_type, status, original_status, created_date, resolution_date, lead_time_minutes, original_project, created_at, updated_at) VALUES
  ('dora-import:42:jira:ABC-7', 'https://jira.test/browse/ABC-7', 'ABC-7', 'Ancien bug', 'INCIDENT', 'Bug', 'TODO', 'open', '2026-07-25 08:00:00.000', NULL, NULL, 'ABC', NOW(3), NOW(3)),
  ('dora-import:42:jira:ABC-5', 'https://jira.test/browse/ABC-5', 'ABC-5', 'Paiement "KO"', 'INCIDENT', 'Incident', 'DONE', 'resolved', '2026-09-02 14:00:00.000', '2026-09-02 18:00:00.000', 240, 'ABC', NOW(3), NOW(3));
INSERT INTO board_issues (board_id, issue_id, created_at, updated_at) VALUES
  ('dora-import:42:jira:ABC', 'dora-import:42:jira:ABC-7', NOW(3), NOW(3)),
  ('dora-import:42:jira:ABC', 'dora-import:42:jira:ABC-5', NOW(3), NOW(3));

COMMIT;

╔═ gocdRunsWithoutProjectCommitsDeliverWhatReachedMainBeforeThem/json ═╗
{
  "project" : {
    "gitlabId" : "42",
    "path" : "octocat/Hello-World",
    "webUrl" : "https://gitlab.test/octocat/Hello-World",
    "defaultBranch" : "main",
    "deploySource" : "REACHABLE",
    "prodPipelines" : [ "prod-ff" ],
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
    "pipeline" : "prod-ff",
    "counter" : 1,
    "at" : "2026-08-03T10:00:00Z",
    "outcome" : "SUCCESS",
    "forced" : false,
    "commits" : [ ],
    "url" : "https://gocd.test/go/pipelines/value_stream_map/prod-ff/1"
  }, {
    "pipeline" : "prod-ff",
    "counter" : 2,
    "at" : "2026-09-02T08:00:00Z",
    "outcome" : "FAILURE",
    "forced" : false,
    "commits" : [ ],
    "url" : "https://gocd.test/go/pipelines/value_stream_map/prod-ff/2"
  }, {
    "pipeline" : "prod-ff",
    "counter" : 3,
    "at" : "2026-09-02T12:00:00Z",
    "outcome" : "SUCCESS",
    "forced" : false,
    "commits" : [ ],
    "url" : "https://gocd.test/go/pipelines/value_stream_map/prod-ff/3"
  }, {
    "pipeline" : "prod-ff",
    "counter" : 4,
    "at" : "2026-09-21T10:00:00Z",
    "outcome" : "SUCCESS",
    "forced" : false,
    "commits" : [ ],
    "url" : "https://gocd.test/go/pipelines/value_stream_map/prod-ff/4"
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
  } ],
  "mainBranch" : {
    "commits" : [ {
      "sha" : "c3",
      "title" : "Troisième",
      "authoredAt" : "2026-10-09T07:00:00Z",
      "committedAt" : "2026-10-10T08:00:00Z"
    }, {
      "sha" : "d2",
      "title" : "petit ménage",
      "authoredAt" : "2026-09-20T09:00:00Z",
      "committedAt" : "2026-09-20T09:00:00Z"
    }, {
      "sha" : "d1",
      "title" : "ABC-9 correctif",
      "authoredAt" : "2026-09-10T10:00:00Z",
      "committedAt" : "2026-09-10T10:05:00Z"
    }, {
      "sha" : "c2",
      "title" : "Deuxième",
      "authoredAt" : "2026-09-01T08:00:00Z",
      "committedAt" : "2026-09-02T07:00:00Z"
    }, {
      "sha" : "c1",
      "title" : "Premier",
      "authoredAt" : "2026-08-01T08:00:00Z",
      "committedAt" : "2026-08-02T09:00:00Z"
    } ],
    "pushes" : [ {
      "at" : "2026-09-10T10:10:00Z",
      "commitFrom" : "c2",
      "commitTo" : "d1",
      "commitCount" : 1
    } ]
  }
}

╔═ withoutGocdEachMergeIsADeployAndWithoutJiraThereIsNoIncident/stdout ═╗
DORA octocat/Hello-World sur 90 jours
  Fréquence de déploiement      0,2 / sem. (3 déploiements réussis)
  Délai de mise en production   25,0 h (médiane sur 3)
  Taux d'échec des changements  — (pas de projet Jira)
  Temps de rétablissement       — (pas de projet Jira)
Écrit dans <dir> : dora-donnees.json, dora.html, middleware-import.sql, devlake-import.sql

╔═ [end of file] ═╗
