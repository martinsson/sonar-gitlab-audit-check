╔═ oddInventoryDates ═╗
<BOM>"id";"path";"name";"namespace";"default_branch";"visibility";"created_at";"last_activity_at";"archived";"fork";"mirror";"total_commits";"repo_size";"bucket";"last_commit";"commits_window";"commits_bots";"authors_window";"merge_commits";"reverts";"active_days";"commits_per_week";"tronque";"exclu";"fuite_filtre";"selectionne";"motif_selection"
"1";"team-a/fork";"FORK";"team-a";"main";"private";"";"2099-01-01T10:00Z";"false";"true";"false";"50";"1000";"A(<100)";"2099-01-01T10:00Z";"6";"0";"1";"0";"0";"6";"0.47";"false";"";"false";"true";"tête de strate A(<100)"
"2";"team-a/fork-2";"FORK-2";"team-a";"main";"private";"2020-01-01T00:00Z";"";"false";"true";"false";"50";"1000";"";"";"";"";"";"";"";"";"";"false";"inactif > 90j";"false";"false";""
"3";"team-a/silent";"SILENT";"team-a";"main";"private";"2020-01-01T00:00Z";"";"false";"false";"false";"50";"1000";"";"";"";"";"";"";"";"";"";"false";"inactif > 90j";"false";"false";""

╔═ oddPracticeAnswersAreReadCautiously/pratiques ═╗
<BOM>"path";"motif_selection";"bucket";"commits_window";"authors_window";"branche_protegee";"push_verrouille";"mr_fusionnees";"ttm_median_j";"auto_merge";"notes_par_mr";"echantillon_approbations";"part_approuvee";"auto_approbation";"pipelines";"taux_succes";"incidents_rouges";"retour_au_vert_h";"rouge_non_resolu";"environnements";"deploiements";"dora_indispo";"ci_sonar";"ci_securite";"cle_sonar";"source_cle_sonar";"fichiers";"id";"fenetre_j";"lignes_modifiees";"commits_lignes";"lignes_tronquees";"cle_pom";"nom_pom"
"team-a/e1";"tête de strate A(<100)";"A(<100)";"25";"1";"true";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"false";"false";"false";"";"";"";"1";"90";"0";"0";"false";"";""
"team-a/e2";"tête de strate A(<100)";"A(<100)";"3";"1";"true";"false";"0";"";"0";"";"";"";"0";"0";"";"";"";"";"";"";"false";"true";"false";"tpl-key";"includes suivis";".gitlab-ci.yml";"2";"90";"0";"0";"false";"";""
"team-a/e3";"tête de strate A(<100)";"A(<100)";"25";"1";"false";"false";"0";"";"0";"";"";"";"0";"1";"0.00";"";"";"";"";"";"false";"true";"false";"tpl-key";"includes suivis";".gitlab-ci.yml";"3";"90";"0";"0";"false";"";""
"team-a/e4";"tête de strate A(<100)";"A(<100)";"3";"1";"false";"false";"0";"";"0";"";"";"";"0";"2";"0.50";"1";"2.00";"0";"";"";"false";"false";"false";"";"";"";"4";"90";"0";"0";"false";"";""
"team-a/e5";"tête de strate A(<100)";"A(<100)";"3";"1";"false";"false";"0";"";"0";"";"";"";"0";"2";"0.50";"1";"5.00";"0";"";"";"false";"false";"false";"";"";"";"5";"90";"0";"0";"false";"";""

╔═ oddPracticeAnswersAreReadCautiously/stdout ═╗
exit 0

Instance : http://gitlab.test

0. Connectivité, identité, licence
----------------------------------
  api/v4/user                      [OK]  alice (Alice A)
  api/v4/metadata                  [VIDE]  GitLab 17.0.0 · Community — DORA et approbations indisponibles

1. Inventaire (étape 0)
-----------------------
  Projets retournés               : 5
  Retenus pour analyse            : 5 (100 % du parc)

2. Filtre de fraîcheur (étape 1) — 0 appel
------------------------------------------
  Actifs sur 90 jours              : 5 / 5 (100 %)
    Coupe la plus rentable du pipeline : gratuite.

3. Activité réelle en commits (étape 2)
---------------------------------------
  Comptés par pagination REST     : 5 projets
  Avec au moins un commit humain  : 5

4. Plancher d'activité (étape 3)
--------------------------------
  Distribution des commits (déciles) : D1=25 D3=25 D5=3 D7=3 D9=3
  Plancher imposé (--floor)       : 0 commits
  Éligibles au tirage             : 5
  Cohorte faible activité         : 0
    Cette cohorte n'est pas perdue : elle se rapporte en effectifs,
    comme les « jamais analysés » côté Sonar. C'est un constat.

5. Sélection par quotas (étape 4)
---------------------------------
  Budget                          : 200
  Sélectionnés                    : 5
    tête de strate                   : 5
  Namespaces représentés          : 1 (plafond 30 par namespace)
    Le témoin permettra d'écrire « couverture X sur les sélectionnés,
    Y sur un tirage du reste » — et si X et Y divergent, la règle
    de sélection est elle-même le sujet.

  Inventaire : 5 lignes.

6. Signaux de pratique sur les 5 sélectionnés
---------------------------------------------
    Pagination interrompue page 1 : HTTP 404
    Pagination interrompue page 1 : HTTP 404

  CI lue : 0 par ci/lint, 2 par repli, 0 depuis le cache.
    ci/lint refusé (HTTP 401) : le jeton n'a pas le droit de 
    déplier la configuration. Le repli ne suit que les includes
    « project: » qu'il sait lire — ci_sonar y sous-compte. D'un
    « component: », il ne lit que le project_key passé en inputs.
  Clé Maven (pom.xml) : 0 projets, dont 0 ont aussi une clé dans la CI.

                                            sélection       témoin
  Branche par défaut protégée                2 (40 %)            —
  Au moins une MR fusionnée                   0 (0 %)            —
  Pipelines sur la branche par défaut        3 (60 %)            —
  CI configurée pour Sonar                   2 (40 %)            —
  Scan de sécurité dans la CI                 0 (0 %)            —
  CODEOWNERS présent                          0 (0 %)            —
  Aucun environnement déclaré                 0 (0 %)            —

  Retour au vert (médiane des médianes) : 3.50 h sur 2 projets.
    Temps pendant lequel la branche par défaut reste cassée. Ce n'est pas
    une métrique DORA : sans environnement déclaré, aucune mise en production
    n'est datable, et rien ici ne s'y substitue.

  3 reverts sur 2 projets.
    Signal de qualité direct, indépendant de Sonar et des environnements.

  2 projets à ≥20 commits sans une seule MR fusionnée.
    Le changement atteint la branche par défaut sans revue.

  Pratiques : 5 lignes.

Synthèse
--------
  46 appels API (46 GET, 0 GraphQL, 0 429 réessayés).
  Parc 5 → retenus 5 → frais 5 → éligibles 5 → sélectionnés 5.
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
