╔═ full_run_with_inventory_writes_croisement_sonar_gitlab_and_appariement/appariement ═╗
﻿"gauche_path";"droite_key";"methode";"confiance";"candidat_nom";"score_nom";"second_nom";"rejet_nom";"m_cle_ci";"m_cle_pom";"m_liaison";"m_ns_map";"m_chemin";"m_nom_pom";"m_liens";"liens_concorde";"m_noms_libre"
"team-a/alpha-service";"alpha-key";"clé lue dans la CI";"exact";"";"";"";"";"alpha-key";"";"";"";"";"";"";"";"alpha-key (0.85)"
"team-a/beta-lib";"org.example:beta-lib";"clé Maven du pom";"exact";"";"";"";"";"";"org.example:beta-lib";"";"org.example:beta-lib";"";"";"";"";"org.example:beta-lib (0.85)"
"team-b/gamma-api";"";"aucune";"none";"team-e_lambda-svc";"0.03";"";"sous le seuil";"";"";"";"";"";"";"";"";"team-e_lambda-svc (0.03, sous le seuil)"
"team-b/delta-portal";"delta-portal-app";"noms ressemblants (TF-IDF)";"suggestion";"delta-portal-app";"0.77";"team-e_lambda-svc (0.03)";"";"";"";"";"";"";"";"";"";"delta-portal-app (0.77)"
"team-c/epsilon-batch";"";"aucune";"none";"team-e_lambda-svc";"0.03";"";"sous le seuil";"";"";"";"";"";"";"";"";"team-e_lambda-svc (0.03, sous le seuil)"
"team-c/zeta-web";"zeta-sonar";"nom du pom = nom Sonar";"derived";"";"";"";"";"";"";"";"";"";"zeta-sonar";"";"";"zeta-sonar (0.83)"
"team-d/eta-one";"";"aucune";"none";"team-e_lambda-svc";"0.03";"";"sous le seuil";"";"";"";"";"";"";"";"";"team-e_lambda-svc (0.03, sous le seuil)"
"team-d/theta-two";"";"aucune";"none";"team-e_lambda-svc";"0.03";"";"sous le seuil";"";"";"";"";"";"";"";"";"team-e_lambda-svc (0.03, sous le seuil)"
"team-d/iota-three";"zz-other-key";"noms ressemblants (TF-IDF)";"suggestion";"zz-other-key";"0.53";"team-e_lambda-svc (0.03)";"";"";"";"";"";"";"";"";"";"zz-other-key (0.53)"
"team-d/kappa-four";"";"aucune";"none";"team-e_lambda-svc";"0.03";"";"sous le seuil";"";"";"";"";"";"";"";"";"team-e_lambda-svc (0.03, sous le seuil)"

