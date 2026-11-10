# Tool details

What the README leaves out: running without JBang, Windows, captures, tests,
and the tools used less often. Options are in each tool's `--help`.

## Running without JBang

Any JDK 25+ (the current LTS) and Maven. The code is a Maven project under `src/main/java`
(package `ch.ge.edg.sonar.gitlab.audit`); the `.java` files at the root are
only JBang entry points that point at it with `//SOURCES`. `JbangScriptsTest`
keeps it that way for every JBang script in the repository: at most 20 lines,
`//SOURCES` only under `src/main/java`, one `main` that calls the real one.
Older scripts that break the rule are listed in
`config/baseline/jbang-scripts.txt`, which may only shrink.

```bash
mvn -q package dependency:copy-dependencies -DoutputDirectory=target/libs
java -cp "target/classes:target/libs/*" ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarAuditCheck \
    --url "$SONAR_URL" --token "$SONAR_TOKEN"
```

Every tool is in the `cli` package under the same name as its root script. On
Windows the classpath separator is `;`. The dependency versions are in
`pom.xml` and in the `//DEPS` lines of the root scripts: keep the two in sync.

## Windows

**CSVs open in Excel as they are.** The files meant for people
(`classement.csv`, the GitLab CSVs, `croisement.csv`) are semicolon-separated
with a UTF-8 BOM and CRLF, so a double-click or `start classement.csv` splits the
columns and keeps the accents. `--comma` writes the machine format instead. The
Sonar inventory is comma-separated by design; open it with Data → From
Text/CSV, origin UTF-8. The readers sniff the separator, so a file that went
through Excel and back still loads.

**The console.** The output is in French. The tools write in the console's code
page when talking to a terminal and in UTF-8 when redirected, and turn `—`, `≥`,
`→` into ASCII where the code page lacks them, so `chcp 65001` is not needed.
Colour is off in conhost unless the terminal announces itself; `--color
always|never` or `NO_COLOR` override it. The logic is in `runtime/Host.java` and
`runtime/Terminal.java`.

## Captures: `--dump-dir` and `--replay-dir`

`--dump-dir` writes every raw API response to disk, named after the request.
`--replay-dir` runs the whole audit from such a directory, with no URL and no
token:

```bash
jbang SonarAuditCheck.java --csv inventaire.csv --dump-dir ./captures
jbang SonarAuditCheck.java --replay-dir ./captures --csv rejoue.csv   # offline
```

Use it to diff responses between versions (unknown fields are ignored
silently, so drift never shows up as an error), to keep samples of the
unhealthy projects whose payloads break naive parsing, and to rank an instance
you cannot reach from a colleague's capture.

## Developing: the inner loop

Build with the Maven wrapper, `./mvnw`, never plain `mvn`: the machine's default JDK is often older
than the one the pom targets (25, see `maven.compiler.release`), and Maven then stops at "release
version 25 not supported". `./mvnw` and `./verify` both source `.mvn/use-jdk.sh`, which keeps
`JAVA_HOME` if it is new enough and otherwise looks for a JDK 25 (macOS `java_home`, SDKMAN,
`~/.jdks`, JBang, which downloads one on first use). With none it stops and says how to install one.
On Windows use Git Bash (`.\verify` does); `mvnw.cmd` does not select a JDK.

| What | Command | Time (warm) |
| --- | --- | --- |
| One test class | `./verify --test FooTest` | 3 to 5 s |
| One test method | `./verify --test FooTest#someMethod` | 3 to 5 s |
| Several | `./verify --test FooTest,BarTest` | a few s |
| All gates a push needs | `./verify` (`./verify --fix` formats first) | about 1 min |
| Docker tests, copy-paste | `./verify --full` | a few min |
| Mutants of what you changed | `./verify --mutants` | under a min for a small change |

`--test` is `./mvnw test -Dtest=... -Dsurefire.failIfNoSpecifiedTests=false` with Spotless,
Checkstyle, PMD, CPD and JaCoCo skipped. Error Prone and NullAway still run, as they are the
compiler. It is feedback while you work, not permission to push: the pre-push hook runs `./verify`.

## Notes on a batch of work

Decisions, compromises, behaviour changes, gate changes and reviewer disagreements are not written to
a file in the repository. The lead agent keeps them in its session memory or scratchpad and puts them
in the pull request (description, or a comment or attachment) when it opens it. Open questions that
outlive the PR go in `ARCHITECTURE.md` ("Open questions for the owner"). Details there.

## CI: every change lands on main through a green pipeline

Nobody pushes to `main`. A change goes on a branch, then a pull request (GitHub)
or a merge request (GitLab). It merges itself, without human review, once the
**`verify`** job is green: `./verify`, the same gates as the pre-push hook, in
about a minute. On Windows, `.\verify` in PowerShell or cmd runs `verify.cmd`,
which hands the bash script to Git Bash.

The pre-push hook runs `./verify` on the checked-out tree, so it refuses (and says why) unless that tree
is what you push: a rebase, merge, cherry-pick or revert in progress, tracked files with uncommitted
changes, or a pushed ref that is not `HEAD` all stop the push. Check out the branch, or push from a worktree of it.

Every night, and on demand (`gh workflow run verify`), **`verify-full`** runs
`./verify --full` on `main`: Docker integration tests and copy-paste
detection. It does not block anything. A red run is fixed by the next pull
request.

