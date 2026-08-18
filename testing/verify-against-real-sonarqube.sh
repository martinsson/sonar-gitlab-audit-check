#!/usr/bin/env bash
#
# Monte une vraie instance SonarQube, y fabrique un périmètre d'audit connu,
# puis lance SonarAuditCheck dessus.
#
# Pourquoi une vraie instance et pas un mock : un mock ne fait que rejouer les
# hypothèses de celui qui l'a écrit. S'il est dérivé du README, il confirme le
# README au lieu de le tester. Les trois constats ci-dessous n'ont été trouvés
# qu'en interrogeant une instance réelle — un mock les aurait tous manqués :
#
#   * un projet analysé SANS rapport de couverture obtient quand même
#     coverage=0.0 (dérivé de lines_to_cover), il n'est pas absent ;
#   * api/sources/scm renvoie 200 avec des auteurs vides quand le scanner
#     tourne sans métadonnées SCM — le cas 'fetch-depth: 1' ;
#   * api/sources/scm ne renvoie pas une entrée par ligne, mais une par
#     changeset : 3 entrées (lignes 1, 13, 21) pour un fichier de 30 lignes.
#
# Vérifié sur SonarQube 26.8.0.126808.
#
# Usage :  ./testing/verify-against-real-sonarqube.sh
# Prérequis : docker, java 21+, maven, et les dépendances JBang du script.

set -euo pipefail

PORT="${PORT:-9500}"
S="http://127.0.0.1:${PORT}"
ADMIN_PW="${ADMIN_PW:-Audit_Test_2026!}"
IMAGE="${IMAGE:-sonarqube:community}"   # mirror.gcr.io/library/sonarqube:community si Docker Hub est bloqué
WORK="$(mktemp -d)"
export no_proxy=127.0.0.1,localhost NO_PROXY=127.0.0.1,localhost

say() { printf '\n\033[1m== %s\033[0m\n' "$1"; }

say "1/6  Démarrage de SonarQube ($IMAGE)"
# Elasticsearch exige vm.max_map_count >= 262144 ; nécessite un conteneur privilégié.
sysctl -w vm.max_map_count=262144 >/dev/null 2>&1 || \
  echo "  (vm.max_map_count non modifiable — SonarQube peut refuser de démarrer)"
docker rm -f sq >/dev/null 2>&1 || true
docker run -d --name sq -p "${PORT}:9000" \
  -e SONAR_ES_BOOTSTRAP_CHECKS_DISABLE=true "$IMAGE" >/dev/null
echo -n "  attente du statut UP "
until curl -s "$S/api/system/status" 2>/dev/null | grep -q '"status":"UP"'; do
  docker ps --format '{{.Names}}' | grep -q '^sq$' || { echo; docker logs sq | tail -20; exit 1; }
  echo -n "."; sleep 10
done
echo " OK"
curl -s "$S/api/system/status"; echo

say "2/6  Mot de passe admin"
curl -s -u admin:admin -X POST "$S/api/users/change_password" \
  -d "login=admin&previousPassword=admin&password=${ADMIN_PW}" -o /dev/null
A=(-u "admin:${ADMIN_PW}")

say "3/6  Six projets, dont deux hors périmètre"
for i in 1 2 3 4 5 6; do
  curl -s "${A[@]}" -X POST "$S/api/projects/create" \
    -d "name=Proj$i&project=com.acme:proj$i" -o /dev/null
done

say "4/6  Compte d'audit restreint (Browse sur proj1..proj4 uniquement)"
curl -s "${A[@]}" -X POST "$S/api/users/create" \
  -d "login=auditor&name=Audit+Bot&password=Auditor_2026_pw!" -o /dev/null
TOKEN=$(curl -s "${A[@]}" -X POST "$S/api/user_tokens/generate" \
  -d "login=auditor&name=audit-run" | sed 's/.*"token":"\([^"]*\)".*/\1/')
# Les projets naissent publics : les passer en privé matérialise la permission
# Browse du groupe sonar-users, qui devient alors révocable.
for i in 1 2 3 4 5 6; do
  curl -s "${A[@]}" -X POST "$S/api/projects/update_visibility" \
    -d "project=com.acme:proj$i&visibility=private" -o /dev/null
done
for i in 5 6; do
  curl -s "${A[@]}" -X POST "$S/api/permissions/remove_group" \
    -d "projectKey=com.acme:proj$i&groupName=sonar-users&permission=user" -o /dev/null
done
# 'Administer System' donne le total réel SANS donner la visibilité : c'est ce
# qui rend l'écart mesurable par un seul token.
curl -s "${A[@]}" -X POST "$S/api/permissions/add_user" \
  -d "login=auditor&permission=admin" -o /dev/null

VISIBLE=$(curl -s -H "Authorization: Bearer $TOKEN" \
  "$S/api/components/search_projects?ps=100" | grep -o '"total":[0-9]*' | head -1 | cut -d: -f2)
echo "  vérité terrain : $VISIBLE projets visibles / 6 réels -> écart attendu = 2"

say "5/6  Analyse réelle (produit des issues et une période de new code)"
mkdir -p "$WORK/src/main/java/demo"
cat > "$WORK/pom.xml" <<'EOF'
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId><artifactId>demo</artifactId><version>1.0</version>
  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
</project>
EOF
cat > "$WORK/src/main/java/demo/Messy.java" <<'EOF'
package demo;
import java.util.*;
public class Messy {
    public void smell() {
        String pwd = "hardcoded";
        List l = new ArrayList();
        for (int i = 0; i < 10; i++) { l.add(pwd + i); }
        System.out.println(l);
    }
}
EOF
( cd "$WORK" && mvn -q -B -Dsonar.host.url="$S" -Dsonar.token="$TOKEN" \
    -Dsonar.projectKey=com.acme:proj1 \
    verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar ) || \
  echo "  (analyse échouée — les mesures resteront vides)"
# Une 2e analyse avec une période de new code peuple les métriques new_*,
# qui n'ont PAS de 'value' racine mais un objet 'period'.
curl -s "${A[@]}" -X POST "$S/api/new_code_periods/set" \
  -d "project=com.acme:proj1&type=PREVIOUS_VERSION" -o /dev/null
cat >> "$WORK/src/main/java/demo/Messy.java" <<'EOF'
class Added { public void more() { String t = "another-secret"; System.out.println(t); } }
EOF
( cd "$WORK" && mvn -q -B -Dsonar.host.url="$S" -Dsonar.token="$TOKEN" \
    -Dsonar.projectKey=com.acme:proj1 -Dsonar.projectVersion=1.1 \
    verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar ) || true

say "6/6  Exécution de SonarAuditCheck"
echo "  SONAR_URL=$S"
echo "  SONAR_TOKEN=$TOKEN"
echo
if command -v jbang >/dev/null 2>&1; then
  jbang "$(dirname "$0")/../SonarAuditCheck.java" --url "$S" --token "$TOKEN" \
    --csv "$WORK/inventory.csv" --dump-dir "$WORK/dumps"
  echo
  echo "CSV       : $WORK/inventory.csv"
  echo "Captures  : $WORK/dumps"
else
  echo "jbang absent. Lance manuellement :"
  echo "  jbang SonarAuditCheck.java --url $S --token $TOKEN"
fi

echo
echo "Attendu : 4 visibles / 6 réels / écart 2 (33%), et un blame SCM signalé"
echo "vide — l'analyse ci-dessus tourne sans dépôt git, donc sans métadonnées SCM."
echo "Nettoyage : docker rm -f sq"
