# sonar-audit-check

A read-only diagnostic you run **before** auditing a SonarQube portfolio.

It answers four questions:

1. Does the API respond, and is my token valid?
2. Which of the queries that actually matter am I allowed to make?
3. How many projects can I see — and how many are invisible to me?
4. What activity signals can I get without Git access?

Then a second step, `SonarRank.java`, turns that inventory into a shortlist
without touching the API again.

A separate entry point, `GitLabProjectReport.java`, looks at **one** project from
the GitLab side instead — cadence, concentration, review — for when you already
know which repository you care about. See *The GitLab half*, below.

Java, run with JBang.

There was a parallel Python port. It was removed rather than fixed: it silently
dropped every `new_*` metric from its CSV — reading only the root `value`, which
those metrics do not have — and two people running "the same" tool got different
numbers. See `KNOWLEDGE.md` for what that cost and what it taught.

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

## Running it

### 1. Get a token

In SonarQube: **My Account → Security → Generate Tokens**, type **User Token**.
It starts with `squ_`. An analysis token will not work — it cannot read these
endpoints.

### 2. Install JBang

```bash
curl -Ls https://sh.jbang.dev | bash -s - app setup
```

Or `sdk install jbang` (SDKMAN), `brew install jbangdev/tap/jbang` (Homebrew),
`choco install jbang` (Windows). JBang fetches its own JDK if you don't have
one, and resolves the three dependencies (Jackson, OpenCSV, picocli) on first
run — so nothing else needs installing.

### 3. Run

```bash
export SONAR_URL=https://sonar.example.com
export SONAR_TOKEN=squ_xxxxxxxx

jbang SonarAuditCheck.java                                  # diagnostic only
jbang SonarAuditCheck.java --csv projects.csv               # + inventory CSV
jbang SonarAuditCheck.java --csv projects.csv --stale-days 120
jbang SonarAuditCheck.java --organization my-org            # SonarQube Cloud
```

Everything is a GET; the tool never writes to your instance. A run is 20-30 API
calls plus one call per 100 projects, and takes a few seconds.

Or put it on your PATH:

```bash
jbang app install --name sonar-audit-check SonarAuditCheck.java
sonar-audit-check --csv projects.csv
```

### Without JBang

If JBang is not an option (locked-down machine, air-gapped CI), any JDK 21+ and
Maven will do. The `//DEPS` lines at the top of the file are the three
dependencies; Maven needs them in a POM to resolve their transitives, so write
a throwaway one:

```bash
cat > pom.xml <<'EOF'
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>x</groupId><artifactId>sonar-audit-check</artifactId><version>1</version>
  <dependencies>
    <dependency><groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId><version>2.17.2</version></dependency>
    <dependency><groupId>com.opencsv</groupId>
      <artifactId>opencsv</artifactId><version>5.9</version></dependency>
    <dependency><groupId>info.picocli</groupId>
      <artifactId>picocli</artifactId><version>4.7.6</version></dependency>
  </dependencies>
</project>
EOF

mvn -q dependency:copy-dependencies -DoutputDirectory=libs
javac -cp "libs/*" -d out SonarAuditCheck.java SonarRank.java
java -cp "out:libs/*" SonarAuditCheck --url "$SONAR_URL" --token "$SONAR_TOKEN"
```

