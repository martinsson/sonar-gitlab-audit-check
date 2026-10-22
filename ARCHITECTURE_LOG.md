# Architecture refactoring log

Work driven by the review in REVIEW_VS_QUALITY_LOG.md (kept outside the repo) and summarised in
ARCHITECTURE.md: PRs #14–#26, October 2026. Like QUALITY_LOG.md, this records the decisions,
compromises, gate changes and reviewer disagreements made along the way, for later review.

## Open questions for the owner (still marked `@SuspectedBug`)

1. `--top N`: should the budget cap the whole selection? Today `--top 1` selects 3 (one per tranche),
   and the solo-author and witness slices go past `--top 2`.
2. Should the solo-author and witness slices respect the per-namespace cap?
3. ProjectMapper reuse totals: where does a manual row whose key vanished count?
4. Should a manual pair withdraw a name suggestion of the same right row to another left row?
5. `namespace-mapping.yaml` / `--namespace-mapping` is loaded and warns when missing, but no matching
   method uses it: make it a key method, or delete the option and the file?

## Not done, for a later session

- Re-cut `dora` (a grab-bag: `Jsonl` belongs next to `csv`, `Times` in `text`, `RepoPath` duplicates
  `matching.Keys.linkedPaths`).
- Contributor identity (`Gitlab.BOT_PATTERN`, `isBot`, `identity`, `isMerge`) into a domain package.
- Sub-packages for `cli.activity` (45 classes): needs `VisibilityTest` to change from "only the command is
  public" to "only its own slice references it".
- The five `Settings` records that bundle option groups for parameter limits; `projectreport.Sections`.
- `matching.Replay` returns an outcome per row (today it bumps counters), and named rule tests for
  `matching` next to its snapshots.
- One GitLab fake for the tests (`GitlabAuditFake`, `GitLabProjectReportFakeGitlab`).
- CodeScene hotspots: `CiConfig` 8.28, `RestClient.pauseBefore` 8.41.
- `Gitlab.MAPPER` / `Sonar.MAPPER`: `ObjectReader`/`ObjectWriter` constants instead of a rule exemption.
- Checkstyle class-coupling shaped some splits (`PreflightReport`, split test classes): judge them as gate
  compromises.


## Timeline
- 2026-10-20 ~21:30 start. Survey of origin/main 7e5f5d4 (new rest/jira/gocd/dora by the parallel author).
- Design v1 → 3 reviewers (architect opus, clean coder opus, CodeScene view sonnet), all "accept with changes".
- v2 → architect + clean coder round 2: accept with small changes (exchange layer, no field cap, param-object audit, AuditContext end condition, MappingFile split in it2, matching.report rule).
- Iteration 1 agent launched.

