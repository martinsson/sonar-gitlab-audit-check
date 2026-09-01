# CSV column dictionary

Five CSV files come out of these tools. This file says what every column holds,
and — more usefully — how to read the ones that are easy to misread.

| File | Written by | Separator |
|---|---|---|
| inventory CSV | `SonarAuditCheck --csv` | comma, UTF-8 |
| `classement.csv` | `SonarRank --out` | semicolon + BOM by default, comma with `--comma` |
| `inventaire.csv` | `GitlabActivityAudit --out-dir` | semicolon + BOM by default, comma with `--comma` |
| `pratiques.csv` | `GitlabActivityAudit --out-dir --deep` | semicolon + BOM by default, comma with `--comma` |
| `croisement.csv` | `CrossAudit --out` | semicolon + BOM by default, comma with `--comma` |

The last two are written by the same run and are meant to be read as a pair:
`pratiques.csv` covers only the selected projects, `inventaire.csv` covers the
portfolio they came from. Any percentage drawn from the practices file needs the
inventory to supply its denominator.

## One rule that applies to all four

**An empty cell is not a zero.** It means the measure is absent, or could not be
read — and those are different findings from a measured zero. Nothing in these
files imputes a value: no column is filled in with 0 to make a table look
complete. Every time you filter or sort, decide deliberately what empty should
do, because a spreadsheet will silently sort it as the smallest value and put
the projects you know least about at one end of your ranking.

---

## 1. Sonar inventory — `SonarAuditCheck --csv`

One row per visible project. This is the raw material for `SonarRank`; nothing
here is scored or ranked.

| Column | Meaning |
|---|---|
| `key` | SonarQube project key. Join key for everything downstream |
| `name` | Display name |
| `analysisDate` | Date of the last analysis. **Empty = never analysed** |
| `days_since_analysis` | Days since that date. Empty for never-analysed projects |

Then the measures, exactly as SonarQube returns them. They cost nothing extra:
the same one call per hundred projects carries all of them.

**Size and shape**

| Column | Meaning | Read it as |
|---|---|---|
| `ncloc` | Lines of code, excluding comments and blanks | Size. Used for stratification |
| `files` | Files analysed | Sanity check on `ncloc` |
| `complexity` | Cyclomatic complexity | Paired with change frequency, this is where refactoring pays |
| `cognitive_complexity` | Cognitive complexity | Closer to "hard to read" than the cyclomatic one |
| `comment_lines_density` | Comment lines, % | Weak on its own; useful against `complexity` |
| `duplicated_lines_density` | Duplicated lines, % of total | Level, not velocity |
| `new_duplicated_lines_density` | Duplication in new code, % | Copy-paste in fresh work |

**Tests and coverage**

| Column | Meaning | Read it as |
|---|---|---|
| `coverage` | Overall test coverage, % | Never read alone — see the note below |
| `line_coverage` / `branch_coverage` | The two halves of it, % | High line, low branch = tests that execute code without deciding anything |
| `new_coverage` | Coverage of new code in the leak period, % | The one that reflects current habits |
| `lines_to_cover` | Lines the analyser considers coverable | **0 means the percentage is meaningless**, not that coverage is bad |
| `uncovered_lines` | Of those, how many are not covered | Gives the effort a volume. 40% on 200 lines and 40% on a monolith are not the same job |
| `tests` | Unit tests SonarQube saw | `0` = no suite it can see. Not the same as low coverage |
| `test_failures` / `test_errors` | Failing and erroring tests | Non-zero means the suite is red and shipping anyway |
| `skipped_tests` | Tests marked skipped | The quiet way a suite stops protecting anything |
| `test_success_density` | Passing tests, % | |

**Debt and defects**

| Column | Meaning | Read it as |
|---|---|---|
| `sqale_index` | Remediation cost, in minutes | Debt in absolute terms. **Was fetched and silently dropped from this CSV until now** |
| `sqale_debt_ratio` | Remediation cost ÷ development cost, % | Debt carried, relative to size |
| `sqale_rating` | Maintainability rating, `1`–`5` | 1 = A … 5 = E |
| `bugs` / `vulnerabilities` / `code_smells` | The counts behind the three ratings | These add up and divide by `ncloc`. The ratings do not |
| `violations` | All open issues | |
| `reliability_rating` | Bug rating, `1`–`5` | 1 = A … 5 = E |
| `security_rating` | Vulnerability rating, `1`–`5` | 1 = A … 5 = E |
| `security_hotspots` | Hotspots raised | |
| `security_hotspots_reviewed` | Hotspots reviewed, % | |