That pulls 11 jars (the three above plus opencsv's transitives). On Windows,
use `out;libs/*` as the classpath separator.

### What a run looks like

Four sections: connectivity and identity, which endpoints your token can
actually reach, the project inventory, and activity signals. The line that
matters most is in section 3:

```
  Projets visibles avec ce token : 4
  Projets réellement présents    : 6
  → 2 projet(s) hors de ton périmètre. Ton classement sera tronqué
    de 33% sans aucun message d'erreur.
```

If the second line reads `Total réel indisponible`, your token lacks Administer
System and the gap is unknown rather than zero — have an admin compare the
numbers. The interface is in French.

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

## Ranking: `SonarRank.java`

The diagnostic tells you what you can see. `SonarRank.java` turns the inventory
CSV into a shortlist. It makes **no API calls at all** — it only re-reads the
CSV, so it is free to re-run with different thresholds until the shortlist looks
right. The method is in `ANALYSIS.md`; the short version:

- **Cohorts first.** Never-analysed and stale projects are not ranked. They are
  governance findings, and a stale project has no velocity to measure.
- **Percentiles within size strata.** Active projects are bucketed by `ncloc`
  (`<1k`, `1k-10k`, `10k-100k`, `>100k`) and each signal becomes a percentile
  rank *inside its own bucket*, so small projects and monoliths stop competing.
- **Four signals**: new issues per 1000 new lines, new-code coverage, new-code
  duplication, and `sqale_debt_ratio` at half weight.
- **Absent is never zero.** A missing metric is dropped from the mean, and the
  `signaux_presents` column (0-4) tells you how much of the score is real.
  Anything below two signals is not shortlisted.
- **Exclusions are counted, not hidden.** Too small, no new code in the window,
  too few signals — each is reported with its count and written to the CSV.

```bash
jbang SonarAuditCheck.java --csv projets.csv       # 1. inventory (hits the API)
jbang SonarRank.java --in projets.csv              # 2. ranking (pure local)
```

Output is one `classement.csv` holding three lists, distinguished by the `liste`
column: `VELOCITE` (creating debt), `DETTE_PORTEE` (carrying it, no new code),
`ABANDON_RECENT` (stale 90-365d and in bad shape). Filter `retenu = O` for the
shortlist.

| Option | Default | Meaning |
|---|---|---|
| `--in` | — | Inventory CSV from `--csv` (required) |
| `--out` | `classement.csv` | Where to write the ranking |
| `--stale-days` | 90 | Same threshold as the diagnostic |
| `--abandoned-days` | 365 | Past this, a stale project is dormant, not abandoned |
| `--min-ncloc` | 500 | Below this, ratios are noise |
| `--top-percent` | 10 | Share of each stratum shortlisted |
| `--min-signals` | 2 | Signals required before a score is trusted |
| `--comma` | off | Comma-separated, no BOM — for tools rather than Excel |

### Opening the CSV in Excel (Windows)

`classement.csv` is written **for Excel by default**: semicolon-separated, with
a UTF-8 BOM and CRLF line endings. That combination matters more than it should.
Without the BOM, Excel reads UTF-8 as ANSI and mangles every accent; with commas
under a French locale, it drops the whole row into column A. Getting one of the
two wrong makes the tool look broken.

So a double-click works. From a terminal:

```bat
rem Command Prompt
start classement.csv
```

```powershell
# PowerShell
Invoke-Item .\classement.csv        # or: start .\classement.csv
```

To force Excel specifically (useful when .csv is associated with something else):

```bat
start excel "%CD%\classement.csv"
```

Chain the whole thing in one line:

```powershell
jbang SonarRank.java --in projets.csv --out classement.csv; Invoke-Item .\classement.csv
```

Two things worth doing once the file is open, both one click: freeze the header
row (View → Freeze Panes → Freeze Top Row) and turn on filters (Ctrl+Shift+L).
Filtering `liste` then `retenu` is the entire intended workflow.

The inventory CSV from `SonarAuditCheck --csv` is comma-separated machine format
by design — `SonarRank` reads it, and it sniffs the separator, so an inventory
that has been through Excel and back still loads. If you want to look at the raw
inventory in Excel, use Data → From Text/CSV rather than double-clicking, and
set the origin to UTF-8.

On macOS or Linux, `open classement.csv` and `xdg-open classement.csv`
respectively.

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

---

## The GitLab half: `GitLabProjectReport.java`

Sonar describes the state of the code at analysis time. GitLab describes the
process that produced it — and for a single project, the process is usually the
more interesting half. This tool answers one question: **what does the way this
team works look like?**

It is a separate entry point, not a step in the portfolio pipeline. Two distinct
use cases:

- *I want the worst projects in the portfolio* → `SonarAuditCheck` + `SonarRank`.
- *I care about this one project, and I know its GitLab path* → this tool.

It needs no Sonar at all. A repository that has never been analysed is perfectly
legible here, and the crossings with Sonar (change frequency against complexity,
stale against abandoned, quality gate against merged MRs) come later, on top.

### Running it

```bash
export GITLAB_URL=https://gitlab.example.com     # default: https://gitlab.com
export GITLAB_TOKEN=glpat_xxxxxxxx               # personal access token, read_api

jbang GitLabProjectReport.java --path group/subgroup/project
jbang GitLabProjectReport.java --path 918776 --days 365
jbang GitLabProjectReport.java --path https://gitlab.com/group/proj/-/tree/main
```

The path accepts what you have in front of you: a namespace path, a numeric id,
or a browser URL — the `/-/tree/main` tail and a trailing `.git` are stripped.
Everything is a GET. A run is 5-20 calls plus one per 100 commits and one per
sampled merge request.

The token may be omitted for a **public** project, which is how the tool is
verified against a real instance without owning one. It is not an audit mode:
merge request notes return 401 anonymously, so the review-delay section goes
missing — reported, not silently dropped.

| Option | Default | Meaning |
|---|---|---|
| `--url` | `$GITLAB_URL`, else gitlab.com | Instance URL |
| `--token` | `$GITLAB_TOKEN` | PAT, `read_api` scope; optional on a public project |
| `--path` | — | `group/sub/project`, a numeric id, or a URL (required) |
| `--days` | 180 | Observation window |
| `--max-commits` | 5000 | Cap on commits fetched |
| `--max-mrs` | 500 | Cap on merge requests fetched |
| `--sample` | 30 | MRs sampled for review latency (one call each) |
| `--include-bots` | off | Count bots as contributors |
| `--bot-pattern` | see below | Regex identifying bots |
| `--dump-dir` | — | Log every raw API response here |
| `--timeout` | 30 | Per-request timeout, seconds |
| `--insecure` | off | Skip TLS validation |

### What it measures

Five sections: the project, cadence, contributors, merge requests, and findings.

**Cadence** — commits per week, days with any commit, median gap and longest
silence, weekday distribution, weekend share, share outside 8h-19h *in the
author's own timezone offset* rather than the auditor's. Then commit size
(median, ninth decile, share above 2000 lines) and **lines touched over net
lines**: the net figure hides rework, since a file rewritten five times moves
nothing.

**Contributors** — distinct authors, the bus factor (how many people it takes to
cover 80% of commits), the top author's share, and how many were still active
recently. Identity is the email address, the only stable key; the count of
distinct *names* is printed alongside, because the gap between the two is itself
the signal.

**Merge requests** — created, merged, closed; lead time from creation to merge
(median and ninth decile); share with no comment at all; share merged by their
own author; squash rate. Then, on a sample, the delay until the first review
comment by someone other than the author.

**Findings** — nothing new is computed here. The section rereads the three above
and keeps what stands out. The thresholds are coarse and meant to be: they orient
a conversation, they do not grade a team.

### Traps, and what a live run confirmed

Verified against gitlab.com, not against the documentation:

- **Merge commits are not work.** They carry no lines of their own and their date
  is the moment someone clicked *merge*, so counting them crowns the maintainer
  top contributor. On `gitlab-org/cli`, excluding them moved the top author from
  35% to 26% — and changed who it was. Every measurement here excludes them,
  detected via `parent_ids` having more than one entry.
- **Notes require a token, even on a public project** — 401, where the project,
  commits and contributors endpoints all answer anonymously.
- **`statistics=true` is ignored below Reporter**, silently: no error, the field
  is simply absent. Absent is reported as absent, never as zero.
- **Bots dominate if unfiltered.** Renovate and friends finish first contributor
  and distort cadence, concentration and commit size alike. The default pattern is
  deliberately narrow — `bot` glued to other letters would catch *Abbot* — and the
  identities it excludes are printed, not hidden.
- **Pagination is by header.** `x-next-page` is empty on the last page; a full
  batch can be the last one, so batch size cannot be used to detect the end.
- **A cap changes the denominator.** When `--max-commits` or `--max-mrs` is hit,
  the rate is computed over the span actually covered, not over `--days`, and says
  so. Otherwise a truncated fetch reads as a slow team.
- **Squash destroys per-commit history.** Where the squash rate is high, the
  commit timestamps are merge times and the cadence measures the merge button. The
  findings section says this rather than letting the numbers imply otherwise.
- **Dates carry an offset** (`2026-08-18T09:12:33.000+02:00`). Hour-of-day is read
  in that offset — the committer's local time — while ages are normalised to local
  time.

### Not yet done

The Sonar crossing. Resolving a Sonar project key from the GitLab path
(`sonar-project.properties` and `.gitlab-ci.yml` first, then a guess against the
Sonar project list, then asking), and on top of it: change frequency against
complexity per file, exclusions against the real repository size, quality gate
events against what was merged anyway, and `GIT_DEPTH` in CI against Sonar's empty
blame.

## Licence

TODO — pick one before making the repo public.