## Design review outcome (what changed because of reviewers)
- matching = all of cli.mapper (architect: nothing there is ProjectMapper-only; MappingFile/Evaluation reach every class).
- naming → matching.similarity; exclusions = domain (architect).
- exchange: own layer below domain (architect, round 2: matching.Spec already names exchanged columns).
- Runtime only at the edge, pieces passed separately (clean coder + architect); CodeScene view wanted Runtime grouped into fewer components — compatible.
- Funnel shape: sealed CommitMeasure/Fate, computed counts (clean coder).
- Disagreement resolved: CodeScene view proposed a 5-positional-field cap on sub-records; clean coder objected (limit-driven design is what produced the tricks). Taken: group by meaning, no cap.
- Dropped: Mockito ArchUnit rule (can't fail; enforcer keeps it off the classpath).
- Deviation from architect: dora not split now (other author's fresh code); proposal recorded for them.

## Verification policy (owner, 2026-10-20: fast iterations, verification <= ~3x implementation time, watch tokens)
- Local: compile per step, `./verify` (fast gates + tests + coverage, ~45 s) before each push. No local PIT, no local `--full`.
- CI: verify + verify-full (PIT, CPD, failsafe) run on the PR; auto-merge only when green. While CI runs, the next iteration starts on top (stacked branch); a red verify-full is fixed on the following branch or the PR is backed out.
- Compromise: a mutation-score drop is found ~15 min after the push instead of before; backtracking is by commit.
- Delegation: one implementation agent per iteration (opus for structural work, sonnet for mechanical renames), short reports; reviewer agents only at design time and at the end (not every iteration).

## Iteration 1 (PR #14, ~17 min agent + ~10 min integration)
- Pure moves, all .ss renames only. 7 command slices + 3 new ones from the parallel author (jiraextract, gocdextract, doralocate) moved under the same layout during the merge of main.
- ArchUnit 1.4.1 → 1.4.2: 1.4.1 can't read Java 26 class files and silently imports nothing (every rule would pass vacuously). Worth knowing: an ArchUnit rule that passes may check nothing; the agent verified the cycle and visibility rules fail when broken.
- Scoped exceptions: NO_PACKAGE_CYCLES ignores MappingFile → matching.report (removed in iteration 2); and ignores the shared `testing` package both ways (test wiring: GoCD/Jira fakes build real clients and are used by those clients' tests).
- GocdFixtures.gocd() built a slice-internal class, so it moved back into the slice's tests (GocdOnFake).
- Friction: the parallel author's new commands landed on main in the old layout while iteration 1 ran; the rules caught it at merge time (28 public internals). That is the rules doing their job.
- PR #14 was "conflicting" when opened (main moved: fix-linux-ci), so the auto-merge workflow (on: opened) never fired and no checks ran; after merging main into arch-1 (clean, in a second worktree since the it2 agent uses the first) I enabled auto-merge by hand (`gh pr merge 14 --auto --merge`), as the workflow would have.
- Parallel session changed CI: verify-full (PIT) now runs nightly / on demand, not per PR. So mutation drops from this work show up the next night, not before merge. Compromise accepted by the owner's "mutation testing later" guidance; I'll run PIT once on demand before the final report.

## Iteration 2

### Behaviour changes
- One zone per run, the clock's (`Clock.system(ZoneId.systemDefault())` in production):
  - sonaraudit: unchanged in production (it already used the system zone); now through the clock.
  - activity: the window start and the `--active-within` cut used `OffsetDateTime.now(UTC)`; they
    now use the clock's zone. Same instant, different offset in what is sent as `since`/compared.
    No snapshot changed (tests run on a UTC clock, as before).
  - projectreport, gocdextract, jiraextract, doralocate: their separate `ZoneId.systemDefault()` /
    `Clock.systemUTC()` pair became the one clock and its zone. Unchanged in production.
  - `now` in activity is read once at the start of the run (was re-read at the commit stage).
- GitlabActivityAuditSonarKeyTest.ss (everyWayToFindTheSonarKey/stdout): the stderr section with
  the JDK's `[Fatal Error] :1:25: ...` line is gone. The XML parser of Pom printed it straight on
  System.err; it now has a silent error handler (an unreadable pom still reads as "no pom").
- CrossAuditOptionsTest.ss and ProjectMapperOptionsCliTest.ss (help): new option
  `--namespace-mapping=<yaml>` (default namespace-mapping.yaml in the working directory), asked
  for by the iteration. The warning text when it is missing is unchanged.
- Retry tests (GitlabClientTest, SonarClientTest) no longer sleep: they assert the pauses asked
  of the Sleeper.

### Gate config changes
- None in pom/checkstyle/PMD. .gitignore untouched: namespace-mapping.yaml stays ignored, the test
  working directory is made under target/ at run time.
- Checkstyle limits were met by design changes, not by raising them: CrossAudit's wide pass moved
  into `cli.cross.WidePass`, ProjectMapper's console lines into `cli.mapper.MapperReport`,
  `matching.report.MappingFilePrinter.mapAndKeep` (map, keep, say) shared by both commands.
- Error Prone's AvoidCommonTypeNames refused `runtime.Runtime` (clashes with java.lang.Runtime):
  the record is `runtime.Host`. ARCHITECTURE.md updated.

### Rules added (architecture/EdgeTest)
- THE_PROCESS_IS_READ_ONLY_IN_RUNTIME_AND_MAIN, TIME_AND_SLEEP_COME_FROM_RUNTIME,
  ONLY_COMMANDS_SEE_THE_HOST, ONLY_MATCHING_REPORT_PRINTS, STATIC_FIELDS_ARE_CONSTANTS,
  STATIC_INITIALISERS_DO_NO_IO (transitive through this code base's own calls).
- SlicesTest.NO_PACKAGE_CYCLES: the MappingFile → matching.report exception is removed.

### Rule exceptions and residues left
- STATIC_FIELDS_ARE_CONSTANTS allows Jackson's ObjectMapper by name (5 fields: Gitlab, Sonar,
  RestResponse, Jsonl, GocdIndex). Candidate fix: ObjectReader/ObjectWriter. No iteration owns it.
- picocli resolves `${env:GITLAB_TOKEN}`-style defaults with System.getenv itself: Host.env is
  not what option defaults read. Candidate: a default value provider fed from Host.env.
- `System.setProperty("jdk.internal.httpclient.disableHostnameVerification")` stays in
  GitlabHttp, sonar Transport and RestClient (not in the rule list).
- activity: AuditContext is gone. Gitlab, Screen (the activity printer over Console) and Counters
  are passed explicitly; `Funnel` (options, now, window start, bot pattern) is the immutable run
  value. Left for iteration 5: Counters is still mutable and threaded through the stages;
  `Funnel` carries the whole FunnelOptions (picocli-mutated fields) rather than each stage
  getting only the numbers it reads.
- The namespace mapping feeds no matching method (Keys.mappedGitlabPaths had no production
  caller, and none since its introduction): the commands load it at the edge only for its
  warning. Owner's call: wire it into MAPPED or delete it.
- Console carries no locale yet (planned "later").

## Lead notes after iteration 2 (PR #15)
- Iteration 2 cost 43 min, ~420k agent tokens, 246 tool calls: too slow for the owner's goal. Changed delegation: shared COMMON_RULES.md, explicit verify budget (≤4 `./verify` per agent), time target per agent, iterations 3 (opus, HTTP) and 4 (sonnet, options) in parallel worktrees off arch-2.
- Finding for the owner: the namespace mapping (namespace-mapping.yaml) is loaded but used by no matching method, and never was; it only produces a "file missing" warning. Decide: wire it in or delete it.
- Leftovers from iteration 2: picocli `${env:...}` defaults read the environment directly (not via Host); insecure-TLS System.setProperty in three clients (iteration 3 targets it); the static-field rule allows Jackson ObjectMapper by name.

## Iteration 4
- cli.shared: ColorOption, CommaOption, ExclusionsOption, GitlabConnectionOptions (activity + project report), StyleOptions (color+comma composite, to stay under Checkstyle coupling limits in CrossAudit/ProjectMapper/Vues/SonarRank). ProjectMapper's --left-key/--right-key/--exact/--derived/--link/--names moved to mapper.MethodOptions (CDAC/fan-out limit).
- Behaviour/help changes (snapshots overwritten, diffs read): param labels `<colorMode>` -> `<mode>`, `<exclusionsFile>` -> `<file>`; --exclusions text now "clés ou chemins" in both audits; --comma/--color positions in help of Cross/Mapper/SonarAudit follow their `order` (20/28). GitLabProjectReport now gets shared --url/--token/--timeout/--insecure/--dump-dir texts (its own "facultatif sur un projet public" token wording and "défaut : https://gitlab.com" url wording dropped; behaviour unchanged) and --dump-dir/--timeout/--insecure gained shared help/order.
- Gate config: pom Error Prone args += -XepOpt:NullAway:ExcludedFieldAnnotations=picocli.CommandLine.Option,Parameters,Mixin,Spec. Justification: picocli injects these fields after construction, so NullAway's "uninitialised field" check is a false positive; required options (--in, --left, --right, --sonar, --gitlab) are now non-null fields without requireNonNull; optional ones stay @Nullable.
- ArchUnit: SharedOptionsTest (cli.shared classes must be option groups with @Option/@Mixin fields, or ITypeConverter).
- Left as is: -h/-V in GitlabActivityAudit (mixinStandardHelpOptions has no order; options would jump to top of the sortOptions=false help, and mixin can't carry per-command order). Sonar --timeout/--insecure/--dump-dir (ScanOptions/OutputOptions) and Jira/Gocd/DoraLocate url/token/insecure left per-command (different env vars/names; Sonar/Jira/Gocd clients belong to the parallel agent's area). DoraLocate nullable url/token fields remain (optional, genuinely).
- Lead note on iteration 4 (PR #16, 5.5 min, sonnet): `StyleOptions` (colour + comma) exists partly to stay under Checkstyle's coupling limits on CrossAudit/ProjectMapper — a possible limit-driven grouping; it is at least cohesive (both are "how output looks"). For the final review to judge.

## Iteration 3
Branch arch-3, commit 2d5ea88. Goals 1, 2, 4 done; goal 3 scoped out (see below).

Behaviour changes (deliberate):
- Captures (all clients): names come from the request, `<slug>-<sha1 prefix of path+sorted query[+method+body]>.json`, plus index.txt. GitLab (`0001-api_v4_user.json`) and Jira/GoCD (`0001-200-....txt`) numbered dumps are gone. Sonar names unchanged, so old Sonar captures still replay.
- A non-200 status is written to `<name>.json.status` (deleted again when a later run gets 200). No status file = 200, so old body-only captures replay as before. A captured 403/404 now replays with its status: @SuspectedBug `aCaptured403ReplaysAsA200WithTheErrorBody` became `aCaptured403ReplaysWithItsStatus`, plus a test that a body-only capture replays as 200.
- SonarAuditCheckReplayTest: the replayed CSV now equals the live CSV exactly (the old 403->200 replacement is gone). The `replay` snapshot gains the line "dont 1 liaison(s) illisible(s) : lecture refusée, pas absente", the same as live.
- Only final answers are dumped (Jira/GoCD used to dump every attempt). Network failures are now dumped too (error text as the body, status 0), as Sonar already did.
- Retries are per-cause counters (Retries record): Jira/GoCD 2x429 + 2 network cuts (before: 3 attempts shared between the two); GitLab 3x429; Sonar 3x429 + 2x502/503/504 at 1 s then 2 s. An interrupted sleep now returns status 0 "interrompu" for Jira/GoCD too (before: the 429 was returned).
- Sonar with no token sends no Authorization header (before: "Bearer null"). In replay no HttpClient is built.
- RestClient constructor takes a Capture instead of PrintStream err; the unused `err` parameters of Gocd.connect, Access.jira/gocd, JiraSource.connect were removed.

Gate config: none changed. New rules: architecture/TransportTest — only rest uses java.net.http, javax.net.ssl, HttpURLConnection or URL.openConnection; only rest calls System.setProperty. The hostname-verification property stays: the JDK HttpClient has no other way to turn it off.
PMD GodClass on RestClient fixed by splitting out rest.Wire (the single HTTP exchange).

Not done, goal 3 (error model): there are no per-project barriers today. A network failure is a status-0 response that every caller already treats as "not ok", so there is nothing to narrow. To do it: make Wire throw SourceException(status?) for IO/URI failures and the replay misses, drop RestResponse.unreachable, Gitlab/Sonar Response.unreachable() and the status-0 checks in Preflight, Connectivity and Access.why, then add one rest.Barrier helper around the per-project work in sonaraudit, activity, doralocate and gocd/jira extract. That changes the "not a url" / closed-port test expectations and snapshots.
- Lead decision on iteration 3 (PR #17, 10 min): the planned `SourceException` + barrier helper was dropped. Every client already models a transport failure as a status-0 response ("a response is data"), and no per-project catch-all barrier remained to narrow. One consistent model already existed; throwing instead would have changed four commands for no gain. ARCHITECTURE.md now states the model actually used. This contradicts the round-1 reviewers' wording ("only transport/parse failures throw") but meets their intent (one model, no catch-alls). For the final review.

## Iteration 5a
- Tests only: FunnelRulesTest (30 tests in 6 nested groups), SelectionRulesTest (10), shared FunnelRulesTestBase (fixed clock 2030-06-15T12:00Z, rows read from inventaire.csv by path).
- No production change, no snapshot change, no gate config change, no new suppressions.
- Rules noticed but not pinned as rules (questionable): unmeasurable projects still feed zeros into the floor's distribution (ActivityFloor uses commitsOrZero on all non-excluded); a control-sample project whose commit count is refused (non-200) counts as "no leak" (CommitPager.count returns 0); the witness tranche ignores the namespace cap (already @SuspectedBug).
- Iteration 5a (PR #18, 6 min, opus): 40 named rule tests for the activity funnel. Three questionable rules left unpinned (agent's log): unmeasurable projects feed zeros into the floor's distribution; a refused commit count in the control sample counts as "no leak"; the witness tranche ignores the namespace cap (already @SuspectedBug).
- Iterations 5b (opus, funnel) and 5c (sonnet, exchange/param objects/text helpers/hotspots outside activity) run in parallel, both on arch-5a + arch-3.

## Iteration 5b
- Gate config: VisibilityTest ONLY_THE_COMMAND_IS_PUBLIC_IN_A_COMMAND_PACKAGE now flags a class only when it
  and every enclosing class are public. Justification: the variants of a package-private sealed interface
  (Fate, CommitMeasure) are public by the language yet unreachable from other packages, which is exactly what
  the rule's "because" protects. A public top-level class is still flagged.

## Iteration 5c
- Behaviour changes: none (snapshots untouched). RankReport.truncate kept its "..." behaviour as Text.ellipsize (SuspectedBug test kept, now on Text).
- Gate config: LayersTest Infrastructure layer gains `text..` (new package `text.Text`, the merged string/number helpers). Justification: matching must not depend on `console` (EdgeTest.ONLY_MATCHING_REPORT_PRINTS), and the helpers are pure, so a pure infrastructure package is the place. ARCHITECTURE.md updated (layer list, locale rule).
- New: `exchange` (SonarInventory, GitlabInventory, GitlabPractices, Crossing, Mapping); matching.Spec.sonarGitlab uses it. Producer->consumer tests: cli/sonaraudit/InventoryContractTest, cli/cross/MappingContractTest, cli/vues/CrossingContractTest, cli/activity/ExchangeContractTest (NEW file in the activity test package; reads CsvFiles.INVENTORY/PRATIQUES, will need adapting if the activity rewrite renames them).
- Guards(threshold, margin) now one top-level matching type (SonarToGitlab.NameThresholds removed). Rejections kept (names the hand-rejected pairs/paths).
- Rule exceptions left: none. Value types (goal 5): not added, nothing cheap enough.
- CodeScene before->after: Mapper 8.15->9.84, Evaluation 8.95->9.68, Csv 9.09->9.38, Findings 9.38->9.68, NameMatcher 9.38->9.68, Times 9.38->9.68.
- Lead note on 5c (12 min, sonnet): new records `Side` (NameMatcher), `Measured`, `Sections` (projectreport) exist to get under 5 parameters — possibly the "ceremony" the clean coder warned against; final review to judge. `text` package added to infrastructure (EdgeTest forbids matching → console).
- Behaviour changes: none; all snapshots and the 40 rule tests unchanged. One unobservable edge: the
  "datés par GraphQL" count now counts heads whose committedDate parsed (was: whose committedDate was text).
- New shared type: csv.Column<T>(name, cell) + Column.write; inventaire.csv and pratiques.csv are column lists
  (CsvFiles.inventory/practices). 5c can reuse it for the other packages.
- Exceptions left: CiFacts stays a mutable class (it is the Sonar-cache entry, built field by field by
  SonarProbe/SonarKeys and serialised by SonarCache); one field per line now. CiConfig keeps one volatile int
  (lintRefusedStatus): a run-wide memo shared by the parallel deep pass, not a counter. Preflight still prints
  as it checks (a checklist). Model unit tests split (FunnelModelTest, PracticesCsvTest) to stay under
  Checkstyle's ClassDataAbstractionCoupling.

## Iteration 7a
- Renames: activity Verdict->ProbeVerdict, Reviews->CodeReviewProbe, Fmt->CellFormat; projectreport Verdict->EndpointVerdict, Reviews->ReviewMeasures, Format->ReportFormat; sonaraudit Verdict->CallVerdict. Fields gl->gitlab, con->screen, c()->paint() (Screen, Report, RankReport), Evaluation/NameEvaluation r/s/ms->result/spec/matches, gocdextract Output w->writer.
- ExchangeContractTest was uncompilable on the base (CsvFiles.INVENTORY/PRATIQUES gone): now reads column names from CsvFiles.inventory/practices.
- Parallel.inline kept: a plain loop needs `catch (Exception)` (PMD AvoidCatchingGenericException) because Task throws Exception; FutureTask is the honest way to share the exception policy without a suppression. Not changed.
- SonarAuditCheckFake.historyOf -> setHistory (computeIfAbsent, no null assertion).
- Goal 4: no multi-variable field declarations remain (grep hits were parameters/record components/locals).
- Preflight split: Preflight.check() returns Result(probes, advice, instance); PreflightReport prints. ActivityAudit only references PreflightReport (CDAC limit). No output change.
- No behaviour, snapshot or gate-config changes.
- PR #20 failed CI: after merging 5c into 5b, ExchangeContractTest (5c) still read CsvFiles.INVENTORY/PRATIQUES (removed by 5b). My local ./verify was a false green: incremental compile kept stale classes. Same trap as during the quality-gates work. Policy change: `mvn -q clean` before the ./verify that precedes a push (+~20 s). CI caught it, as intended.

## Iteration 6
Suspected bugs (34): (a) fixed 25, (b) accepted 4, (c) owner 5.
(a) Keys.mappedSonarKey(null) -> empty; KeyMethod filter value trimmed; namespace-mapping: directory -> warning, keys upper-cased on load; Gitlab query with only null params -> no '?'; « Aucune MR » two lines one indent; « 1 réponse 403 », « 1 projet »; ci/lint message no trailing space; NoneInWindow: merges/reverts/active_days 0 (were blank); Sonar cadence = span / gaps; links probe ignores blank URLs; Values.parseDate reads +0200; pagination message « après 40 pages »; a rejected pair keeps its droite_key on rewrite (Choice.rejectedKey) so it stays rejected next run; « p0 → (manuel) » single space; Text.ellipsize <= max; rank: too-few-signals projects out of percentiles and quota; label « Actifs (≤ 90 j) »; Recoding replaces invalid UTF-8; 4 locale tests (French default locale now writes 1.5 / 1.50 / +18.0 / scores with a point).
(b) malformed namespace mapping (non-string value) fails loudly (documented on load); a commit's day is the run clock zone's; two dead homonym originals stay unpaired (DerivedMethods comment); a manual row whose key vanished is reported and rewritten with the fresh match.
(c) questions for the owner:
 1. --top N: should the budget cap the whole selection (today --top 1 selects 3, one per tranche, and the single-author and witness slices go past --top 2)?
 2. Should the single-author and witness slices respect the per-namespace cap (today 8 selected from one namespace capped at 3)?
 3. Reuse totals in ProjectMapper: where should a manual row whose key vanished count (today in none: totals add to 2 for 3 left rows)?
 4. Should a manual pair withdraw a name suggestion of the same right row to another left row (today both rows point at it)?
 5. namespace-mapping.yaml / --namespace-mapping is loaded (and warns when missing) but no matching method uses NamespaceMapping.mappedGitlabPaths: wire it in as a key method, or drop the option and the file?
Behaviour changes (snapshots overwritten deliberately, class by class): the lines above; plus rank percentiles/scores move in strata that had too-few-signals projects (SonarRankCliTest csv), rank table names cut at 41+... (44 max), CrossAuditMappingReuseTest second run: alpha-service stays rejected (sonar-gitlab « aucune », one more GitLab project without Sonar). All output formatting now Locale.ROOT (was default locale; tests already pinned ROOT, so no snapshot moved for it).
Formatting rule: FormattingTest (2 ArchUnit rules). Everywhere: no String.format(String,…), String.formatted, PrintStream/PrintWriter printf/format without Locale, DateTimeFormatter.ofPattern(String), NumberFormat.get*Instance() without Locale, new DecimalFormat, new Formatter without Locale. Outside console/csv/text: no String.format / printf / format at all -> Text.format(pattern, args) and Console.printf(pattern, args), both Locale.ROOT, both @FormatMethod. ~250 call sites rewritten mechanically.
Build: added com.google.errorprone:error_prone_annotations (provided, ${errorprone.version}) to the pom and a //DEPS line to the 10 JBang scripts that compile src/main: needed for @FormatMethod, since Error Prone's AnnotateFormatMethod (warnings are errors) flags a format pass-through; switching the check off was refused by the ratchet, rightly.
Also: ExchangeContractTest did not compile on the base (CsvFiles.INVENTORY/PRATIQUES gone in the arch-5b/5c merge); repaired against CsvFiles.inventory()/practices() column names.
Exceptions left: none new.
- Lead notes on iteration 6 (18 min, opus): 25 suspected bugs fixed, 4 accepted, 5 open questions for the owner. New dependency `error_prone_annotations` (provided) + `//DEPS` line in 10 JBang scripts, for @FormatMethod on Console.printf/Text.format; risk: the client network's Maven mirror must have it (it already serves Error Prone for the build, same group). Running 6 and 7a in parallel cost a 22-file conflict (7a's renames × 6's printf rerouting): parallelism on code that both touch broadly is a false economy; handed back to the iteration-6 agent to resolve.

## Iteration 7b
- 487 snake_case test methods renamed to camelCase (script, no collisions); .ss headers renamed, no content change; no stale keys.
- Input/output assertion blocks parameterized (SpecTest, KeysTest, GitlabActivityAuditHelpersTest, sonaraudit HelpersTest, ProjectReportUnitTest Text helpers). Grab-bag tests (SonarRankUnitTest.presentationHelpers, GitlabActivityAuditModelTest cache test, DeployedCommitsTest) are heterogeneous or stateful: left.
- Test placement: no test imports another cli slice's package; no exceptions. No gate config changes, no behaviour changes.

## Final checks
- verify-full dispatched on main (ae9710a) failed: PIT "109 tests did not pass without mutation". Cause: Selfie's JUnit listener throws on ArchUnit @ArchTest FieldSources; in PIT's single JVM that breaks later snapshot tests. Introduced by iteration 1 (first ArchUnit tests) and unseen for the whole run because verify-full moved to nightly (parallel session's CI change) and local PIT was skipped by policy. Fix PR #24: exclude architecture tests from PIT. Cost of the compromise: the mutation gate was effectively broken on main for ~5 hours of merges.
- Final CodeScene (main): line-weighted 9.84 (was 9.78), 10 files < 9.5, worst CiConfig 8.28 (untouched), RestClient 8.41 (new). Tests: mean 9.77, 7 files < 9, worst JiraExtractTest 8.03.
- Lead mistake: during iteration 3's integration one command ran in the owner's main checkout (cwd reset after a `cd` in an earlier call): a commit 6ca8361 (my ARCHITECTURE.md error-model text + the untracked REVIEW_VS_QUALITY_LOG.md) landed on the owner's local `main`, never pushed. Found by the final architect (the doc on origin still described SourceException). Undone with `git reset --mixed ed54a40` + checkout of ARCHITECTURE.md (only my own changes; status verified clean before). Since then every command starts with an explicit `cd <worktree> &&`.
- Final reviews (architect, clean coder): both "partly"; agreed top item = three response types with three meanings of ok(). Iteration 9a (opus): one response type, connections in clients, 401 rule once, duplicates. 9b (sonnet): rule leaks (constants invisible to ArchUnit, SharedOptionsTest too lax, TransportTest gaps, no empty-import guard), doc, cheap cleanups.
- Not planned (recorded for later): re-cut dora (grab-bag: Jsonl, Times, RepoPath ≈ Keys.linkedPaths), contributor identity out of Gitlab into a domain, activity sub-packages (needs VisibilityTest → "only own slice references it"), the five `Settings` bags, Replay returning outcomes + matching rule tests, merge the two GitLab fakes, CiConfig 8.28, RestClient.pauseBefore 8.41, ObjectMapper statics → ObjectReader/Writer constants.

## Iteration 9b
- Exchange column names are `Columns.named(...)` (non-constant), so javac no longer inlines them; activity CsvFiles uses them. No behaviour change.
- New rules: SharedOptionsTest (allowed dependencies + no I/O calls), TransportTest (+Socket, URLConnection, URL.openStream), ImportIsNotVacuousTest (>=150 classes, every layer package non-empty); LayersTest withOptionalLayers(false). No exceptions.
- Tests: Locale.setDefault removed except activity/FrenchLocaleTest (cross/rank FrenchLocaleTest and two duplicate French-locale tests deleted).
- ARCHITECTURE.md: command table completed, HTTP/errors section describes the single response type (states, not exceptions) written ahead of the parallel agent's work.
- No gate config change, no snapshot change.

## Iteration 9a
Behaviour changes (deliberate):
- One ok() = 2xx and not an HTML page. GitLab used to accept a 2xx HTML page as ok; Sonar used to
  require exactly 200 (its GETs never answer another 2xx, so only an SSO/login HTML 200 now reads as
  not-ok, which is the point). Call sites that need an exact status say `is(200)`.
- An unreachable instance now prints "injoignable" where "HTTP 0" used to appear: projectreport
  "Projet introuvable" line (test GitLabProjectReportEdgeTest updated), activity "ignoré" inventory
  lines, commit-walk Unmeasurable status, GitLab paging-interrupted warning, Sonar pagination failure
  and ALM-binding "refusé (HTTP 0)".
- Unified message(): a Sonar error body whose errors[] carry no msg now shows the body excerpt instead
  of an empty string; a Sonar 429 (after retries) shows "limite de débit atteinte (429)".
- Sonar query string with only null-valued params no longer appends a bare "?" (capture names unchanged).
- Jira/GoCD fixtures in tests now connect with a dummy token (fakes ignore auth).
No snapshot changed. No gate config changed. No suppressions added.
Exceptions left:
- CellFormat.orEmpty kept: CsvFiles (owned by the parallel agent) imports it; switch it to Text.orEmpty there.
- sonaraudit.Session.read still returns @Nullable (13 callers) via `as(type).orElse(null)`.
- doralocate.Access.why stays (one-line "reason or HTTP n" formatter for GitLab failures).
- Commands still import rest.RestResponse, rest.Query (params) and rest.Stop - none import RestClient/Capture/AuthHeaders.

## GoCD responses into records (2026-10-22)
Owner's request: map API responses to Java records with Jackson instead of walking JsonNode, starting
with GoCD (Jira done elsewhere). No OpenAPI/JSON Schema exists for GoCD: records are hand-written from
the docs and the 25.3 captures, and hold only the fields the tool reads (`gocd.Dashboard`, `Run`,
`Material`, `Stage`, `HistoryPage`; one reader in `gocd.GocdJson`: snake_case, unknown fields ignored,
absent/null text and lists read as empty, dates as epoch ms or ISO-8601). Generated code, for APIs that
have a schema, would go in a separate committed source folder, not under src/.
- `Materials` and `PipelineGroups` became methods of `Material` and `Dashboard`.
- `GocdApi` returns `Run`s and keeps each run's raw JSON in its cache (`raw()`), so gocd-runs.jsonl is
  still GoCD's full response, not the record.
Behaviour changes (deliberate):
- A field of the wrong shape (e.g. a text counter that is not a number) now stops with
  IllegalStateException "Réponse GoCD inattendue pour ..." instead of reading as 0/"": better stop than
  extract wrong numbers. Absent fields still read as empty, as before.
- A stage without counter now reads as 1 everywhere; gocd-stages.csv used to print an empty
  `compteur_stage` for it (GoCD always sends one: no capture changed).
- GoCD dates no longer accept the Jira format (`...SSS+0100`), which GoCD never writes.
Gate change: EdgeTest.STATIC_FIELDS_ARE_CONSTANTS lists ObjectReader as immutable (it is, by contract;
ObjectMapper, already listed, is not). Answers the "ObjectReader constants" item above for new code.
Not done: probes/GocdProbe.java (diagnostic JBang script, outside src/) still walks JsonNode.
