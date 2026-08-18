# What we know about the SonarQube API

Everything below was measured against a live **SonarQube 26.8.0.126808** unless
marked otherwise. Anything not confirmed that way is labelled. The point of this
file is that most of it is invisible from a healthy instance, and several items
contradict what the API documentation implies.

Reproduce with `testing/verify-against-real-sonarqube.sh`.

---

## 1. The method matters more than any single finding

We first verified the tool against a mock SonarQube written from this
repository's own README. Every documented claim passed. The mock was worthless
for the claims that mattered, because it was **derived from the same beliefs it
was testing** — wherever the README was wrong about the API, the mock was wrong
in exactly the same direction, and the code sailed through.

Three of the behaviours in section 3 were found only by querying a real
instance, and the mock had asserted the opposite of two of them.

What a mock *did* catch, and catch well:

- **Differential bugs.** Two implementations, same input, divergent output —
  one of them is wrong regardless of whether the fixture is realistic. This is
  what exposed the Python `new_*` bug.
- **Crash paths.** A `TypeError` is a `TypeError` whatever the payload's
  provenance.
- **Error branches you cannot summon on demand:** 403s, an SSO proxy answering
  200 with an HTML login page, a missing `paging` block, connection refused.

What a mock structurally cannot catch: it answers whatever you ask it. It never
validates that `componentKeys` is still the right parameter name, or that
`f=analysisDate,leakPeriodDate` is accepted. A real instance returns 400; a mock
returns data.

**Rule of thumb.** Use a real instance for response shapes and semantics; use a
mock for error branches and regressions. Never write the mock from the
documentation you are trying to verify — write it from captured payloads
(`--dump-dir` exists for exactly this).

---

## 2. Permissions

**`api/components/search_projects` filters silently.** Confirmed. With Browse on
4 of 6 projects, it returns 4 with HTTP 200, no warning, no partial-result flag.
The two missing projects are indistinguishable from not existing.

**`Administer System` grants the count without granting visibility.** This one
corrected an assumption of ours. We expected global admin to imply Browse
everywhere, which would make the gap unmeasurable by any single token — you
would need two accounts. It does not:

| Token | `search_projects` | `api/projects/search` |
|---|---|---|
| Browse on 4 of 6 | 4 | 403 |
| Same + `Administer System` | **4** | **6** |

So one restricted-but-admin token measures the gap correctly. The tool's
central design works. Note the older warning in the README about admin tokens
"seeing everything" is about *your own* account, which typically holds Browse
everywhere too — not about the `admin` permission itself.

**Project visibility gates whether permissions are even stored.** Projects are
created public. While public, `api/permissions/remove_group` for `user` (Browse)
returns **400** — there is no stored grant to remove. Switching the project to
private *materialises* the default template's `sonar-users` Browse grant, which
is then revocable. Any script that restricts permissions must set visibility
first, or its revocations silently no-op.

---

## 3. Response shapes

**Measure values are JSON strings.** `"12.5"`, `"22"`, `"0.0"` — never numbers.
Parsing with an integer parser breaks on `"12000.0"`; use a float parser and
keep the string for output.

**`new_*` metrics have no root `value`.** They nest under `period`. Real payload
from `api/measures/search`:

```json
{ "metric": "new_lines", "component": "com.acme:proj1",
  "period": { "index": 1, "value": "9" } }
```

Reading only `value` yields *nothing* for every new-code metric — silently, as
empty cells that look like "no data". Older versions used a `periods` array
instead; handle `value` → `period.value` → `periods[0].value`.

These metrics are absent entirely until a new-code period exists. A brand-new
project's first analysis has none; set one (`api/new_code_periods/set`, e.g.
`PREVIOUS_VERSION`) and analyse a second time.

**`api/issues/search` puts `total` at the root**, not under `paging`, unlike
every other paginated endpoint.

