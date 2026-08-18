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
    run --top 6 --csv "$OUT/inventaire.csv" --deep --pratiques "$OUT/pratiques.csv"

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
check "$OUT/inventaire.csv" '"equipe-c/mono-auteur".*"45","0","1"' "identités d'auteur fusionnées"
# Le projet actif mais marqué inactif doit être retrouvé par l'échantillon.
check "$OUT/inventaire.csv" '"equipe-d/fuite".*"true"' "fuite du filtre de fraîcheur détectée"
# Le parc contient un dépôt dont l'activité est entièrement robotique.
check "$OUT/inventaire.csv" 'activité robotique seule' "activité robotique isolée"

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