**New code**

| Column | Meaning | Read it as |
|---|---|---|
| `new_lines` | Lines of new code in the leak period | Denominator. **0 or empty means no velocity can be computed** |
| `new_violations` | Issues raised on new code | Numerator of the injection rate |
| `new_bugs` / `new_vulnerabilities` / `new_code_smells` | Split of the above | Which kind of debt is being created |
| `alert_status` | Quality gate: `OK`, `ERROR`, `WARN` | Says which gate, not which standard — gates differ per project |

**The coverage column, specifically.** An analysed project with no coverage
report still gets `coverage = 0.0`, derived from `lines_to_cover` — it is
present, and it sorts last, correctly. The measure is genuinely *empty* mainly
for projects never analysed at all, and for languages whose analyser reports no
lines to cover. So a large count of empty `coverage` cells reads mostly as a
never-analysed count. `KNOWLEDGE.md §2` has the measurement behind this.

**And that is exactly why `tests` is here.** `coverage = 0` is two different
findings that call for opposite responses: a team with no test suite, and a team
with 400 passing tests whose coverage report never reaches SonarQube. The
percentage cannot tell them apart; `tests` and `lines_to_cover` can. The second
case is a CI plumbing problem worth an afternoon, and it is invisible in every
report that looks only at the percentage — `CrossAudit` surfaces it by name.

**Not every instance knows every metric.** `api/measures/search` rejects the
whole request — 400, no measures at all — if a single metric key is unknown to
it. The tool asks `api/metrics/search` once and sends only the intersection,
naming anything it dropped. Without that, one renamed metric on an older
SonarQube would return a portfolio with no measures rather than one measure
fewer.

**The ratings are ordinal, not numeric.** A `sqale_rating` of 4 is not twice as
bad as 2, and averaging them produces a number with no meaning. Filter on them;
do not do arithmetic with them.

---

## 2. `classement.csv` — `SonarRank`

Three ranked lists stacked in one file, distinguished by the `liste` column.
Filter `liste` first, then `retenu`.

| Column | Meaning |
|---|---|
| `liste` | Which list this row belongs to — see below |
| `rang` | Rank within the list. **Empty when the project is excluded from ranking** |
| `retenu` | `O` = on the shortlist, `N` = ranked but below the cut |
| `key`, `name` | From the inventory |
| `strate` | Size stratum: `XS` <1k, `S` 1k–10k, `M` 10k–100k, `L` >100k lines |
| `score` | Weighted mean of the percentiles present. Higher = worse. Empty when excluded |
| `signaux_presents` | How many of the 4 signals were actually available (0–4). `VELOCITE` only |
| `exclusion` | Why the project was removed from ranking. Empty means it was ranked |

`liste` takes three values:

- **`VELOCITE`** — the main ranking: projects *creating* debt. `retenu = O` is
  the top decile of each stratum.
- **`DETTE_PORTEE`** — high `sqale_debt_ratio` with no new code. Debt *carried*,
  not created. `retenu = O` marks the top 30.
- **`ABANDON_RECENT`** — stale 90–365 days with a high debt ratio: someone
  stopped working on a project in bad shape. `retenu = O` marks the top 30.

`exclusion` takes three values, and an excluded project is removed from the
ranking rather than penalised in it:

- `trop petit` — below `--min-ncloc`; any ratio on it is volatile noise.
- `pas de code neuf sur la fenêtre` — no new lines, so velocity is undefined.
- `trop peu de signaux` — fewer than `--min-signals` measures available. A
  project ranked on one signal is a guess wearing a number.

### The percentile columns

