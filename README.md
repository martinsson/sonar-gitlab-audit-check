# sonar-audit-check

A read-only diagnostic you run **before** auditing a SonarQube portfolio.

It answers four questions:

1. Does the API respond, and is my token valid?
2. Which of the queries that actually matter am I allowed to make?
3. How many projects can I see — and how many are invisible to me?
4. What activity signals can I get without Git access?

The supported implementation is Java (JBang). `sonar_audit_check.py` is **legacy
and unmaintained** — it is missing `--dump-dir`, drops every `new_*` metric from
its CSV (it reads only the root `value`, which those metrics do not have), and
crashes on some legal-but-unusual responses. Use the Java version.

---

## Why this exists

Auditing a SonarQube portfolio usually means ranking projects by technical debt.
That ranking is misleading in two ways, and this tool exists to expose both
before you start.

**`api/components/search_projects` filters silently.** It returns only the
projects your token can browse. A missing permission produces no error, no
warning, no 403 — just a shorter list. The projects that fall out are
statistically the ones with the worst practices, because neglected projects tend
to have neglected permissions. Your ranking comes out truncated and looks
complete.

**Absent is not zero — but rarer than you would think.** A project with no
`coverage` measure at all does not appear in a coverage ranking: it sorts below
nothing, because it *is* nothing.

Measured against a real instance (SonarQube 26.8, Java), the case is narrower
than that framing suggests. A project *analysed* without any coverage report
still gets `coverage = "0.0"`, derived from `lines_to_cover` — it is present,
and it does sort last. The measure is genuinely absent mainly for projects that
have **never been analysed at all**, and for languages whose analyser reports no
lines to cover. So read the "no coverage data" count below largely as a
never-analysed count, alongside `Jamais analysés`.

The tool reports both: the gap between what you see and what exists, and the
count of projects with no coverage data whatsoever.

---

## What it measures

The framing throughout is **debt velocity, not debt level**. A large old codebase
with high `sqale_index` is carrying debt. A small project whose debt-per-line-written
is climbing is *creating* it. Only the second one tells you a team needs help.

Accordingly the tool leans on:

- New-code metrics (`new_coverage`, `new_violations`, `new_duplicated_lines_density`)
  over their overall equivalents
- `sqale_debt_ratio` over `sqale_index`, since the latter mostly ranks projects by size
- Δ`sqale_index` / Δ`ncloc` over a window — debt added per line written
- Author concentration via the `author` facet on issues, as a bus-factor proxy
- Analysis cadence and version events from `project_analyses`

---

## Requirements

The token must be a **User token** (`squ_...`) — not a project or global
analysis token. Analysis tokens cannot read these endpoints.

No admin rights are needed. `Browse` on each project is enough for everything
except two things:

| Endpoint | Extra permission |
|---|---|
| `api/projects/search` (the real project total) | Administer System |
| `api/sources/scm` (blame data) | See Source Code |

Both are optional. The tool degrades gracefully and tells you what it could not
reach and why it matters.

For a recurring audit, prefer a dedicated account over your own: a token with
`admin` sees everything, which means the diagnostic no longer reflects the
scope a restricted audit account would have. The tool warns you if it detects this.

---

## Usage

### Java

```bash
export SONAR_URL=https://sonar.example.com
export SONAR_TOKEN=squ_xxxxxxxx

jbang SonarAuditCheck.java
jbang SonarAuditCheck.java --csv projects.csv --stale-days 120
jbang SonarAuditCheck.java --organization my-org      # SonarQube Cloud
```

Or install it to your PATH:

```bash
jbang app install --name sonar-audit-check SonarAuditCheck.java
sonar-audit-check --csv projects.csv
```

JBang fetches its own JDK if you don't have one. Dependencies (Jackson, OpenCSV,
picocli) are resolved and cached on first run.

### Options

| Option | Default | Meaning |
|---|---|---|
| `--url` | `$SONAR_URL` | Instance URL |
| `--token` | `$SONAR_TOKEN` | User token |
| `--organization` | `$SONAR_ORG` | SonarQube Cloud organization |
| `--project` | first visible | Sample project used for capability probes |
| `--csv` | — | Write the project inventory here |
| `--dump-dir` | — | Log every raw API response here |
| `--stale-days` | 90 | Age past which a project counts as stale |
| `--activity-days` | 90 | Activity window |
| `--timeout` | 30 | Per-request timeout, seconds |
| `--insecure` | off | Skip TLS validation |

---

## `--dump-dir`

Not debug scaffolding — a permanent feature, for two reasons.

Jackson is configured with `FAIL_ON_UNKNOWN_PROPERTIES=false`, so a new field in
a later SonarQube version is absorbed silently and a removed field becomes null.
Neither shows up as an error. Diffing dumps between instances or versions is the
only way to see that drift.

Second, the response shapes that break naive parsing only appear in unhealthy
projects — the ones this tool exists to find. Capture three references
deliberately: a normal project, one that has never been analysed, and one with
no coverage report. A sample taken only from healthy projects will not show you
what the payloads look like when fields go missing.

---

## Known response shapes

Documented here because a single sample from a healthy project will not reveal
any of them:

- **Measure values are JSON strings, not numbers.** `"12.5"`, not `12.5`.
- **`new_*` metrics nest their value under `period`** — or under a `periods`
  array on older versions. The top-level `value` is absent for these.
- **`api/issues/search` puts `total` at the root**, while every other paginated
  endpoint uses `paging.total`.
- **`api/sources/scm` returns positional arrays**: `[line, author, date, revision]`,
  and entries are sometimes shorter than four elements. The line number is a JSON
  number, not a string.
- **`api/sources/scm` returns one entry per changeset, not per line.** Consecutive
  lines sharing a commit are collapsed onto the first. Measured on 26.8: a 30-line
  file returned 3 entries, at lines 1, 13 and 21. Any "first N lines" framing is
  really counting changesets.
- **A file indexed without SCM metadata still returns 200**, with `author` and
  `revision` as empty strings rather than an error or an empty list. Emptiness is
  therefore not how you detect a shallow clone — blank authors are.

The Java implementation handles all of these, and keeps every numeric field boxed
so that absent stays null rather than binding to `0.0`. Verified against a live
SonarQube 26.8 by `testing/verify-against-real-sonarqube.sh`.

---

## Blame data and shallow clones

If `api/sources/scm` returns nothing, there are two possible causes: the token
lacks See Source Code, or the scanner runs without SCM metadata — which is what
happens with `fetch-depth: 1` in CI.

The second case is itself a finding. A team whose CI strips blame metadata has
also disabled Sonar's ability to attribute issues to authors and to compute new
code correctly.

---

## Verifying a change

`testing/verify-against-real-sonarqube.sh` starts a real SonarQube in Docker,
creates six projects, grants a restricted audit token Browse on only four, runs
two real analyses, and points the tool at the result. Expected output is
`4 visible / 6 real / gap 2 (33%)` and a blame section reporting **no SCM
metadata** — the analysis runs without a git repository, which is the
`fetch-depth: 1` case.

Prefer it to a mock. A mock replays whatever its author believed the API does;
if it is written from this README it confirms the README rather than testing it.
Three of the response behaviours documented above were found only by querying a
real instance, and a README-derived mock had asserted the opposite.

## Licence

TODO — pick one before making the repo public.