╔═ full_run_with_inventory_writes_croisement_sonar_gitlab_and_appariement/croisement ═╗
﻿"projet";"methode_jointure";"confiance";"candidat_nom";"score_nom";"second_nom";"rejet_nom";"m_cle_ci";"m_cle_pom";"m_liaison";"m_ns_map";"m_chemin";"m_nom_pom";"m_liens";"liens_concorde";"m_noms_libre";"gl_path";"gl_bucket";"gl_commits_window";"gl_authors_window";"gl_mr_fusionnees";"gl_auto_merge";"gl_part_approuvee";"gl_branche_protegee";"gl_taux_succes";"gl_rouge_non_resolu";"gl_ci_sonar";"gl_ci_securite";"gl_cle_sonar";"gl_source_cle_sonar";"gl_cle_pom";"gl_nom_pom";"gl_id";"gl_fenetre_j";"gl_lignes_modifiees";"gl_commits_lignes";"gl_lignes_tronquees";"sq_key";"sq_name";"sq_analysisDate";"sq_days_since_analysis";"sq_ncloc";"sq_coverage";"sq_new_coverage";"sq_tests";"sq_test_failures";"sq_lines_to_cover";"sq_uncovered_lines";"sq_sqale_index";"sq_sqale_debt_ratio";"sq_bugs";"sq_vulnerabilities";"sq_code_smells";"sq_violations";"sq_new_lines";"sq_new_violations";"sq_alert_status";"sq_duplicated_lines_density";"sq_new_duplicated_lines_density";"sq_alm";"sq_alm_repository";"sq_liens";"sq_tendance_analyses";"sq_tendance_jours";"sq_violations_pente_mois";"sq_violations_kloc_pente_pct_mois";"sq_dette_ratio_pente_pct_mois";"issues_par_kloc_modifie";"issues_par_commit"
"team-a/alpha-service";"clé lue dans la CI";"exact";"";"";"";"";"alpha-key";"";"";"";"";"";"";"";"alpha-key (0.85)";"team-a/alpha-service";"actif";"30";"";"0";"";"";"";"";"";"true";"";"alpha-key";"";"";"";"11";"90";"1000";"10";"false";"alpha-key";"Alpha Service";"2026-01-10";"";"5000";"10";"";"0";"";"";"";"300";"";"";"";"";"";"";"5";"";"";"";"";"";"";"6";"";"6";"";"";"18.00";"1.80"
"team-a/beta-lib";"clé Maven du pom";"exact";"";"";"";"";"";"org.example:beta-lib";"";"org.example:beta-lib";"";"";"";"";"org.example:beta-lib (0.85)";"team-a/beta-lib";"dormant";"0";"";"2";"";"";"";"";"";"false";"";"";"";"org.example:beta-lib";"";"12";"90";"0";"0";"false";"org.example:beta-lib";"Beta Lib";"2025-03-01";"";"2000";"0";"";"12";"";"";"";"120";"";"";"";"";"";"";"0";"";"";"";"";"";"";"2";"";"";"";"";"";""
"team-b/gamma-api";"aucune";"none";"team-e_lambda-svc";"0.03";"";"sous le seuil";"";"";"";"";"";"";"";"";"team-e_lambda-svc (0.03, sous le seuil)";"team-b/gamma-api";"actif";"50";"";"4";"";"";"";"";"";"true";"";"gamma-wrong";"";"";"";"13";"90";"800";"20";"false";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";""
"team-b/delta-portal";"noms ressemblants (TF-IDF)";"suggestion";"delta-portal-app";"0.77";"team-e_lambda-svc (0.03)";"";"";"";"";"";"";"";"";"";"delta-portal-app (0.77)";"team-b/delta-portal";"actif";"25";"";"1";"";"";"";"";"";"false";"";"";"";"";"";"14";"";"";"";"";"delta-portal-app";"Delta Portal App";"2026-02-02";"";"3000";"55";"";"40";"";"";"";"10";"";"";"";"";"";"";"0";"";"";"";"";"";"";"";"";"";"";"";"";""
"team-c/epsilon-batch";"aucune";"none";"team-e_lambda-svc";"0.03";"";"sous le seuil";"";"";"";"";"";"";"";"";"team-e_lambda-svc (0.03, sous le seuil)";"team-c/epsilon-batch";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"15";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";""
"team-c/zeta-web";"nom du pom = nom Sonar";"derived";"";"";"";"";"";"";"";"";"";"zeta-sonar";"";"";"zeta-sonar (0.83)";"team-c/zeta-web";"actif";"40";"";"0";"";"";"";"";"";"false";"";"";"";"";"Zeta Web";"16";"90";"600";"12";"true";"zeta-sonar";"Zeta Web";"2026-02-03";"";"1500";"15";"";"0";"";"";"";"0";"";"";"";"";"";"";"3";"";"";"";"";"";"";"7";"";"2";"";"";"";""
"team-d/eta-one";"aucune";"none";"team-e_lambda-svc";"0.03";"";"sous le seuil";"";"";"";"";"";"";"";"";"team-e_lambda-svc (0.03, sous le seuil)";"team-d/eta-one";"actif";"21";"";"0";"";"";"";"";"";"true";"";"";"";"";"";"17";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";""
"team-d/theta-two";"aucune";"none";"team-e_lambda-svc";"0.03";"";"sous le seuil";"";"";"";"";"";"";"";"";"team-e_lambda-svc (0.03, sous le seuil)";"team-d/theta-two";"actif";"22";"";"0";"";"";"";"";"";"false";"";"";"";"";"";"18";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";""
"team-d/iota-three";"noms ressemblants (TF-IDF)";"suggestion";"zz-other-key";"0.53";"team-e_lambda-svc (0.03)";"";"";"";"";"";"";"";"";"";"zz-other-key (0.53)";"team-d/iota-three";"actif";"23";"";"0";"";"";"";"";"";"false";"";"";"";"";"";"19";"";"";"";"";"zz-other-key";"(iota-three) Legacy";"2026-01-07";"";"600";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";""
"team-d/kappa-four";"aucune";"none";"team-e_lambda-svc";"0.03";"";"sous le seuil";"";"";"";"";"";"";"";"";"team-e_lambda-svc (0.03, sous le seuil)";"team-d/kappa-four";"actif";"24";"";"0";"";"";"";"";"";"false";"";"";"";"";"";"20";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";""

