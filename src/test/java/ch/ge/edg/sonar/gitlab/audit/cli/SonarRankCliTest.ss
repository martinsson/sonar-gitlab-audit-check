╔═ a_full_ranking_on_all_four_signals_has_no_partial_note/stdout ═╗

1. Gouvernance (constats qui ne demandent aucun score)
----------------------------------------------------------
  Projets dans l'inventaire       : 2
  Jamais analysés                 : 0 (0%)
  Analysés, obsolètes (> 90 j)    : 0 (0%)
  Actifs (< 90 j)                 : 2 (100%)

  Sans donnée de couverture       : 0 sur 2
    dont projets actifs           : 0 (0%)

  → Population classable          : 2 projets actifs.

2. Créent de la dette (classement vélocité, projets actifs)
---------------------------------------------------------------
  Projets classés                 : 2
  Retenus (décile haut / strate)  : 1

  #    projet                                       strate     ncloc   score   sig.
  1    S One                                        S           2000    75.0    4/4

3. Listes annexes
---------------------
  Portent la dette sans en créer  : 0 (actifs, aucune ligne neuve)
  Abandons récents (90-365 j)     : 0
    Les deux se classent sur sqale_debt_ratio seul : un niveau, pas une vitesse.

  Classement écrit : <dir>/classement.csv
  Filtrer la colonne 'liste', puis 'retenu' = O pour la shortlist.
  Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start <dir>/classement.csv`.
  Pour un autre outil : relancer avec --comma.

╔═ active_projects_all_excluded_are_listed_as_such/stdout ═╗

1. Gouvernance (constats qui ne demandent aucun score)
----------------------------------------------------------
  Projets dans l'inventaire       : 2
  Jamais analysés                 : 0 (0%)
  Analysés, obsolètes (> 90 j)    : 0 (0%)
  Actifs (< 90 j)                 : 2 (100%)

  Sans donnée de couverture       : 1 sur 2
    dont projets actifs           : 1 (50%)
    Un projet analysé obtient coverage=0.0 dès que son analyseur
    rapporte des lignes couvrables. Une couverture *absente* sur un
    projet actif signale donc un langage sans instrumentation, ou un
    rapport jamais branché sur le scanner — pas du code non testé.
    C'est un constat d'outillage, et il ne se classe pas.

  → Population classable          : 2 projets actifs.

2. Créent de la dette (classement vélocité, projets actifs)
---------------------------------------------------------------
  Aucun projet actif ne réunit assez de signaux pour être classé.

  Écartés du classement :
    trop petit                             1
    trop peu de signaux                    1

3. Listes annexes
---------------------
  Portent la dette sans en créer  : 0 (actifs, aucune ligne neuve)
  Abandons récents (90-365 j)     : 0
    Les deux se classent sur sqale_debt_ratio seul : un niveau, pas une vitesse.

  Classement écrit : <dir>/classement.csv
  Filtrer la colonne 'liste', puis 'retenu' = O pour la shortlist.
  Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start <dir>/classement.csv`.
  Pour un autre outil : relancer avec --comma.

╔═ an_inventory_without_active_projects_has_nothing_to_rank/csv ═╗
﻿"liste";"rang";"retenu";"key";"name";"strate";"score";"signaux_presents";"exclusion";"pct_issues_neuves_par_kloc";"pct_couverture_code_neuf";"pct_duplication_code_neuf";"pct_ratio_dette";"ncloc";"new_lines";"new_violations";"new_coverage";"coverage";"new_duplicated_lines_density";"sqale_debt_ratio";"sqale_rating";"reliability_rating";"security_rating";"alert_status";"analysisDate";"days_since_analysis"

╔═ an_inventory_without_active_projects_has_nothing_to_rank/stdout ═╗

1. Gouvernance (constats qui ne demandent aucun score)
----------------------------------------------------------
  Projets dans l'inventaire       : 1
  Jamais analysés                 : 1 (100%)
  Analysés, obsolètes (> 90 j)    : 0 (0%)
  Actifs (< 90 j)                 : 0 (0%)

  Sans donnée de couverture       : 1 sur 1
    dont projets actifs           : 0

  → Population classable          : 0 projets actifs.

2. Créent de la dette (classement vélocité, projets actifs)
---------------------------------------------------------------
  Aucun projet actif dans cet inventaire : rien à classer.

