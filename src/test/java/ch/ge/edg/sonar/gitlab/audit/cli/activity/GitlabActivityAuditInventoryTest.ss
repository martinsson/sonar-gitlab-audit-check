╔═ aProjectsFileIsReadOneProjectAtATime ═╗
exit 0

Instance : http://gitlab.test

0. Connectivité, identité, licence
----------------------------------
  api/v4/user                      [OK]  alice (Alice A)
  api/v4/metadata                  [VIDE]  GitLab 17.0.0 · Community — DORA et approbations indisponibles

1. Inventaire (étape 0)
-----------------------
    team-a/missing                           HTTP 404 — ignoré
    team-a/secret                            HTTP 403 — ignoré
  Projets retournés               : 1
  Retenus pour analyse            : 1 (100 % du parc)
    1 réponses 403 : le jeton ne voit pas tout le parc.
    Un trou de permission raccourcit la liste sans lever d'erreur.

2. Filtre de fraîcheur (étape 1) — 0 appel
------------------------------------------
  Actifs sur 90 jours              : 1 / 1 (100 %)
    Coupe la plus rentable du pipeline : gratuite.

3. Activité réelle en commits (étape 2)
---------------------------------------
  GraphQL indisponible sur cette instance — repli REST.
  Comptés par pagination REST     : 1 projets
  Avec au moins un commit humain  : 1

4. Plancher d'activité (étape 3)
--------------------------------
  Distribution des commits (déciles) : D1=7 D3=7 D5=7 D7=7 D9=7
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

Synthèse
--------
  7 appels API (6 GET, 1 GraphQL, 0 429 réessayés).
  Parc 1 → retenus 1 → frais 1 → éligibles 1 → sélectionnés 1.
  Aucun fichier écrit : ajoute --out-dir <rép> pour conserver l'inventaire
  et les pratiques. Le rapport ci-dessus ne se rejoue pas sans les CSV.
  Rappel : tout classement qui ne dit pas ce qu'il a écarté se lit comme
  complet alors qu'il ne l'est pas. Le CSV porte le motif d'exclusion de
  chaque projet, et la sélection porte son motif d'entrée.


╔═ everyExclusionReasonIsCountedAndWritten/csv ═╗
<BOM>"id";"path";"name";"namespace";"default_branch";"visibility";"created_at";"last_activity_at";"archived";"fork";"mirror";"total_commits";"repo_size";"bucket";"last_commit";"commits_window";"commits_bots";"authors_window";"merge_commits";"reverts";"active_days";"commits_per_week";"tronque";"exclu";"fuite_filtre";"selectionne";"motif_selection"
"1";"team-a/alpha";"ALPHA";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"A(<100)";"2099-01-01T10:00Z";"6";"0";"1";"0";"0";"6";"0.47";"false";"";"false";"true";"tête de strate A(<100)"
"2";"team-a/legacy/archived-one";"ARCHIVED-ONE";"team-a/legacy";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"true";"false";"false";"50";"1000";"";"";"";"";"";"";"";"";"";"false";"archivé";"false";"false";""
"3";"team-a/empty-one";"EMPTY-ONE";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"";"";"";"";"";"";"";"";"";"false";"dépôt vide";"false";"false";""
"4";"team-a/mirror-one";"MIRROR-ONE";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"true";"50";"1000";"";"";"";"";"";"";"";"";"";"false";"miroir";"false";"false";""
"5";"team-a/fork-same";"FORK-SAME";"team-a";"main";"private";"2020-01-01T00:00Z";"2020-01-01T12:00Z";"false";"true";"false";"50";"1000";"";"";"";"";"";"";"";"";"";"false";"fork non divergé";"false";"false";""
"6";"team-a/fork-diverged";"FORK-DIVERGED";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"true";"false";"50";"1000";"";"2099-01-01T10:00Z";"3";"0";"1";"0";"0";"3";"0.23";"false";"sous le plancher (3 commits)";"false";"false";""
"7";"team-a/no-branch";"NO-BRANCH";"team-a";"";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"";"";"";"";"";"";"";"";"";"false";"sans branche par défaut";"false";"false";""
"8";"team-a/excluded-new";"EXCLUDED-NEW";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"";"";"";"";"";"";"";"";"";"false";"liste d'exclusion";"false";"false";""
"9";"team-a/sleepy";"SLEEPY";"team-a";"main";"private";"2020-01-01T00:00Z";"2000-01-01T10:00Z";"false";"false";"false";"50";"1000";"";"";"";"";"";"";"";"";"";"false";"inactif > 90j";"false";"false";""
"10";"team-a/excluded-old";"EXCLUDED-OLD";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"true";"false";"false";"50";"1000";"";"";"";"";"";"";"";"";"";"false";"liste d'exclusion";"false";"false";""
"11";"team-a/fork-day-later";"FORK-DAY-LATER";"team-a";"main";"private";"2020-01-01T00:00Z";"2020-01-02T00:00Z";"false";"true";"false";"50";"1000";"";"";"1";"";"";"";"";"";"0.08";"false";"inactif > 90j";"true";"false";""

╔═ everyExclusionReasonIsCountedAndWritten/stdout ═╗
exit 0

Instance : http://gitlab.test

