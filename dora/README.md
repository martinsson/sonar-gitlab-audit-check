# DORA extraction: Jira and GoCD

Three JBang scripts, PAT-authenticated, GET only. Like `probes/`, they live
outside the Maven build and its quality gates: prototypes until the data says
what the real tool should be.

| Script | Input | Output |
|---|---|---|
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