3. Listes annexes
---------------------
  Portent la dette sans en créer  : 0 (actifs, aucune ligne neuve)
  Abandons récents (90-365 j)     : 0
    Les deux se classent sur sqale_debt_ratio seul : un niveau, pas une vitesse.

  Classement écrit : <dir>/classement.csv
  Filtrer la colonne 'liste', puis 'retenu' = O pour la shortlist.
  Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start <dir>/classement.csv`.
  Pour un autre outil : relancer avec --comma.

╔═ every_threshold_option_changes_the_ranking/csv ═╗
"liste","rang","retenu","key","name","strate","score","signaux_presents","exclusion","pct_issues_neuves_par_kloc","pct_couverture_code_neuf","pct_duplication_code_neuf","pct_ratio_dette","ncloc","new_lines","new_violations","new_coverage","coverage","new_duplicated_lines_density","sqale_debt_ratio","sqale_rating","reliability_rating","security_rating","alert_status","analysisDate","days_since_analysis"
"VELOCITE","1","O","few","Few Signals","S","83.3","1","","","83","","","3000","100","","40","","","","","","","","2026-06-01","10"
"VELOCITE","2","O","s-three","S Three","S","67.9","4","","75","50","75","75","8000","200","4","60","","3","5","A","B","A","ERROR","2026-06-01","12.7"
"VELOCITE","3","O","m-one","","M","50.0","2","","","50","50","","50000","2000","","70","","2","","","","","","2026-06-01","1"
"VELOCITE","4","O","l-one","Large project whose display name is longer than the column","L","50.0","4","","50","50","50","50","200000","3000","30","10","20","8","25","E","C","B","ERROR","2026-06-01","1"
"VELOCITE","5","N","s-two","S Two","S","22.6","4","","25","17","25","25","4000","500","1","90","80","1","2","A","A","A","OK","2026-06-01","3"
"VELOCITE","","N","stale-small","Stale Small","XS","","0","trop petit","","","","","100","","","","","","30","","","","","2026-01-01","100"
"VELOCITE","","N","stale-nodebt","Stale Nodebt","S","","0","pas de code neuf sur la fenêtre","","","","","5000","","","","","","","","","","","2026-01-01","100"
"VELOCITE","","N","tiny","Tiny","XS","","0","trop petit","","","","","300","50","1","80","70","0","1","","","","","2026-06-01","5"
"VELOCITE","","N","carry-a","Carry A","S","","0","pas de code neuf sur la fenêtre","","","","","3000","0","","","60","","4","","","","","2026-06-01","10"
"VELOCITE","","N","carry-b","Carry B","S","","0","pas de code neuf sur la fenêtre","","","","","3000","","","","","","9","","","","","2026-06-01","90"
"VELOCITE","","N","carry-none","Carry None","S","","0","pas de code neuf sur la fenêtre","","","","","3000","0","","","","","","","","","","2026-06-01","10"
"VELOCITE","","N","s-one","S One","S","","0","trop petit","","","","","2000","1000","20","30","50","5","10","A","A","A","OK","2026-06-01","3"
"VELOCITE","","N","xs-one","XS One","XS","","0","trop petit","","","","","800","100","0","100","100","0","0","A","A","A","OK","2026-06-01","1"
"DETTE_PORTEE","1","O","carry-b","Carry B","S","9.0","","","","","","","3000","","","","","","9","","","","","2026-06-01","90"
"DETTE_PORTEE","2","O","carry-a","Carry A","S","4.0","","","","","","","3000","0","","","60","","4","","","","","2026-06-01","10"
"ABANDON_RECENT","1","O","stale-mid","Stale Mid","S","7.0","","","","","","","5000","","","","","","7.0","B","A","A","OK","2026-01-01","200"

╔═ every_threshold_option_changes_the_ranking/stdout ═╗

1. Gouvernance (constats qui ne demandent aucun score)
----------------------------------------------------------
  Projets dans l'inventaire       : 18
  Jamais analysés                 : 2 (11%)
  Analysés, obsolètes (> 150 j)    : 3 (17%)
  Actifs (< 150 j)                 : 13 (72%)

  Sans donnée de couverture       : 12 sur 18
    dont projets actifs           : 7 (54%)
    Un projet analysé obtient coverage=0.0 dès que son analyseur
    rapporte des lignes couvrables. Une couverture *absente* sur un
    projet actif signale donc un langage sans instrumentation, ou un
    rapport jamais branché sur le scanner — pas du code non testé.
    C'est un constat d'outillage, et il ne se classe pas.

  → Population classable          : 13 projets actifs.

2. Créent de la dette (classement vélocité, projets actifs)
---------------------------------------------------------------
  Projets classés                 : 5
  Retenus (décile haut / strate)  : 4

  #    projet                                       strate     ncloc   score   sig.
  1    Few Signals                                  S           3000    83.3    1/4
  2    S Three                                      S           8000    67.9    4/4
  3    m-one                                        M          50000    50.0    2/4
  4    Large project whose display name is longer ... L         200000    50.0    4/4

  2 des 5 projets classés le sont sur un sous-ensemble de signaux.
  Une métrique absente est retirée de la moyenne, jamais comptée comme 0 :
  lire signaux_presents avant de comparer deux scores.

  Écartés du classement :
    trop petit                             4
    pas de code neuf sur la fenêtre        4

3. Listes annexes
---------------------
  Portent la dette sans en créer  : 2 (actifs, aucune ligne neuve)
  Abandons récents (150-250 j)     : 1
    Les deux se classent sur sqale_debt_ratio seul : un niveau, pas une vitesse.

  Classement écrit : <dir>/classement.csv
  Filtrer la colonne 'liste', puis 'retenu' = O pour la shortlist.

╔═ ranks_the_full_inventory_with_default_thresholds/csv ═╗
﻿"liste";"rang";"retenu";"key";"name";"strate";"score";"signaux_presents";"exclusion";"pct_issues_neuves_par_kloc";"pct_couverture_code_neuf";"pct_duplication_code_neuf";"pct_ratio_dette";"ncloc";"new_lines";"new_violations";"new_coverage";"coverage";"new_duplicated_lines_density";"sqale_debt_ratio";"sqale_rating";"reliability_rating";"security_rating";"alert_status";"analysisDate";"days_since_analysis"
"VELOCITE";"1";"O";"s-one";"S One";"S";"79.8";"4";"";"67";"88";"83";"83";"2000";"1000";"20";"30";"50";"5";"10";"A";"A";"A";"OK";"2026-06-01";"3"
"VELOCITE";"2";"N";"s-three";"S Three";"S";"51.2";"4";"";"67";"38";"50";"50";"8000";"200";"4";"60";"";"3";"5";"A";"B";"A";"ERROR";"2026-06-01";"12.7"
"VELOCITE";"3";"O";"m-one";"";"M";"50.0";"2";"";"";"50";"50";"";"50000";"2000";"";"70";"";"2";"";"";"";"";"";"2026-06-01";"1"
"VELOCITE";"4";"O";"l-one";"Large project whose display name is longer than the column";"L";"50.0";"4";"";"50";"50";"50";"50";"200000";"3000";"30";"10";"20";"8";"25";"E";"C";"B";"ERROR";"2026-06-01";"1"
"VELOCITE";"5";"O";"xs-one";"XS One";"XS";"50.0";"4";"";"50";"50";"50";"50";"800";"100";"0";"100";"100";"0";"0";"A";"A";"A";"OK";"2026-06-01";"1"
"VELOCITE";"6";"N";"s-two";"S Two";"S";"15.5";"4";"";"17";"13";"17";"17";"4000";"500";"1";"90";"80";"1";"2";"A";"A";"A";"OK";"2026-06-01";"3"
"VELOCITE";"";"N";"tiny";"Tiny";"XS";"";"0";"trop petit";"";"";"";"";"300";"50";"1";"80";"70";"0";"1";"";"";"";"";"2026-06-01";"5"
"VELOCITE";"";"N";"carry-a";"Carry A";"S";"";"0";"pas de code neuf sur la fenêtre";"";"";"";"";"3000";"0";"";"";"60";"";"4";"";"";"";"";"2026-06-01";"10"
"VELOCITE";"";"N";"carry-b";"Carry B";"S";"";"0";"pas de code neuf sur la fenêtre";"";"";"";"";"3000";"";"";"";"";"";"9";"";"";"";"";"2026-06-01";"90"
"VELOCITE";"";"N";"carry-none";"Carry None";"S";"";"0";"pas de code neuf sur la fenêtre";"";"";"";"";"3000";"0";"";"";"";"";"";"";"";"";"";"2026-06-01";"10"
"VELOCITE";"";"N";"few";"Few Signals";"S";"";"1";"trop peu de signaux";"";"63";"";"";"3000";"100";"";"40";"";"";"";"";"";"";"";"2026-06-01";"10"
"DETTE_PORTEE";"1";"O";"carry-b";"Carry B";"S";"9.0";"";"";"";"";"";"";"3000";"";"";"";"";"";"9";"";"";"";"";"2026-06-01";"90"
"DETTE_PORTEE";"2";"O";"carry-a";"Carry A";"S";"4.0";"";"";"";"";"";"";"3000";"0";"";"";"60";"";"4";"";"";"";"";"2026-06-01";"10"
"ABANDON_RECENT";"1";"O";"stale-worse";"Stale Worse";"S";"12.5";"";"";"";"";"";"";"2000";"";"";"";"";"";"12.5";"C";"A";"A";"ERROR";"2026-01-01";"300"
"ABANDON_RECENT";"2";"O";"stale-mid";"Stale Mid";"S";"7.0";"";"";"";"";"";"";"5000";"";"";"";"";"";"7.0";"B";"A";"A";"OK";"2026-01-01";"200"

╔═ ranks_the_full_inventory_with_default_thresholds/stdout ═╗

1. Gouvernance (constats qui ne demandent aucun score)
----------------------------------------------------------
  Projets dans l'inventaire       : 18
  Jamais analysés                 : 2 (11%)
  Analysés, obsolètes (> 90 j)    : 5 (28%)
  Actifs (< 90 j)                 : 11 (61%)

  Sans donnée de couverture       : 12 sur 18
    dont projets actifs           : 5 (45%)
    Un projet analysé obtient coverage=0.0 dès que son analyseur
    rapporte des lignes couvrables. Une couverture *absente* sur un
    projet actif signale donc un langage sans instrumentation, ou un
    rapport jamais branché sur le scanner — pas du code non testé.
    C'est un constat d'outillage, et il ne se classe pas.

  → Population classable          : 11 projets actifs.

2. Créent de la dette (classement vélocité, projets actifs)
---------------------------------------------------------------
  Projets classés                 : 6
  Retenus (décile haut / strate)  : 4

  #    projet                                       strate     ncloc   score   sig.
  1    S One                                        S           2000    79.8    4/4
  2    m-one                                        M          50000    50.0    2/4
  3    Large project whose display name is longer ... L         200000    50.0    4/4
  4    XS One                                       XS           800    50.0    4/4

  1 des 6 projets classés le sont sur un sous-ensemble de signaux.
  Une métrique absente est retirée de la moyenne, jamais comptée comme 0 :
  lire signaux_presents avant de comparer deux scores.

  Écartés du classement :
    trop petit                             1
    pas de code neuf sur la fenêtre        3
    trop peu de signaux                    1

3. Listes annexes
---------------------
  Portent la dette sans en créer  : 2 (actifs, aucune ligne neuve)
  Abandons récents (90-365 j)     : 2
    Les deux se classent sur sqale_debt_ratio seul : un niveau, pas une vitesse.

  Classement écrit : <dir>/classement.csv
  Filtrer la colonne 'liste', puis 'retenu' = O pour la shortlist.
  Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start <dir>/classement.csv`.
  Pour un autre outil : relancer avec --comma.

