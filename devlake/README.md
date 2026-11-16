# Apache DevLake: computing the DORA measures from our extractions

DevLake is only used here to measure DORA. `DoraProject` still writes `dora.html`, which stays the
readable view, and `middleware-import.sql` for Middleware HQ. It also writes `devlake-import.sql`:
the same deploys, merge requests and incidents, written into DevLake's database with our rules
already applied.

| File | What it is |
|---|---|
| `compose.yaml`, `.env.example` | The stack on one machine: Podman Desktop, `podman compose` or `docker compose` |
| `helm/values-openshift.yaml`, `helm/openshift-post-render.sh` | The official chart on OpenShift (restricted-v2 SCC) |

Versions: DevLake **v1.0.2** everywhere (server, config UI, Grafana image, chart 1.0.2). It is the
latest stable release. The `v1.0.3-betaN` images published since are pre-releases, though the
chart's `main` branch defaults to them. The SQL was checked against the v1.0.2 schema.

## 1. Start it locally (Podman Desktop)

```bash
cd devlake
cp .env.example .env     # set ENCRYPTION_SECRET and the three passwords; .env is git-ignored
podman compose up -d     # or: docker compose up -d
```

- Config UI: <http://localhost:4000>. Grafana: <http://localhost:4000/grafana> (user `admin`,
  `GRAFANA_ADMIN_PASSWORD`).
- The DevLake API without the UI's API-key check is on <http://localhost:8080>. MySQL is on
  `localhost:3306`. Both are bound to 127.0.0.1 only.
- On first start, DevLake creates its tables (about 30 s). Wait until `curl -s localhost:8080/ping`
  answers.

The stack is rootless-friendly: no privileged container, named volumes only (so no SELinux `:Z`
relabelling is needed), and every port is above 1024. In Podman Desktop, *Compose → Up* on this file
does the same as the command.

## 2. Deploy it on OpenShift

This uses a thin wrapper around the official chart, not a chart of our own:

```bash
helm repo add devlake https://apache.github.io/incubator-devlake-helm-chart
helm repo update
oc new-project devlake
oc create secret generic devlake-encryption \
  --from-literal=ENCRYPTION_SECRET="$(openssl rand -base64 2000 | tr -dc 'A-Z' | fold -w 128 | head -n 1)"
oc create secret generic devlake-mysql-auth \
  --from-literal=MYSQL_PASSWORD='…' --from-literal=MYSQL_ROOT_PASSWORD='…'
helm upgrade --install devlake devlake/devlake --version 1.0.2 -n devlake \
  -f helm/values-openshift.yaml --set ingress.hostname=devlake.apps.<cluster domain> \
  --post-renderer helm/openshift-post-render.sh
```

What the values change, and why:

- **No fixed UID, GID or fsGroup.** Restricted-v2 assigns them from the namespace range.
  - The Grafana sub-chart pins 472, and Helm cannot unset a sub-chart default from values.
    `openshift-post-render.sh` deletes those three lines.
  - Without the post-renderer, an administrator can instead grant the Grafana service account the
    `nonroot-v2` SCC: `oc adm policy add-scc-to-user nonroot-v2 -z devlake-grafana`.
  - Grafana's chown init container (root) is turned off.
- **DevLake server.**
  - `DISABLED_REMOTE_PLUGINS=true`: the Python plugins run through poetry under `/app/.local`, which
    only the image's own user can read. Under an arbitrary UID they stop the server at start. None
    is needed (GitLab, Jira, webhook and dora are Go plugins).
  - A writable `emptyDir` on `/app/logs`, which the image's own user owns.
- **Every container** drops all capabilities, cannot escalate, and uses `RuntimeDefault` seccomp.
- **Routing.** An Ingress with an empty class, which OpenShift's router turns into an edge Route on
  its default certificate. Set `ingress.tlsSecretName` for your own certificate.
- **Storage.** PVCs on the cluster's default storage class. `mysql.storage.class` and
  `grafana.persistence.storageClassName` choose another.
- **Secrets** are created by hand, never written in the values.

Checked: `helm template` of chart 1.0.2 with these values and the post-renderer renders, with no
`runAsUser`, `runAsGroup` or `fsGroup` left. Each image was also started in Docker as UID 1000680000
with group 0, the way restricted-v2 runs them: MySQL 8.0.40, Grafana, the config UI, and DevLake with
the two changes above all came up. Without them, DevLake does not start. **Not done: a real
OpenShift install.**

## 3. Feed it our data

