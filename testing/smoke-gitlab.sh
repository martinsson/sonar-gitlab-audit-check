#!/usr/bin/env bash
#
# Fait tourner GitlabActivityAudit contre le faux GitLab de testing/fake-gitlab.py.
#
# Ce que ce test prouve, et ce qu'il ne prouve pas. KNOWLEDGE.md §1 : un mock
# répond ce qu'on lui demande, donc il ne valide AUCUNE sémantique de l'API
# GitLab — ni le nom d'un paramètre, ni la présence de X-Total, ni la forme de
# Repository.tree.lastCommit. Tout cela reste à vérifier contre une instance
# réelle, et le script porte cette réserve dans sa sortie.
#
# Ce qu'il attrape, en revanche, et qu'il a effectivement attrapé :
#
#   * une même personne comptée deux fois quand ses commits alternent entre
#     « adresse présente » et « nom seul » — le bus factor doublait ;
#   * un 403 sur les commits d'un projet lu comme « 0 commit », donc classé
#     sous le plancher : la confusion absent/zéro que ce dépôt documente ;
#   * du budget de sélection laissé sur la table quand une tranche de quota ne
#     trouvait pas preneur ;
#   * l'encodage de sortie, en simulant une console cp850 — le cas PowerShell,
#     invérifiable autrement depuis une machine Unix.
#
# Usage :  ./testing/smoke-gitlab.sh
# Prérequis : python3, jbang — ou un JDK 25+ avec AUDIT_CP pointant les jars.

set -euo pipefail

PORT="${PORT:-8099}"
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$(mktemp -d)"

cleanup() {
    [[ -n "${SERVER_PID:-}" ]] && kill "$SERVER_PID" 2>/dev/null || true
}
trap cleanup EXIT

python3 "$HERE/fake-gitlab.py" "$PORT" &
SERVER_PID=$!
sleep 1

# jbang par défaut ; AUDIT_CP permet de lancer sans jbang, avec les jars déjà
# résolus (utile en CI ou sur une machine verrouillée) — java sait exécuter un
# fichier source unique tant que les dépendances sont sur le classpath.
run() {
    if [[ -n "${AUDIT_CP:-}" ]]; then
        java ${JVM_OPTS:-} -cp "$AUDIT_CP" "$HERE/../GitlabActivityAudit.java" "$@"
    else
        jbang ${JVM_OPTS:-} "$HERE/../GitlabActivityAudit.java" "$@"
    fi
}

GITLAB_URL="http://127.0.0.1:$PORT" GITLAB_TOKEN=faux \
    run --top 6 --pratiques "$OUT/pratiques.csv"

# --pratiques seul doit tout de même déposer l'inventaire à côté : sans lui, le
# fichier de pratiques n'a pas de parc de référence.
if [[ -f "$OUT/inventaire.csv" ]]; then
    echo "  (inventaire écrit à côté de --pratiques)"
else
    echo "  ÉCHEC --pratiques n'a pas écrit d'inventaire"
    exit 1
fi

# Deuxième passage, console cp850 simulée : c'est le cas PowerShell, où envoyer
# de l'UTF-8 affiche « Ã© » au lieu de « é ». On vérifie que la sortie est bien
# dans la page de code demandée et que le tiret cadratin est translittéré.
JVM_OPTS="-Daudit.console.charset=IBM850" GITLAB_URL="http://127.0.0.1:$PORT" GITLAB_TOKEN=faux \
    run --top 4 > "$OUT/cp850.txt" 2>&1 || true
iconv -f CP850 -t UTF-8 "$OUT/cp850.txt" > "$OUT/cp850-relu.txt"

echo
echo "--- vérifications ---"

fail=0
check() {
    if grep -q "$2" "$1"; then
        echo "  OK   $3"
    else
        echo "  ÉCHEC $3"
        fail=1
    fi
}

# Un refus de permission ne doit jamais ressortir en « 0 commit ».
check "$OUT/inventaire.csv" 'activité non mesurable (HTTP 403)' "403 ≠ zéro commit"
# Le projet mono-auteur planté doit être compté à 1 auteur, pas 2.
# Le séparateur est le point-virgule depuis que les CSV sont écrits pour Excel :
# ce motif attendait encore la virgule, donc il passait sans rien vérifier.
check "$OUT/inventaire.csv" '"equipe-c/mono-auteur".*"45"."0"."1"' "identités d'auteur fusionnées"
# Le projet actif mais marqué inactif doit être retrouvé par l'échantillon.
check "$OUT/inventaire.csv" '"equipe-d/fuite".*"true"' "fuite du filtre de fraîcheur détectée"
# Le parc contient un dépôt dont l'activité est entièrement robotique.
check "$OUT/inventaire.csv" 'activité robotique seule' "activité robotique isolée"

# Le job Sonar n'est ni dans le .gitlab-ci.yml du projet ni dans le premier
# template inclus : il est deux niveaux plus bas, et sa clé est une variable.
# C'est le cas majoritaire du parc visé, et celui qu'un grep sur le fichier brut
# ne peut pas voir.
check "$OUT/pratiques.csv" '"equipe-a/service-actif".*"equipe-a-service-actif"' \
    "clé Sonar résolue à travers deux niveaux d'include"
# sonar-project.properties porte la clé en clair : il doit gagner sur la CI.
check "$OUT/pratiques.csv" '"equipe-c/mono-auteur".*"equipe-c_mono-auteur"' \
    "clé littérale préférée à la clé dérivée"

# ci/lint refusé : le repli doit trouver la même chose, en plus d'appels. Une
# instance sur deux ne donne pas ce droit à un jeton Reporter.
python3 "$HERE/fake-gitlab.py" "$((PORT + 1))" refuse &
REFUSE_PID=$!
sleep 1
GITLAB_URL="http://127.0.0.1:$((PORT + 1))" GITLAB_TOKEN=faux \
    run --top 6 --no-cache --pratiques "$OUT/repli/pratiques.csv" > "$OUT/repli.txt" 2>&1 || true
kill "$REFUSE_PID" 2>/dev/null || true
check "$OUT/repli.txt" "ci/lint refusé" "repli annoncé, pas silencieux"
if diff -q <(cut -d';' -f1,23,24,25 "$OUT/pratiques.csv" | sort) \
           <(cut -d';' -f1,23,24,25 "$OUT/repli/pratiques.csv" | sort) > /dev/null; then
    echo "  OK   le repli trouve les mêmes clés que ci/lint"
else
    echo "  ÉCHEC le repli et ci/lint divergent"
    fail=1
fi

check "$OUT/cp850-relu.txt" 'Filtre de fraîcheur' "accents intacts sur une console cp850"
if grep -q '—' "$OUT/cp850-relu.txt"; then
    echo "  ÉCHEC tiret cadratin non translittéré"
    fail=1
else
    echo "  OK   caractères hors page de code translittérés en ASCII"
fi

echo
if [[ $fail -eq 0 ]]; then
    echo "Chemins de plantage : OK. La sémantique de l'API reste NON vérifiée ici."
else
    echo "Des vérifications ont échoué. Sorties conservées dans $OUT"
    exit 1
fi
