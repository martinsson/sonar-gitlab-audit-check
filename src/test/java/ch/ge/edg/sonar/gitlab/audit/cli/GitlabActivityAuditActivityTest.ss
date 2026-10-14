╔═ activeWithinSkipsQuietProjectsByTheirGraphqlDate/csv ═╗
<BOM>"id";"path";"name";"namespace";"default_branch";"visibility";"created_at";"last_activity_at";"archived";"fork";"mirror";"total_commits";"repo_size";"bucket";"last_commit";"commits_window";"commits_bots";"authors_window";"merge_commits";"reverts";"active_days";"commits_per_week";"tronque";"exclu";"fuite_filtre";"selectionne";"motif_selection"
"1";"team-a/alpha";"ALPHA";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"A(<100)";"2099-01-01T10:00Z";"6";"0";"1";"0";"0";"6";"0.00";"false";"";"false";"true";"tête de strate A(<100)"
"2";"team-a/quiet";"QUIET";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"";"2000-01-01T10:00Z";"";"";"";"";"";"";"";"false";"hors coupe d'activité (dernier commit > 30j)";"false";"false";""

╔═ activeWithinSkipsQuietProjectsByTheirGraphqlDate/stdout ═╗
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
  Actifs sur 36500 jours              : 2 / 2 (100 %)
    Coupe la plus rentable du pipeline : gratuite.

3. Activité réelle en commits (étape 2)
---------------------------------------
  Dernier commit daté par GraphQL : 2 projets en 1 requêtes
    dont aucun commit dans la fenêtre : 0
    (last_activity_at récent sans commit récent : tickets, wiki, CI)
  Coupe --active-within 30        : 1 projets écartés sur 2
    Écartés sans être comptés : « pas regardé » n'est pas « rien vu ».
    Gratuit — la date vient de la passe GraphQL, pas d'un appel de plus.
  Comptés par pagination REST     : 1 projets
  Avec au moins un commit humain  : 1

4. Plancher d'activité (étape 3)
--------------------------------
  Distribution des commits (déciles) : D1=6 D3=6 D5=6 D7=6 D9=6
  Plancher déduit des données     : 5 commits sur 36500 jours
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

  Inventaire : 2 lignes.

Synthèse
--------
  5 appels API (4 GET, 1 GraphQL, 0 429 réessayés).
  Parc 2 → retenus 2 → frais 2 → éligibles 1 → sélectionnés 1.
  1 projets laissés de côté par --active-within 30, non mesurés.
  Fichiers écrits :
    inventaire : <TMP>/inv.csv
    Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start inv.csv`. Pour un autre outil : --comma.
  Rappel : tout classement qui ne dit pas ce qu'il a écarté se lit comme
  complet alors qu'il ne l'est pas. Le CSV porte le motif d'exclusion de
  chaque projet, et la sélection porte son motif d'entrée.


╔═ activeWithinThatLeavesNothingToCount ═╗
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
  Actifs sur 36500 jours              : 1 / 1 (100 %)
    Coupe la plus rentable du pipeline : gratuite.

3. Activité réelle en commits (étape 2)
---------------------------------------
  Coupe --active-within 1         : 1 projets écartés sur 1
    Écartés sans être comptés : « pas regardé » n'est pas « rien vu ».
    Gratuit — GraphQL indisponible, la coupe porte sur last_activity_at,
    qui majore l'activité : elle écarte donc moins, jamais à tort.
  Comptés par pagination REST     : 0 projets
  Avec au moins un commit humain  : 0

4. Plancher d'activité (étape 3)
--------------------------------
  Distribution des commits (déciles) : D1=0 D3=0 D5=0 D7=0 D9=0
  Plancher déduit des données     : 5 commits sur 36500 jours
  Éligibles au tirage             : 0
  Cohorte faible activité         : 0
    Cette cohorte n'est pas perdue : elle se rapporte en effectifs,
    comme les « jamais analysés » côté Sonar. C'est un constat.

5. Sélection par quotas (étape 4)
---------------------------------
  Aucun projet au-dessus du plancher.

  Inventaire : 1 lignes.

Synthèse
--------
  3 appels API (3 GET, 0 GraphQL, 0 429 réessayés).
  Parc 1 → retenus 1 → frais 1 → éligibles 0 → sélectionnés 0.
  1 projets laissés de côté par --active-within 1, non mesurés.
  Fichiers écrits :
    inventaire : <TMP>/inv.csv
    Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start inv.csv`. Pour un autre outil : --comma.
  Rappel : tout classement qui ne dit pas ce qu'il a écarté se lit comme
  complet alors qu'il ne l'est pas. Le CSV porte le motif d'exclusion de
  chaque projet, et la sélection porte son motif d'entrée.


