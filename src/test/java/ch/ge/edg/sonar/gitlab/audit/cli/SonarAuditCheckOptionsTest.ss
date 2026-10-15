╔═ colorAlwaysPaintsTheReportAndShowsProgress/stdout ═╗
[1m
Instance : <URL>[0m

[1m=== 1. Connectivité et identité [0m[1m=======================================[0m
  [32mOK      [0m api/system/status                          [2mUP · v10.4.1 · inst-1[0m
  [32mOK      [0m api/authentication/validate                [2mtoken valide[0m
  [32mOK      [0m api/users/current                          [2malice (Alice Audit)[0m

  Permissions globales : [2mscan, provisioning[0m
  Groupes              : [2mteam-a, sonar-users[0m
  Métriques inconnues de cette instance : [33mtest_errors[0m
[2m    Retirées de la requête. Demandées quand même, elles feraient échouer[0m
[2m    l'appel entier et le parc ressortirait sans aucune mesure.[0m

[1m=== 2. Capacités du token sur les endpoints utiles [0m[1m====================[0m
  Projet témoin : [2malpha-service[0m

  [32mOK      [0m Inventaire projets (search_projects)       [2m4 projets visibles[0m
  [32mOK      [0m Mesures en masse (measures/search)         [2m11 mesures[0m
  [32mOK      [0m Historique de mesures (search_history)     [2m4 points[0m
  [32mOK      [0m Issues + facettes (issues/search)          [2m42 issues[0m
  [32mOK      [0m Contournements (WONTFIX / FALSE-POSITIVE)  [2m3 issues neutralisées[0m
  [32mOK      [0m Exclusions (settings/values)               [2m1 réglage(s) d'exclusion[0m
  [32mOK      [0m Quality gate du projet                     [2mTeam Gate[0m
  [32mOK      [0m Quality profiles du projet                 [2m2 profil(s)[0m
  [32mOK      [0m Historique d'analyses (project_analyses)   [2m12 analyses[0m
  [32mOK      [0m Arbre de composants (components/tree)      [2m57 fichiers[0m
  [32mOK      [0m Total réel des projets (admin uniquement)  [2m6 projets au total[0m
  [32mOK      [0m Liaison DevOps (alm_settings/get_binding)  [2mGITLAB, dépôt 101[0m
  [32mOK      [0m Liens du projet (project_links/search)     [2m3 lien(s)[0m
  [32mOK      [0m Blame SCM (sources/scm)                    [2m4 changeset(s)[0m

[1m=== 3. Inventaire des projets visibles [0m[1m================================[0m
  Projets visibles avec ce token : [1m4[0m
  Projets réellement présents    : [1m6[0m
[31m  → 2 projet(s) hors de ton périmètre. Ton classement sera tronqué
    de 33% sans aucun message d'erreur.[0m
  Branches lues 0/4  Branches lues 4/4  Branches lues 4/4
  Dernier scan sur une branche non principale : [1m2[0m
[2m    dont 1 sans aucune analyse de la branche principale[0m
[2m    (invisibles dans search_projects et measures/search)[0m

  Analysés il y a < 90 j        : 2
  Analysés il y a > 90 j        : [33m1[0m
  Jamais analysés                : [33m1[0m
[2m    (projets créés puis abandonnés, ou analyse jamais configurée)[0m
  Mesures de branche 0/2  Mesures de branche 2/2  Mesures de branche 2/2
  Sans aucune donnée de couverture: [33m1[0m
[2m    (coverage absente ≠ coverage à 0 : ces projets sont invisibles[0m
[2m     dans tout tri par couverture, et souvent les plus à risque)[0m
  Liaisons et liens 0/4  Liaisons et liens 4/4  Liaisons et liens 4/4

  Liés à une plateforme DevOps   : [1m2[0m / 4  {github=1, gitlab=1}
[33m    dont 1 liaison(s) illisible(s) : lecture refusée, pas absente[0m
  Avec au moins un lien saisi    : 1 / 4
  Historiques lus 0/4  Historiques lus 4/4  Historiques lus 4/4

  Tendance des issues/kLOC sur 90 j (2 projets mesurables) :
    en hausse (> +2 %/mois)    : [33m1[0m
    en baisse (< -2 %/mois)    : [32m1[0m
    stables                      : 0
    non mesurables               : 2
[2m    (moins de 3 analyses ou moins de 14 j d'historique dans la fenêtre, ou
     aucune issue : une pente relative à zéro n'existe pas)[0m

  CSV écrit : [1m<TMP>/inventaire.csv[0m
[2m  Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start inventaire.csv`. Pour un autre outil : --comma.[0m
  Historique écrit : [1m<TMP>/inventaire-historique.csv[0m

[1m=== 4. Signaux d'activité disponibles sans Git (sur alpha-service) [0m[1m====[0m
  Analyses sur 90 j            : [1m4[0m
  Versions livrées                : 2
  Cadence moyenne                 : 1 analyse / 10.0 j

  Auteurs distincts (90 j)      : [1m2[0m
  Auteur principal                : alice@example.test (80% des issues récentes)
[33m    → forte concentration : proxy de bus factor faible[0m

  Lignes neuves (new code)        : 40
  Violations neuves               : 3
  Couverture du code neuf         : n/a

  Δ lignes sur 90 j (50 j mesurés) : +200
  Δ dette sur 90 j (min)       : +150
  Dette ajoutée par ligne écrite  : [1m+0.75 min/LOC[0m
[2m    (négatif = l'équipe rembourse ; > 10 = signal fort)[0m

[32m  Blame SCM accessible via l'API :[0m
    Fichier témoin                : src/main/java/org/example/App.java
    Auteurs distincts (4 changesets): 3
    Dernière modification         : <D-15>
    Plus ancienne ligne           : <D-200>
[2m    → churn et âge du code calculables sans accès Git[0m

[1m=== Synthèse [0m[1m==========================================================[0m
  49 appels API effectués.
[2m  Rappel : search_projects filtre silencieusement sur ce que le token
  peut voir. Une permission manquante ne produit pas d'erreur, seulement
  un classement incomplet — et les projets manquants sont souvent ceux
  qui auraient le plus besoin d'aide.
[0m

╔═ commaWritesMachineCsvWithoutBomAndWithLf/history ═╗
"key","date","violations","ncloc","sqale_debt_ratio"
"alpha-service","<J-60>","100","1000","2.0"
"alpha-service","<J-40>","90","1000","x"
"alpha-service","<J-20>","85","1100",""
"alpha-service","<J-10>","80","1200","2.4"
"beta-api","<J-30>","10","500",""
"beta-api","<J-15>","15","500",""
"beta-api","<J-5>","20","500",""
"gamma-lib","<J-20>","5","",""
"gamma-lib","<J-10>","5","",""

╔═ commaWritesMachineCsvWithoutBomAndWithLf/inventory ═╗
"key","name","analysisDate","days_since_analysis","ncloc","files","complexity","cognitive_complexity","comment_lines_density","duplicated_lines_density","new_duplicated_lines_density","coverage","line_coverage","branch_coverage","new_coverage","lines_to_cover","uncovered_lines","tests","test_failures","test_errors","skipped_tests","test_success_density","sqale_index","sqale_debt_ratio","sqale_rating","bugs","reliability_rating","vulnerabilities","security_rating","security_hotspots","security_hotspots_reviewed","code_smells","violations","new_lines","new_violations","new_bugs","new_vulnerabilities","new_code_smells","alert_status","branch","is_main_branch","main_branch_analysisDate","alm","alm_repository","liens","tendance_analyses","tendance_jours","violations_pente_mois","violations_kloc_pente_pct_mois","dette_ratio_pente_pct_mois"
"alpha-service","Alpha Service","<J-100>","100","1200","40","","","","","","81.5","","","70.0","","","120","","","","","650","","1.0","","","","","","","","80","40","3","","","","OK","main","true","<J-100>","gitlab","101","scm https://git.example.test/team-a/alpha | autre https://wiki.example.test/alpha","4","50","-11.44","-23.37",""
"beta-api","Beta API","<J-5>","5","800","","","","","","","55.0","","","","","","","","","","","","","","3","","","","","","","","","","","","","ERROR","develop","false","<J-120>","","","","3","25","11.84","78.95",""
"gamma-lib","Gamma Lib","<J-20>","20","50","","","","","","","0.0","","","","","","","","","","","","","","","","","","","","","","","","","","","","develop","false","","github","","","2","10","","",""
"delta-legacy","Delta; ""Legacy""","","","300","","","","","","","","","","","","","","","","","","","","","0","","","","","","","","","","","","","","","","","illisible (HTTP 403)","","","","","","",""

╔═ helpPrintsTheUsage/usage ═╗
Usage : SonarAuditCheck [--csv <fichier>] [options]
Diagnostic d'accès Sonar avant audit de parc.
      --url=<url>            URL de l'instance
      --token=<token>        User token squ_...
      --organization=<organization>
                             SonarQube Cloud
      --project=<project>    projet témoin (sinon : le premier visible)
      --csv=<csv>            chemin du CSV d'inventaire à écrire
      --exclusions=<exclusionsFile>
                             fichier de clés à exclure, une par ligne, * et **
                               permis (sinon : exclusions.txt s'il existe)
      --comma                CSV séparé par des virgules, sans BOM (pour un
                               outil, pas Excel)
      --dump-dir=<dumpDir>   répertoire où consigner les réponses brutes
      --replay-dir=<replayDir>
                             rejouer un --dump-dir au lieu d'appeler l'instance
                               (hors ligne)
      --concurrency=<concurrency>
                             appels en vol à la fois ; 1 = séquentiel (défaut :
                               8)
      --main-branch-only     ne lire que la branche principale, comme
                               search_projects (sans chercher le dernier scan
                               sur les autres branches)
      --no-bindings          ne pas lire la liaison GitLab ni les liens de
                               chaque projet (deux appels de moins par projet ;
                               CrossAudit perd sa jointure exacte côté Sonar)
      --no-trend             ne pas lire l'historique de chaque projet (un
                               appel de moins par projet ; les colonnes de
                               tendance restent vides)
      --stale-days=<staleDays>
                             seuil d'obsolescence (défaut : 90)
      --activity-days=<activityDays>
                             fenêtre d'activité (défaut : 90)
      --timeout=<timeout>
      --insecure             ignorer la validation TLS
      --color=<colorMode>    auto | always | never (défaut : auto)
  -h, --help                 Show this help message and exit.
  -V, --version              Print version information and exit.

Cas d'usage courants :

  Vérifier l'accès avant de commencer — aucun fichier écrit :
    SonarAuditCheck

  Le diagnostic et l'inventaire du parc, en un passage :
    SonarAuditCheck --csv inventaire.csv

  Puis le classement, qui ne rappelle pas l'API :
    SonarRank --in inventaire.csv --out classement.csv

  SonarQube Cloud :
    SonarAuditCheck --organization mon-org --csv inventaire.csv

  Capturer, puis rejouer hors ligne — ni URL ni jeton au rejeu :
    SonarAuditCheck --csv inventaire.csv --dump-dir ./captures
    SonarAuditCheck --replay-dir ./captures --csv rejoue.csv

SONAR_URL et SONAR_TOKEN peuvent remplacer --url et --token. Le jeton
doit être de type 'User' (squ_...), pas un jeton d'analyse.
Colonnes du CSV : COLUMNS.md. Méthode de classement : ANALYSIS.md.

╔═ mainBranchOnlyReadsSearchProjectsAsIs/inventory ═╗
"key";"name";"analysisDate";"days_since_analysis";"ncloc";"files";"complexity";"cognitive_complexity";"comment_lines_density";"duplicated_lines_density";"new_duplicated_lines_density";"coverage";"line_coverage";"branch_coverage";"new_coverage";"lines_to_cover";"uncovered_lines";"tests";"test_failures";"test_errors";"skipped_tests";"test_success_density";"sqale_index";"sqale_debt_ratio";"sqale_rating";"bugs";"reliability_rating";"vulnerabilities";"security_rating";"security_hotspots";"security_hotspots_reviewed";"code_smells";"violations";"new_lines";"new_violations";"new_bugs";"new_vulnerabilities";"new_code_smells";"alert_status";"branch";"is_main_branch";"main_branch_analysisDate";"alm";"alm_repository";"liens";"tendance_analyses";"tendance_jours";"violations_pente_mois";"violations_kloc_pente_pct_mois";"dette_ratio_pente_pct_mois"
"alpha-service";"Alpha Service";"<J-100>";"100";"1200";"40";"";"";"";"";"";"81.5";"";"";"70.0";"";"";"120";"";"";"";"";"650";"";"1.0";"";"";"";"";"";"";"";"80";"40";"3";"";"";"";"OK";"";"";"";"gitlab";"101";"scm https://git.example.test/team-a/alpha | autre https://wiki.example.test/alpha";"4";"50";"-11.44";"-23.37";""
"beta-api";"Beta API";"<J-120>";"120";"999";"";"";"";"";"";"";"1.0";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";""
"gamma-lib";"Gamma Lib";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"github";"";"";"";"";"";"";""
"delta-legacy";"Delta; ""Legacy""";"";"";"300";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"0";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"illisible (HTTP 403)";"";"";"";"";"";"";""

╔═ mainBranchOnlyReadsSearchProjectsAsIs/stdout ═╗

Instance : <URL>

=== 1. Connectivité et identité =======================================
  OK       api/system/status                          UP · v10.4.1 · inst-1
  OK       api/authentication/validate                token valide
  OK       api/users/current                          alice (Alice Audit)

  Permissions globales : scan, provisioning
  Groupes              : team-a, sonar-users
  Métriques inconnues de cette instance : test_errors
    Retirées de la requête. Demandées quand même, elles feraient échouer
    l'appel entier et le parc ressortirait sans aucune mesure.

=== 2. Capacités du token sur les endpoints utiles ====================
  Projet témoin : alpha-service

  OK       Inventaire projets (search_projects)       4 projets visibles
  OK       Mesures en masse (measures/search)         11 mesures
  OK       Historique de mesures (search_history)     4 points
  OK       Issues + facettes (issues/search)          42 issues
  OK       Contournements (WONTFIX / FALSE-POSITIVE)  3 issues neutralisées
  OK       Exclusions (settings/values)               1 réglage(s) d'exclusion
  OK       Quality gate du projet                     Team Gate
  OK       Quality profiles du projet                 2 profil(s)
  OK       Historique d'analyses (project_analyses)   12 analyses
  OK       Arbre de composants (components/tree)      57 fichiers
  OK       Total réel des projets (admin uniquement)  6 projets au total
  OK       Liaison DevOps (alm_settings/get_binding)  GITLAB, dépôt 101
  OK       Liens du projet (project_links/search)     3 lien(s)
  OK       Blame SCM (sources/scm)                    4 changeset(s)

=== 3. Inventaire des projets visibles ================================
  Projets visibles avec ce token : 4
  Projets réellement présents    : 6
  → 2 projet(s) hors de ton périmètre. Ton classement sera tronqué
    de 33% sans aucun message d'erreur.

  Analysés il y a < 90 j        : 0
  Analysés il y a > 90 j        : 2
  Jamais analysés                : 2
    (projets créés puis abandonnés, ou analyse jamais configurée)
  Sans aucune donnée de couverture: 2
    (coverage absente ≠ coverage à 0 : ces projets sont invisibles
     dans tout tri par couverture, et souvent les plus à risque)

  Liés à une plateforme DevOps   : 2 / 4  {github=1, gitlab=1}
    dont 1 liaison(s) illisible(s) : lecture refusée, pas absente
  Avec au moins un lien saisi    : 1 / 4

  Tendance des issues/kLOC sur 90 j (1 projets mesurables) :
    en hausse (> +2 %/mois)    : 0
    en baisse (< -2 %/mois)    : 1
    stables                      : 0
    non mesurables               : 3
    (moins de 3 analyses ou moins de 14 j d'historique dans la fenêtre, ou
     aucune issue : une pente relative à zéro n'existe pas)

  CSV écrit : <TMP>/inventaire.csv
  Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start inventaire.csv`. Pour un autre outil : --comma.
  Historique écrit : <TMP>/inventaire-historique.csv

=== 4. Signaux d'activité disponibles sans Git (sur alpha-service) ====
  Analyses sur 90 j            : 4
  Versions livrées                : 2
  Cadence moyenne                 : 1 analyse / 10.0 j

  Auteurs distincts (90 j)      : 2
  Auteur principal                : alice@example.test (80% des issues récentes)
    → forte concentration : proxy de bus factor faible

  Lignes neuves (new code)        : 40
  Violations neuves               : 3
  Couverture du code neuf         : n/a

  Δ lignes sur 90 j (50 j mesurés) : +200
  Δ dette sur 90 j (min)       : +150
  Dette ajoutée par ligne écrite  : +0.75 min/LOC
    (négatif = l'équipe rembourse ; > 10 = signal fort)

  Blame SCM accessible via l'API :
    Fichier témoin                : src/main/java/org/example/App.java
    Auteurs distincts (4 changesets): 3
    Dernière modification         : <D-15>
    Plus ancienne ligne           : <D-200>
    → churn et âge du code calculables sans accès Git

=== Synthèse ==========================================================
  43 appels API effectués (8 en vol).
  Rappel : search_projects filtre silencieusement sur ce que le token
  peut voir. Une permission manquante ne produit pas d'erreur, seulement
  un classement incomplet — et les projets manquants sont souvent ceux
  qui auraient le plus besoin d'aide.


╔═ [end of file] ═╗
