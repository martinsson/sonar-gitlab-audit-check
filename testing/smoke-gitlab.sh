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
OUT="${KEEP_OUT:-$(mktemp -d)}"

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

# Liste d'exclusion : glob sur le chemin, commentaire après #.
printf '%s\n' '# projets hors audit' 'EQUIPE-A/**   # casse ignorée' > "$OUT/exclusions.txt"
GITLAB_URL="http://127.0.0.1:$PORT" GITLAB_TOKEN=faux \
    run --top 4 --exclusions "$OUT/exclusions.txt" --csv "$OUT/inventaire-exclu.csv" \
    > "$OUT/exclu.txt" 2>&1 || true

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
# Le composant Sonar partagé sans project_key : pas de clé dans la CI, la clé
# est groupId:artifactId du pom, groupId hérité du <parent>, pas de la
# dépendance déclarée plus bas.
check "$OUT/pratiques.csv" '"equipe-a/service-calme";.*;"";"variable non résolue[^"]*";.*"ch.ge.equipe-a:service-calme";"Service Calme"' \
    "clé Maven lue dans le pom, groupId hérité du parent"
# Le même composant avec project_key en inputs : la surcharge est la clé CI,
# le pom reste lu à côté.
check "$OUT/pratiques.csv" '"equipe-d/sans-total";.*;"equipe-d-sans-total";"ci/lint";.*"ch.ge.equipe-d:sans-total";"Sans Total"' \
    "project_key du composant lu, pom lu à côté"
# ${app.name} dans artifactId : pas une clé.
check "$OUT/pratiques.csv" '"equipe-a/monolithe";.*;"";""[[:space:]]*$' \
    "pom en \${…} : clé Maven laissée vide, pas devinée"

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

# Le croisement : il lit les deux CSV et n'appelle rien. Un inventaire Sonar
# minimal suffit — ce qui est vérifié ici, c'est que la jointure trouve la clé
# écrite par la passe profonde, et que les suggestions ne comptent pas.
cat > "$OUT/sonar.csv" <<'SONAR'
key,name,analysisDate,days_since_analysis,ncloc,coverage,tests,sqale_index,new_lines,new_violations,alert_status,tendance_analyses,violations_pente_mois
equipe-a-service-actif,service-actif,2026-08-30,2,42000,12.4,0,18400,1200,64,ERROR,12,30
equipe-c_mono-auteur,mono-auteur,2026-06-02,91,8600,0.0,412,4200,0,0,OK,2,
ch.ge.equipe-a:service-calme,Service Calme,2026-09-20,9,3100,71.0,88,300,40,1,OK,6,-2
ch.ge.equipe-d:autre-artefact,Sans Total,2026-09-01,28,5000,40.0,10,900,0,0,OK,3,1
SONAR
runx() {
    if [[ -n "${AUDIT_CP:-}" ]]; then
        java ${JVM_OPTS:-} -cp "$AUDIT_CP" "$HERE/../CrossAudit.java" "$@"
    else
        jbang ${JVM_OPTS:-} "$HERE/../CrossAudit.java" "$@"
    fi
}
runx --sonar "$OUT/sonar.csv" --gitlab "$OUT/pratiques.csv" \
    --out "$OUT/croisement.csv" --report "$OUT/appariement.json" > "$OUT/croisement.txt" 2>&1 || true
check "$OUT/croisement.txt" 'clé lue dans la CI' "jointure exacte sur cle_sonar"
# L'inventaire minimal n'a ni liaison ni liens : ces méthodes doivent être dites
# non évaluées, pas comptées à zéro, et le rapport doit sortir quand même.
check "$OUT/croisement.txt" 'Non évaluée : liaison DevOps' "méthode sans colonnes dite non évaluée"
check "$OUT/appariement.json" '"thresholds"' "rapport d'appariement écrit"
# Des tests comptés et une couverture à zéro : le constat que ni l'un ni l'autre
# rapport ne peut produire seul, et qui a motivé l'ajout de la métrique tests.
check "$OUT/croisement.txt" "la couverture n'arrive pas : 1" \
    "tests sans couverture repérés par le croisement"