| Column | Signal | Weight |
|---|---|---|
| `pct_issues_neuves_par_kloc` | `new_violations ÷ new_lines × 1000` | 1.0 |
| `pct_couverture_code_neuf` | `new_coverage` | 1.0 |
| `pct_duplication_code_neuf` | `new_duplicated_lines_density` | 1.0 |
| `pct_ratio_dette` | `sqale_debt_ratio` | 0.5 |

Each is a percentile rank **within the project's own size stratum**, 0–100, and
in all four **higher means worse**. That includes coverage, whose sign is
inverted on the way in — `pct_couverture_code_neuf = 90` means *less covered
than 90% of comparable projects*, not better than them. Getting this backwards
inverts the whole reading, so it is worth checking against one project you know.

An empty percentile means that signal was absent for this project. It is dropped
from the mean rather than imputed, which is what `signaux_presents` is there to
tell you: a score of 70 on 4 signals and a score of 70 on 2 are not comparable
claims.

### Raw columns

`ncloc`, `new_lines`, `new_violations`, `new_coverage`, `coverage`,
`new_duplicated_lines_density`, `sqale_debt_ratio`, `sqale_rating`,
`reliability_rating`, `security_rating`, `alert_status`, `analysisDate`,
`days_since_analysis` are copied verbatim from the inventory, so a rank can be
argued with without opening two files. Same meanings as §1.

---

## 3. `inventaire.csv` — `GitlabActivityAudit --csv`

One row per project **in scope**, including every project excluded along the
way. The excluded rows are the point: they carry the denominator.

### Identity and metadata, from the project inventory

| Column | Meaning |
|---|---|
| `id` | GitLab numeric project id |
| `path` | `path_with_namespace` — the join key against Sonar, when a join is attempted |
| `name` | Project name |
| `namespace` | Full group path. What the per-namespace selection cap counts |
| `default_branch` | Branch all commit and pipeline measures are taken on |
| `visibility` | `private`, `internal`, `public` |
| `created_at` | Project creation |
| `last_activity_at` | Last activity of **any** kind — see the warning below |
| `archived`, `fork`, `mirror` | `true`/`false`. Each is an exclusion reason |
| `total_commits` | All-time commit count, from project statistics. Needs Reporter |
| `repo_size` | Repository size in bytes. Fallback for stratification when `total_commits` is absent |
| `bucket` | Size stratum: `A(<100)`, `B(<1k)`, `C(<10k)`, `D(>10k)` all-time commits |

**`last_activity_at` is not an activity measure.** Any event moves it — an issue
comment, a label change, a wiki edit. A repository with no commits in two years
and a busy issue tracker looks recent here. It is used as a *filter* precisely
because it errs by over-including, never by under-including; it is not used, and
should not be used, as a ranking.

### Commit activity, measured over `--since` days

| Column | Meaning |
|---|---|
| `last_commit` | Date of the newest commit seen on the default branch |
| `commits_window` | **Human** commits in the window. Empty = not measured, see below |
| `commits_bots` | Commits attributed to bots, filtered out of `commits_window` |
| `authors_window` | Distinct human authors. Worth ±1 — identities are normalised, not resolved |
| `merge_commits` | Commits with more than one parent |
| `reverts` | Commits whose title starts with `Revert`/`revert:`. A direct quality signal, needing neither Sonar nor environments |
| `active_days` | Distinct days carrying a commit. 40 commits over 12 weeks ≠ 40 in one day |
| `commits_per_week` | `commits_window × 7 ÷ --since`. The ranking measure |
| `tronque` | `true` = paging stopped at `--max-commit-pages`, so the count is a **floor**, not a total |

**`commits_window` empty versus 0.** Empty means the commits could not be read —
a 403, or an error — and the project is excluded as *activité non mesurable*.
Zero means it was read and there was nothing there. Reading a permission gap as
inactivity would drop exactly the projects most likely to need attention.

**A repository whose only activity is Renovate** shows `commits_window = 0` with
`commits_bots > 0`. That is a finding, not an empty row, and it gets its own
exclusion label.

**Squash-merge teams look inactive here.** A group squashing every MR to a single
commit shows 5–10× fewer commits than an identical team using merge commits.
This is a workflow difference, not an activity difference. Read `commits_window`
next to `mr_fusionnees` in `pratiques.csv` before concluding anything.

### Funnel outcome

