╔═ dumpThenReplayGivesTheSameInventoryWithoutTheInstance/live ═╗

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
  Dernier scan sur une branche non principale : 2
    dont 1 sans aucune analyse de la branche principale
    (invisibles dans search_projects et measures/search)

  Analysés il y a < 90 j        : 2
  Analysés il y a > 90 j        : 1
  Jamais analysés                : 1
    (projets créés puis abandonnés, ou analyse jamais configurée)
  Sans aucune donnée de couverture: 1
    (coverage absente ≠ coverage à 0 : ces projets sont invisibles
     dans tout tri par couverture, et souvent les plus à risque)

  Liés à une plateforme DevOps   : 2 / 4  {github=1, gitlab=1}
    dont 1 liaison(s) illisible(s) : lecture refusée, pas absente
  Avec au moins un lien saisi    : 1 / 4

  Tendance des issues/kLOC sur 90 j (2 projets mesurables) :
    en hausse (> +2 %/mois)    : 1
    en baisse (< -2 %/mois)    : 1
    stables                      : 0
    non mesurables               : 2
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
  49 appels API effectués.
  Rappel : search_projects filtre silencieusement sur ce que le token
  peut voir. Une permission manquante ne produit pas d'erreur, seulement
  un classement incomplet — et les projets manquants sont souvent ceux
  qui auraient le plus besoin d'aide.


╔═ dumpThenReplayGivesTheSameInventoryWithoutTheInstance/replay ═╗

Instance : rejeu de <TMP>/captures

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
  Dernier scan sur une branche non principale : 2
    dont 1 sans aucune analyse de la branche principale
    (invisibles dans search_projects et measures/search)

  Analysés il y a < 90 j        : 2
  Analysés il y a > 90 j        : 1
  Jamais analysés                : 1
    (projets créés puis abandonnés, ou analyse jamais configurée)
  Sans aucune donnée de couverture: 1
    (coverage absente ≠ coverage à 0 : ces projets sont invisibles
     dans tout tri par couverture, et souvent les plus à risque)

  Liés à une plateforme DevOps   : 2 / 4  {github=1, gitlab=1}
    dont 1 liaison(s) illisible(s) : lecture refusée, pas absente
  Avec au moins un lien saisi    : 1 / 4

  Tendance des issues/kLOC sur 90 j (2 projets mesurables) :
    en hausse (> +2 %/mois)    : 1
    en baisse (< -2 %/mois)    : 1
    stables                      : 0
    non mesurables               : 2
    (moins de 3 analyses ou moins de 14 j d'historique dans la fenêtre, ou
     aucune issue : une pente relative à zéro n'existe pas)

  CSV écrit : <TMP>/rejoue.csv
  Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start rejoue.csv`. Pour un autre outil : --comma.
  Historique écrit : <TMP>/rejoue-historique.csv

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
  49 appels API effectués.
  Rappel : search_projects filtre silencieusement sur ce que le token
  peut voir. Une permission manquante ne produit pas d'erreur, seulement
  un classement incomplet — et les projets manquants sont souvent ceux
  qui auraient le plus besoin d'aide.


╔═ [end of file] ═╗
