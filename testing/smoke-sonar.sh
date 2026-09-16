#!/usr/bin/env bash
#
# Fait tourner SonarAuditCheck contre le faux SonarQube de testing/fake-sonar.py.
#
# Ce que ce test prouve, et ce qu'il ne prouve pas. KNOWLEDGE.md §1 : un mock
# répond ce qu'on lui demande, donc il ne valide AUCUNE sémantique de l'API
# SonarQube — ni le nom d'un paramètre, ni la forme exacte d'une réponse. Cela
# reste le travail de verify-against-real-sonarqube.sh, qui monte une vraie
# instance.
#
# Ce qu'il attrape, en revanche, et qu'une instance réelle ne sait pas produire
# sur commande :
#
#   * les métriques new_* lues sous `period` ET sous `periods` — la forme
#     ancienne : les lire mal vide silencieusement la moitié du CSV, ce qui a
#     déjà coûté un portage entier (KNOWLEDGE.md §2) ;
#   * un projet dont seule develop est analysée, indiscernable d'un projet mort
#     tant qu'on ne demande pas ses branches ;
#   * un historique à un seul point, ou troué : la vélocité doit être ABSENTE,
#     et surtout pas 0 — « l'équipe n'a rien dégradé » est le pire des faux
#     positifs ;
#   * un 429 avec Retry-After et un 502 isolé, au milieu d'une passe
#     concurrente : une reprise ratée ressort en projet sans mesures ;
#   * le rejeu : --dump-dir puis --replay-dir doivent rendre le MÊME CSV, sans
#     instance. C'est la non-régression de tout le reste.
#
# Usage :  ./testing/smoke-sonar.sh
# Prérequis : python3, jbang — ou un JDK 25+ avec AUDIT_CP pointant les jars.

set -euo pipefail

PORT="${PORT:-9099}"
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$(mktemp -d)"

cleanup() {
    [[ -n "${SERVER_PID:-}" ]] && kill "$SERVER_PID" 2>/dev/null || true
}
trap cleanup EXIT

python3 "$HERE/fake-sonar.py" "$PORT" &
SERVER_PID=$!
sleep 1

# jbang par défaut ; AUDIT_CP permet de lancer sans jbang, avec les jars déjà
# résolus (utile en CI ou sur une machine verrouillée) — java sait exécuter un
# fichier source unique tant que les dépendances sont sur le classpath.
run() {
    if [[ -n "${AUDIT_CP:-}" ]]; then
        java ${JVM_OPTS:-} -cp "$AUDIT_CP" "$HERE/../SonarAuditCheck.java" "$@"
    else
        jbang ${JVM_OPTS:-} "$HERE/../SonarAuditCheck.java" "$@"
    fi
}

export SONAR_URL="http://127.0.0.1:$PORT"
export SONAR_TOKEN=squ_faux

run --csv "$OUT/inventaire.csv" --dump-dir "$OUT/captures" > "$OUT/live.txt" 2>&1

# Un projet dont l'historique n'a qu'un point : le calcul de vélocité doit se
# taire. C'est un projet témoin séparé, donc un second passage.
run --project org:un-point --csv "$OUT/jetable.csv" > "$OUT/un-point.txt" 2>&1

# Sans instance, à partir des seules captures du premier passage.
unset SONAR_URL SONAR_TOKEN
run --replay-dir "$OUT/captures" --csv "$OUT/rejeu.csv" > "$OUT/rejeu.txt" 2>&1

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

# Le projet scanné sur develop doit être retrouvé, avec SES mesures et le nom
# de la branche qui les porte.
check "$OUT/inventaire.csv" '"org:develop".*"5400"' "mesures lues sur la branche non principale"
check "$OUT/inventaire.csv" '"org:develop".*"develop";"false"' "branche du dernier scan nommée"
check "$OUT/live.txt" 'Dernier scan sur une branche non principale : 1' "branche non principale comptée"

# new_* sous period (forme courante) et sous periods (forme ancienne). Les deux
# doivent arriver dans le CSV ; une cellule vide ici ressemblerait à « pas de
# données » et ne se verrait pas.
check "$OUT/inventaire.csv" '"org:sain".*"800";"9"' "new_* lues sous period"
check "$OUT/inventaire.csv" '"org:legacy".*"300";"15"' "new_* lues sous periods (forme ancienne)"

# Absent n'est pas zéro, des deux côtés.
check "$OUT/live.txt" 'Sans aucune donnée de couverture: 2' "couverture absente comptée à part"
check "$OUT/live.txt" "Total réel indisponible" "403 sur projects/search ≠ périmètre complet"

# La métrique inconnue doit être retirée de la requête, pas faire échouer les
# treize autres.
check "$OUT/live.txt" 'Métriques inconnues de cette instance : cognitive_complexity' \
    "métrique inconnue négociée hors de la requête"

# La fenêtre mesurée n'est pas la fenêtre demandée : elle doit être affichée.
check "$OUT/live.txt" 'Δ lignes sur 90 j (88 j mesurés)' "fenêtre réellement mesurée affichée"

# Un seul point d'historique : pas de Δ, et surtout pas +0.
check "$OUT/un-point.txt" 'Vélocité de dette indisponible' "historique trop court dit indisponible"
if grep -q 'Dette ajoutée par ligne écrite' "$OUT/un-point.txt"; then
    echo "  ÉCHEC un seul point d'historique a produit une vélocité"
    fail=1
else
    echo "  OK   aucune vélocité inventée sur un seul point"
fi

# La liaison et les liens : lus, écrits, et un 404 de liaison n'est pas un refus.
check "$OUT/inventaire.csv" '"org:sain".*"gitlab";"101";"scm git@gitlab.example.com:org/sain.git"' \
    "liaison GitLab et lien écrits dans l'inventaire"
check "$OUT/live.txt" 'Liés à une plateforme DevOps   : 2 / 7' "liaisons comptées, 404 = absence"
if grep -q 'liaison(s) illisible(s)' "$OUT/live.txt"; then
    echo "  ÉCHEC un 404 de liaison compté comme refus"
    fail=1
else
    echo "  OK   un 404 de liaison n'est pas un refus"
fi

# Le 429 et le 502 ont été servis une fois chacun : ils doivent être repris, et
# la reprise doit être dite.
check "$OUT/live.txt" 'ralenti(s) (429) et 1 repris (5xx)' "429 et 5xx repris et rapportés"

# Le rejeu doit rendre le même fichier, sans instance.
if diff -q "$OUT/inventaire.csv" "$OUT/rejeu.csv" > /dev/null; then
    echo "  OK   le rejeu hors ligne rend un CSV identique"
else
    echo "  ÉCHEC le rejeu diverge de la capture"
    diff "$OUT/inventaire.csv" "$OUT/rejeu.csv" | head -5
    fail=1
fi
check "$OUT/rejeu.txt" 'Projets visibles avec ce token : 7' "parc complet rejoué depuis les captures"
if grep -q 'capture(s) manquante(s)' "$OUT/rejeu.txt"; then
    echo "  ÉCHEC des captures manquaient au rejeu"
    fail=1
else
    echo "  OK   aucune capture manquante"
fi

echo
if [[ $fail -eq 0 ]]; then
    echo "Tout est passé. Rappel : rien de tout cela ne prouve ce que répond une"
    echo "vraie instance — c'est le rôle de verify-against-real-sonarqube.sh."
else
    echo "Des vérifications ont échoué. Sortie complète dans $OUT"
fi
exit $fail
