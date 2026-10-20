╔═ aRefusedLintFallsBackToFollowingIncludes/pratiques ═╗
<BOM>"path";"motif_selection";"bucket";"commits_window";"authors_window";"branche_protegee";"push_verrouille";"mr_fusionnees";"ttm_median_j";"auto_merge";"notes_par_mr";"echantillon_approbations";"part_approuvee";"auto_approbation";"pipelines";"taux_succes";"incidents_rouges";"retour_au_vert_h";"rouge_non_resolu";"environnements";"deploiements";"dora_indispo";"ci_sonar";"ci_securite";"cle_sonar";"source_cle_sonar";"fichiers";"id";"fenetre_j";"lignes_modifiees";"commits_lignes";"lignes_tronquees";"cle_pom";"nom_pom"
"team-a/k1";"tête de strate A(<100)";"A(<100)";"1";"1";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"false";"true";"true";"team-a_k1";"includes suivis (variables)";".gitlab-ci.yml";"1";"90";"0";"0";"false";"";""
"team-a/k2";"tête de strate A(<100)";"A(<100)";"1";"1";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"false";"true";"false";"k2-key";"includes suivis";".gitlab-ci.yml";"2";"90";"0";"0";"false";"";""

╔═ aRefusedLintFallsBackToFollowingIncludes/stdout ═╗
exit 0

Instance : http://gitlab.test

0. Connectivité, identité, licence
----------------------------------
  api/v4/user                      [OK]  alice (Alice A)
  api/v4/metadata                  [VIDE]  GitLab 17.0.0 · Community — DORA et approbations indisponibles

1. Inventaire (étape 0)
-----------------------
  Projets retournés               : 2
  Retenus pour analyse            : 2 (100 % du parc)

2. Filtre de fraîcheur (étape 1) — 0 appel
------------------------------------------
  Actifs sur 90 jours              : 2 / 2 (100 %)
    Coupe la plus rentable du pipeline : gratuite.

3. Activité réelle en commits (étape 2)
---------------------------------------
  Comptés par pagination REST     : 2 projets
  Avec au moins un commit humain  : 2

4. Plancher d'activité (étape 3)
--------------------------------
  Distribution des commits (déciles) : D1=1 D3=1 D5=1 D7=1 D9=1
  Plancher imposé (--floor)       : 0 commits
  Éligibles au tirage             : 2
  Cohorte faible activité         : 0
    Cette cohorte n'est pas perdue : elle se rapporte en effectifs,
    comme les « jamais analysés » côté Sonar. C'est un constat.

5. Sélection par quotas (étape 4)
---------------------------------
  Budget                          : 200
  Sélectionnés                    : 2
    tête de strate                   : 2
  Namespaces représentés          : 1 (plafond 30 par namespace)
    Le témoin permettra d'écrire « couverture X sur les sélectionnés,
    Y sur un tirage du reste » — et si X et Y divergent, la règle
    de sélection est elle-même le sujet.

  Inventaire : 2 lignes.

6. Signaux de pratique sur les 2 sélectionnés
---------------------------------------------

  CI lue : 0 par ci/lint, 2 par repli, 0 depuis le cache.
    ci/lint refusé (HTTP 403) : le jeton n'a pas le droit de 
    déplier la configuration. Le repli ne suit que les includes
    « project: » qu'il sait lire — ci_sonar y sous-compte. D'un
    « component: », il ne lit que le project_key passé en inputs.
  Clé Maven (pom.xml) : 0 projets, dont 0 ont aussi une clé dans la CI.

                                            sélection       témoin
  Branche par défaut protégée                 0 (0 %)            —
  Au moins une MR fusionnée                   0 (0 %)            —
  Pipelines sur la branche par défaut         0 (0 %)            —
  CI configurée pour Sonar                  2 (100 %)            —
  Scan de sécurité dans la CI                1 (50 %)            —
  CODEOWNERS présent                          0 (0 %)            —
  Aucun environnement déclaré                 0 (0 %)            —

  Pratiques : 2 lignes.

