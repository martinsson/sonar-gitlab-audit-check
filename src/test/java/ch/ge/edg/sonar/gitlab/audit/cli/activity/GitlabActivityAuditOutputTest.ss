╔═ outDirWithDeepWritesBothFilesAndSaysHowToReadThem/pratiques ═╗
<BOM>"path";"motif_selection";"bucket";"commits_window";"authors_window";"branche_protegee";"push_verrouille";"mr_fusionnees";"ttm_median_j";"auto_merge";"notes_par_mr";"echantillon_approbations";"part_approuvee";"auto_approbation";"pipelines";"taux_succes";"incidents_rouges";"retour_au_vert_h";"rouge_non_resolu";"environnements";"deploiements";"dora_indispo";"ci_sonar";"ci_securite";"cle_sonar";"source_cle_sonar";"fichiers";"id";"fenetre_j";"lignes_modifiees";"commits_lignes";"lignes_tronquees";"cle_pom";"nom_pom"
"team-a/alpha";"tête de strate A(<100)";"A(<100)";"6";"1";"false";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"false";"false";"false";"";"";"README.md";"1";"90";"0";"0";"false";"";""

╔═ outDirWithDeepWritesBothFilesAndSaysHowToReadThem/stdout ═╗
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
  Distribution des commits (déciles) : D1=6 D3=6 D5=6 D7=6 D9=6
  Plancher déduit des données     : 5 commits sur 90 jours
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
  Clé Maven (pom.xml) : 0 projets, dont 0 ont aussi une clé dans la CI.

                                            sélection       témoin
  Branche par défaut protégée                 0 (0 %)            —
  Au moins une MR fusionnée                   0 (0 %)            —
  Pipelines sur la branche par défaut         0 (0 %)            —
  CI configurée pour Sonar                    0 (0 %)            —
  Scan de sécurité dans la CI                 0 (0 %)            —
  CODEOWNERS présent                          0 (0 %)            —
  Aucun environnement déclaré                 0 (0 %)            —

  Pratiques : 1 lignes.

Synthèse
--------
  10 appels API (10 GET, 0 GraphQL, 0 429 réessayés).
  Parc 1 → retenus 1 → frais 1 → éligibles 1 → sélectionnés 1.
  Fichiers écrits :
    inventaire : <TMP>/audit/inventaire.csv
    pratiques  : <TMP>/audit/pratiques.csv
    Les deux se lisent ensemble : pratiques.csv ne contient que les
    sélectionnés, l'inventaire porte le parc dont ils sortent. Colonnes : COLUMNS.md.
    Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start inventaire.csv`. Pour un autre outil : --comma.
  Rappel : tout classement qui ne dit pas ce qu'il a écarté se lit comme
  complet alors qu'il ne l'est pas. Le CSV porte le motif d'exclusion de
  chaque projet, et la sélection porte son motif d'entrée.


╔═ [end of file] ═╗