# Pente Sonar × lignes GitLab : seul le projet à 12 analyses porte un ratio ;
# celui à 2 analyses n'a pas de pente et doit rester vide, pas à zéro.
check "$OUT/croisement.txt" 'Chaque modification ajoute des issues : 1' \
    "issues par kLOC modifié calculées sur la paire jointe"
# La clé Maven apparie le composant sans project_key ; le nom du pom apparie
# un projet dont la clé ne correspond pas mais dont le nom est unique.
check "$OUT/croisement.txt" 'clé Maven du pom *: *1' "jointure exacte sur cle_pom"
check "$OUT/croisement.txt" 'nom du pom = nom Sonar *: *1' "jointure sur le nom du pom"

# L'appariement est écrit à côté de pratiques.csv, puis repris : une paire
# rejetée à la main doit le rester au lancement suivant, une paire saisie à la
# main doit être tenue, les autres gardées.
check "$OUT/appariement.csv" '"equipe-c/mono-auteur";"equipe-c_mono-auteur"' \
    "appariement écrit à côté de pratiques.csv"
cp "$OUT/croisement.csv" "$OUT/croisement-1.csv"
perl -pi -e 's/^("equipe-c\/mono-auteur";[^;]*;[^;]*;)"exact"/$1"rejete"/;
             s/^"equipe-b\/petit-outil";"";("[^"]*");"none"/"equipe-b\/petit-outil";"equipe-c_mono-auteur";$1;"manuel"/' \
    "$OUT/appariement.csv"
runx --sonar "$OUT/sonar.csv" --gitlab "$OUT/pratiques.csv" \
    --out "$OUT/croisement.csv" > "$OUT/croisement-2.txt" 2>&1 || true
check "$OUT/croisement-2.txt" '3 gardées, 1 saisies à la main, 1 rejetées' \
    "appariement repris, décisions manuelles respectées"
check "$OUT/croisement.csv" '"equipe-b/petit-outil";"saisi à la main";"manuel"' \
    "paire saisie à la main tenue"
check "$OUT/croisement.csv" '"equipe-c/mono-auteur";"rejeté à la main";"rejete"' \
    "paire rejetée absente du croisement"
check "$OUT/appariement.csv.bak" '"exact"' "ancienne version gardée en .bak"
# Remettre la paire pour les vérifications qui suivent.
cp "$OUT/appariement.csv.bak" "$OUT/appariement.csv"
cp "$OUT/croisement-1.csv" "$OUT/croisement.csv"

# Les vues : extraites du croisement, sans rien recalculer.
runv() {
    if [[ -n "${AUDIT_CP:-}" ]]; then
        java ${JVM_OPTS:-} -cp "$AUDIT_CP" "$HERE/../Vues.java" "$@"
    else
        jbang ${JVM_OPTS:-} "$HERE/../Vues.java" "$@"
    fi
}
runv --in "$OUT/croisement.csv" > "$OUT/vues.txt" 2>&1 || true
check "$OUT/croisement-essentiel.csv" '^.\?"projet";"confiance";"sq_violations_kloc_pente_pct_mois"' \
    "vue essentiel : projet d'abord, colonnes importantes ensuite"
check "$OUT/croisement-derive.csv" '"equipe-a/service-actif";"[^"]*modifs ajoutent des issues' \
    "vue dérive : signaux nommés, projet le plus inquiétant en tête"
check "$OUT/vues.txt" 'Vue dérive    : 4 projets appariés' "vue dérive limitée aux paires jointes"
check "$OUT/croisement-non-apparies.csv" '^.\?"projet";"confiance";"gl_commits_window"' \
    "vue non appariés écrite"
if grep -qE '"(exact|derived|manuel)"' "$OUT/croisement-non-apparies.csv"; then
    echo "  ÉCHEC un projet apparié figure dans la vue non appariés"
    fail=1
fi

check "$OUT/inventaire-exclu.csv" "\"equipe-a/service-actif\".*\"liste d'exclusion\"" \
    "projet exclu par la liste, gardé dans l'inventaire avec sa raison"
check "$OUT/exclu.txt" "exclu — liste d'exclusion *: 3" "exclusions comptées dans l'entonnoir"

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
