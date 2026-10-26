# Architecture

Read-only audit tools for SonarQube, GitLab, Jira and GoCD. Each tool is a picocli command,
started by a one-line JBang script at the root. This file describes the structure and the rules
that hold it in place: `src/test/java/ch/ge/edg/sonar/gitlab/audit/architecture/`, ArchUnit. When
this file and the rules disagree, the rules decide.

## Layers

```
 cli.<command>    one package per tool: the @Command class, its options, its logic and output
      │
 cli.shared       picocli option groups and converters several commands use (nothing else)
      │
 domain           matching (Sonar ↔ GitLab pairing, with .similarity and .report),
                  exclusions, dora (incl. the commits a GoCD run deployed)
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

## Commands

| Package | Root script | Holds |
|---|---|---|
| `cli.sonaraudit` | SonarAuditCheck | Sonar inventory, history, trends |
| `cli.activity` | GitlabActivityAudit | GitLab funnel: inventory → activity → selection → practices |
| `cli.projectreport` | GitLabProjectReport | One project up close |
| `cli.rank` | SonarRank | Ranking of the Sonar inventory |
| `cli.mapper` | ProjectMapper | The matcher on its own |
| `cli.cross` | CrossAudit | Crossing Sonar and GitLab, including the SonarToGitlab wide pass |
| `cli.vues` | Vues | Views over the CSVs |
| `cli.doralocate` | dora/DoraLocate | Where the DORA data lives: finds the Jira and GoCD sources of the projects |
| `cli.doraproject` | dora/DoraProject | The four DORA measures of one project: a page, and an import for Middleware HQ |
| `cli.gocdextract` | dora/GocdExtract | GoCD pipeline runs extracted for the DORA measures |
| `cli.jiraextract` | dora/JiraExtract | Jira issues and their transitions extracted for the DORA measures |

In a command package only the `@Command` class is public.

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
