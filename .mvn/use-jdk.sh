# Sourced by ./mvnw and ./verify (not run on its own; POSIX sh). Makes Maven run on the JDK the build
# targets: the pom's maven.compiler.release or newer. Plain `mvn` on a machine whose default JDK is
# older stops at "release version N not supported", so look for a JDK of the right version in the
# usual places and set JAVA_HOME to it. Order: JAVA_HOME, java on the PATH, macOS java_home, SDKMAN,
# JBang (it downloads one if it has none). Stops with a clear message when none is found.
#
# Not covered: mvnw.cmd (Windows cmd); on Windows run ./mvnw or ./verify from Git Bash, or put a
# JDK of that version first on the PATH.
# shellcheck shell=sh

_jdk_major() { # $1 = a java executable; prints its major version, or nothing
  [ -x "$1" ] || return 0
  "$1" -version 2>&1 | sed -n 's/^[a-z]* version "\([0-9]*\).*/\1/p' | head -1
}

_jdk_select() {
  _root=${JDK_ROOT:-$(cd "$(dirname "$0")" && pwd)}
  _release=$(sed -n 's:.*<maven.compiler.release>\([0-9]*\)</maven.compiler.release>.*:\1:p' "$_root/pom.xml")
  [ -n "$_release" ] || return 0

  if [ -n "${JAVA_HOME:-}" ]; then
    _have=$(_jdk_major "$JAVA_HOME/bin/java")
  else
    _have=$(_jdk_major "$(command -v java 2>/dev/null)")
  fi
  [ "${_have:-0}" -ge "$_release" ] && return 0

  # Each candidate is checked: macOS java_home answers with the default JDK when none matches.
  for _c in "$(/usr/libexec/java_home -v "$_release"+ 2>/dev/null)" \
    "$HOME"/.sdkman/candidates/java/"$_release"* "$HOME"/.jdks/*"$_release"* \
    "$(jbang jdk home "$_release" 2>/dev/null)"; do
    if [ "$(_jdk_major "$_c/bin/java")" -ge "$_release" ] 2>/dev/null; then
      JAVA_HOME=$_c
      export JAVA_HOME
      return 0
    fi
  done
  echo "This build needs a JDK $_release or newer (the pom's maven.compiler.release); found ${_have:-none}." >&2
  echo "Install one (brew install openjdk@$_release, sdk install java $_release-tem, jbang jdk install $_release)" >&2
  echo "or point JAVA_HOME at it, then run this again." >&2
  return 1
}

_jdk_select || exit 2