╔═ botsMergesRevertsAndIdentitiesAreSortedOut/csv ═╗
<BOM>"id";"path";"name";"namespace";"default_branch";"visibility";"created_at";"last_activity_at";"archived";"fork";"mirror";"total_commits";"repo_size";"bucket";"last_commit";"commits_window";"commits_bots";"authors_window";"merge_commits";"reverts";"active_days";"commits_per_week";"tronque";"exclu";"fuite_filtre";"selectionne";"motif_selection"
"1";"team-a/alpha";"ALPHA";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"A(<100)";"2099-01-03T10:00Z";"8";"1";"3";"1";"2";"3";"0.62";"false";"";"false";"true";"tête de strate A(<100)"
"2";"team-a/robots";"ROBOTS";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"";"";"0";"2";"0";"0";"0";"0";"0.00";"false";"activité robotique seule (2 commits de bots)";"false";"false";""

╔═ botsMergesRevertsAndIdentitiesAreSortedOut/stdout ═╗
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
  GraphQL indisponible sur cette instance — repli REST.
  Comptés par pagination REST     : 2 projets
  Avec au moins un commit humain  : 1
  Activité exclusivement robotique : 1
    Renovate seul sur un dépôt est un constat, pas une ligne vide.

4. Plancher d'activité (étape 3)
--------------------------------
  Distribution des commits (déciles) : D1=8 D3=8 D5=0 D7=0 D9=0
  Plancher imposé (--floor)       : 1 commits
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

  Inventaire : 2 lignes.

Synthèse
--------
  6 appels API (5 GET, 1 GraphQL, 0 429 réessayés).
  Parc 2 → retenus 2 → frais 2 → éligibles 1 → sélectionnés 1.
  Fichiers écrits :
    inventaire : <TMP>/inv.csv
    Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start inv.csv`. Pour un autre outil : --comma.
  Rappel : tout classement qui ne dit pas ce qu'il a écarté se lit comme
  complet alors qu'il ne l'est pas. Le CSV porte le motif d'exclusion de
  chaque projet, et la sélection porte son motif d'entrée.


╔═ commitPagingStopsAtTheCapAndSaysItIsAFloor/csv ═╗
<BOM>"id";"path";"name";"namespace";"default_branch";"visibility";"created_at";"last_activity_at";"archived";"fork";"mirror";"total_commits";"repo_size";"bucket";"last_commit";"commits_window";"commits_bots";"authors_window";"merge_commits";"reverts";"active_days";"commits_per_week";"tronque";"exclu";"fuite_filtre";"selectionne";"motif_selection"
"1";"team-a/capped";"CAPPED";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"A(<100)";"2099-01-01T10:00Z";"200";"0";"1";"0";"0";"200";"15.56";"true";"";"false";"true";"tête de strate A(<100)"
"2";"team-a/two-pages";"TWO-PAGES";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"A(<100)";"2099-01-01T10:00Z";"150";"0";"1";"0";"0";"150";"11.67";"false";"";"false";"true";"tête de strate A(<100)"
"3";"team-a/exact";"EXACT";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"A(<100)";"2099-01-01T10:00Z";"100";"0";"1";"0";"0";"100";"7.78";"false";"";"false";"true";"tête de strate A(<100)"

╔═ commitPagingStopsAtTheCapAndSaysItIsAFloor/stdout ═╗
exit 0

Instance : http://gitlab.test

0. Connectivité, identité, licence
----------------------------------
  api/v4/user                      [OK]  alice (Alice A)
  api/v4/metadata                  [VIDE]  GitLab 17.0.0 · Community — DORA et approbations indisponibles

1. Inventaire (étape 0)
-----------------------
  Projets retournés               : 3
  Retenus pour analyse            : 3 (100 % du parc)

2. Filtre de fraîcheur (étape 1) — 0 appel
------------------------------------------
  Actifs sur 90 jours              : 3 / 3 (100 %)
    Coupe la plus rentable du pipeline : gratuite.

3. Activité réelle en commits (étape 2)
---------------------------------------
  Comptés par pagination REST     : 3 projets
  Avec au moins un commit humain  : 3

4. Plancher d'activité (étape 3)
--------------------------------
  Distribution des commits (déciles) : D1=200 D3=200 D5=150 D7=100 D9=100
  Plancher déduit des données     : 5 commits sur 90 jours
  Éligibles au tirage             : 3
  Cohorte faible activité         : 0
    Cette cohorte n'est pas perdue : elle se rapporte en effectifs,
    comme les « jamais analysés » côté Sonar. C'est un constat.

5. Sélection par quotas (étape 4)
---------------------------------
  Budget                          : 200
  Sélectionnés                    : 3
    tête de strate                   : 3
  Namespaces représentés          : 1 (plafond 30 par namespace)
    Le témoin permettra d'écrire « couverture X sur les sélectionnés,
    Y sur un tirage du reste » — et si X et Y divergent, la règle
    de sélection est elle-même le sujet.

  Inventaire : 3 lignes.

Synthèse
--------
  9 appels API (9 GET, 0 GraphQL, 0 429 réessayés).
  Parc 3 → retenus 3 → frais 3 → éligibles 3 → sélectionnés 3.
  1 projets tronqués à 2 pages de commits : leur volume est
  un plancher, pas un compte. Ils sont marqués dans le CSV.
  Fichiers écrits :
    inventaire : <TMP>/inv.csv
    Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start inv.csv`. Pour un autre outil : --comma.
  Rappel : tout classement qui ne dit pas ce qu'il a écarté se lit comme
  complet alors qu'il ne l'est pas. Le CSV porte le motif d'exclusion de
  chaque projet, et la sélection porte son motif d'entrée.


