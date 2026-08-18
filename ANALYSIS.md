# First-pass portfolio analysis

Design for turning the inventory into a shortlist. Written against a real
portfolio of ~1657 visible projects: 437 never analysed, 680 analysed but stale
(>90d), 540 active (<90d), 1042 with no coverage measure.

The diagnostic (`SonarAuditCheck.java`) answers *can I see the data*. This
answers *what do I do with it*. It stays deliberately rough: the output is a
shortlist of ~30-50 projects worth a human look, not a score anyone should
defend to three decimals.

---

## 1. Split the portfolio before ranking anything

Ranking all 1657 together is the mistake. Three cohorts, three different
questions, only one of which is about code quality.

**Never analysed — 437 (26%).** No data at all. This is not a quality signal;
it is a governance signal, and at 26% it is probably the single largest finding
in the audit. Report as a count with a sample of keys and their creation dates.
Do not score them. The question they raise is "why does a quarter of the
portfolio exist in SonarQube without ever being scanned" — dead projects never
deleted, onboarded-then-abandoned, or scanner never wired into CI. Splitting
that 437 by creation date separates "registered years ago and forgotten" from
"onboarded last month and not finished".

**Analysed but stale — 680 (41%).** Debt is frozen, so *velocity is undefined*.
Excluded from the main ranking. But run one cheap side-list over them: projects
with high `sqale_debt_ratio` **and** a last analysis 90-365 days old are
recently-abandoned liabilities — someone stopped working on a project in bad
shape. Past ~365 days, treat as dormant and report only as a count.

**Active — 540 (33%).** The ranking population. Everything below applies here.

---

## 2. Rank on what the bulk call already returns

Stage 1 costs **zero extra API calls**: `--csv` already fetches all 13 metrics
for every project, 100 per request. Do the whole first pass on that CSV before
spending a single per-project call.

The framing stays *velocity, not level*. Four signals, all already in the CSV:

| Signal | Formula | Reads as |
|---|---|---|
| Injection rate | `new_violations / max(new_lines, 1)` | issues created per line written |
| New-code coverage | `new_coverage` | is the new code tested |
| New duplication | `new_duplicated_lines_density` | copy-paste in fresh work |
| Debt level | `sqale_debt_ratio` | context — half weight, see §6 |

`sqale_index` stays out — it ranks by size, which is what we are trying not to do.

### Stratify by size, then rank by percentile

Two problems solved at once. Small projects produce volatile ratios (3 issues on
40 new lines looks catastrophic); large ones dominate any absolute measure.

Bucket the 540 by `ncloc` — `<1k`, `1k-10k`, `10k-100k`, `>100k` — and convert
each signal to a **percentile rank within its own bucket**. Then the composite is
the mean of a project's available percentiles.

Percentiles rather than normalised raw values, on purpose: robust to outliers,
no unit reconciliation, and nobody has to argue about weights. A project at the
90th percentile for injection rate is worse than 90% of *comparably sized*
projects, which is the claim we actually want to make.

Take the top decile of each bucket. That is ~54 projects, spread across sizes
instead of being four monoliths.

### Absent is never zero

Per KNOWLEDGE.md §2 the absent-vs-zero distinction is real and the code must not
paper over it. A missing metric is **dropped from that project's mean**, never
imputed. Carry a `signals_present` count (0-4) alongside every score, and refuse
to shortlist anything scoring on fewer than 2 — a project ranked on one signal is
a guess wearing a number.

Expect this to bite on coverage specifically: with 1042 projects lacking any
coverage measure, a large share of the 540 will score on 3 signals, not 4.
Report that count. "We could not assess coverage on N of 540 active projects"
is a finding, not a footnote.

### Gates, not penalties

Two conditions disqualify a project from the velocity ranking rather than
lowering its score:

- `new_lines` absent or 0 — no new code in the window, so there is no velocity
  to measure. These go to a separate list ranked by `sqale_debt_ratio`:
  *carrying* debt, not creating it.
- `ncloc < 500` — too small for any ratio to mean anything. Side list.

---

## 3. Spend the expensive calls only on the shortlist

Stage 2 runs per-project queries over the ~54 survivors, not over 540. Two calls
each, ~110 GETs total, a couple of minutes:

- `api/measures/search_history` for `sqale_index` and `ncloc` → **Δdebt / Δncloc**
  over the window. This is the one true velocity measure; everything in stage 1
  is a proxy for it. It confirms or kills the ranking.
- `api/issues/search` with the `author` facet → concentration (share of issues
  from the top author) as a bus-factor proxy.

Both are already implemented in section 4 of the diagnostic, against a single
sample project. The work is fanning them out and keeping the results.

Treat the author facet as weak evidence: it covers open issues only, so a
project with few issues yields a meaningless concentration. Require an issue
count floor (say 20) before reporting it.

Where the stage-2 Δdebt/Δncloc contradicts the stage-1 composite, **stage 2
wins** — it measures the thing directly. Disagreements are worth reading; they
usually mean a project's new-code metrics are shaped by a quality-gate change
rather than by the code.

---

## 4. What comes out

Four outputs, in descending order of how much anyone should trust them:

1. **Governance** — 437 never analysed (split by age), 680 stale, ~605 analysed
   with no coverage measure. Counts and cohorts. The most defensible findings in
   the whole audit, and they need no scoring at all.
2. **Creating debt** — top ~30 by stage-2 Δdebt/Δncloc, drawn from the stage-1
   shortlist, with size bucket, signals_present, and the raw metrics shown
   beside the rank. These are the candidate conversations.
3. **Carrying debt** — high `sqale_debt_ratio`, no new code. Not urgent, but
   the list someone will ask for.
4. **Recently abandoned** — stale 90-365d with high debt ratio.

Every list ships with its own denominator and the count of projects excluded
from it and why. A ranking that does not say what it dropped reads as complete
when it is not — the same failure mode as `search_projects` filtering silently.

---

## 5. Cost

| Stage | Calls |
|---|---|
| Diagnostic (unchanged) | ~25 |
| Inventory, 1657 projects @ 100/page | ~17 |
| Stage 1 ranking | 0 (pure CSV) |
| Stage 2, ~54 shortlisted @ 2 | ~110 |
| **Total** | **~150 GETs** |

All reads. The expensive part of a portfolio audit is not the API, it is
deciding what not to look at.

---

## 6. Deliberately excluded from the first pass

- **Blame / `api/sources/scm`.** Per-file, so per-project cost is unbounded.
  Worth it only on the final ~10, if at all.
- **Quality gate history.** A gate definition change moves every `new_*` metric
  at once and would explain some rankings — but reconstructing gate changes over
  time is a project of its own.
- **Weight tuning.** Percentile means with one deliberate exception:
  `sqale_debt_ratio` carries half weight, because it measures level rather than
  velocity and at full weight it drags the ranking back toward large old
  projects — the thing this whole approach exists to avoid. The other three stay
  equal until someone reads a shortlist and says which signal misled them.
  Tuning before that is fitting to an intuition nobody has stated.
