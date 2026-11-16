#!/usr/bin/env bash
# Builds the weekly report for GitLab Pages: runs the usual chain (SonarAuditCheck, GitlabActivityAudit
# --deep, CrossAudit, Vues), with no LLM involved, and writes a static site: the viz/ pages, the CSVs
# in data/, and data/manifest.json, which the pages read when they open.
#
# The CI job `pages` (ci/audit-pages.yml) runs it; it runs the same way locally:
#   SONAR_URL=... SONAR_TOKEN=... GITLAB_URL=... GITLAB_TOKEN=... ci/build-pages.sh
#   python3 -m http.server -d public   # then http://localhost:8000 (file:// cannot fetch the CSVs)
#
# Optional variables (TOOLS.md, "Weekly report on GitLab Pages"):
#   AUDIT_GROUP        GitLab group to audit (default: the whole instance)
#   AUDIT_SINCE        activity window in days (--since, default 90)
#   AUDIT_TOP          deep-pass budget (--top, default 200)
#   AUDIT_EXCLUSIONS   exclusions file, read on both sides (default: exclusions.txt, if present)
#   AUDIT_APPARIEMENT  appariement.csv to start from, with the hand-made pairs. Unset in CI: the
#                      one the last successful `pages` job published
#   AUDIT_SONAR_ARGS, AUDIT_GITLAB_ARGS  more options for either tool, split on spaces
#   AUDIT_CP           classpath of tools already built (skips the Maven build)
#   AUDIT_WORK         working directory (default target/pages-audit)
#   PUBLIC_DIR         the site (default public)
#
# Tokens come from the environment and are never printed. The output is client data: publish it
# only where Pages access is limited to the project's members, and never commit it.
set -euo pipefail
cd "$(dirname "$0")/.."

WORK=${AUDIT_WORK:-target/pages-audit}
PUBLIC=${PUBLIC_DIR:-public}
PKG=ch.ge.edg.sonar.gitlab.audit.cli

missing=""
for v in SONAR_URL SONAR_TOKEN GITLAB_URL GITLAB_TOKEN; do
  [ -n "${!v:-}" ] || missing="$missing $v"
done
if [ -n "$missing" ]; then
  echo "Missing variable(s):$missing (CI/CD variables, masked for the tokens)." >&2
  exit 2
fi

step() { printf '\n=== %s\n' "$1"; }

# The tools, built once with the Maven wrapper (TOOLS.md, "Running without JBang").
if [ -z "${AUDIT_CP:-}" ]; then
  step "Build"
  ./mvnw -q -B compile dependency:copy-dependencies -DoutputDirectory=target/libs -DincludeScope=runtime \
    -Dspotless.check.skip=true -Dcheckstyle.skip=true
  AUDIT_CP="target/classes:target/libs/*"
fi
# shellcheck source=.mvn/use-jdk.sh
JDK_ROOT=$PWD . .mvn/use-jdk.sh
JAVA=java
[ -n "${JAVA_HOME:-}" ] && JAVA="$JAVA_HOME/bin/java"
tool() {
  local main=$1
  shift
  "$JAVA" -cp "$AUDIT_CP" "$PKG.$main" "$@"
}

rm -rf "$WORK" "$PUBLIC"
mkdir -p "$WORK" "$PUBLIC/data"

exclusions=()
if [ -n "${AUDIT_EXCLUSIONS:-}" ]; then
  [ -f "$AUDIT_EXCLUSIONS" ] || {
    echo "AUDIT_EXCLUSIONS must be a file (a CI/CD variable of type File)." >&2
    exit 2
  }
  exclusions=(--exclusions "$AUDIT_EXCLUSIONS")
fi