```bash
# 1. The DevLake project, once: config UI (Projects → New project, DORA on), or the API.
curl -X POST localhost:8080/projects -H 'Content-Type: application/json' \
  -d '{"name":"mon-projet","enable":true,"metrics":[{"pluginName":"dora","pluginOption":{},"enable":true}]}'

# 2. The import, from an extraction or from a saved snapshot (no call at all with --from).
jbang dora/DoraProject.java --gitlab-project group/sub/mon-projet --gocd-pipeline mon-projet-prod \
    --jira ABC --devlake-project mon-projet
jbang dora/DoraProject.java --from extraction/mon-projet/dora/dora-donnees.json --devlake-project mon-projet

# 3. Into DevLake's MySQL.
podman exec -i devlake-mysql-1 mysql -u merico -p"$MYSQL_PASSWORD" lake < extraction/mon-projet/dora/devlake-import.sql
#    OpenShift: oc -n devlake exec -i devlake-mysql-0 -- sh -c 'mysql -u merico -p"$MYSQL_PASSWORD" lake' < devlake-import.sql

# 4. DevLake's DORA computation, alone (see the warning below).
curl -X POST localhost:8080/pipelines -H 'Content-Type: application/json' -d '{"name":"dora mon-projet","plan":[
  [{"plugin":"dora","subtasks":["generateDeployments","generateDeploymentCommits","enrichPrevSuccessDeploymentCommits"],"options":{"projectName":"mon-projet"}}],
  [{"plugin":"dora","subtasks":["calculateChangeLeadTime","ConvertIssuesToIncidents","ConnectIncidentToDeployment"],"options":{"projectName":"mon-projet"}}]]}'
#    OpenShift: oc -n devlake port-forward svc/devlake-lake 8080 first.

# 5. Grafana → Dashboards → DORA, project mon-projet, a period that covers the extraction.
```

- **Re-running.** The script refuses to run if the project does not exist: MySQL answers *Column
  'project_name' cannot be null* and nothing is written. Otherwise it can be run again: it first
  deletes what it wrote last time for this GitLab project (ids `dora-import:<gitlab id>:…`), then
  writes it again.
- **Do not use "Collect data" (the blueprint) on this project.** In v1.0.2 the blueprint starts with
  `org/setProjectMapping`, which rewrites the project's mappings from its own connections, and so
  removes ours. The prototype saw it happen. Run the pipeline above instead. If the project also
  has native connections (step 4 of section 4), run the blueprint first, then import again, then
  run this pipeline.

### Writing to DevLake: the one exception

Every tool in this repository only reads (GET) GitLab, Jira, GoCD and Sonar, and that does not
change. `DoraProject` does not write to DevLake either. It writes a file, `devlake-import.sql`. You
apply it yourself to DevLake's own database, which you run, as with `middleware-import.sql`. The
only HTTP writes are the two `curl` calls above, to your local DevLake, and you run them by hand.

## 4. How our rules are translated

### Why the domain layer, and not the webhook or the native plugins

DevLake offers three ways in. The SQL import is the only one that keeps our rules:

1. **Native plugins** (GitLab, Jira) plus DevLake's own deploy detection. DevLake has no GoCD
   plugin. Its GitLab plugin also dates a merge request's deploy through its `merge_commit_sha`,
   which a fast-forward merge does not have.
2. **The webhook API** (`POST /plugins/webhook/connections/:id/deployments`, `/issues`,
   `/issue/:key/close`).
   - A deployment carries the *head* commit per repository. DevLake then needs a git clone of the
     repository (`gitextractor`, then `refdiff`) to work out which commits each deploy brought.
     That is a git ancestry walk, not our GoCD upstream walk, and not the « reached main » rule.
   - It also refuses a deployment with no commit (`deployment_commits is empty`). Our forced
     re-runs and our runs without project materials would be lost.
3. **The domain layer**, the tables the `dora` plugin reads: `cicd_deployments`,
   `cicd_deployment_commits`, `commits_diffs`, `pull_requests`, `issues`, `board_issues` and
   `project_mapping`.
   - `devlake-import.sql` writes these tables, with our deploy ↔ commit mapping already decided.
   - DevLake's own `dora` plugin then does the rest, unchanged: previous successful deploy,
     per-PR lead time, incidents linked to deploys.
   - Its Grafana DORA dashboard reads the result.

The mapping, table by table:

| Ours | DevLake |
|---|---|
| A prod GoCD pipeline | a `cicd_scopes` row, `project_mapping` (`cicd_scopes`) |
| A GoCD run (any outcome) | a `cicd_deployments` row and a `cicd_deployment_commits` row, environment `PRODUCTION`, result `SUCCESS`, `FAILURE`, or empty for cancelled or running. Dates: the run's scheduled time |
| The commits a successful run put in production, by our rule (GoCD materials with failed-run carry-over, or « reached main ») | its `commits_diffs` rows, against the previous successful run of the same pipeline. The deploy's commit is the last one it delivered or embarked; otherwise the previous run's |
| (no equivalent) | one *reference* deploy per pipeline, dated 1970-01-01, with no commit. DevLake only dates changes from a pipeline's second successful deploy, since the first has no predecessor. With the reference, our first deploy counts too. It falls outside any period a dashboard shows |
| A merge request | a `pull_requests` row. Its `merge_commit_sha` is one of its commits that our rule ties to its deploy. Its `pull_request_commits` rows all carry the MR's first commit date, the only one DevLake reads |
| « Reached main », a feature without MR | a `pull_requests` row too, created at its first commit and merged when it reached main. DevLake's per-PR lead time then equals our per-feature lead time |
| A Jira project | a `boards` row, `project_mapping` (`boards`) |
| An incident (`--incident-types` or `--incident-jql`) | an `issues` row of type `INCIDENT`, `DONE` once resolved, with `lead_time_minutes`. DevLake's `dora` plugin turns it into an `incidents` row |