**`api/sources/scm` returns one entry per changeset, not per line.** Consecutive
lines from the same commit collapse onto the first. A 30-line file returned 3
entries, at lines 1, 13 and 21. Requesting `from=1&to=200` does not give you 200
rows, and "authors over the first 200 lines" is really counting changesets.

**`api/sources/scm` entries are positional arrays** — `[line, author, date,
revision]` — with the line number as a JSON *number*, not a string. Entries can
be shorter than four elements. (The short-entry case is documented upstream; we
did not reproduce it on 26.8, where entries came back full length.)

**A file indexed without SCM metadata still returns 200**, with `author` and
`revision` as empty strings:

```json
{ "scm": [ [ 1, "", "2026-08-18T06:00:11+0000", "" ] ] }
```

This is the single most misleading response in the set. Emptiness is *not* how
you detect a shallow clone — **blank authors are**. Testing only for "rows
present" reports a successful blame with zero authors, which is precisely
backwards: it is the `fetch-depth: 1` finding, reported as a pass.

**Dates carry an offset** — `2026-08-16T05:43:07+0200`. Truncating to 19
characters discards it and compares a local clock to a remote one, producing
ages off by hours and "-1 days" on a fresh analysis.

---

## 4. `coverage` is usually present, even with no coverage report

This contradicts the premise the tool was built on, so it is worth stating
precisely.

A project analysed **with no coverage report of any kind** returned:

```json
{ "coverage": "0.0", "lines_to_cover": "7",
  "uncovered_lines": "7", "line_coverage": "0.0" }
```

SonarQube derives 0% from `lines_to_cover`. The project is therefore **present**
in a coverage ranking and sorts last — which is roughly where you want it.

"Absent is not zero" still holds, but for a narrower population than assumed:
projects **never analysed at all** (no measures whatsoever), and languages whose
analyser reports no lines to cover. Read the tool's "no coverage data" count
mainly as a never-analysed count, and read it next to `Jamais analysés`.

Caveat: one language (Java), one version (26.8). Worth re-checking for
JS/TS/Python analysers before relying on the generalisation.

---

## 5. Environment notes

- **Auth:** `Authorization: Bearer squ_...` works on 26.8. Not verified on the
  older versions that use the `periods` array; those may need Basic auth with
  the token as username.
- **Running SonarQube in a container:** needs `vm.max_map_count >= 262144` for
  Elasticsearch (`sysctl -w`, requires privilege) and roughly 2 GB of RAM.
  `SONAR_ES_BOOTSTRAP_CHECKS_DISABLE=true` helps. First boot takes minutes; poll
  `api/system/status` for `"status":"UP"` rather than sleeping.
- **Behind a restrictive egress proxy:** if Docker Hub's blob CDN
  (`production.cloudfront.docker.com`) is blocked while the registry API is not,
  pull through `mirror.gcr.io/library/<image>` instead.
- **Output encoding:** the interface is French. Java's `stdout.encoding` falls
  back to the native encoding when output is redirected; under a C locale (a CI
  container with no `LANG`) that is ASCII and every accent becomes `?`. Pin
  stdout/stderr to UTF-8 in `main` rather than relying on the environment.

---

## 6. Still unverified

- **SonarQube Cloud / `--organization`.** `sonarcloud.io` was unreachable from
  the test environment (egress policy). Cloud requires `organization` on
  `api/projects/search`; without it the call 400s and the tool would report
  "requires Administer System" — a false blind spot on its most important
  number. The endpoint list in `needsOrganization()` was widened from the API
  docs but **never exercised against Cloud**. Also unknown: Cloud has no
  `api/system/status`, so connectivity check 1 likely degrades.
- **The `periods` array.** Handled in code, reproduced only against a mock. Would
  need an old image (`sonarqube:8.9-community` or similar) to confirm, along
  with whether Bearer auth works there.
- **Short `scm` entries.** Defended against, not observed on 26.8.