| Column | Meaning |
|---|---|
| `exclu` | Why the project left the funnel. Empty = it survived to the draw |
| `fuite_filtre` | `true` = found by the validation sample: marked inactive by `last_activity_at`, yet has commits. A measured false negative of the recency gate |
| `selectionne` | `true` = in the deep-analysis budget |
| `motif_selection` | *Why* it was selected — see below |

`exclu` values: `archivé`, `dépôt vide`, `miroir`, `fork non divergé`,
`sans branche par défaut`, `inactif > Nj`, `sous le plancher (N commits)`,
`activité robotique seule (N commits de bots)`,
`activité non mesurable (HTTP 403)`.

`motif_selection` values, and why the distinction matters:

- `tête de strate X` — top of its size bucket. The core of the selection.
- `mono-auteur, forte activité` — bus-factor slot. Raw ranking buries these.
- `témoin aléatoire` — **the control sample.** Drawn at random from everything
  above the activity floor, seeded by `--seed` so runs reproduce. This is what
  lets a report say "practice coverage is X among the selected, Y among a random
  draw of the rest". Excluding these rows when computing headline figures is the
  whole point — they are the comparison, not part of the result.
- `reliquat de budget` — the budget was not exhausted by the quota slices and
  the remainder went back to the ranking.

---

## 4. `pratiques.csv` — `GitlabActivityAudit --deep --pratiques`

One row per **selected** project. Everything here costs API calls, which is why
it only exists for the ~200 that the funnel chose.

| Column | Meaning |
|---|---|
| `path`, `motif_selection`, `bucket`, `commits_window`, `authors_window` | Carried over from the inventory, so this file stands alone |
| `branche_protegee` | Default branch appears in the protected branches list |
| `push_verrouille` | Its push access level is *no one* — pushes must go through a merge request |
| `mr_fusionnees` | Merge requests merged into the default branch during the window |
| `ttm_median_j` | **Median** days from MR creation to merge. Median deliberately: the mean is noise here |
| `auto_merge` | MRs merged by their own author |
| `notes_par_mr` | Mean comment count per MR. A rough "was it actually read" |
| `echantillon_approbations` | How many MRs were sampled for approvals (≤10, Enterprise only) |
| `part_approuvee` | Share of that sample with at least one approval, 0–1 |
| `auto_approbation` | Approvals given by the MR's own author, within the sample |
| `pipelines` | Pipelines on the default branch in the window (last 100 max) |
| `taux_succes` | Share of those that succeeded, 0–1 |
| `incidents_rouges` | Red streaks on the default branch. Consecutive failures are **one** incident, not several |
| `retour_au_vert_h` | Median hours from the first failure of a streak to the next success. **Not a DORA metric** — see below |
| `rouge_non_resolu` | `1` = still red at the end of the window. The incident is real, its duration is not yet known |
| `environnements` | Environments declared. **`0` invalidates the DORA column** |
| `deploiements` | Deployments over the window, summed from DORA deployment frequency |
| `dora_indispo` | `true` = DORA returned 403/404. Not available, as distinct from zero |
| `ci_sonar` | The **fully expanded** CI configuration mentions Sonar — the project *intends* to be scanned. Shared templates included |
| `ci_securite` | It includes SAST, secret detection or dependency scanning |
| `cle_sonar` | The `sonar.projectKey` the scanner sends. **The join key against the Sonar inventory.** Empty when it cannot be resolved without guessing |
| `source_cle_sonar` | Where the key was read: `sonar-project.properties`, `ci/lint`, `includes suivis`, or why it is empty |
| `fichiers` | Space-separated list of watched files found: `.gitlab-ci.yml`, `README.md`, `CODEOWNERS`, `Dockerfile`, `renovate.json` |

### Three columns that will mislead you if read plainly

**`branche_protegee = false` may mean "not allowed to look".** Reading protected
branches needs a higher role than the rest of this file — Maintainer on most
GitLab versions. On a Reporter token the call returns 403 and the column
currently records `false`, which reads as *unprotected* when the truth is
*unknown*. Until that is fixed, check the role your token's user holds before
reporting anything from this column. A run that also shows `push_verrouille`
false everywhere, with no exceptions at all, is the signature of this problem
rather than of a portfolio with no protection anywhere.