╔═ side_lists_keep_thirty_projects_on_the_shortlist/stdout ═╗

1. Gouvernance (constats qui ne demandent aucun score)
----------------------------------------------------------
  Projets dans l'inventaire       : 32
  Jamais analysés                 : 0 (0%)
  Analysés, obsolètes (> 90 j)    : 0 (0%)
  Actifs (< 90 j)                 : 32 (100%)

  Sans donnée de couverture       : 32 sur 32
    dont projets actifs           : 32 (100%)
    Un projet analysé obtient coverage=0.0 dès que son analyseur
    rapporte des lignes couvrables. Une couverture *absente* sur un
    projet actif signale donc un langage sans instrumentation, ou un
    rapport jamais branché sur le scanner — pas du code non testé.
    C'est un constat d'outillage, et il ne se classe pas.

  → Population classable          : 32 projets actifs.

2. Créent de la dette (classement vélocité, projets actifs)
---------------------------------------------------------------
  Aucun projet actif ne réunit assez de signaux pour être classé.

  Écartés du classement :
    pas de code neuf sur la fenêtre        32

3. Listes annexes
---------------------
  Portent la dette sans en créer  : 32 (actifs, aucune ligne neuve)
  Abandons récents (90-365 j)     : 0
    Les deux se classent sur sqale_debt_ratio seul : un niveau, pas une vitesse.

  Classement écrit : <dir>/classement.csv
  Filtrer la colonne 'liste', puis 'retenu' = O pour la shortlist.

╔═ [end of file] ═╗
