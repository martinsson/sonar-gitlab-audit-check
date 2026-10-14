# sonar-audit-check

Read-only diagnostics for auditing a SonarQube portfolio and the GitLab projects
behind it. Java, run with JBang. Every call is a GET; nothing is written to
either instance.

| Tool | What it answers |
|---|---|
| `SonarAuditCheck` | What can my token see, how much is invisible to it, and what does each project measure |
| `SonarRank` | Which Sonar projects to look at first, from that inventory, with no API call |
| `GitlabActivityAudit` | Which GitLab projects are really worked on, and with what practices |
| `CrossAudit` | Which Sonar project is which GitLab project, and what the two say together |
| `Vues` | Readable extracts of the crossing |
| `viz/index.html` | Charts of all of the above, in the browser |
| `GitLabProjectReport` | How one project you already picked is worked on |

Further reading: `COLUMNS.md` (every CSV column), `TOOLS.md` (running without
JBang, Windows, captures, tests, the less-used tools), `ANALYSIS.md` and
`GITLAB_ANALYSIS.md` (the method on each side), `KNOWLEDGE.md` (what the
SonarQube API really returns).

---

## The usual run: Sonar, GitLab, then both

The most common use: inventory Sonar, audit GitLab, cross the two, then read
the result as views and charts. The first two steps call the instances; the
rest works from the CSVs on disk and can be re-run freely.

```bash
export SONAR_URL=https://sonar.example.com   SONAR_TOKEN=squ_xxxxxxxx
export GITLAB_URL=https://gitlab.example.com GITLAB_TOKEN=glpat-xxxxxxxx

# 1. Sonar inventory: inventaire.csv, plus inventaire-historique.csv (the history series)
jbang SonarAuditCheck.java --csv inventaire.csv

# 2. GitLab, the whole instance: rank by commit activity, then measure practice
#    on the selection → ./audit/inventaire.csv and ./audit/pratiques.csv
jbang GitlabActivityAudit.java --deep --out-dir ./audit

# 3. Cross the two → ./audit/croisement.csv (and ./audit/appariement.csv, kept between runs)
jbang CrossAudit.java --sonar inventaire.csv --gitlab ./audit/pratiques.csv \
    --out ./audit/croisement.csv

# 4. Views: important columns first, projects drifting badly, active projects Sonar does not see
jbang Vues.java --in ./audit/croisement.csv

# 5. Charts: open the page, drop the four CSVs on it
open viz/index.html
```

`--deep` is what makes step 3 possible: it reads the Sonar key out of each
project's CI and `pom.xml`. Without it you get the GitLab funnel and shortlist
only, for far fewer calls.

