# Quality-gate log

Choices made while bringing the code under the gates of `./verify` that a reviewer may want to
question. One entry per choice: what, why, and what the alternative would have been.

## Environment

- **Maven must run on JDK 26.** The pom targets `release 26`; Maven on JDK 25 fails with
  "release version 26 not supported". Used `JAVA_HOME=~/.jbang/cache/jdks/26` (the JDK JBang
  downloads for the scripts' `//JAVA 26`).
- **A lenient copy of the pom while writing tests.** The code had ~240 Error Prone / NullAway
  errors, so nothing compiled under the real pom and no test could run. The tests were written
  against a local, untracked `pom-lenient.xml` (the real pom minus Error Prone, `-Werror`,
  Spotless, Checkstyle, PMD, CPD and the JaCoCo gate). It never reached the repo.

## Tests

- **Fake HTTP server on a raw socket.** Checkstyle bans `com.sun.*`, which rules out the JDK's
  `com.sun.net.httpserver`. `FakeHttpServer` speaks just enough HTTP/1.1 for `java.net.http`.
- **`*.csv` is git-ignored** (audit output may hold client data). Added a negation for
  `src/test/resources/**/*.csv` so synthetic fixtures can be committed.
- **JBang scripts source the whole tree.** Each root script now has one
  `//SOURCES src/main/java/ch/ge/edg/sonar/gitlab/audit/**/*.java` instead of a hand-kept list.
  Splitting the big classes (Checkstyle's FileLength 800 / MethodLength 50 / fan-out limits)
  creates many files; keeping seven lists in sync by hand would have broken a script sooner or
  later. Cost: each script compiles all ~10k lines on first run. `org.jspecify:jspecify` was
  added to the `//DEPS` since NullAway fixes bring `@Nullable` into the sources.
- **Selfie snapshots are only created with `-Dselfie=overwrite`** (this environment runs Selfie
  read-only by default). Never run that while refactoring: a snapshot that changes is a
  behaviour change.
- **Tests pin the current behaviour, bugs included.** The suspected bugs the test writers found
  are listed below, not fixed: fixing them is a behaviour change, to decide separately.
- **No clock / sleeper seams yet.** Code calls `now()` and sleeps on retries. Tests use dates
  relative to now (or far in the past/future) and normalise them in snapshots; the retry tests
  really sleep (~10 s in total). A `Clock` and a sleeper would be cleaner.
- **Working-directory files.** `ProjectMapper` loads `namespace-mapping.yaml` and the tools load
  `exclusions.txt` from the working directory. Tests assume neither exists at the repo root (or
  pass an explicit empty `--exclusions`). Running `mvn` from a checkout that has a real
  `namespace-mapping.yaml` would break ProjectMapper snapshots.

## Shared libraries (gitlab, csv, console, exclusions, naming)

- `@SuppressWarnings("SystemConsoleNull")` on `ConsoleOut.terminal()`: Error Prone assumes
  `System.console()` is never null, but on JDK 22+ it is null when stdin/stdout are redirected;
  the check has to stay.
- `@SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")` on `Gitlab.Response.asList`:
  null means "unreadable", distinct from an empty list, and callers/tests rely on it.
- `Gitlab` keeps its old constructor, which passes `System.out`/`System.err` as default streams;
  a new `Gitlab(Connection, PrintStream, PrintStream)` lets commands pass theirs.
- `GitlabHttp` catches `IOException | IllegalArgumentException` instead of `Exception` (the second
  is a malformed URL).
- `Csv.Row` became a record over a `List`, so its equality is by value instead of array identity
  (nothing compares rows).
- New `parallel/Parallel` (bounded map/forEach), shared by Gitlab and Sonar (CPD duplication).
- Tests that reached moved code now call `JaroWinkler` / `SoftTfIdf`; four GitLab tests build their
  client inside the output capture. No asserted value changed.

## Suspected bugs pinned by the tests (behaviour unchanged)

- ProjectMapper `mappedSonarKey(null)` throws NPE before its own null check.
- ProjectMapper `mapNamespace` upper-cases the GitLab namespace but YAML keys are kept as written:
  lowercase keys never match.
- ProjectMapper: only `IOException` is caught when loading the YAML; a non-string value or a
  directory escapes the static initialiser.
- ProjectMapper `?col=val` filter: the value is not trimmed, `? kind = v` never matches.
- ProjectMapper reuse: a manual row whose key vanished is counted in no total (totals don't add
  up), and is replaced by the fresh match in the rewritten file; a manual pair does not withdraw
  a suggestion of the same right row to another left row.
- CrossAudit / SonarToGitlab: a rejected pair comes back on the second run — the rewritten
  `appariement.csv` clears `droite_key` on a rejected row, so the rejection only covers the path.
- SonarToGitlab: the tie-break between originals with 0 commits ignores `last_activity_at`
  although the comparator's doc says "puis la plus récente".
- ConsoleOut `Recoding`: the decoder reports malformed input instead of replacing it; one invalid
  UTF-8 byte silences everything written after it. `if (r.isError()) r.isMalformed();` is a no-op.
- Locale: CrossAudit (`%+.1f`), SonarRank, GitLabProjectReport and GitlabActivityAudit format
  decimals with the default locale (a French machine writes `1,50` in CSVs). Tests pin
  `Locale.ROOT`.
- SonarRank `truncate` returns `max+2` characters; projects excluded for too few signals still
  weigh in their stratum's percentiles and quota; "Actifs (< 90 j)" includes exactly 90 days.
- GitlabActivityAudit selection: `--top 1` selects 3 projects; the single-author and random
  witness slices ignore the budget and the per-namespace cap.
- GitlabActivityAudit: a project GraphQL says has no recent commit gets 0 commits but blank
  merges/active days.
- Gitlab: with all parameters null the URL still ends in `?`. Commit queries build their
  parameters with `Map.of`, whose order varies between JVM runs, so `--dump-dir` file names are
  not reproducible.
- SonarAuditCheck `parseDate`: `ISO_OFFSET_DATE_TIME` rejects Sonar's `+0200`, so the offset is
  always dropped (the opposite of the comment).
- Sonar replay: captures keep the body, not the status: a 403 replays as "no binding", a 404
  history as an empty series.
- SonarAuditCheck: "interrompue à 20 000 projets" is really 40 pages of whatever page size;
  analysis cadence divides by the number of analyses instead of gaps; the `project_links` probe
  counts blank URLs the CSV leaves out.
- GitLabProjectReport: falls back to `https://gitlab.com` on a blank `--url` (untested: would hit
  the internet); reads dates in the system time zone.
- Cosmetic: "1 réponses 403", "1 projets", `→  (manuel)` double space, uneven indentation in the
  "Aucune MR créée" block, a trailing space in the ci/lint refusal message.

## CrossAudit, SonarToGitlab, SonarRank, Vues

- Required picocli options (`--in`, `--sonar`, `--gitlab`) are `@Nullable` fields, checked with
  `Objects.requireNonNull` in `call()`: picocli rejects a missing required option before `call()`,
  but NullAway cannot know that. Options with a `defaultValue` also get it as field initializer.
- `SonarToGitlab.map` went from 7 to 5 parameters through records `Rejections` and
  `NameThresholds` (ParameterNumber 5).
- `Signal` (SonarRank) replaced its `Function` field by a `severity(row)` switch (Error Prone
  ImmutableEnumChecker); tests call `severity(r)` instead of `severity.apply(r)`.
- Classes that call package-private `ProjectMapper` code stay in package `cli`, so `cli` holds
  more classes than a clean layout would (SonarToGitlab's parts, CrossAudit's parts).
- Tests updated to reach moved code (`rank.*`, `CrossPair`, `CrossReport`, `CrossCsv`,
  `SonarToGitlabCsv`, `SonarToGitlabReport`); VuesCliTest's long fixture method builds the same
  rows from compact strings. No asserted value changed.

## ProjectMapper

- `Summary` and `NameStats` had more than 8 components (RecordComponentNumber 8): each got a
  `@JsonUnwrapped` sub-record (`Guards`, `Vocabulary`) so the JSON report is byte-identical.
- The namespace-mapping warning still prints once per JVM at class load (static initialiser in
  `Keys`, triggered from `ProjectMapper`), as before. `NamespaceMapping.load(file, err)` is now a
  seam, but the default table is still static.
- Two dead private methods and the reverse namespace map they used were removed.
- `mappedSonarKey` takes `@Nullable` and calls `requireNonNull` first, to keep the NPE the tests
  pin (see suspected bugs).
- `KeyMethod.parse` / `NameSpec.parse` throw `IllegalArgumentException`; the command turns it into
  the same picocli `ParameterException` as before.
- `String.split` (Error Prone StringSplitter) replaced by precompiled `Pattern.split`;
  `linkedPaths` uses `split(…, -1)`, same result since empty entries never yield a path.
- Moved tests: `KeysTest`, `SpecTest`, `NamespaceMappingTest` (now against `load(file, err)`
  instead of the global table) live in `cli/mapper/`.

## Sonar, SonarAuditCheck

- **Narrowed catch-alls, a small behaviour change at the edges.** `Transport.send` catches
  `IOException | IllegalArgumentException` instead of `Exception` (PMD
  AvoidCatchingGenericException): any other runtime exception from the HTTP client now escapes
  instead of becoming "status 0". Same pattern in `GitlabHttp`. `parseDate` catches
  `DateTimeException`, which is everything those parses throw.
- The `safely` catch-all around probe hints was removed; the hints handle missing fields
  themselves. The one pinned case (`"scm": [null]` → empty description) is reproduced explicitly
  in `ScmResponse.describe()`.
- Two multi-line messages became text blocks with `.stripTrailing()`: Error Prone flags the old
  `\s` trick (MisleadingEscapedSpace) and the plain concatenation (StringConcatToTextBlock).
- `SonarAuditCheck` options are split into three picocli `@Mixin` groups declared in the same
  order, so `--help` is unchanged (snapshot).
- Tests: the stderr-capture test passes its buffer as the warnings stream instead of swapping
  `System.err`; helpers tests moved to `cli/sonaraudit/` and split for the coupling limit.
