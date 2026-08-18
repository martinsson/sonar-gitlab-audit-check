# sonar-audit-check

A read-only diagnostic you run **before** auditing a SonarQube portfolio.

It answers four questions:

1. Does the API respond, and is my token valid?
2. Which of the queries that actually matter am I allowed to make?
3. How many projects can I see — and how many are invisible to me?
4. What activity signals can I get without Git access?

Two equivalent implementations: Python (stdlib only) and Java (JBang).

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

**Absent is not zero.** A project with no coverage report has no `coverage`
measure at all. Sort by coverage ascending and it does not appear — it sorts
below nothing, because it *is* nothing. These projects are frequently the ones
most in need of attention, and they are invisible in every naive ranking.

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

### Python

```bash
export SONAR_URL=https://sonar.example.com
export SONAR_TOKEN=squ_xxxxxxxx

python3 sonar_audit_check.py
python3 sonar_audit_check.py --csv projects.csv --stale-days 120
python3 sonar_audit_check.py --organization my-org      # SonarQube Cloud
```

Python 3.8+. No dependencies.

### Java

```bash
jbang SonarAuditCheck.java --csv projects.csv
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
  and entries are sometimes shorter than four elements.

Both implementations handle all four. The Java version keeps every numeric field
boxed so that absent stays null rather than binding to `0.0`.

---

## Blame data and shallow clones

If `api/sources/scm` returns nothing, there are two possible causes: the token
lacks See Source Code, or the scanner runs without SCM metadata — which is what
happens with `fetch-depth: 1` in CI.

The second case is itself a finding. A team whose CI strips blame metadata has
also disabled Sonar's ability to attribute issues to authors and to compute new
code correctly.

---

## Licence

TODO — pick one before making the repo public.
