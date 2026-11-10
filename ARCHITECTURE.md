# Architecture

Read-only audit tools for SonarQube, GitLab, Jira and GoCD. Each tool is a picocli command,
started by a one-line JBang script at the root. This file describes the structure and the rules
that hold it in place: `src/test/java/ch/ge/edg/sonar/gitlab/audit/architecture/`, ArchUnit. When
this file and the rules disagree, the rules decide.

## Layers

```
 cli.<command>    one package per tool: the @Command class, its options, its logic and output (sub-packages for its stages)
      │
 cli.shared       picocli option groups and converters several commands use (nothing else)
      │
 domain           matching (Sonar ↔ GitLab pairing, with .similarity and .report),
                  exclusions, dora (incl. the commits a GoCD run deployed),
                  contributors (bots, one person across identities, merge commits)
      │
 exchange         the contract of the CSV files one command writes and another reads
      │
 clients          gitlab, sonar, jira, gocd: one per external system
      │
 infrastructure   rest (HTTP, TLS, retries, capture/replay), csv, console, text, parallel, runtime
```

A package depends only on layers below it. Within a layer, command packages don't depend on each
other, domain packages don't, and clients don't. What two commands share moves down: option
groups to `cli.shared`, logic to a domain package. Two commands may share one domain when they
really work on the same concepts. ProjectMapper (the generic matcher) and CrossAudit (the Sonar ↔
GitLab preset plus the wide pass) share `matching`.

The GitLab path a git URL designates (`gitlab.RepoPath`) is one function, in the clients layer:
`matching` (`Keys.linkedPaths`) and the dora commands both read URLs through it, so only the GitLab
client knows GitLab's URL forms. What is not DORA lives in infrastructure: `text.Times` (dates), `csv.Jsonl` (JSON lines). Reading a
date out of a JSON node is the client's job, next to the response (`Gitlab.instant`,
`JiraApi.instant`).

## Commands

| Package | Root script | Holds |
|---|---|---|
| `cli.sonaraudit` | SonarAuditCheck | Sonar inventory, history, trends |
| `cli.activity` | GitlabActivityAudit | GitLab funnel, one sub-package per stage: `model` (shared records), `inventory`, `commits`, `selection`, `preflight`, `practices`, `report`. The root holds the command and the stage order |
| `cli.projectreport` | GitLabProjectReport | One project up close |
| `cli.rank` | SonarRank | Ranking of the Sonar inventory |
| `cli.mapper` | ProjectMapper | The matcher on its own |
| `cli.cross` | CrossAudit | Crossing Sonar and GitLab, including the SonarToGitlab wide pass |
| `cli.vues` | Vues | Views over the CSVs |
| `cli.doralocate` | dora/DoraLocate | Where the DORA data lives: finds the Jira and GoCD sources of the projects |
| `cli.doraproject` | dora/DoraProject | The four DORA measures of one project: a page, and an import for Middleware HQ |
| `cli.gocdextract` | dora/GocdExtract | GoCD pipeline runs extracted for the DORA measures |
| `cli.jiraextract` | dora/JiraExtract | Jira issues and their transitions extracted for the DORA measures |

A command is its package and that package's sub-packages (`cli.activity`, `cli.activity.model`, ...).
Only its `@Command` class is used from outside it. Inside, a public class is used by another package
of the same command: a sub-package's public class exists because a sibling package needs it, and
anything else stays package-private (`VisibilityTest`).

## The outside world comes in at the edge

`runtime.Host.system()` is the only place that reads `System.out`/`err`, the environment, the
system clock and zone, the console and the working directory (`Host`, not `Runtime`: that name is
`java.lang.Runtime`'s). A command's `main` builds the `Host` and its `call()` hands each piece on
separately: a `Console` (streams, colour, locale) to printers, a `Clock` to whatever needs the
time, a `Sleeper` to the HTTP clients. Nothing below a command sees `Host`.
Tests build it with fixed values.

- No static state other than constants. No I/O in static initialisers.
- One time zone per run, the clock's. No `now()` without a clock, no sleeping outside the
  sleeper.
- Default files (`exclusions.txt`, `namespace-mapping.yaml`) are option defaults, resolved
  against the working directory at the edge.
- Numbers and dates are formatted with an explicit locale, only in `console`, `text` and in the CSV
  column formatters. Elsewhere a line is built with `Text.format` and printed with
  `Console.printf`, both on `Locale.ROOT` (FormattingTest).
- Clients don't print. Progress goes through a listener.

## HTTP and errors

`rest` is the only package that opens connections. It owns TLS, authentication headers, retries
and capture/replay. A capture keeps the status and body under a name that is stable across runs.

There is one response type in `rest`. An unreachable server and an unreadable body are states of
the response, not exceptions: the response says whether it got an answer, its status, and whether
the body could be read. An expected status (403, 404) is data in the same way. The clients read
the response and decide what a state means for their data; nothing in `rest` throws for the
outcome of a call. The "one project failing must not stop the audit" barrier therefore works on
values, through one shared helper, rather than by catching exceptions.

## Data

- A CSV file is a list of columns: a name plus a function from the domain value to a cell. The
  column names of files exchanged between commands live in `exchange`.
- A multi-stage pipeline (the activity funnel) passes immutable stage results. Absence is a type
  (sealed variants), not a null or a zero. Counts are computed from the results, not kept in
  counters.
- Each stage computes, then a printer prints what it computed.
- Records group fields by meaning; their size is whatever the meaning gives. A parameter object
  names a domain concept, or it does not exist.
- Inside `matching`, only `matching.report` prints.

## Open questions for the owner

Decisions the code cannot make. Each is pinned by a test marked `@SuspectedBug` (or says why
not); answer one, then fix the code and remove the marker. Delete the entry when it is settled.

1. `--top N`: should the budget cap the whole selection? Today `--top 1` selects 3 (one per tranche),
   and the solo-author and witness slices go past `--top 2`.
2. Should the solo-author and witness slices respect the per-namespace cap?
3. ProjectMapper reuse totals: where does a manual row whose key vanished count? Today in none.
4. Should a manual pair withdraw a name suggestion of the same right row to another left row?
5. `namespace-mapping.yaml` / `--namespace-mapping` is loaded and warns when missing, but no matching
   method uses it: make it a key method, or delete the option and the file?
6. Activity funnel rules noticed but not pinned: unmeasurable projects still feed zeros into the
   floor's distribution; a control-sample project whose commit count is refused (non-200) counts as
   "no leak".
7. Design residues to look at in the next review: `matching.Replay` is an enum whose `stale` is
   nullable (absence is not a type there); `cli.activity.model` holds helpers (`Api`, `CellFormat`)
   because everything can see it, a possible grab bag; `HelpersTest` in `cli.sonaraudit` is one.

## Reporting a batch of work

There is no committed log of decisions. For a refactoring or quality batch, the lead agent keeps
the per-iteration notes in its session memory or scratchpad: decisions, compromises, behaviour
changes (with the snapshot that moved), gate changes (with the reason), reviewer disagreements,
rules noticed but not pinned. Sub-agents report to the lead in whatever form the lead asks for.
When the pull request is made, the lead puts those notes in the PR description, or in a comment or
attachment on the PR, for a human to read afterwards. A question that outlives the PR goes in the
section above; a rule that must hold goes in a test. History before this convention: the file
`ARCHITECTURE_LOG.md`, in git history before this change.