**Mutation testing (PIT) is not in CI**: it used up the CI budget. Run it
locally, beside your work: `./verify --mutants` mutates only the production
classes changed since `origin/main` (committed or not) and takes under a minute
for a small change. It never fails and blocks nothing; surviving mutants are in
`target/pit-reports/index.html`. The whole code base:
`./mvnw -Ppit test-compile pitest:mutationCoverage` (about 5 minutes, 99% killed today).

It cannot run on each merge: an auto-merge is made with the workflow token, and
GitHub starts no workflow for that token's events.

The ratchet compares with the target branch, so a `Ratchet-Override:` line in
one of the branch's commits still counts.

```bash
git switch -c my-change      # work, commit
git push -u origin my-change
gh pr create --fill          # auto-merge.yml sets it to merge when green
```

**GitHub.** `.github/workflows/verify.yml` and `auto-merge.yml`. The `main`
ruleset requires a pull request (no approval) and the `verify` check. It also refuses force pushes and deletion, with no bypass. The
repository allows auto-merge and deletes merged branches. Pull requests from
forks run the checks but never merge themselves: on a public repository, that
would let anyone land code that merely passes the tests.

**GitLab.** `.gitlab-ci.yml`, with the same two jobs, plus `auto-merge`.
`verify-full` runs only on the default branch. Blocking
needs these project settings, which a file cannot set:

- Settings → Repository → Protected branches: `main`, allowed to merge:
  Maintainers, allowed to push and merge: **No one**.
- Settings → Merge requests → Merge checks: **Pipelines must succeed**.
  Approvals: none required.
- A project access token (role Developer, scope `api`) in the CI/CD variable
  `GITLAB_AUTOMERGE_TOKEN`, masked, **not** protected. Merge request pipelines
  run on unprotected branches. Without the token, `auto-merge` is skipped and a
  merge request waits for a click.
- `verify-full` needs a runner that allows the `docker:dind` service (privileged)
  for Testcontainers.

## Tests

- `testing/verify-against-real-sonarqube.sh` starts a real SonarQube in Docker,
  with a restricted token that sees four of six projects, and checks the gap is
  reported. Use it for anything about **what the API says**: a mock only
  confirms what its author believed.
- `testing/smoke-sonar.sh` and `testing/smoke-gitlab.sh` run against fake
  servers (`testing/fake-*.py`) in seconds, for **what the tools do with an
  answer**: odd shapes, retries, branches, exclusions, replay byte-identical.

What was learnt from the real API is in `KNOWLEDGE.md`.

## Ranking: `SonarRank.java`

Reads the Sonar inventory, makes no API calls, and writes `classement.csv`
with three lists in the `liste` column: `VELOCITE` (creating debt),
`DETTE_PORTEE` (carrying it), `ABANDON_RECENT`. Filter `retenu = O` for the
shortlist. Never-analysed and stale projects are not ranked; the rest is ranked
by percentile inside size strata, and a missing metric is dropped rather than
read as zero. The method is in `ANALYSIS.md`.

## One project up close: `GitLabProjectReport.java`

```bash
jbang GitLabProjectReport.java --path group/subgroup/project
jbang GitLabProjectReport.java --path https://gitlab.com/group/proj/-/tree/main --days 365
```

A report on a single project's way of working: cadence (gaps, weekday, hour in
the author's own offset, commit size, lines touched over net lines),
contributors (bus factor, top share, identities), merge requests (lead time,
uncommented, self-merged, squash rate, delay to first review), then findings.
No Sonar needed. The token can be omitted on a public project, but MR notes
then answer 401 and the review section is reported missing.

Checked against gitlab.com:

- Merge commits are excluded everywhere: counting them crowns whoever presses
  *merge*.
- `statistics=true` is silently ignored below Reporter; absent is reported as
  absent.
- Bots are filtered by a deliberately narrow pattern, and the excluded
  identities are printed.
- When `--max-commits` or `--max-mrs` is hit, rates use the span actually
  covered, and say so.
- A high squash rate turns commit timestamps into merge times; the findings say
  so.

`gitlab/Gitlab.java` holds what both GitLab tools share: the client, `BOT_PATTERN`,
`identity()` and `isMerge()`. `sonar/Sonar.java` is its counterpart.

## The matcher on its own: `ProjectMapper.java`

It matches the rows of one CSV to another and grades each method. `CrossAudit`
calls it with a Sonar × GitLab preset; anything else names its columns:

```bash
jbang ProjectMapper.java --left cmdb.csv --right jira.csv \
    --left-key appli --right-key ref \
    --exact code:code=code_appli --names appli=libelle --plain-names \
    --report rapport.json
```

Methods are `id:left_column=right_column[?column=value]`. `--exact` needs strict
equality, `--derived` compares after normalisation, `--link` reads repository
URLs and is informative only, and `--names` runs last on what is left and only
suggests. The JSON report runs every method alone on every row: coverage,
agreement between methods, name-score precision and recall per threshold (to
pick `--seuil-nom` from evidence), and examples of failures to improve on next.
It contains project names; handle it like the CSVs.

## Charts: adding one

One chart is one file in `viz/charts/` and one `<script>` line in
`viz/index.html`; delete the line and it is gone. A chart calls
`Viz.add({ id, title, help, needs, uses, spec })`: `needs` the files it cannot
do without, `uses` the ones it reads if present. `spec` gets the loaded tables
and returns a Vega-Lite or Vega spec, or a sentence saying why there is nothing
to draw. Shared helpers are in `viz/app.js`. Each chart's `…` menu opens its
spec in the Vega editor.

The activity charts' measures are a list at the top of
`viz/charts/quadrant.js`, with `lowerIsWorse` to flip an axis so the corner to
look at stays top right.