╔═ full_run_with_inventory_writes_croisement_sonar_gitlab_and_appariement/sonarGitlab ═╗
﻿"sonar_key";"sonar_name";"gitlab_path";"gitlab_id";"methode";"confiance";"score";"autres_candidats";"raison";"cles_sonar_sur_ce_projet";"dans_selection";"gl_commits_window";"gl_last_activity_at";"gl_exclu";"sq_analysisDate";"sq_ncloc"
"alpha-key";"Alpha Service";"team-a/alpha-service";"11";"croisement : clé lue dans la CI";"exact";"";"";"";"1";"oui";"30";"2026-02-01";"";"2026-01-10";"5000"
"org.example:beta-lib";"Beta Lib";"team-a/beta-lib";"12";"croisement : clé Maven du pom";"exact";"";"";"";"1";"oui";"0";"2025-01-01";"";"2025-03-01";"2000"
"delta-portal-app";"Delta Portal App";"team-b/delta-portal";"14";"noms ressemblants (TF-IDF)";"suggestion";"0.77";"";"";"1";"oui";"25";"2026-02-01";"";"2026-02-02";"3000"
"zeta-sonar";"Zeta Web";"team-c/zeta-web";"16";"croisement : nom du pom = nom Sonar";"derived";"";"";"";"1";"oui";"40";"2026-02-01";"";"2026-02-03";"1500"
"orphan-x";"Orphan X";"team-z/orphan";"101";"liaison DevOps Sonar → GitLab";"exact";"";"";"";"1";"non";"12";"2026-02-01";"";"";""
"team-e_lambda-svc";"Lambda Svc";"team-e/lambda-svc";"102";"clé normalisée = chemin GitLab";"derived";"1.00";"";"";"1";"non";"3";"2026-01-01";"";"2026-01-05";"800"
"mu-tool";"Mu Tool";"team-f/mu-tool";"103";"nom court de la clé = dernier segment / nom GitLab (2 homonymes, départagés par activité GitLab)";"suggestion";"0.98";"team-f/mu-tool (0.98), alice/mu-tool (0.92)";"";"1";"non";"15";"2026-01-20";"";"2026-01-06";"700"
"nu-thing";"Nu Thing";"";"";"aucune";"none";"";"";"aucun candidat";"";"";"";"";"";"";""
"zz-other-key";"(iota-three) Legacy";"team-d/iota-three";"19";"nom Sonar = dernier segment / nom GitLab";"derived";"0.55";"";"";"1";"oui";"23";"2026-02-01";"";"2026-01-07";"600"

╔═ full_run_with_inventory_writes_croisement_sonar_gitlab_and_appariement/stdout ═╗