╔═ graphqlDatesTheLastCommitAndSparesTheStaleProjects/csv ═╗
<BOM>"id";"path";"name";"namespace";"default_branch";"visibility";"created_at";"last_activity_at";"archived";"fork";"mirror";"total_commits";"repo_size";"bucket";"last_commit";"commits_window";"commits_bots";"authors_window";"merge_commits";"reverts";"active_days";"commits_per_week";"tronque";"exclu";"fuite_filtre";"selectionne";"motif_selection"
"1";"team-a/alpha";"ALPHA";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"A(<100)";"2099-02-01T00:00Z";"6";"0";"1";"0";"0";"6";"0.47";"false";"";"false";"true";"tête de strate A(<100)"
"2";"team-a/beta";"BETA";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"";"2000-01-01T10:00Z";"0";"0";"0";"";"";"";"0.00";"false";"sous le plancher (0 commits)";"false";"false";""
"3";"team-a/gamma";"GAMMA";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"A(<100)";"2099-01-01T10:00Z";"8";"0";"1";"0";"0";"8";"0.62";"false";"";"false";"true";"tête de strate A(<100)"

╔═ graphqlDatesTheLastCommitAndSparesTheStaleProjects/stdout ═╗
exit 0

Instance : http://gitlab.test

0. Connectivité, identité, licence
----------------------------------
  api/v4/user                      [OK]  alice (Alice A)
  api/v4/metadata                  [VIDE]  GitLab 17.0.0 · Community — DORA et approbations indisponibles

1. Inventaire (étape 0)
-----------------------
  Projets retournés               : 3
  Retenus pour analyse            : 3 (100 % du parc)

2. Filtre de fraîcheur (étape 1) — 0 appel
------------------------------------------
  Actifs sur 90 jours              : 3 / 3 (100 %)
    Coupe la plus rentable du pipeline : gratuite.

3. Activité réelle en commits (étape 2)
---------------------------------------
  Dernier commit daté par GraphQL : 2 projets en 1 requêtes
    dont aucun commit dans la fenêtre : 1
    (last_activity_at récent sans commit récent : tickets, wiki, CI)
  Comptés par pagination REST     : 2 projets
  Avec au moins un commit humain  : 2

