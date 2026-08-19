# GitLab commit activity & practice signals — what to extract

Companion to the SonarQube portfolio audit, and **independently useful**. Sonar
answers *what is the code like*; GitLab answers *how is it being worked on*.

Two standing decisions shape everything below:

- **The two reports stand alone.** The GitLab report is complete and publishable
  without a single Sonar project matched to it. Joining is an overlay attempted
  after both reports exist, never a precondition for either. See §6.
- **Activity means commits.** Not `last_activity_at`, not event streams. See §3.

Implemented in `GitlabActivityAudit.java` (§1–§4 and the practice signals of
§5). What is *not* implemented is called out where it arises. Nothing here has
been verified against a real GitLab instance — `testing/smoke-gitlab.sh` covers
crash paths only, and `KNOWLEDGE.md §1` explains why that distinction matters.

Assumed licence: **Ultimate/Enterprise** — DORA, approval rules, vulnerability
data and audit events are all in play, and several free-tier proxies proposed
earlier are dropped in favour of the real measure.

---

## 1. Scope — run it on a group, not the instance

A full-instance run is supported — §2 exists to make 2000 projects tractable —
but a scoped run is the one you will actually iterate on. Every pass takes a
scope and reports its own denominator.

```
--group <full/path>        one group, --include-subgroups to descend
--projects <file>          explicit list of paths or numeric ids
--since <days>             activity window, default 90
--top <N>                  size of the deep-analysis budget, default 200
--floor <commits>          activity floor, default derived from the data
--include-archived         off by default, always counted
```

`--top` is a **budget**, not a cut-off: §2 spends it by quota across size
buckets, namespaces and a random control slice, rather than handing it to the
200 busiest projects.

Group scope is one call: `GET /groups/:id/projects?include_subgroups=true&per_page=100&archived=false`.
Instance scope is `GET /projects?per_page=100` with the same downstream handling.

This matters beyond convenience. A 150-project group runs in ~2 minutes, which
means the analysis can be iterated on — run it, read the shortlist, find out
which signal misled you, change it, run again. A 1700-project instance sweep
gets run once and then defended. Build for the first.

**Always report, for any scope:** projects in scope, archived excluded, empty
repos excluded, forks/mirrors excluded, and how many failed with 403 (a
permission gap in GitLab is the same silent-truncation risk as
`search_projects` on the Sonar side — it produces a shorter list, not an error).

---

## 2. Pre-selection — 2000 down to ~200, without 2000 expensive calls

The deep pass in §4 costs ~25 calls per project. Run naively over 2000 that is
50k calls and a report nobody reads. This funnel spends ~90–850 calls to choose
the 150–200 that earn the deep pass, and — the part that matters more — makes
the cut auditable.

### Stage 0 — inventory. ~20 calls, all 2000

