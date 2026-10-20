╔═ aCommunityProjectWithNothingReadable/pratiques ═╗
<BOM>"path";"motif_selection";"bucket";"commits_window";"authors_window";"branche_protegee";"push_verrouille";"mr_fusionnees";"ttm_median_j";"auto_merge";"notes_par_mr";"echantillon_approbations";"part_approuvee";"auto_approbation";"pipelines";"taux_succes";"incidents_rouges";"retour_au_vert_h";"rouge_non_resolu";"environnements";"deploiements";"dora_indispo";"ci_sonar";"ci_securite";"cle_sonar";"source_cle_sonar";"fichiers";"id";"fenetre_j";"lignes_modifiees";"commits_lignes";"lignes_tronquees";"cle_pom";"nom_pom"
"team-a/alpha";"tête de strate A(<100)";"A(<100)";"25";"1";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"0";"";"false";"false";"false";"";"";"";"1";"90";"";"";"false";"";""

╔═ aCommunityProjectWithNothingReadable/stdout ═╗
exit 0

Instance : http://gitlab.test

0. Connectivité, identité, licence
----------------------------------
  api/v4/user                      [OK]  alice (Alice A)
  api/v4/metadata                  [VIDE]  GitLab 17.0.0 · Community — DORA et approbations indisponibles

1. Inventaire (étape 0)
-----------------------
  Projets retournés               : 1
  Retenus pour analyse            : 1 (100 % du parc)

2. Filtre de fraîcheur (étape 1) — 0 appel
------------------------------------------
  Actifs sur 90 jours              : 1 / 1 (100 %)
    Coupe la plus rentable du pipeline : gratuite.

3. Activité réelle en commits (étape 2)
---------------------------------------
  Comptés par pagination REST     : 1 projet
  Avec au moins un commit humain  : 1

4. Plancher d'activité (étape 3)
--------------------------------
  Distribution des commits (déciles) : D1=25 D3=25 D5=25 D7=25 D9=25
  Plancher imposé (--floor)       : 0 commits
  Éligibles au tirage             : 1
  Cohorte faible activité         : 0
    Cette cohorte n'est pas perdue : elle se rapporte en effectifs,
    comme les « jamais analysés » côté Sonar. C'est un constat.

5. Sélection par quotas (étape 4)
---------------------------------
  Budget                          : 200
  Sélectionnés                    : 1
    tête de strate                   : 1
  Namespaces représentés          : 1 (plafond 30 par namespace)
    Le témoin permettra d'écrire « couverture X sur les sélectionnés,
    Y sur un tirage du reste » — et si X et Y divergent, la règle
    de sélection est elle-même le sujet.

  Inventaire : 1 lignes.

6. Signaux de pratique sur les 1 sélectionnés
---------------------------------------------
    Pagination interrompue page 1 : HTTP 404
  Clé Maven (pom.xml) : 0 projets, dont 0 ont aussi une clé dans la CI.

                                            sélection       témoin
  Branche par défaut protégée                 0 (0 %)            —
  Au moins une MR fusionnée                   0 (0 %)            —
  Pipelines sur la branche par défaut         0 (0 %)            —
  CI configurée pour Sonar                    0 (0 %)            —
  Scan de sécurité dans la CI                 0 (0 %)            —
  CODEOWNERS présent                          0 (0 %)            —
  Aucun environnement déclaré               1 (100 %)            —

  1 projet à ≥20 commits sans une seule MR fusionnée.
    Le changement atteint la branche par défaut sans revue.

  Pratiques : 1 lignes.