### Where DevLake's formulas differ from ours

| Measure | Ours (`dora.html`) | DevLake (DORA dashboard) | Effect |
|---|---|---|---|
| Deployment frequency | successful deploys × 7 / days | median number of *days with a deploy* per week (elite ≥ 5), per month, per six months. A short period shows N/A: it needs six months | two deploys on one day count once in DevLake. Compare the band, not the number |
| Lead time | median over commits (GoCD materials) or over features (« reached main »): from a commit's author date to the first successful deploy carrying it | median over pull requests of the PR cycle time: first commit → PR opened → merged → deploy finished. The pieces add up to first commit → deploy | the same per MR and per feature. With materials, ours weighs every commit, DevLake every MR. DevLake takes the *lower* median (the largest value whose percent rank ≤ 0.5); ours averages the two middle values |
| Change failure rate | deploys **of any outcome** followed by an incident before the next deploy, over **all** deploys (Middleware's rule) | **successful** deploys followed by an incident before the next successful deploy, over successful deploys | DevLake is higher as soon as a run fails: 1/2 instead of 1/3 on the fake data. Not reconcilable without changing DevLake's dashboard query |
| Time to restore | median, incident creation → Jira resolution | 2021 report: the same (`lead_time_minutes`), lower median. 2023 report (*failed deployment recovery time*): from the deploy that caused it to the resolution | identical with the 2021 report |
| DORA band | DORA 2023 thresholds, applied to our numbers | the same thresholds | the page's tiles and the dashboard agree when the numbers do |

### The « reached main » rule (`--deploy-mapping reachable`)

The rule is for GoCD materials that say nothing of the project's commits. With fast-forward merges
there is no merge commit, so it works in two steps.

**Step 1: when each commit reached main.** This is its own unit, `Features.java`. It uses the first
source available:

1. the `merged_at` of the merge request that contains the commit;
2. failing that, the time of the push to main that brought it (GitLab events,
   `commit_from..commit_to` placed on the main branch's commit list);
3. as a last resort, its committer date. A rebase before a fast-forward rewrites it, so it is
   roughly the rebase time: an approximation.

The commits are then grouped into features: by merge request; failing that, by the Jira key in
their message; failing that, by push; failing that, one commit alone. A feature reaches main when
its last commit does.

**Step 2: which deploy delivered it.** A feature is delivered by the first successful prod run
scheduled at or after that moment. Its lead time runs from its first commit's author date to that
run. The measure stays the median.

**Choosing the rule.** `--deploy-mapping auto` is the default. It applies « reached main » when no
GoCD run carries a commit of the project; `materials` and `reachable` force one or the other. The
page names the rule applied, and counts the features by source. The snapshot keeps the main-branch
commits and the push events, so `--from` rebuilds the same files.

## 5. What the prototype checked

These checks ran in a container, with Docker 29 and the compose file above.

- **Setup.** Both fake projects of `DoraProjectTest`, rendered with `DoraProject --from`, were
  imported into DevLake v1.0.2. Then the dora pipeline above ran, and the dashboard's own SQL was
  read back, through Grafana's query API.
  - `octocat/Hello-World`: GoCD materials.
  - `octocat/Hello-FF`: fast-forward merges, GoCD with no project commit, so « reached main »: MR
    `merged_at` for the three MRs, a push event for one direct commit, the committer date for the
    other.

- **Results.**

  | Fake project | Measure | `dora.html` | DevLake |
  |---|---|---|---|
  | Hello-World | lead time | 26.5 h (median over 4 commits) | 28.0 h (lower median of the two deployed MRs: 50 h and 28 h, the same as ours per MR) |
  | Hello-World | change failure rate | 33 % (1 of 3 runs) | 50 % (1 of 2 successful deploys) |
  | Hello-World | time to restore | 4.0 h | 4.0 h (2021 report); 6.0 h for the 2023 report's deploy → resolution |
  | Hello-FF | lead time | 39.0 h (median of 25 h, 28 h, 50 h, 11 d) | 28.0 h (lower median of the same four values: per-PR times 25 h, 28 h, 50 h, 264 h match ours exactly) |
  | Hello-FF | change failure rate | 25 % (1 of 4 runs) | 33.3 % (1 of 3 successful deploys) |
  | Hello-FF | time to restore | 4.0 h | 4.0 h |

  - Deployment frequency over a one-year range: *fewer than once per month (low)* in DevLake. Over
    the 90 days alone the dashboard shows N/A, as it needs six months.

- **Behaviour.**
  - An import into a missing project stops with nothing written.
  - Importing twice gives the same rows.