A project to leave out of everything goes in `exclusions.txt` (see
[Leaving projects out](#leaving-projects-out)); both steps 1 and 2 read it.

Other entry points:

```bash
jbang SonarRank.java --in inventaire.csv --out classement.csv   # Sonar shortlist, no API call
jbang GitLabProjectReport.java --path my/group/project          # one project, read closely
```

Each tool's `--help` lists its options and ends with more recipes:
whole-instance runs, longer windows, SonarQube Cloud.

---

## Setup

- **JBang**: `curl -Ls https://sh.jbang.dev | bash -s - app setup`, or `brew
  install jbangdev/tap/jbang`, `sdk install jbang`, `choco install jbang`. It
  fetches a JDK and the dependencies on first run. Without JBang: `TOOLS.md`.
- **Sonar token**: a **User token** (`squ_…`, My Account → Security). An
  analysis token cannot read these endpoints. `Browse` per project is enough;
  Administer System adds the real project total, See Source Code adds blame.
  The tool says what it could not reach. Prefer a dedicated audit account: an
  admin token sees everything and hides the gap you are trying to measure.
- **GitLab token**: a personal access token with `read_api`. Approvals, DORA
  and push rules need GitLab Enterprise; the tool reads the plan up front and
  leaves those columns empty on Community, rather than guessing.

The output is in French.

---

## Why this exists

A SonarQube ranking is misleading before it starts:

- **`search_projects` filters silently.** It returns only what your token can
  browse — no error, just a shorter list, and the projects that fall out tend to
  be the neglected ones. `SonarAuditCheck` compares visible against real and
  prints the gap:

  ```
    Projets visibles avec ce token : 4
    Projets réellement présents    : 6
    → 2 projet(s) hors de ton périmètre. Ton classement sera tronqué
      de 33% sans aucun message d'erreur.
  ```

  `Total réel indisponible` means the token lacks Administer System: the gap is
  unknown, not zero.
- **The main branch is not the last scan.** A project scanned only on `develop`
  looks never analysed. The tool reads each project's most recently analysed
  branch (`--main-branch-only` restores the naive read).
- **Absent is not zero.** A missing measure stays empty in every CSV; it is never
  ranked as a zero.
- **Sonar says nothing about projects never onboarded**, nor about how code is
  worked on. That is the GitLab side's job, and the crossing tells the two apart.

The framing throughout is **debt velocity, not debt level**: new-code metrics
over overall ones, debt ratio over debt size, trends over the activity window.

---

## The GitLab side

`GitlabActivityAudit` spends its calls where they matter. A funnel —
inventory, recency gate, real commit counts, activity floor, then a quota
across size strata, namespaces and a random control sample — picks ~200
projects; only those get the expensive practice pass (`--deep`: branch
protection, MRs and approvals, pipelines, CI contents, the Sonar key).
Every exclusion is counted with its reason in `inventaire.csv`, so the
percentages in `pratiques.csv` always have a population behind them. The method
is in `GITLAB_ANALYSIS.md`.

**The Sonar key is the hard part.** The Sonar job usually comes from a shared
template through `include:`, so the tool asks GitLab for the expanded CI
(`ci/lint`), resolves `${CI_PROJECT_PATH_SLUG}`-style variables, and for Maven
reads `groupId:artifactId` from the `pom.xml`. Keys are cached by commit SHA in
`.sonar-keys.json`. An unresolved key stays empty: a wrong join is worse than
none.

---

## Crossing the two: `CrossAudit`

Reads both CSVs, makes no API calls. Every pair carries the method that made it
and a confidence:

| Method | Confidence |
|---|---|
| Key read from the CI, or `groupId:artifactId` from the pom, equals a Sonar key | exact |
| Sonar's own GitLab binding points at the project | exact |
| Sonar key normalised against the GitLab path; unique pom name equals a Sonar name | derived |
| Name similarity (TF-IDF, typo-tolerant, one-to-one) | suggestion — never counted |

The match rate is itself a finding: a low one means nobody can tell whether a
repo is analysed without checking by hand. The run then reports what neither
side shows alone: active projects Sonar never sees, CI configured for Sonar with
nothing received, debt created on unreviewed code, code that moves with neither
tests nor review, debt carried by dead repos, tests that run while coverage
never arrives. `pratiques.csv` holds only the selected projects, so a Sonar
project without a GitLab match may simply not have been drawn.

### Correcting the pairs by hand

The pairs are kept in `appariement.csv` next to `pratiques.csv` and replayed on
each run, row by row, on the `confiance` column:

| `confiance` | Next run |
|---|---|
| `manuel` | Paired with the key in `droite_key`, whatever the methods say |
| `rejete` | No pair, never proposed again |
| `exact`, `derived` | Kept; recomputed only if the Sonar key disappeared |
| `suggestion`, `none` | Recomputed, since a newer method may find them |

To confirm a suggestion write `manuel`; to fix a pair put the right key in
`droite_key` and write `manuel`; to refuse one write `rejete`. `--reapparier`
recomputes `exact` and `derived` too but keeps the hand-made rows. The previous
file is left as `appariement.csv.bak`. `ProjectMapper.java` is the matcher on
its own, for any two CSVs: `TOOLS.md`.

---

## Leaving projects out

`SonarAuditCheck` and `GitlabActivityAudit` read `--exclusions <file>`, or
`exclusions.txt` in the current directory. One entry per line, matched against
the GitLab path or the Sonar key; `*` is one path segment, `**` any number, case
is ignored, `#` starts a comment.

```text
equipe-x/bac-a-sable      # throwaway prototype
formation/**              # training exercises
org.exemple:*-demo        # Sonar keys work the same way
```

On the GitLab side an excluded project stays in `inventaire.csv` with `exclu` =
`liste d'exclusion`, and is counted like any other exclusion. On the Sonar side
it is dropped, and the console says how many.

---

## Charts: `viz/index.html`

A static page. Drop the CSVs on it: `./audit/inventaire.csv`, `pratiques.csv`,
`croisement.csv` and `inventaire-historique.csv`. Files are recognised by their
columns and read in the browser, never sent anywhere; the chart libraries load
from jsDelivr. The top of the page lists which files each chart needs and the
command that writes them.

| Chart | Question |
|---|---|
| Coverage funnel | How many GitLab projects make it to a recent Sonar measure |
| Activity × *measure* | Lines changed against issues/kLOC, coverage, new-code coverage, debt ratio… one dot per paired project; the top-right corner is where to act |
| Active projects without Sonar | The most active unpaired projects, and whether their CI mentions Sonar |
| Issue density drift | Where each project's issue density started and where it is now |
| Practices by namespace | Share of each team's projects following each practice |
| Map of the estate | Commits by namespace, coloured by quality gate, dark where Sonar sees nothing |
| History | Issues/kLOC over time, one small chart per project |

The wheel zooms the y axis only; double-click resets. **Exporter une page
autonome** downloads one HTML file with the charts, their data and the
libraries, which opens offline: zip it before emailing (mail servers block
`.html`), or publish it on GitLab Pages. It holds project names and quality
figures, so check who receives it. Adding a chart: `TOOLS.md`.

---

## Not yet done

Per-file work, on both sides: change frequency against complexity per file,
exclusions against the real repository size, quality gate events against what
was merged anyway, and `GIT_DEPTH` in CI against Sonar's empty blame.

## Licence

TODO — pick one before making the repo public.
