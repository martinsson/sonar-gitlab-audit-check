# Access probes: Jira and GoCD

Two throwaway prototypes that answer one question before any DORA work: **with
the credentials we have, what can we read?** Every call is a GET. They are
standalone JBang scripts, outside the Maven build and its quality gates.

By default the report masks project, pipeline and environment names
(`pipeline#1`, `projet#2`) and prints only counts, HTTP codes and
configuration names (issue types, field names, statuses). That makes it safe
to bring back out of the client network. `--show-names` prints the real names;
`--dump-dir` keeps every raw response. Both are for local reading only:
never commit or share them.

## Jira (Server / Data Center, PAT)

```bash
export JIRA_URL=https://jira.example.com JIRA_TOKEN=xxxxxxxx   # profile > Personal Access Tokens
export GITLAB_URL=https://gitlab.example.com                    # optional: recognises GitLab links
jbang probes/JiraProbe.java > jira-probe.txt
```

It checks the PAT on `/myself` (a rejected token shows up as
`X-AUSERNAME: anonymous`, and a CAPTCHA lock as `X-Seraph-LoginReason`), then
reports:

- visible projects;
- issue types, priorities, statuses and resolutions;
- custom fields whose name suggests platform, severity, release or a git link;
- JQL totals per issue type over `--days` (365 by default);
- per-project defect share and defect resolution time, from up to `--max-issues` issues;
- for `--sample` recent issues: changelog, filled fields, and GitLab traces in
  comments, remote links and the development panel;
- versions and components of the most active project;
- agile boards.

`JIRA_USER`/`JIRA_PASSWORD` act as a Basic-auth fallback if the PAT is refused.

## GoCD (several ways in)

```bash
export GOCD_URL=https://gocd.example.com
export GOCD_TOKEN=xxxxxxxx                      # user menu > Personal Access Tokens, if offered
export GOCD_USER=jdoe GOCD_PASSWORD=xxxxxxxx    # Basic auth, works with LDAP / password-file login
export GOCD_COOKIE=xxxxxxxx                     # JSESSIONID copied from the browser's dev tools
export GITLAB_URL=https://gitlab.example.com
jbang probes/GocdProbe.java > gocd-probe.txt
```

Set whichever you have. The probe always tries anonymous access first, then each
mode with a credential (`--auth none,token,basic,cookie` forces the list), and
digs further with the one that sees the most pipelines. It reports:

- the dashboard: groups, pipelines, pipelines-as-code, and platform words in
  group, pipeline and stage names;
- which admin APIs answer;
- for `--samples` pipelines (one prod-like, one staging-like and the most
  recent, or `--pipeline NAME`): 12 months of run history, outcomes,
  manual vs automatic triggers, material types, and git repos on the GitLab
  host;
- commit → run lead time, read directly or by walking upstream pipelines;
- job durations and the value stream map;
- the cctray and Atom feeds.

### Checked against a real GoCD 25.3.0 (Docker, password-file auth)

- `Accept: application/vnd.go.cd+json` (unversioned) is accepted by every API
  used. The probe still falls back to numbered versions for older servers.
- Token, Basic and session cookie all work on the REST APIs. A bad token gets a
  401 with `WWW-Authenticate: Basic`.
- Once security is on, a pipeline group with no view permission is invisible to
  non-admins. The dashboard is the list of what the account may see, not of what
  exists.
- `admin/environments` and `admin/config_repos` answer **200 with an empty
  list** to a non-admin. A zero there proves nothing. `admin/pipeline_groups`,
  `admin/templates` and `admin/pipelines/<name>` answer 403.
- A non-admin with view permission still gets the git URL of each material
  from the run history (`material.description` = `URL: …, Branch: …`). The
  pipeline → GitLab link needs no admin right.
- A deploy pipeline usually depends on a build pipeline, not on git: its
  material revision reads `build-pipeline/12/stage/1`. Reading that instance
  (`/go/api/pipelines/<name>/<counter>`) gives the commits, so lead time is
  computable through the API alone.
- The value stream map (`/go/pipelines/value_stream_map/<p>/<n>.json`) is an
  internal page: with a token it redirects (302) to the login page; it only
  works with a session cookie.
- `cctray.xml` came back empty for a non-admin who could see the pipelines;
  don't rely on it.

The Jira probe has only been run against a fake server (it catches crashes, not
API semantics); its first real run is the one inside the client network.
