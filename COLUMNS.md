# CSV column dictionary

Four CSV files come out of these tools. This file says what every column holds,
and — more usefully — how to read the ones that are easy to misread.

| File | Written by | Separator |
|---|---|---|
| inventory CSV | `SonarAuditCheck --csv` | comma, UTF-8 |
| `classement.csv` | `SonarRank --out` | semicolon + BOM by default, comma with `--comma` |
| `inventaire.csv` | `GitlabActivityAudit --out-dir` | semicolon + BOM by default, comma with `--comma` |
| `pratiques.csv` | `GitlabActivityAudit --out-dir --deep` | semicolon + BOM by default, comma with `--comma` |

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

Then the 13 measures, exactly as SonarQube returns them:

| Column | Meaning | Read it as |
|---|---|---|
| `ncloc` | Lines of code, excluding comments and blanks | Size. Used for stratification |
| `coverage` | Overall test coverage, % | See the coverage note below |
| `new_coverage` | Coverage of new code in the leak period, % | The one that reflects current habits |
| `duplicated_lines_density` | Duplicated lines, % of total | Level, not velocity |
| `new_duplicated_lines_density` | Duplication in new code, % | Copy-paste in fresh work |
| `sqale_debt_ratio` | Remediation cost ÷ development cost, % | Debt carried, relative to size |
| `sqale_rating` | Maintainability rating, `1`–`5` | 1 = A … 5 = E |
| `new_violations` | Issues raised on new code | Numerator of the injection rate |
| `new_lines` | Lines of new code in the leak period | Denominator. **0 or empty means no velocity can be computed** |
| `reliability_rating` | Bug rating, `1`–`5` | 1 = A … 5 = E |
| `security_rating` | Vulnerability rating, `1`–`5` | 1 = A … 5 = E |
| `security_hotspots_reviewed` | Hotspots reviewed, % | |
| `alert_status` | Quality gate: `OK`, `ERROR`, `WARN` | Says which gate, not which standard — gates differ per project |

**The coverage column, specifically.** An analysed project with no coverage
report still gets `coverage = 0.0`, derived from `lines_to_cover` — it is
present, and it sorts last, correctly. The measure is genuinely *empty* mainly
for projects never analysed at all, and for languages whose analyser reports no
lines to cover. So a large count of empty `coverage` cells reads mostly as a
never-analysed count. `KNOWLEDGE.md §2` has the measurement behind this.

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
| `environnements` | Environments declared. **`0` invalidates the DORA column** |
| `deploiements` | Deployments over the window, summed from DORA deployment frequency |
| `dora_indispo` | `true` = DORA returned 403/404. Not available, as distinct from zero |
| `ci_sonar` | `.gitlab-ci.yml` mentions Sonar — the project *intends* to be scanned |
| `ci_securite` | It includes SAST, secret detection or dependency scanning |
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

**`ci_sonar` is about intent, not results.** It says the pipeline is configured
to run Sonar, not that Sonar has data. That is exactly what makes it useful
without a working Sonar↔GitLab join: `ci_sonar = true` on a project absent from
the SonarQube inventory is a concrete, checkable finding on its own.

### What is not here, on purpose

No per-person columns, no lines added or removed, no commit-message quality
score. The unit of analysis is the project throughout; author counts feed a bus
factor judgement and nothing else. `GITLAB_ANALYSIS.md §8` has the reasoning.