**`deploiements = 0` with `environnements = 0` says nothing about deployment.**
DORA counts deployments to declared environments. A project deploying daily from
a pipeline that never registers a `production` environment scores zero. That is
a data-availability finding, and a different conversation from a project that
genuinely does not deploy.

**`retour_au_vert_h` is not `time_to_restore_service`.** DORA measures restoring
a *service*, which requires a production event to date. Without declared
environments there is none, and nothing here substitutes for one. This column
measures how long the team leaves the default branch broken — a CI fact, not a
production one. It is free, though: it comes out of the same pipeline list that
already produces `pipelines` and `taux_succes`, at no extra API cost. Statuses
that are neither `success` nor `failed` — canceled, skipped, running, manual —
are skipped rather than read as the end of an outage.

**`ci_sonar` is about intent, not results.** It says the pipeline is configured
to run Sonar, not that Sonar has data. That is exactly what makes it useful
without a working Sonar↔GitLab join: `ci_sonar = true` on a project absent from
the SonarQube inventory is a concrete, checkable finding on its own.

**`ci_sonar` undercounts when the run says it fell back.** The column is read
from the CI configuration with every `include:` expanded — normally by
`GET /projects/:id/ci/lint`, which returns the whole tree already merged. Where
the token is refused that call, the tool follows the `include: project:`
directives itself, and it only sees what it knows how to read: no `component:`,
no `remote:`, no dynamic includes. The run prints which route was used and how
often. A parc read mostly through the fallback has a `ci_sonar` that is a floor,
not a count.

**`cle_sonar` empty is three different things**, and `source_cle_sonar` says
which. *No Sonar in the pipeline at all* — the honest zero. *Sonar runs but the
key is a variable this tool cannot compute*, reported as `variable non résolue`
with the raw text, because publishing a half-expanded key would produce a wrong
join against SonarQube, which is worse than no join. Or *the key is implicit* —
the scanner defaulting to a key derived by the GitLab integration rather than
one written down anywhere. GitLab's own predefined variables
(`CI_PROJECT_PATH_SLUG`, `CI_PROJECT_PATH`, `CI_PROJECT_NAME`, `CI_PROJECT_ID`
and friends) *are* computed here, at no extra API call — they are how a shared
template names a project it cannot hard-code, so they are the common case rather
than the exception.

### What is not here, on purpose

No per-person columns, no lines added or removed, no commit-message quality
score. The unit of analysis is the project throughout; author counts feed a bus
factor judgement and nothing else. `GITLAB_ANALYSIS.md §8` has the reasoning.

---

## 5. `croisement.csv` — `CrossAudit`

One row per project in `pratiques.csv`, matched or not. Every column is
prefixed with the side it came from — `gl_` for GitLab, `sq_` for SonarQube —
because without the prefix `coverage` and `commits_window` on one row read as
one measurement, when they are two systems, two dates and two definitions.

| Column | Meaning |
|---|---|
| `methode_jointure` | How the pair was made: `clé lue dans la CI`, `clé normalisée = chemin GitLab`, `noms voisins`, or `aucune` |
| `confiance` | `exact`, `derived`, `suggestion`, `none` |
| `gl_…` | Columns carried over from `pratiques.csv` |
| `sq_…` | Columns carried over from the Sonar inventory. **All empty when nothing matched** |

**`confiance = suggestion` is not a match.** Those rows are name resemblances
for a human to confirm, and they are excluded from every count the run prints.
Two projects called `api` in different namespaces resemble each other perfectly
and are not the same project. Filter on this column before doing anything with
the file.

**An unmatched Sonar project may just not have been drawn.** `pratiques.csv`
holds only the projects the GitLab audit selected, not the whole estate. The
count of Sonar projects with no GitLab match is bounded by that, not by the
integration's health — the run says so, and it is the one number in the
crossing that is easy to over-read.

**The match rate belongs in the report.** It is not a diagnostic about the
tool. A low rate says the GitLab↔SonarQube integration is not configured, which
is the same class of governance finding as the never-analysed projects — and it
is the finding that has to be fixed before any of the others can be trusted at
scale.