Synthèse
--------
  28 appels API (28 GET, 0 GraphQL, 0 429 réessayés).
  Parc 2 → retenus 2 → frais 2 → éligibles 2 → sélectionnés 2.
  Fichiers écrits :
    inventaire : <TMP>/inventaire.csv
    pratiques  : <TMP>/pratiques.csv
    Les deux se lisent ensemble : pratiques.csv ne contient que les
    sélectionnés, l'inventaire porte le parc dont ils sortent. Colonnes : COLUMNS.md.
    Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start inventaire.csv`. Pour un autre outil : --comma.
  Rappel : tout classement qui ne dit pas ce qu'il a écarté se lit comme
  complet alors qu'il ne l'est pas. Le CSV porte le motif d'exclusion de
  chaque projet, et la sélection porte son motif d'entrée.


╔═ aSecondRunReadsTheKeysFromTheCache ═╗
{
  "version" : 2,
  "entries" : {
    "1@sha1" : {
      "ciSonar" : true,
      "ciSecurity" : false,
      "sonarKey" : "k1key",
      "keySource" : "ci/lint",
      "ciRoute" : "ci/lint",
      "unresolvedKey" : null,
      "pomKey" : "g:a",
      "pomName" : null,
      "pomUnresolved" : null
    }
  }
}
╔═ everyWayToFindTheSonarKey/cache ═╗
{
  "version" : 2,
  "entries" : {
    "7@sha7" : {
      "ciSonar" : true,
      "ciSecurity" : false,
      "sonarKey" : "team-a.K7.7.team-a/k7",
      "keySource" : "ci/lint (variables)",
      "ciRoute" : "ci/lint",
      "unresolvedKey" : null,
      "pomKey" : null,
      "pomName" : null,
      "pomUnresolved" : null
    },
    "12@sha12" : {
      "ciSonar" : false,
      "ciSecurity" : false,
      "sonarKey" : null,
      "keySource" : null,
      "ciRoute" : null,
      "unresolvedKey" : null,
      "pomKey" : "g:a12",
      "pomName" : null,
      "pomUnresolved" : null
    },
    "4@sha4" : {
      "ciSonar" : true,
      "ciSecurity" : false,
      "sonarKey" : "k4",
      "keySource" : "includes suivis (variables)",
      "ciRoute" : "includes suivis",
      "unresolvedKey" : null,
      "pomKey" : null,
      "pomName" : null,
      "pomUnresolved" : null
    },
    "10@sha10" : {
      "ciSonar" : false,
      "ciSecurity" : false,
      "sonarKey" : null,
      "keySource" : null,
      "ciRoute" : null,
      "unresolvedKey" : null,
      "pomKey" : null,
      "pomName" : null,
      "pomUnresolved" : null
    },
    "11@sha11" : {
      "ciSonar" : false,
      "ciSecurity" : false,
      "sonarKey" : null,
      "keySource" : null,
      "ciRoute" : null,
      "unresolvedKey" : null,
      "pomKey" : null,
      "pomName" : null,
      "pomUnresolved" : null
    },
    "9@sha9" : {
      "ciSonar" : true,
      "ciSecurity" : false,
      "sonarKey" : "k9key",
      "keySource" : "ci/lint",
      "ciRoute" : "ci/lint",
      "unresolvedKey" : null,
      "pomKey" : null,
      "pomName" : null,
      "pomUnresolved" : "${org}:k9"
    },
    "1@sha1" : {
      "ciSonar" : true,
      "ciSecurity" : true,
      "sonarKey" : "team-a_k1",
      "keySource" : "sonar-project.properties",
      "ciRoute" : "ci/lint",
      "unresolvedKey" : null,
      "pomKey" : null,
      "pomName" : null,
      "pomUnresolved" : null
    },
    "6@sha6" : {
      "ciSonar" : true,
      "ciSecurity" : false,
      "sonarKey" : "real-key",
      "keySource" : "ci/lint",
      "ciRoute" : "ci/lint",
      "unresolvedKey" : null,
      "pomKey" : null,
      "pomName" : null,
      "pomUnresolved" : null
    },
    "3@sha3" : {
      "ciSonar" : true,
      "ciSecurity" : true,
      "sonarKey" : "literal-key",
      "keySource" : "ci/lint",
      "ciRoute" : "ci/lint",
      "unresolvedKey" : null,
      "pomKey" : null,
      "pomName" : null,
      "pomUnresolved" : null
    },
    "5@sha5" : {
      "ciSonar" : true,
      "ciSecurity" : false,
      "sonarKey" : null,
      "keySource" : "variable non résolue",
      "ciRoute" : "ci/lint",
      "unresolvedKey" : "$CUSTOM_KEY",
      "pomKey" : null,
      "pomName" : null,
      "pomUnresolved" : null
    },
    "2@sha2" : {
      "ciSonar" : true,
      "ciSecurity" : false,
      "sonarKey" : "team-a-k2",
      "keySource" : "ci/lint (variables)",
      "ciRoute" : "ci/lint",
      "unresolvedKey" : null,
      "pomKey" : null,
      "pomName" : null,
      "pomUnresolved" : null
    },
    "8@sha8" : {
      "ciSonar" : false,
      "ciSecurity" : false,
      "sonarKey" : null,
      "keySource" : null,
      "ciRoute" : null,
      "unresolvedKey" : null,
      "pomKey" : "ch.example:k8-app",
      "pomName" : "K8 App",
      "pomUnresolved" : null
    }
  }
}
╔═ everyWayToFindTheSonarKey/pratiques ═╗
<BOM>"path";"motif_selection";"bucket";"commits_window";"authors_window";"branche_protegee";"push_verrouille";"mr_fusionnees";"ttm_median_j";"auto_merge";"notes_par_mr";"echantillon_approbations";"part_approuvee";"auto_approbation";"pipelines";"taux_succes";"incidents_rouges";"retour_au_vert_h";"rouge_non_resolu";"environnements";"deploiements";"dora_indispo";"ci_sonar";"ci_securite";"cle_sonar";"source_cle_sonar";"fichiers";"id";"fenetre_j";"lignes_modifiees";"commits_lignes";"lignes_tronquees";"cle_pom";"nom_pom"
"team-a/k1";"tête de strate A(<100)";"A(<100)";"1";"1";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"false";"true";"true";"team-a_k1";"sonar-project.properties";".gitlab-ci.yml";"1";"90";"0";"0";"false";"";""
"team-a/k2";"tête de strate A(<100)";"A(<100)";"1";"1";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"false";"true";"false";"team-a-k2";"ci/lint (variables)";".gitlab-ci.yml";"2";"90";"0";"0";"false";"";""
"team-a/k3";"tête de strate A(<100)";"A(<100)";"1";"1";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"false";"true";"true";"literal-key";"ci/lint";".gitlab-ci.yml";"3";"90";"0";"0";"false";"";""
"team-a/k4";"tête de strate A(<100)";"A(<100)";"1";"1";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"false";"true";"false";"k4";"includes suivis (variables)";".gitlab-ci.yml";"4";"90";"0";"0";"false";"";""
"team-a/k5";"tête de strate A(<100)";"A(<100)";"1";"1";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"false";"true";"false";"";"variable non résolue : $CUSTOM_KEY";".gitlab-ci.yml";"5";"90";"0";"0";"false";"";""
"team-a/k6";"tête de strate A(<100)";"A(<100)";"1";"1";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"false";"true";"false";"real-key";"ci/lint";".gitlab-ci.yml";"6";"90";"0";"0";"false";"";""
"team-a/k7";"tête de strate A(<100)";"A(<100)";"1";"1";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"false";"true";"false";"team-a.K7.7.team-a/k7";"ci/lint (variables)";".gitlab-ci.yml";"7";"90";"0";"0";"false";"";""
"team-a/k8";"tête de strate A(<100)";"A(<100)";"1";"1";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"false";"false";"false";"";"";"";"8";"90";"0";"0";"false";"ch.example:k8-app";"K8 App"
"team-a/k9";"tête de strate A(<100)";"A(<100)";"1";"1";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"false";"true";"false";"k9key";"ci/lint";".gitlab-ci.yml";"9";"90";"0";"0";"false";"";""
"team-a/k10";"tête de strate A(<100)";"A(<100)";"1";"1";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"false";"false";"false";"";"";"";"10";"90";"0";"0";"false";"";""
"team-a/k11";"tête de strate A(<100)";"A(<100)";"1";"1";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"false";"false";"false";"";"";".gitlab-ci.yml";"11";"90";"0";"0";"false";"";""
"team-a/k12";"tête de strate A(<100)";"A(<100)";"1";"1";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"false";"false";"false";"";"";"";"12";"90";"0";"0";"false";"g:a12";""

╔═ everyWayToFindTheSonarKey/stdout ═╗
exit 0

Instance : http://gitlab.test

0. Connectivité, identité, licence
----------------------------------
  api/v4/user                      [OK]  alice (Alice A)
  api/v4/metadata                  [VIDE]  GitLab 17.0.0 · Community — DORA et approbations indisponibles

1. Inventaire (étape 0)
-----------------------
  Projets retournés               : 12
  Retenus pour analyse            : 12 (100 % du parc)

2. Filtre de fraîcheur (étape 1) — 0 appel
------------------------------------------
  Actifs sur 90 jours              : 12 / 12 (100 %)
    Coupe la plus rentable du pipeline : gratuite.

3. Activité réelle en commits (étape 2)
---------------------------------------
  Comptés par pagination REST     : 12 projets
  Avec au moins un commit humain  : 12

4. Plancher d'activité (étape 3)
--------------------------------
  Distribution des commits (déciles) : D1=1 D3=1 D5=1 D7=1 D9=1
  Plancher imposé (--floor)       : 0 commits
  Éligibles au tirage             : 12
  Cohorte faible activité         : 0
    Cette cohorte n'est pas perdue : elle se rapporte en effectifs,
    comme les « jamais analysés » côté Sonar. C'est un constat.

5. Sélection par quotas (étape 4)
---------------------------------
  Budget                          : 200
  Sélectionnés                    : 12
    tête de strate                   : 12
  Namespaces représentés          : 1 (plafond 30 par namespace)
    Le témoin permettra d'écrire « couverture X sur les sélectionnés,
    Y sur un tirage du reste » — et si X et Y divergent, la règle
    de sélection est elle-même le sujet.

  Inventaire : 12 lignes.

6. Signaux de pratique sur les 12 sélectionnés
----------------------------------------------

  CI lue : 7 par ci/lint, 1 par repli, 0 depuis le cache.
  Clé Maven (pom.xml) : 2 projets, dont 0 ont aussi une clé dans la CI.
    1 pom avec ${…} dans groupId/artifactId : clé inconnue, pas devinée.

                                            sélection       témoin
  Branche par défaut protégée                 0 (0 %)            —
  Au moins une MR fusionnée                   0 (0 %)            —
  Pipelines sur la branche par défaut         0 (0 %)            —
  CI configurée pour Sonar                   8 (67 %)            —
  Scan de sécurité dans la CI                2 (17 %)            —
  CODEOWNERS présent                          0 (0 %)            —
  Aucun environnement déclaré                 0 (0 %)            —

  Pratiques : 12 lignes.

Synthèse
--------
  105 appels API (105 GET, 0 GraphQL, 0 429 réessayés).
  Parc 12 → retenus 12 → frais 12 → éligibles 12 → sélectionnés 12.
  Fichiers écrits :
    inventaire : <TMP>/inventaire.csv
    pratiques  : <TMP>/pratiques.csv
    Les deux se lisent ensemble : pratiques.csv ne contient que les
    sélectionnés, l'inventaire porte le parc dont ils sortent. Colonnes : COLUMNS.md.
    Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start inventaire.csv`. Pour un autre outil : --comma.
  Rappel : tout classement qui ne dit pas ce qu'il a écarté se lit comme
  complet alors qu'il ne l'est pas. Le CSV porte le motif d'exclusion de
  chaque projet, et la sélection porte son motif d'entrée.


╔═ [end of file] ═╗
