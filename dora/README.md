# DORA extraction: Jira and GoCD

Four tools, PAT-authenticated, GET only. The scripts here are JBang entry
points; the code is in `src/main/java/.../cli/` (`DoraProject`, `JiraExtract`,
`GocdExtract`, `DoraLocate` and their subpackages), under every gate. Every CSV
can also be written for a tool with `--comma`.

| Script | Input | Output |
|---|---|---|
| `DoraProject` | a GitLab project, its prod GoCD pipeline(s) and Jira project(s), each optional but GitLab | the four DORA measures: `dora-donnees.json`, `dora.html`, `middleware-import.sql` |
| `DoraLocate` | a GitLab project | the Jira project(s) and GoCD pipelines that probably go with it, and the extraction commands |
| `JiraExtract` | Jira project key(s) | `jira-tickets.csv`, `jira-transitions.csv`, `jira-versions.csv`, `jira-tickets.jsonl` |
| `GocdExtract` | pipeline name(s) or group(s) | `gocd-runs.csv`, `gocd-stages.csv`, `gocd-commits.csv`, `gocd-runs.jsonl` |

Output goes under `extraction/` by default, which is git-ignored: it is client
data. The CSVs follow the repository's format for people (semicolon, UTF-8
BOM, CRLF, decimal comma, local time). The `.jsonl` files keep the raw API
objects, so a new rule (what counts as a production incident, which pipeline
is prod) can be applied without extracting again.

```bash
export GITLAB_URL=… GITLAB_TOKEN=…
export JIRA_URL=…   JIRA_TOKEN=…     # Jira Data Center PAT, sent as Bearer
export GOCD_URL=…   GOCD_TOKEN=…     # GoCD PAT, sent as Bearer

jbang dora/DoraLocate.java --gitlab-project group/sub/project
jbang dora/JiraExtract.java --project ABC --out-dir extraction/project/jira
jbang dora/GocdExtract.java --pipeline project-build,project-deploy-prod --out-dir extraction/project/gocd
```

## DoraProject: the four measures for one project, now

```bash
jbang dora/DoraProject.java --gitlab-project group/sub/project \
    --gocd-pipeline project-deploy-prod --jira ABC
open extraction/project/dora/dora.html
```

It reads, over `--days` (90 by default):

- **GitLab**: the merge requests merged into the default branch, each with its
  commits (one call per MR, the `--max-mrs` most recent).
- **GoCD** (`--gocd-pipeline`, the pipeline(s) whose successful run *is* a
  production deploy): every run, and the project's commits it deployed, walking
  the upstream pipelines exactly as `GocdExtract` does. Without GoCD, every merge
  into the default branch counts as a deploy, as Middleware does without CI.
- **Jira** (`--jira`): the issues created in the period whose type is one of
  `--incident-types` (`Incident,Bug` by default; the types seen are printed, to
  adjust). `--incident-jql` replaces the search: every issue it returns is an
  incident.

It writes three files in `--out-dir` (`extraction/<project>/dora` by default):

- `dora-donnees.json`: what was read. `--from dora-donnees.json` rebuilds the
  two others with no call at all.
- `dora.html`: a self-contained page, no script and nothing external: the four
  measures, the rules applied, then the deploys, merge requests and incidents
  they are made of. The JSON is embedded too.
- `middleware-import.sql`: the deploys and incidents for Middleware HQ.

The rules, also printed on the page:

| Measure | Rule |
|---|---|
| Deployment frequency | successful deploys per week |
| Lead time for changes | median, from a commit's author date to the first successful deploy that carried it (without GoCD: from an MR's first commit to its merge). A failed run retried without a new material carries nothing in GoCD's eyes: its commits go to the next successful run of the same pipeline |
| Change failure rate | deploys, successful or not, followed by at least one incident before the next deploy, over all deploys: Middleware's own rule, so both give the same figure |
| Time to restore | median, from an incident's creation to its Jira resolution date |

### Importing into Middleware HQ

Middleware (open source, `middlewarehq/middleware`) has no import nor webhook
in its released versions: it reads deploys from GitHub Actions only and
incidents from revert MRs only (a `deployments-webhook` branch exists upstream,
unmerged since July 2025). Its database has room for other sources, so the
script writes there, in the most standard way available:

