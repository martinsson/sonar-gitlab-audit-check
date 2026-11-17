# Apache DevLake: a stack for the DORA measures

DevLake is only used here to measure DORA. `DoraProject` writes `dora.html`, which stays the
readable view, and `middleware-import.sql` for Middleware HQ. It does not feed DevLake yet: see
section 3.

| File | What it is |
|---|---|
| `compose.yaml`, `.env.example` | The stack on one machine: Podman Desktop, `podman compose` or `docker compose` |
| `helm/values-openshift.yaml`, `helm/openshift-post-render.sh` | The official chart on OpenShift (restricted-v2 SCC) |

Versions: DevLake **v1.0.2** everywhere (server, config UI, Grafana image, chart 1.0.2). It is the
latest stable release. The `v1.0.3-betaN` images published since are pre-releases, though the
chart's `main` branch defaults to them.

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

## 3. Feeding it our data

Not done yet. Feeding DevLake will go through its REST webhook API
(`POST /plugins/webhook/connections/:id/deployments`, `/issues`), from the shared deploy list of
`DoraProject` (`Deploy`: one deploy, its environment, its date and the commit it deployed; DevLake
works out the delivered commits from git itself). That is issue #52, and the standard path
(`ARCHITECTURE.md`). Every tool here still only reads GitLab, Jira, GoCD and Sonar.

## 4. Where DevLake's formulas differ from ours

| Measure | Ours (`dora.html`) | DevLake (DORA dashboard) | Effect |
|---|---|---|---|
| Deployment frequency | successful deploys × 7 / days | median number of *days with a deploy* per week (elite ≥ 5), per month, per six months. A short period shows N/A: it needs six months | two deploys on one day count once in DevLake. Compare the band, not the number |
| Lead time | median over commits (GoCD materials): from a commit's author date to the first successful deploy carrying it | median over pull requests of the PR cycle time: first commit → PR opened → merged → deploy finished. The pieces add up to first commit → deploy | the same per MR. Ours weighs every commit, DevLake every MR. DevLake takes the *lower* median (the largest value whose percent rank ≤ 0.5); ours averages the two middle values |
| Change failure rate | deploys **of any outcome** followed by an incident before the next deploy, over **all** deploys (Middleware's rule) | **successful** deploys followed by an incident before the next successful deploy, over successful deploys | DevLake is higher as soon as a run fails: 1/2 instead of 1/3 with one failed run out of three. Not reconcilable without changing DevLake's dashboard query |
| Time to restore | median, incident creation → Jira resolution | 2021 report: the same (`lead_time_minutes`), lower median. 2023 report (*failed deployment recovery time*): from the deploy that caused it to the resolution | identical with the 2021 report |
| DORA band | DORA 2023 thresholds, applied to our numbers | the same thresholds | the page's tiles and the dashboard agree when the numbers do |