# The hand-made pairs survive from one run to the next through appariement.csv: the file given, or
# else the one the last successful `pages` job published (the job token may read this project's
# artifacts; the path is the one in the artifact, whatever PUBLIC_DIR is here). Without either,
# CrossAudit starts a new one.
appariement=$WORK/appariement.csv
if [ -n "${AUDIT_APPARIEMENT:-}" ]; then
  [ -f "$AUDIT_APPARIEMENT" ] || {
    echo "AUDIT_APPARIEMENT must be a file (a CI/CD variable of type File)." >&2
    exit 2
  }
  cp "$AUDIT_APPARIEMENT" "$appariement"
  echo "appariement.csv: from AUDIT_APPARIEMENT"
elif [ -n "${CI_JOB_TOKEN:-}" ] && [ -n "${CI_API_V4_URL:-}" ]; then
  if curl -sSf -o "$appariement" -H "JOB-TOKEN: $CI_JOB_TOKEN" \
    "$CI_API_V4_URL/projects/$CI_PROJECT_ID/jobs/artifacts/$CI_COMMIT_REF_NAME/raw/public/data/appariement.csv?job=${CI_JOB_NAME:-pages}"; then
    echo "appariement.csv: from the last successful ${CI_JOB_NAME:-pages} job"
  else
    rm -f "$appariement"
    echo "appariement.csv: none from a previous run, CrossAudit starts one"
  fi
fi

# Unquoted on purpose: extra options are split on spaces.
read -r -a sonar_args <<<"${AUDIT_SONAR_ARGS:-}"
read -r -a gitlab_args <<<"${AUDIT_GITLAB_ARGS:-}"
[ -n "${AUDIT_GROUP:-}" ] && gitlab_args+=(--group "$AUDIT_GROUP")
[ -n "${AUDIT_SINCE:-}" ] && gitlab_args+=(--since "$AUDIT_SINCE")
[ -n "${AUDIT_TOP:-}" ] && gitlab_args+=(--top "$AUDIT_TOP")

step "1. Sonar inventory"
tool sonaraudit.SonarAuditCheck --csv "$WORK/sonar-inventaire.csv" \
  "${exclusions[@]+"${exclusions[@]}"}" "${sonar_args[@]+"${sonar_args[@]}"}"

step "2. GitLab activity and practices"
tool activity.GitlabActivityAudit --deep --out-dir "$WORK" \
  "${exclusions[@]+"${exclusions[@]}"}" "${gitlab_args[@]+"${gitlab_args[@]}"}"

step "3. Crossing"
tool cross.CrossAudit --sonar "$WORK/sonar-inventaire.csv" --gitlab "$WORK/pratiques.csv" \
  --appariement "$appariement" --out "$WORK/croisement.csv"

step "4. Views"
tool vues.Vues --in "$WORK/croisement.csv"

step "Site: $PUBLIC"
cp -R viz/. "$PUBLIC/"

# The pages load `files` (recognised by their columns, as when dropped); `extras` are links only:
# the views have the same columns as croisement.csv and would replace it.
json_list() {
  local sep="" f out=""
  for f in "$@"; do
    [ -f "$WORK/$f" ] || continue
    cp "$WORK/$f" "$PUBLIC/data/$f"
    out="$out$sep\"$f\""
    sep=", "
  done
  printf '[%s]' "$out"
}
json_string() { printf '"%s"' "$(printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g')"; }

files=$(json_list sonar-inventaire.csv sonar-inventaire-historique.csv inventaire.csv pratiques.csv \
  croisement.csv sonar-gitlab.csv appariement.csv)
extras=$(json_list croisement-essentiel.csv croisement-derive.csv croisement-non-apparies.csv)
cat >"$PUBLIC/data/manifest.json" <<EOF
{
  "generated": $(json_string "$(date -u +%Y-%m-%dT%H:%M:%SZ)"),
  "pipeline": $(json_string "${CI_PIPELINE_URL:-}"),
  "sonar_url": $(json_string "$SONAR_URL"),
  "gitlab_url": $(json_string "$GITLAB_URL"),
  "group": $(json_string "${AUDIT_GROUP:-}"),
  "files": $files,
  "extras": $extras
}
EOF
cat "$PUBLIC/data/manifest.json"
echo "SITE READY: $PUBLIC/index.html"