Synthèse
--------
  10 appels API (10 GET, 0 GraphQL, 0 429 réessayés).
  Parc 1 → retenus 1 → frais 1 → éligibles 1 → sélectionnés 1.
  Fichiers écrits :
    inventaire : <TMP>/inventaire.csv
    pratiques  : <TMP>/pratiques.csv
    Les deux se lisent ensemble : pratiques.csv ne contient que les
    sélectionnés, l'inventaire porte le parc dont ils sortent. Colonnes : COLUMNS.md.
    Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start inventaire.csv`. Pour un autre outil : --comma.
  Rappel : tout classement qui ne dit pas ce qu'il a écarté se lit comme
  complet alors qu'il ne l'est pas. Le CSV porte le motif d'exclusion de
  chaque projet, et la sélection porte son motif d'entrée.


╔═ anEnterpriseProjectGetsEveryPracticeSignal/inventaire ═╗
<BOM>"id";"path";"name";"namespace";"default_branch";"visibility";"created_at";"last_activity_at";"archived";"fork";"mirror";"total_commits";"repo_size";"bucket";"last_commit";"commits_window";"commits_bots";"authors_window";"merge_commits";"reverts";"active_days";"commits_per_week";"tronque";"exclu";"fuite_filtre";"selectionne";"motif_selection"
"1";"team-a/alpha";"ALPHA";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"A(<100)";"2099-01-01T10:00Z";"27";"1";"2";"1";"1";"25";"2.10";"false";"";"false";"true";"tête de strate A(<100)"
"2";"team-a/beta";"BETA";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"A(<100)";"2099-01-01T10:00Z";"4";"0";"1";"0";"0";"4";"0.31";"false";"";"false";"true";"tête de strate A(<100)"
"3";"team-a/gamma";"GAMMA";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"A(<100)";"2099-01-01T10:00Z";"3";"0";"1";"0";"0";"3";"0.23";"false";"";"false";"true";"tête de strate A(<100)"
"4";"team-a/witness";"WITNESS";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"A(<100)";"2099-01-01T10:00Z";"2";"0";"2";"0";"0";"1";"0.16";"false";"";"false";"true";"témoin aléatoire"

╔═ anEnterpriseProjectGetsEveryPracticeSignal/pratiques ═╗
<BOM>"path";"motif_selection";"bucket";"commits_window";"authors_window";"branche_protegee";"push_verrouille";"mr_fusionnees";"ttm_median_j";"auto_merge";"notes_par_mr";"echantillon_approbations";"part_approuvee";"auto_approbation";"pipelines";"taux_succes";"incidents_rouges";"retour_au_vert_h";"rouge_non_resolu";"environnements";"deploiements";"dora_indispo";"ci_sonar";"ci_securite";"cle_sonar";"source_cle_sonar";"fichiers";"id";"fenetre_j";"lignes_modifiees";"commits_lignes";"lignes_tronquees";"cle_pom";"nom_pom"
"team-a/alpha";"tête de strate A(<100)";"A(<100)";"27";"2";"true";"true";"3";"1.75";"1";"2.00";"2";"0.50";"1";"8";"0.25";"2";"12.50";"1";"2";"5.50";"false";"false";"false";"";"";"README.md CODEOWNERS Dockerfile renovate.json";"1";"90";"375";"25";"false";"";""
"team-a/beta";"tête de strate A(<100)";"A(<100)";"4";"1";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"true";"false";"false";"";"";"";"2";"90";"0";"0";"false";"";""
"team-a/gamma";"tête de strate A(<100)";"A(<100)";"3";"1";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"true";"false";"false";"";"";"";"3";"90";"0";"0";"false";"";""
"team-a/witness";"témoin aléatoire";"A(<100)";"2";"2";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"true";"false";"false";"";"";"";"4";"90";"0";"0";"false";"";""

╔═ anEnterpriseProjectGetsEveryPracticeSignal/stdout ═╗
exit 0

Instance : http://gitlab.test

0. Connectivité, identité, licence
----------------------------------
  api/v4/user                      [OK]  alice (Alice A)
  api/v4/metadata                  [OK]  GitLab 17.0.0 · Enterprise

1. Inventaire (étape 0)
-----------------------
  Projets retournés               : 4
  Retenus pour analyse            : 4 (100 % du parc)

2. Filtre de fraîcheur (étape 1) — 0 appel
------------------------------------------
  Actifs sur 90 jours              : 4 / 4 (100 %)
    Coupe la plus rentable du pipeline : gratuite.

3. Activité réelle en commits (étape 2)
---------------------------------------
  Comptés par pagination REST     : 4 projets
  Avec au moins un commit humain  : 4

4. Plancher d'activité (étape 3)
--------------------------------
  Distribution des commits (déciles) : D1=27 D3=4 D5=3 D7=3 D9=2
  Plancher imposé (--floor)       : 0 commits
  Éligibles au tirage             : 4
  Cohorte faible activité         : 0
    Cette cohorte n'est pas perdue : elle se rapporte en effectifs,
    comme les « jamais analysés » côté Sonar. C'est un constat.

5. Sélection par quotas (étape 4)
---------------------------------
  Budget                          : 10
  Sélectionnés                    : 4
    témoin aléatoire                 : 1
    tête de strate                   : 3
  Namespaces représentés          : 1 (plafond 3 par namespace)
    Le témoin permettra d'écrire « couverture X sur les sélectionnés,
    Y sur un tirage du reste » — et si X et Y divergent, la règle
    de sélection est elle-même le sujet.

  Inventaire : 4 lignes.

6. Signaux de pratique sur les 4 sélectionnés
---------------------------------------------
    Pagination interrompue page 1 : HTTP 404
    Pagination interrompue page 1 : HTTP 404
    Pagination interrompue page 1 : HTTP 404
  Clé Maven (pom.xml) : 0 projets, dont 0 ont aussi une clé dans la CI.

                                            sélection       témoin
  Branche par défaut protégée                1 (33 %)      0 (0 %)
  Au moins une MR fusionnée                  1 (33 %)      0 (0 %)
  Pipelines sur la branche par défaut        1 (33 %)      0 (0 %)
  CI configurée pour Sonar                    0 (0 %)      0 (0 %)
  Scan de sécurité dans la CI                 0 (0 %)      0 (0 %)
  CODEOWNERS présent                         1 (33 %)      0 (0 %)
  Aucun environnement déclaré                 0 (0 %)      0 (0 %)

  Retour au vert (médiane des médianes) : 12.50 h sur 1 projet.
    Temps pendant lequel la branche par défaut reste cassée. Ce n'est pas
    une métrique DORA : sans environnement déclaré, aucune mise en production
    n'est datable, et rien ici ne s'y substitue.

  1 revert sur 1 projet.
    Signal de qualité direct, indépendant de Sonar et des environnements.

  Pratiques : 4 lignes.

Synthèse
--------
  38 appels API (38 GET, 0 GraphQL, 0 429 réessayés).
  Parc 4 → retenus 4 → frais 4 → éligibles 4 → sélectionnés 4.
  Fichiers écrits :
    inventaire : <TMP>/inventaire.csv
    pratiques  : <TMP>/pratiques.csv
    Les deux se lisent ensemble : pratiques.csv ne contient que les
    sélectionnés, l'inventaire porte le parc dont ils sortent. Colonnes : COLUMNS.md.
    Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start inventaire.csv`. Pour un autre outil : --comma.
  Rappel : tout classement qui ne dit pas ce qu'il a écarté se lit comme
  complet alors qu'il ne l'est pas. Le CSV porte le motif d'exclusion de
  chaque projet, et la sélection porte son motif d'entrée.


