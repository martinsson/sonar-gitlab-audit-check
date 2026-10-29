# Sonar feedback for agents: survey (abandoned)

Status: **abandoned** (2026-10-30). Nothing was implemented. This records what was looked at, in
case the idea comes back.

## The idea

Give the agent the feedback SonarLint gave us in the IDE: when files change and the project is
connected to a SonarQube server, check them against that server's rules and quality profile, and
give the violations back to the agent.

## Design sketched

Stages by severity:

| Sonar severity            | Pre-commit       | Pre-push (`./verify`) | Not gated |
|---------------------------|------------------|-----------------------|-----------|
| Blocker / Critical (High) | **no new ones**  | no new ones           |           |
| Major (Medium)            | reported         | **no new ones**       |           |
| Minor / Info (Low)        | reported         | reported              | yes       |

- Rule: no file gets worse, and changed lines stay clean ("Clean as You Code"). Don't make the
  agent clean whole files: unbounded diffs, cleanup mixed with features, harder reviews.
- How to check it: analyse the previous version (`git show HEAD:file`) and the staged version
  (`git show :file`) locally, with the same engine and rules. A count per (file, rule, severity)
  must not grow, like the existing ratchet. `git diff -U0` ranges only make the message better
  ("introduced here"). Don't gate on them alone: deleting the last caller raises "unused method"
  on a line nobody touched. The server provides the rules, not the baseline. Its analyzer version
  never quite matches the working copy, so comparing against it gives false "new" issues.
- Some Java rules need compiled classes, so pre-commit (no compile) sees fewer rules than
  pre-push.
- Server unavailable: use a cached quality profile (outside the repo) and print a warning; with
  no cache, skip with a loud warning and never block.
- CI runs outside the company network and cannot reach Sonar, so pre-push is the last place this
  is enforced. A `--no-verify` push gets through, and auto-merge on green would merge it.
- Overlap with Checkstyle/PMD: keep it. They run everywhere, CI included, and are the fallback.
  Turn off duplicate local rules only once a Sonar gate has proven reliable.

## What could run locally (state in October 2026)

1. **SonarQube CLI (`sonar`)**, SonarSource's tool for agents. `sonar analyze` (`--staged`,
   `--file`, `--base <ref>`, `--format json`). Exit codes: 0 = no new issues, 51 = new issues,
   1 = analysis failed, 2 = invalid flags. `sonar integrate claude` installs `PostToolUse` hooks and
   the MCP server. The code analysis part ("Agentic Analysis" / "Vortex") needs SonarQube Cloud on
   a Team or Enterprise plan, or SonarQube Server 2026.5+ in Enterprise or Data Center edition,
   plus a paid subscription. It also uses context from a previous CI scan. Without that, only
   secrets scanning. The docs disagree in places about server support, and the CLI is marked beta.
2. **SonarQube MCP server.** `analyze_file_list` sends the files to a running SonarQube for IDE
   instance: close to the IDE experience, but it needs an IDE open, so it doesn't work headless or
   in a git hook. `analyze_code_snippet` works with a self-hosted server and a token, and applies
   the project's quality profile when given the project key. But it is deprecated, and less
   reliable without the full file context.
3. **`sonar-scanner` / `mvn sonar:sonar`, then read the issues from the API.** Works with any
   edition, but it's a full scan that takes minutes and creates an analysis on the server. Branch
   analysis needs Developer edition or higher. Only realistic at pre-push.
4. **Run sonarlint-core / the SonarLint language server headless.** The real engine behind the IDE
   plugins, but no supported command-line tool for it (`sonarlint-cli` died around 2017). Wrapping
   it ourselves would be a project of its own.

The choice depended on the server's version and edition (`/api/server/version`, and the
`edition` field of `/api/navigation/global`). That was never checked.

## Sources

- https://docs.sonarsource.com/sonarqube-cli/analysis/analyzing-local-changes
- https://docs.sonarsource.com/sonarqube-developer-tools/sonarqube-cli/using-sonarqube-cli/commands
- https://www.sonarsource.com/blog/sonarqube-cli-brings-multilayered-verification-to-agentic-development/
- https://docs.sonarsource.com/sonarqube-mcp-server/reference/tools
- https://community.sonarsource.com/t/mcp-analysis-always-returns-0-issues/172497