`GET /projects?per_page=100&statistics=true`, or group-scoped per §1. Hard
exclusions, each **counted and reported**, never silently dropped: `archived`,
`empty_repo`, undiverged forks, pull-mirrors (their activity is someone else's),
and 403s. Expect this to take 20–35% off before any activity measure is involved.

### Stage 1 — recency gate. 0 extra calls

`last_activity_at` is useless as a *ranking* signal (§3) but is exactly right as
an *exclusion* filter, because it fails in the safe direction: it is inflated by
non-commit events, so anything with commits in the window necessarily has a
recent `last_activity_at`. Filtering on it over-includes; it does not
under-include.

Drop everything older than the window. On a real portfolio this is the largest
single cut — commonly around half.

**Validate the claim instead of trusting it.** There is a leak class: history
rewrites, imports and project transfers can put the two out of step. Sample ~30
projects from *below* the cut, run the real commit count on them, and report the
false-negative rate in the output. Zero means the gate is sound and stays free.
Non-zero means you have a number to quote instead of an assumption.

### Stage 2 — true commit recency and volume

Two routes; worth probing which your instance supports.

**GraphQL, batched — ~40 calls for 2000 projects.** Query projects in pages of
~50 asking for `repository { rootRef tree(ref:…) { lastCommit { committedDate author { username } } } }`
alongside `statistics { commitCount }`. That yields the date of the actual last
commit on the default branch — a true commit signal, not an event proxy — at
roughly two orders of magnitude less cost than REST. Field shape varies by
GitLab version: probe it against one project before building on it.

**REST fallback — 1 call per stage-1 survivor.** `commits?ref_name=<default>&since=&per_page=1`,
read `X-Total`. Perhaps 700–900 calls after the recency gate. Slower, certain.

Either route, this is where a project first carries a number that means commits.

### Stage 3 — the activity floor

Apply a floor before ranking, for the same reason `ANALYSIS.md` gates on
`ncloc < 500`: below some volume the deep signals are arithmetic on noise. A
project with 3 commits and 1 MR in the window cannot support a review-coverage
percentage, a DORA figure or a pipeline success rate — it returns 0% or 100%,
and both are meaningless.

Starting point: **≥10 non-bot commits in window**. But set it from the data
rather than in advance — plot the commits-in-window distribution across the
stage-2 survivors. It is reliably long-tailed, and the natural break often falls
near the 150–250 you were aiming for, at which point the floor is descriptive
instead of arbitrary.

What falls below the floor is not lost. It is the **low-activity cohort**,
reported as counts and cohorts exactly like the never-analysed 437 on the Sonar
side. That is a finding, and it needs no scoring.

### Stage 4 — quota, not top-N

Taking the top 200 by commit count is the mistake here, and it is the same
mistake as ranking Sonar projects by `sqale_index`: you get the biggest, busiest
monoliths, mostly from two or three teams, and you learn what you already knew.

Allocate the 200 instead:

| Slice | ~Count | Why |
|---|---|---|
| Top decile within each size bucket (4 buckets) | 140 | Comparable peers, spread across scales |
| Namespace cap — no group above ~15% of the budget | reallocation | One busy team cannot eat the run |
| High activity + single author | 20 | Bus-factor cases raw ranking buries |
| Random control drawn from above the floor | 30 | The only way to know what the selection missed |

The random control slice is the one that feels wasteful and is not. Without it,
every statement in the final report is conditional on a selection rule nobody
validated. With it you can write "practice coverage among the 170 selected is X;
among a random sample of the rest of the eligible pool it is Y" — and if X and Y
diverge sharply, the selection rule is itself the story.

### What pre-selection costs

| Stage | GraphQL route | REST route |
|---|---|---|
| 0 — inventory, 2000 @ 100/page | 20 | 20 |
| 1 — recency gate | 0 | 0 |
| 2 — commit recency/volume | ~40 | ~800 |
| Stage-1 validation sample, 30 projects | 30 | 30 |
| **Total** | **~90** | **~850** |

Against ~5000 for the deep pass on 200. Pre-selection is 2–15% of the run either
way, so the REST fallback is perfectly acceptable if GraphQL disappoints.


## 3. Commit activity — the ranking measure

One call per project gets the count without paging:

```
GET /projects/:id/repository/commits?ref_name=<default_branch>&since=<T-Nd>&per_page=1
→ read the X-Total response header
```

`X-Total` is omitted above ~10k rows; treat a missing header as *very active*,
never as zero.

For everything in scope after the cheap count, page the commits properly —
`per_page=100`, typically 1–5 pages for a 90-day window. One pass yields four
things, so do it once and keep the raw commits:

| Derived | From |
|---|---|
| Commits in window | count |
| Distinct authors in window | `author_email`, normalised |
| Merge-commit ratio | `parent_ids.length > 1` |
| Commit spread over time | `committed_date` — 40 commits over 12 weeks ≠ 40 in one day |

Add `first_parent=true` for a second count restricted to the mainline. The gap
between the two numbers *is* the branching pattern: equal means everything lands
directly, a large gap means work happens on branches and merges in.

### Three ways this number lies — handle all three

**Squash-merge teams look inactive.** A group squashing every MR to one commit
shows 5–10× fewer commits than an identical team using merge commits. This is a
workflow difference read as an activity difference, and it is the single most
distorting effect in the whole ranking. Mitigation: carry **MRs merged in
window** alongside commit count and rank on the pair; where they disagree
sharply, the squash setting (`squash_option` on the project) usually explains
it. Fetch it and record it.

**Bots inflate.** Renovate, release automation, CI back-commits. Filter on
`author_email` patterns and `committer` vs `author` divergence, and **report the
filtered count per project** rather than dropping silently — a repo whose only
activity is Renovate is a finding, not an empty row.

**Author identity fragments.** The same person commits under three emails.
Normalise on name plus email local-part before counting distinct authors, and
treat the distinct-author number as ±1, not exact. It feeds a bus-factor
judgement, so it only has to be right about 1 vs 5.

### Ranking

Stratify by repository size (`statistics.commit_count` all-time, or
`repository_size`) and rank within bucket, for the same reason the Sonar pass
stratifies by `ncloc`: a ratio computed on a tiny repo is volatile, an absolute
count on a large one is foregone.

Rank on **commits per week in window** and **distinct authors in window**. Keep
them as two columns. Collapsing them into one score hides the case that matters
most — high commits, one author.

---

## 4. Practice signals

Only on the projects selected by §2. A dormant project's practice signals are
not interesting; that it is dormant is the finding.

### Tier 1 — how change reaches the default branch

**Review coverage.** `GET /projects/:id/merge_requests?state=merged&updated_after=<T-Nd>&per_page=100`,
then per MR `GET /merge_requests/:iid/approvals` (Ultimate — gives actual
approvers, not a proxy).

The number that matters is the **share of default-branch commits that arrived
via an approved MR**. Not the MR count. 200 MRs alongside 150 direct pushes to
`main` is not code review.

Per MR keep: `created_at`→`merged_at` (time-to-merge — report the **median**,
the mean is noise), `changes_count` (MR size, the strongest available predictor
of whether review was real), `user_notes_count`, and approver identities vs
`author.id` (self-approval, which Ultimate lets you measure directly instead of
inferring from self-merge).

**Approval rules as configured.** `GET /projects/:id/approval_rules` and
`GET /projects/:id/approvals` — required approver count, whether authors can
approve their own MRs, whether the rule can be overridden. Configuration and
behaviour diverge constantly; having both is what makes either meaningful.

**Branch protection.** `GET /projects/:id/protected_branches` — is the default
branch protected, and `push_access_levels`. Binary, unambiguous, and it explains
the direct-push number above.

**Push rules.** `GET /projects/:id/push_rule` (Premium+) — commit message
regex, signed commits, secret file prevention.

### Tier 2 — delivery

**DORA, directly.** `GET /projects/:id/dora/metrics?metric=<m>&interval=all&start_date=&end_date=`
for `deployment_frequency`, `lead_time_for_changes`, `change_failure_rate`,
`time_to_restore_service`. Four calls, and they replace most of what would
otherwise be reconstructed from pipelines and deployments.

Caveat worth stating in the report: DORA is only as good as the environments
configured. A project deploying from a pipeline that never registers a
`production` environment scores zero deployment frequency while deploying daily.
Cross-check `GET /projects/:id/environments` — **no environments configured** is
a data-availability finding, distinct from a low score.

**Pipelines.** `GET /projects/:id/pipelines?ref=<default>&updated_after=<T-Nd>&per_page=100`
→ runs per week, success rate, median duration. Commits with no pipelines is one
finding; pipelines at 40% success is a worse one — it exists and is being ignored.

### Tier 3 — security & dependency posture

`GET /projects/:id/vulnerabilities` or the group-level vulnerability findings
endpoint → open vulnerabilities by severity, and age of the oldest.
`GET /projects/:id/dependencies` → dependency list, and whether it is empty
(scanner not configured) rather than genuinely dependency-free.

Distinguish *no vulnerabilities* from *no scanning* everywhere. They render the
same in a table and mean opposite things.

### Tier 4 — repo contents, near-free

`HEAD /projects/:id/repository/files/<url-encoded>?ref=<default>` returns 200/404
without transferring: `.gitlab-ci.yml`, `README.md`, `CODEOWNERS`, `Dockerfile`,
renovate/dependabot config, lockfiles (a manifest with no lockfile is an
unpinned tree).

Then fetch `.gitlab-ci.yml` on the shortlist and grep for a `sonar` job, the
`SAST`/`Secret-Detection`/`Dependency-Scanning` template includes, and a deploy
stage. The sonar grep is the useful one even standalone: *projects whose CI is
configured to run Sonar* is a fact about intent that stands with or without a
successful join.

---

## 5. What the GitLab report says on its own

Written to be publishable with no Sonar data at all:

1. **Coverage of practice** — of N active projects: how many protect the default branch, require approval, run CI, run security scanning, have a CODEOWNERS. Counts and percentages, no scoring. The most defensible section.
2. **Activity ranking** — commits/week and distinct authors, by size bucket, with bot-filtered counts shown.
3. **Review is nominal** — high commit activity, low approved-MR share or high self-approval. Change is landing without review.
4. **Single-maintainer, high activity** — a bus factor that actually matters.
5. **Configured but ignored** — CI present, success rate low; or scanners present, findings unaddressed and ageing.
6. **Dormant** — in scope, no commits in window. Counted, not scored.

---

## 6. The join — best effort, additive, never load-bearing

Attempted only after both reports exist. It adds a section; it cannot invalidate
one.

Try in order, and **label every match with how it was made**:

| Method | Confidence |
|---|---|
| `api/alm_settings/get_binding` on the Sonar project → GitLab path | exact |
| Sonar `api/project_links/search`, `scm` link → repo URL | exact |
| Sonar project key or name normalised against `path_with_namespace` | derived |
| Fuzzy name match | suggestion only |

Only exact and derived matches feed any cross-report claim. Fuzzy matches go in
an appendix as *candidates for a human to confirm*, never into a table that
looks computed.

Report the match rate as a finding in its own right. A low rate is itself
informative — it means the GitLab↔Sonar integration is not configured, which is
the same class of governance gap as the projects that were never analysed.

If the join lands well, three claims become available that neither side supports
alone:

- **Active in GitLab, absent from Sonar** — live projects with no scanner, separated at last from dead repos.
- **High Sonar debt velocity + low review coverage** — the strongest "this team needs help" signal in the audit. Sonar says debt is being created; GitLab says nothing is stopping it.
- **High Sonar debt + zero commits** — confirms *carrying, not creating* with independent evidence, rather than inferring it from a stale analysis date.

If it lands badly, delete the section. Nothing above depends on it.

---

## 7. Cost, end to end on a 2000-project instance

| Stage | Calls |
|---|---|
| Pre-selection §2 (GraphQL route) | ~90 |
| Pre-selection §2 (REST route) | ~850 |
| Commit paging over the ~200 selected @ ~2 pages | ~400 |
| Tier 1, 200 @ ~5 + MR approvals | ~1600 |
| Tier 2 DORA + pipelines + environments, 200 @ 6 | ~1200 |
| Tier 3 security, 200 @ 2 | ~400 |
| Tier 4 file HEADs, 200 @ ~8 | ~1600 |
| **Total** | **~5300 (GraphQL) / ~6000 (REST)** |

A single group of 150 with `--top 60` runs the same code for ~1800.

The shape to notice: **pre-selection is under 15% of the run in the worst case.**
There is no reason to cheapen the funnel by guessing — measure commits properly
on everything that survives the recency gate, and spend the budget on the
selection being defensible.

All reads, read-only PAT scoped `read_api`. GitLab's authenticated default is
2000 req/min but instances routinely lower it — implement `Retry-After` handling
before the first run, not after the first 429. At ~6000 calls a full run is
minutes, so parallelism buys little and risks the rate limiter; keep it modest.

---

## 8. Out of scope, deliberately

- **Per-file blame / churn.** Unbounded per project. Same verdict as the Sonar side.
- **Commit message quality.** Cheap to compute, near-impossible to interpret. Conventional-commit adherence measures whether a convention was adopted, not whether the team works well.
- **Lines added/removed as productivity.** No. It will be misread the moment it appears in a table.
- **Anything per-person.** The unit of analysis is the project throughout. Author counts feed bus factor and nothing else.