1. Link the GitLab project in Middleware as usual (Integrations → GitLab, then
   add the repository to a team). Middleware reads the MRs itself; the script
   refuses to run until the repository is there.
2. Run the script against Middleware's database, as often as needed (every row
   is inserted or updated, nothing deleted):

   ```bash
   psql "postgresql://postgres:postgres@localhost:5434/mhq-oss" -f middleware-import.sql
   # or, with the Docker image: docker exec -i middleware psql -U postgres -d mhq-oss < middleware-import.sql
   ```

What it writes: one deployment workflow per GoCD pipeline and one run per GoCD
run, and the repository's teams switched to workflow deploys; one incident
service per Jira project, attached to those teams, and one incident per issue.
Middleware reads a workflow's provider as an enum that knows only
`GITHUB_ACTIONS` and `CIRCLE_CI`, and syncs only the first: the workflow
declares `CIRCLE_CI`, its id (`gocd:<pipeline>`) and `meta` say where it comes
from. No Middleware sync touches these rows.

Checked against Middleware's schema (commit 844eb42) on PostgreSQL 16, read
back by Middleware's own analytics server: same deploy count, change failure
rate and time to restore as the page.

## DoraLocate: an exploratory aid

**Jira.** It reads the project's commits, merge requests (title, branch,
description) and branch names over `--days`, extracts every `ABC-123`-shaped
key, and ranks the prefixes. Each prefix is checked against Jira, which drops
look-alikes such as `UTF-8` or `ISO-8859`. If the GitLab Jira integration is
configured and readable (Maintainer), its project key is shown first. A
prefix cited in at least 5 % of commits gets an extraction command.

**GoCD.** Without admin rights, a pipeline's git repository is only visible in
its runs, so the first search reads one history page per visible pipeline and
saves an index (`extraction/gocd-index.json`, reused for a day, `--refresh-index`
to rebuild). It matches pipelines whose git URL has the project's path
(http and ssh forms alike), then follows dependency materials downstream:
build, then staging deploy, then prod deploy. Pipelines that never ran have no
known material and cannot be matched.

Both answers are suggestions to check by eye, project by project.

## JiraExtract

It takes every issue of the project(s) updated in the last `--days` (`--jql`
replaces the query), with all fields and the full changelog. If the search
truncates a changelog, the issue is fetched on its own.

- **`jira-tickets.csv`**: one row per issue. Type, status and status
  category, priority, resolution. Created, first in progress, last done and
  resolved dates, with resolution and cycle times in days. Components, fix and
  affected versions, labels, parent and epic. Then extra columns: every custom
  field whose name suggests environment, severity, impact, incident, cause or
  detection, plus any `--field` (id or name).
- **`jira-transitions.csv`**: every status change: issue, date, from, to, and
  the target's category (`new`, `indeterminate`, `done`). Authors are left out.
- **`jira-versions.csv`**: the project's versions with release dates, a
  possible cross-check for production releases.

It uses `/rest/api/2/search`, falling back to `/rest/api/latest/search`. Only
run against a fake so far: the first real run on Jira 11.3.10 is the test.

## GocdExtract

It reads every run scheduled in the last `--days` for the named pipelines,
plus all pipelines of each `--group`.

- **`gocd-runs.csv`**: one row per run.
  - Counter, label, scheduled time.
  - Automatic or forced trigger.
  - Outcome (`succès`, `échec`, `annulé`, `en cours`) and per-stage results.
  - Git repositories and upstream revisions.
  - The commits embarked: count, first and last commit dates, first commit → run time in hours.
  - The Jira keys found in their messages.
  - `plateforme_supposee` is a guess from words in the name (prod, recette, dev…): check it.
- **`gocd-stages.csv`**: one row per stage run.
  - Result, manual or automatic approval, rerun, start.
  - End and duration with `--durations`, which costs one more call per stage.
- **`gocd-commits.csv`**: every commit a run embarked, with the path it came by:
  - `direct`: a git material of the run itself;
  - `build/12`: arrived through that upstream run.

  When the previous prod deploy took `recette/3` and this one `recette/5`,
  the commits of `recette/4` and `recette/5` count. The same applies at every
  level down to git (`--max-upstream` bounds the calls per run). A forced
  rebuild with no new commit embarks nothing, which is what GoCD records.

Checked against a real GoCD 25.3.0 in Docker: a build → staging → prod chain,
with a manual prod stage and a failing deploy.