╔═ churnStopsAtTheCommitPageCap ═╗
<BOM>"path";"motif_selection";"bucket";"commits_window";"authors_window";"branche_protegee";"push_verrouille";"mr_fusionnees";"ttm_median_j";"auto_merge";"notes_par_mr";"echantillon_approbations";"part_approuvee";"auto_approbation";"pipelines";"taux_succes";"incidents_rouges";"retour_au_vert_h";"rouge_non_resolu";"environnements";"deploiements";"dora_indispo";"ci_sonar";"ci_securite";"cle_sonar";"source_cle_sonar";"fichiers";"id";"fenetre_j";"lignes_modifiees";"commits_lignes";"lignes_tronquees";"cle_pom";"nom_pom"
"team-a/alpha";"tête de strate A(<100)";"A(<100)";"100";"1";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"false";"false";"false";"";"";"";"1";"90";"200";"100";"true";"";""

╔═ doraAnswerThatIsNotAListIsIgnored ═╗
<BOM>"path";"motif_selection";"bucket";"commits_window";"authors_window";"branche_protegee";"push_verrouille";"mr_fusionnees";"ttm_median_j";"auto_merge";"notes_par_mr";"echantillon_approbations";"part_approuvee";"auto_approbation";"pipelines";"taux_succes";"incidents_rouges";"retour_au_vert_h";"rouge_non_resolu";"environnements";"deploiements";"dora_indispo";"ci_sonar";"ci_securite";"cle_sonar";"source_cle_sonar";"fichiers";"id";"fenetre_j";"lignes_modifiees";"commits_lignes";"lignes_tronquees";"cle_pom";"nom_pom"
"team-a/alpha";"tête de strate A(<100)";"A(<100)";"3";"1";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"false";"false";"false";"";"";"";"1";"90";"0";"0";"false";"";""

╔═ doraRefusedIsMarkedUnavailable ═╗
<BOM>"path";"motif_selection";"bucket";"commits_window";"authors_window";"branche_protegee";"push_verrouille";"mr_fusionnees";"ttm_median_j";"auto_merge";"notes_par_mr";"echantillon_approbations";"part_approuvee";"auto_approbation";"pipelines";"taux_succes";"incidents_rouges";"retour_au_vert_h";"rouge_non_resolu";"environnements";"deploiements";"dora_indispo";"ci_sonar";"ci_securite";"cle_sonar";"source_cle_sonar";"fichiers";"id";"fenetre_j";"lignes_modifiees";"commits_lignes";"lignes_tronquees";"cle_pom";"nom_pom"
"team-a/alpha";"tête de strate A(<100)";"A(<100)";"3";"1";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"true";"false";"false";"";"";"";"1";"90";"0";"0";"false";"";""
"team-a/beta";"tête de strate A(<100)";"A(<100)";"3";"1";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"false";"false";"false";"";"";"";"2";"90";"0";"0";"false";"";""

╔═ manyMergeRequestsArePagedThreeTimesAndTenAreSampled ═╗
<BOM>"path";"motif_selection";"bucket";"commits_window";"authors_window";"branche_protegee";"push_verrouille";"mr_fusionnees";"ttm_median_j";"auto_merge";"notes_par_mr";"echantillon_approbations";"part_approuvee";"auto_approbation";"pipelines";"taux_succes";"incidents_rouges";"retour_au_vert_h";"rouge_non_resolu";"environnements";"deploiements";"dora_indispo";"ci_sonar";"ci_securite";"cle_sonar";"source_cle_sonar";"fichiers";"id";"fenetre_j";"lignes_modifiees";"commits_lignes";"lignes_tronquees";"cle_pom";"nom_pom"
"team-a/alpha";"tête de strate A(<100)";"A(<100)";"3";"1";"false";"false";"300";"";"0";"0.00";"10";"1.00";"10";"0";"";"";"";"";"0";"";"false";"false";"false";"";"";"";"1";"90";"0";"0";"false";"";""

╔═ [end of file] ═╗