Croisement SonarQube × GitLab
  Côté Sonar   : 9 projets (<tmp>/projets.csv)
  Côté GitLab  : 10 projets (<tmp>/pratiques.csv)

  Appariement : 10 lignes → <tmp>/appariement.csv
    Réutilisé aux prochains lancements. Pour corriger une paire :
    confiance = manuel (et la clé dans droite_…), ou rejete.
  Tout GitLab  : 15 projets (<tmp>/inventaire.csv)

1. Appariement
--------------
  Méthodes appliquées dans l'ordre ; les suggestions de noms ne
  comptent dans aucun constat, elles sortent dans le CSV pour
  qu'un humain tranche.

Méthodes retenues
-----------------
  clé lue dans la CI                 :     1
  clé Maven du pom                   :     1
  liaison DevOps Sonar → GitLab      :     0
  mapping namespace GL→Sonar         :     0
  clé normalisée = chemin GitLab     :     0
  nom du pom = nom Sonar             :     1
  noms ressemblants (TF-IDF)         :     2
  aucune                             :     5

  Jointures sûres : 3 / 10 (30 %) · suggestions : 2 · sans rien : 5
  Lignes de droite jamais retenues : 4 / 9

Chaque méthode, seule
---------------------
  propose = désigne quelque chose · ambiguë = plusieurs candidats
  retenue = a tranché · seule = aucune autre méthode sûre ne propose
                   propose   ambiguë   retenue     seule
  cle_ci                 1         0         1         1
  cle_pom                1         0         1         0
  liaison                0         0         0         0
  ns_map                 1         0         0         0
  chemin                 0         0         0         0
  nom_pom                1         0         1         1
  liens                  0         0         0         0
  noms                   5         0         2         2

Accords entre méthodes
----------------------
  cle_ci     × noms       :    1 en commun,    1 d'accord, 0 en désaccord
  cle_pom    × ns_map     :    1 en commun,    1 d'accord, 0 en désaccord
  cle_pom    × noms       :    1 en commun,    1 d'accord, 0 en désaccord
  ns_map     × noms       :    1 en commun,    1 d'accord, 0 en désaccord
  nom_pom    × noms       :    1 en commun,    1 d'accord, 0 en désaccord