4. Plancher d'activité (étape 3)
--------------------------------
  Distribution des commits (déciles) : D1=8 D3=8 D5=6 D7=0 D9=0
  Plancher déduit des données     : 5 commits sur 90 jours
  Éligibles au tirage             : 2
  Cohorte faible activité         : 1
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

  Inventaire : 3 lignes.

Synthèse
--------
  6 appels API (5 GET, 1 GraphQL, 0 429 réessayés).
  Parc 3 → retenus 3 → frais 3 → éligibles 2 → sélectionnés 2.
  Fichiers écrits :
    inventaire : <TMP>/inv.csv
    Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start inv.csv`. Pour un autre outil : --comma.
  Rappel : tout classement qui ne dit pas ce qu'il a écarté se lit comme
  complet alors qu'il ne l'est pas. Le CSV porte le motif d'exclusion de
  chaque projet, et la sélection porte son motif d'entrée.


╔═ theValidationSampleCountsCommitsUnderTheCut/csv ═╗
<BOM>"id";"path";"name";"namespace";"default_branch";"visibility";"created_at";"last_activity_at";"archived";"fork";"mirror";"total_commits";"repo_size";"bucket";"last_commit";"commits_window";"commits_bots";"authors_window";"merge_commits";"reverts";"active_days";"commits_per_week";"tronque";"exclu";"fuite_filtre";"selectionne";"motif_selection"
"1";"team-a/alpha";"ALPHA";"team-a";"main";"private";"2020-01-01T00:00Z";"2099-01-01T10:00Z";"false";"false";"false";"50";"1000";"A(<100)";"2099-01-01T10:00Z";"6";"0";"1";"0";"0";"6";"0.47";"false";"";"false";"true";"tête de strate A(<100)"
"10";"team-a/sleepy-0";"SLEEPY-0";"team-a";"main";"private";"2020-01-01T00:00Z";"2000-01-01T10:00Z";"false";"false";"false";"50";"1000";"";"";"2";"";"";"";"";"";"0.16";"false";"inactif > 90j";"true";"false";""
"11";"team-a/sleepy-1";"SLEEPY-1";"team-a";"main";"private";"2020-01-01T00:00Z";"2000-01-01T10:00Z";"false";"false";"false";"50";"1000";"";"";"3";"";"";"";"";"";"0.23";"false";"inactif > 90j";"true";"false";""
"12";"team-a/sleepy-2";"SLEEPY-2";"team-a";"main";"private";"2020-01-01T00:00Z";"2000-01-01T10:00Z";"false";"false";"false";"50";"1000";"";"";"";"";"";"";"";"";"";"false";"inactif > 90j";"false";"false";""
"13";"team-a/sleepy-3";"SLEEPY-3";"team-a";"main";"private";"2020-01-01T00:00Z";"2000-01-01T10:00Z";"false";"false";"false";"50";"1000";"";"";"";"";"";"";"";"";"";"false";"inactif > 90j";"false";"false";""

╔═ theValidationSampleCountsCommitsUnderTheCut/stdout ═╗
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
  Actifs sur 90 jours              : 1 / 5 (20 %)
    Coupe la plus rentable du pipeline : gratuite.
  Échantillon de contrôle sous la coupe : 4 projets, 2 faux négatifs (50 %)
    Le filtre laisse passer des projets actifs. Chiffre à publier
    tel quel dans le rapport : la sélection n'est pas exhaustive.

3. Activité réelle en commits (étape 2)
---------------------------------------
  GraphQL indisponible sur cette instance — repli REST.
  Comptés par pagination REST     : 1 projets
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

  Inventaire : 5 lignes.

Synthèse
--------
  10 appels API (9 GET, 1 GraphQL, 0 429 réessayés).
  Parc 5 → retenus 5 → frais 1 → éligibles 1 → sélectionnés 1.
  Filtre de fraîcheur validé sur 4 projets : 2 faux négatifs.
  Fichiers écrits :
    inventaire : <TMP>/inv.csv
    Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start inv.csv`. Pour un autre outil : --comma.
  Rappel : tout classement qui ne dit pas ce qu'il a écarté se lit comme
  complet alors qu'il ne l'est pas. Le CSV porte le motif d'exclusion de
  chaque projet, et la sélection porte son motif d'entrée.


╔═ [end of file] ═╗