0. Connectivité, identité, licence
----------------------------------
  api/v4/user                      [OK]  alice (Alice A)
  api/v4/metadata                  [VIDE]  GitLab 17.0.0 · Community — DORA et approbations indisponibles

1. Inventaire (étape 0)
-----------------------
  Projets retournés               : 11
  Liste d'exclusion               : <TMP>/exclusions.txt (1 entrées)
    exclu — archivé                : 1
    exclu — dépôt vide             : 1
    exclu — fork non divergé       : 1
    exclu — liste d'exclusion      : 2
    exclu — miroir                 : 1
    exclu — sans branche par défaut : 1
  Retenus pour analyse            : 4 (36 % du parc)

2. Filtre de fraîcheur (étape 1) — 0 appel
------------------------------------------
  Actifs sur 90 jours              : 2 / 4 (50 %)
    Coupe la plus rentable du pipeline : gratuite.
  Échantillon de contrôle sous la coupe : 2 projets, 1 faux négatif (50 %)
    Le filtre laisse passer des projets actifs. Chiffre à publier
    tel quel dans le rapport : la sélection n'est pas exhaustive.

3. Activité réelle en commits (étape 2)
---------------------------------------
  GraphQL indisponible sur cette instance — repli REST.
  Comptés par pagination REST     : 2 projets
  Avec au moins un commit humain  : 2

4. Plancher d'activité (étape 3)
--------------------------------
  Distribution des commits (déciles) : D1=6 D3=6 D5=3 D7=3 D9=3
  Plancher déduit des données     : 5 commits sur 90 jours
  Éligibles au tirage             : 1
  Cohorte faible activité         : 1
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

  Inventaire : 11 lignes.

Synthèse
--------
  8 appels API (7 GET, 1 GraphQL, 0 429 réessayés).
  Parc 11 → retenus 4 → frais 2 → éligibles 1 → sélectionnés 1.
  Filtre de fraîcheur validé sur 2 projets : 1 faux négatif.
  Fichiers écrits :
    inventaire : <TMP>/not/yet/inv.csv
    Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start inv.csv`. Pour un autre outil : --comma.
  Rappel : tout classement qui ne dit pas ce qu'il a écarté se lit comme
  complet alors qu'il ne l'est pas. Le CSV porte le motif d'exclusion de
  chaque projet, et la sélection porte son motif d'entrée.


╔═ outDirGetsTheInventory/inventaire ═╗
<BOM>"id";"path";"name";"namespace";"default_branch";"visibility";"created_at";"last_activity_at";"archived";"fork";"mirror";"total_commits";"repo_size";"bucket";"last_commit";"commits_window";"commits_bots";"authors_window";"merge_commits";"reverts";"active_days";"commits_per_week";"tronque";"exclu";"fuite_filtre";"selectionne";"motif_selection"
"1";"team-a/alpha";"ALPHA";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"A(<100)";"2099-01-01T10:00Z";"6";"0";"1";"0";"0";"6";"0.47";"false";"";"false";"true";"tête de strate A(<100)"
"2";"team-a/legacy/archived-one";"ARCHIVED-ONE";"team-a/legacy";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"true";"false";"false";"50";"1000";"";"";"";"";"";"";"";"";"";"false";"archivé";"false";"false";""
"3";"team-a/empty-one";"EMPTY-ONE";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"";"";"";"";"";"";"";"";"";"false";"dépôt vide";"false";"false";""
"4";"team-a/mirror-one";"MIRROR-ONE";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"true";"50";"1000";"";"";"";"";"";"";"";"";"";"false";"miroir";"false";"false";""
"5";"team-a/fork-same";"FORK-SAME";"team-a";"main";"private";"2020-01-01T00:00Z";"2020-01-01T12:00Z";"false";"true";"false";"50";"1000";"";"";"";"";"";"";"";"";"";"false";"fork non divergé";"false";"false";""
"6";"team-a/fork-diverged";"FORK-DIVERGED";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"true";"false";"50";"1000";"";"2099-01-01T10:00Z";"3";"0";"1";"0";"0";"3";"0.23";"false";"sous le plancher (3 commits)";"false";"false";""
"7";"team-a/no-branch";"NO-BRANCH";"team-a";"";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"";"";"";"";"";"";"";"";"";"false";"sans branche par défaut";"false";"false";""
"8";"team-a/excluded-new";"EXCLUDED-NEW";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"";"";"";"";"";"";"";"";"";"false";"liste d'exclusion";"false";"false";""
"9";"team-a/sleepy";"SLEEPY";"team-a";"main";"private";"2020-01-01T00:00Z";"2000-01-01T10:00Z";"false";"false";"false";"50";"1000";"";"";"";"";"";"";"";"";"";"false";"inactif > 90j";"false";"false";""
"10";"team-a/excluded-old";"EXCLUDED-OLD";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"true";"false";"false";"50";"1000";"";"";"";"";"";"";"";"";"";"false";"liste d'exclusion";"false";"false";""
"11";"team-a/fork-day-later";"FORK-DAY-LATER";"team-a";"main";"private";"2020-01-01T00:00Z";"2020-01-02T00:00Z";"false";"true";"false";"50";"1000";"";"";"";"";"";"";"";"";"";"false";"inactif > 90j";"false";"false";""

╔═ [end of file] ═╗