Liens « liens » (informatif, n'apparie rien)
--------------------------------------------
  Lignes de droite avec un lien de dépôt : 0
  Lignes de gauche désignées             : 0
    confirment une jointure sûre         : 0
    confirment une suggestion            : 0
    contredisent la jointure             : 0
    apparieraient une ligne seule        : 0

Ressemblance de noms, jugée sur 3 paires sûres
----------------------------------------------
  Meilleur candidat contre toute la droite, par tranche de score :
    0.8–0.9  juste    3  faux    0
  Avec la marge actuelle, à chaque seuil :
    0.3  proposées    3  justes    3  précision 100 %  rappel 100 %
    0.4  proposées    3  justes    3  précision 100 %  rappel 100 %
    0.5  proposées    3  justes    3  précision 100 %  rappel 100 %
    0.6  proposées    3  justes    3  précision 100 %  rappel 100 %
    0.7  proposées    3  justes    3  précision 100 %  rappel 100 %
    0.8  proposées    3  justes    3  précision 100 %  rappel 100 %
    0.9  proposées    0  justes    0  précision    —  rappel   0 %
  Suggestions refusées : {sous le seuil=5}

  Mots fréquents des lignes seules (* = absent à droite) :
    api* 1, batch* 1, epsilon* 1, eta* 1, four* 1, gamma* 1, kappa* 1, one* 1, theta* 1, two* 1
  Préfixes des clés de droite jamais retenues :
    mu- 1, nu- 1, orphan- 1, team- 1

  Moins d'un projet sur deux apparié. Ce n'est pas un échec de l'outil,
  c'est le constat : l'intégration GitLab↔SonarQube n'est pas configurée,
  et personne ne peut aujourd'hui répondre « ce dépôt est-il analysé ? »
  sans le faire à la main. Même classe de trou de gouvernance que les
  projets jamais analysés.

  Projets Sonar sans dépôt GitLab apparié : 6
    Dans la sélection seulement : la section suivante les cherche dans tout GitLab.

1b. Chaque projet Sonar contre tout GitLab
------------------------------------------
  La sélection ne contient que les projets GitLab retenus ; ici, chaque
  projet Sonar est cherché dans tout l'inventaire. Mêmes règles : seules
  les paires exactes et dérivées comptent, les suggestions sont à vérifier.
  Projets Sonar : 9, dont 6 appariés sûrement (67 %)
    derived    3
    exact      3
    none       1
    suggestion 2
  Appariés à un dépôt hors de la sélection GitLab : 2, dont 1 actifs (10 commits ou plus)
  Sans dépôt GitLab, par raison :
    aucun candidat                       1
  Méthodes dérivées rejouées sur les paires exactes : 2 sur 2 d'accord
    nom Sonar = dernier segment / nom GitLab : 1 d'accord, 0 non
    nom court de la clé = dernier segment / nom GitLab : 1 d'accord, 0 non
  Sonar × tout GitLab : 9 lignes → <tmp>/out/sonar-gitlab.csv

2. Ce qu'aucun des deux rapports ne pouvait dire seul
-----------------------------------------------------

  Actifs dans GitLab, absents de SonarQube : 5
    Vivants et non analysés. Séparés enfin des dépôts morts, qu'un inventaire
    Sonar seul confond avec eux.
      team-b/gamma-api — 50 commits
      team-b/delta-portal — 25 commits
      team-d/kappa-four — 24 commits
      team-d/theta-two — 22 commits
      team-d/eta-one — 21 commits

  CI configurée pour Sonar, sans projet Sonar apparié : 2
    Le pipeline dit lancer une analyse ; rien ne la reçoit sous une clé
    qu'on sache rattacher. Job en échec silencieux, ou clé qui ne correspond
    à rien. Constat concret, vérifiable projet par projet.
      team-b/gamma-api → gamma-wrong
      team-d/eta-one

  Dette créée sur du code que personne n'a relu : 2
    Sonar voit des violations sur le code neuf ; GitLab voit ce code atteindre
    la branche par défaut sans une seule MR fusionnée.
      team-a/alpha-service — 5 violations neuves, 30 commits, 0 MR
      team-c/zeta-web — 3 violations neuves, 40 commits, 0 MR

  Ni tests ni revue, sur du code qui bouge : 2
    Couverture sous 20 %, aucune MR fusionnée, activité soutenue. Les deux
    filets manquent en même temps.
      team-c/zeta-web — couverture 15 %, 40 commits
      team-a/alpha-service — couverture 10 %, 30 commits

  Dette portée, pas créée : 1
    De la dette au bilan, zéro commit sur la fenêtre. Confirmé par le dépôt
    lui-même, pas déduit d'une date d'analyse ancienne.
      team-a/beta-lib — 120 min de dette, 0 commit

  Des tests tournent, la couverture n'arrive pas : 1
    Tests comptés par SonarQube, couverture à zéro : le rapport de couverture
    n'est pas branché. Problème de tuyauterie CI, pas d'ingénierie — et les
    deux se ressemblent tant qu'on ne regarde que le pourcentage.
      team-a/beta-lib — 12 tests, couverture 0 %

  Chaque modification ajoute des issues : 1
    Pente des issues Sonar sur la fenêtre, rapportée aux lignes modifiées dans
    GitLab. Au moins 5 analyses et 500 lignes, sinon c'est du bruit.
      team-a/alpha-service — +18.0 issues / kLOC modifié, 1000 lignes

  Croisement : 10 lignes → <tmp>/out/croisement.csv
  Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start croisement.csv`. Pour un autre outil : --comma.
    Une ligne par projet GitLab, appariée ou non. Filtrer sur confiance
    = suggestion donne la liste des rapprochements qu'un humain doit
    confirmer ; ils ne comptent dans aucun des constats ci-dessus.

╔═ [end of file] ═╗
