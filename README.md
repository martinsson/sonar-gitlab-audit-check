# sonar-audit-check

A read-only diagnostic you run **before** auditing a SonarQube portfolio.

It answers four questions:

1. Does the API respond, and is my token valid?
2. Which of the queries that actually matter am I allowed to make?
3. How many projects can I see — and how many are invisible to me?
4. What activity signals can I get without Git access?

Then a second step, `SonarRank.java`, turns that inventory into a shortlist
without touching the API again.

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

If JBang is not an option (locked-down machine, air-gapped CI), any JDK 25+ and
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

## The GitLab side: `GitlabActivityAudit.java`

SonarQube answers *what is the code like*. It cannot answer *how is it being
worked on*, and it says nothing at all about projects that were never onboarded
— which, on the portfolio this was built against, is 26% of them.

`GitlabActivityAudit.java` is the second half. It ranks projects by **commit
activity**, picks the ~200 worth a deep look, and then measures practice on
those. The full method is in `GITLAB_ANALYSIS.md`; the short version:

```bash
export GITLAB_URL=https://gitlab.example.com
export GITLAB_TOKEN=glpat-xxxxxxxx          # read_api scope is enough
jbang GitlabActivityAudit.java --group my/group --csv inventaire.csv
jbang GitlabActivityAudit.java --group my/group --deep --pratiques pratiques.csv
```

**Two reports, not one.** The GitLab report is complete and publishable without
a single Sonar project matched to it. Joining the two is an overlay attempted
after both exist — never a precondition for either. Match rates on this kind of
join are poor, and an analysis that collapses when the join fails is not worth
building.

**Why a funnel.** The deep pass costs ~25 calls per project. Run naively over
2000 projects that is 50k calls and a report nobody reads. The funnel spends
~90–850 calls to choose who earns the deep pass:

| Stage | Cost | What it does |
|---|---|---|
| 0 — inventory | ~20 calls | archived, empty, mirrors, undiverged forks, 403s — all counted |
| 1 — recency gate | free | `last_activity_at` older than the window |
| 2 — real commit volume | ~40 (GraphQL) or ~800 (REST) | commits, authors, merge ratio, spread |
| 3 — activity floor | free | below it, the deep signals are arithmetic on noise |
| 4 — quota selection | free | strata, namespace cap, bus-factor slots, random control |

**`last_activity_at` is a filter, never a ranking.** Any issue comment moves it,
so a dead repo with a chatty tracker reads as active. But it fails in the *safe*
direction — it is inflated by non-commit events, so anything with commits in the
window necessarily has a recent `last_activity_at`. It over-includes; it does not
under-include. That makes it worthless for ranking and exactly right for
excluding, and it is the largest single cut in the pipeline, for free.

The tool does not ask you to take that on faith. It samples projects from
*below* the cut, counts their commits for real, and reports the false-negative
rate. Zero means the gate holds. Non-zero gives you a number to publish instead
of an assumption.

**Quota, not top-N.** Taking the 200 busiest projects is the same mistake as
ranking Sonar projects by `sqale_index`: you get the big active monoliths of two
or three teams and learn what you already knew. The budget is spent instead
across size strata, with a namespace cap, dedicated slots for single-author
high-activity projects, and a **random control sample**. The control slice is
what lets the final report say "practice coverage is X among the selected, Y
among a random draw of the rest" — and if those diverge, the selection rule is
itself the finding.

**Three ways commit counts lie**, all handled explicitly:

- *Squash-merge teams look 5–10× less active.* Merged-MR counts are carried
  alongside, so the two can be read together.
- *Bots inflate.* Renovate and friends are filtered, but counted separately — a
  repo whose only activity is Renovate is a finding, not an empty row.
- *Author identity fragments.* One person commits under several addresses;
  identities are normalised, and the author count is worth ±1. It feeds a bus
  factor judgement, so it only has to be right about 1 versus 5.

**Absent is not zero, here too.** A 403 on a project's commits is recorded as
*activité non mesurable*, never as zero commits. Reading a permission gap as
inactivity would drop exactly the projects most likely to need attention — the
same failure mode as `search_projects` filtering silently on the Sonar side.

### Options

| Option | Default | What it does |
|---|---|---|
| `--group` | — | Scope to one group; instance-wide otherwise |
| `--since` | 90 | Activity window, in days |
| `--top` | 200 | Deep-analysis budget, spent by quota |
| `--floor` | derived | Activity floor; `-1` derives it from the distribution |
| `--validation-sample` | 30 | Projects drawn below the recency gate to validate it |
| `--graphql` | on | Try the batched GraphQL route, fall back to REST |
| `--deep` | off | Run the practice signals on the selected projects |
| `--seed` | fixed | Seeds the random control draw, so runs are reproducible |

### What is Enterprise-only

Approvals, DORA and push rules need Enterprise. The tool reads
`api/v4/metadata` up front and says which plan it found, because discovering it
after designing the analysis is the expensive mistake. On Community the
approval columns stay empty rather than being silently replaced by a proxy.

Two data-availability traps are reported as findings rather than scores: DORA
reads zero for a project that deploys daily without a registered environment,
and *no vulnerabilities* renders identically to *no scanning*.

---

## Running under PowerShell

The output is in French, and the console encoding is not a cosmetic detail — it
is the difference between a readable report and a page of mojibake. Two halves
of the problem pull in opposite directions, and fixing one alone breaks the
other.

**Redirected** (file, pipe, CI): `stdout.encoding` falls back to the native
encoding. Under a C locale — the norm in a container where `LANG` is unset —
that is ANSI_X3.4-1968 and every accent becomes `?`. So the output must be
forced to UTF-8 there.

**A Windows terminal**: PowerShell renders using the console code page, usually
cp850 or cp1252, never UTF-8 unless you have run `chcp 65001` first. Sending it
UTF-8 produces exactly the reported symptom — `Ã©` where `é` belongs.

The tools therefore write in the *console's* encoding when talking to a
terminal, and in UTF-8 as soon as the output is redirected. All French accents
exist in cp850 and cp1252, so they survive. Characters a legacy code page has
never heard of — `—`, `≥`, `→`, `…` — are transliterated to ASCII rather than
replaced by `?`.

You do not need `chcp 65001`. If you prefer it, it works too: the console then
reports UTF-8 and the tools use it.

ANSI colours are the second Windows symptom. conhost does not interpret them
until a program enables VT mode, so PowerShell 5.1 shows literal `←[1m`. Colour
is therefore off by default on Windows unless the emulator announces itself
(Windows Terminal, ConEmu, ANSICON). Override with `--color always|never`, or
set `NO_COLOR` to silence it everywhere.

`ConsoleOut.java` holds this logic for all three scripts and is pulled in with
JBang's `//SOURCES`. Running without JBang, pass it to `javac` alongside the
script, or let `java GitlabActivityAudit.java` resolve it — JDK 25 compiles
neighbouring source files on its own.

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
