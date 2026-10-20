╔═ aSecondRunOnItsOwnOutputChangesNothing ═╗

Appariement
  Gauche : 14 lignes (<tmp>/pratiques.csv)
  Droite : 16 lignes (<tmp>/inventaire.csv)

  Appariement repris de <tmp>/correspondances.csv
    7 gardées, 0 saisies à la main, 0 rejetées, 7 recalculées, 0 nouveaux projets
    Ancienne version : correspondances.csv.bak

Méthodes retenues
-----------------
  clé lue dans la CI                 :     2
  clé Maven du pom                   :     1
  liaison DevOps Sonar → GitLab      :     1
  mapping namespace GL→Sonar         :     1
  clé normalisée = chemin GitLab     :     1
  nom du pom = nom Sonar             :     1
  noms ressemblants (TF-IDF)         :     2
  aucune                             :     5

  Jointures sûres : 7 / 14 (50 %) · suggestions : 2 · sans rien : 5
  Lignes de droite jamais retenues : 7 / 16

Chaque méthode, seule
---------------------
  propose = désigne quelque chose · ambiguë = plusieurs candidats
  retenue = a tranché · seule = aucune autre méthode sûre ne propose
                   propose   ambiguë   retenue     seule
  cle_ci                 2         0         2         1
  cle_pom                1         0         1         0
  liaison                1         0         1         0
  ns_map                 4         0         1         1
  chemin                 1         0         1         1
  nom_pom                2         1         1         2
  liens                  4         0         0         2
  noms                   9         0         2         3

Accords entre méthodes
----------------------
  cle_ci     × ns_map     :    1 en commun,    0 d'accord, 1 en désaccord
  cle_ci     × liens      :    1 en commun,    1 d'accord, 0 en désaccord
  cle_ci     × noms       :    2 en commun,    1 d'accord, 1 en désaccord
  cle_pom    × ns_map     :    1 en commun,    1 d'accord, 0 en désaccord
  cle_pom    × liens      :    1 en commun,    0 d'accord, 1 en désaccord
  cle_pom    × noms       :    1 en commun,    1 d'accord, 0 en désaccord
  liaison    × ns_map     :    1 en commun,    1 d'accord, 0 en désaccord
  liaison    × noms       :    1 en commun,    1 d'accord, 0 en désaccord
  ns_map     × liens      :    1 en commun,    0 d'accord, 1 en désaccord
  ns_map     × noms       :    4 en commun,    4 d'accord, 0 en désaccord
  chemin     × noms       :    1 en commun,    1 d'accord, 0 en désaccord
  liens      × noms       :    3 en commun,    2 d'accord, 1 en désaccord
      team-m/epsilon-api — cle_ci → org.example:eps-key ; ns_map → org.example:epsilon-api
      team-m/epsilon-api — cle_ci → org.example:eps-key ; noms → org.example:epsilon-api
      team-a/beta — cle_pom → org.example:beta ; liens → org.example:beta-legacy
      team-a/beta — ns_map → org.example:beta ; liens → org.example:beta-legacy
      team-a/beta — liens → org.example:beta-legacy ; noms → org.example:beta

Liens « liens » (informatif, n'apparie rien)
--------------------------------------------
  Lignes de droite avec un lien de dépôt : 3
  Lignes de gauche désignées             : 4
    confirment une jointure sûre         : 1
    confirment une suggestion            : 1
    contredisent la jointure             : 1
    apparieraient une ligne seule        : 1

Ressemblance de noms, jugée sur 7 paires sûres
----------------------------------------------
  Meilleur candidat contre toute la droite, par tranche de score :
    0.1–0.2  juste    0  faux    1
    0.8–0.9  juste    2  faux    0
    0.9–1.0  juste    3  faux    1
  Avec la marge actuelle, à chaque seuil :
    0.3  proposées    6  justes    5  précision  83 %  rappel  71 %
    0.4  proposées    6  justes    5  précision  83 %  rappel  71 %
    0.5  proposées    6  justes    5  précision  83 %  rappel  71 %
    0.6  proposées    6  justes    5  précision  83 %  rappel  71 %
    0.7  proposées    6  justes    5  précision  83 %  rappel  71 %
    0.8  proposées    6  justes    5  précision  83 %  rappel  71 %
    0.9  proposées    4  justes    3  précision  75 %  rappel  43 %
  Suggestions refusées : {ambigu=1, aucun candidat=1, numéros différents=1, déjà proposé=1, sous le seuil=1}

  Fausses propositions sur des paires sûres :
      team-m/epsilon-api — proposé org.example:epsilon-api (0.95), vrai org.example:eps-key (0.00, par cle_ci)

  Paires sûres dont les noms divergent (score < 0.3) :
      team-f/zp-web — org.example:zportal / zeta-portal — score 0.00, apparié par nom_pom
      team-m/epsilon-api — org.example:eps-key / Unrelated Thing — score 0.00, apparié par cle_ci

  Quasi-réussites :
      team-k/report-engine — org.example:reporting-engine-svc (0.85) — déjà proposé à team-j/report-engine
      team-f/common-ui — org.example:common-a (0.57) — ambigu, suivant org.example:common-b (0.57)

  Mots fréquents des lignes seules (* = absent à droite) :
    app 1, common 1, engine 1, extra* 1, gamma 1, lonely* 1, report* 1, stuff* 1, thing 1, tools* 1, ui* 1
  Préfixes des clés de droite jamais retenues :
    org.example: 6
  Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start correspondances.csv`. Pour un autre outil : --comma.

╔═ anEditedMappingWinsWhereItDecidedAndIsKeptInABak/mapping ═╗
<BOM>"gauche_path";"droite_key";"methode";"confiance";"candidat_nom";"score_nom";"second_nom";"rejet_nom";"m_cle_ci";"m_cle_pom";"m_liaison";"m_ns_map";"m_chemin";"m_nom_pom";"m_liens";"liens_concorde";"m_noms_libre"⏎
"team-a/alpha-service";"team-a:alpha";"clé lue dans la CI";"exact";"";"";"";"";"team-a:alpha";"";"";"";"";"";"team-a:alpha";"oui";"team-a:alpha (0.95)"⏎
"team-a/beta";"org.example:beta";"clé Maven du pom";"exact";"";"";"";"";"";"org.example:beta";"";"org.example:beta";"";"";"org.example:beta-legacy";"non";"org.example:beta (0.89)"⏎
"team-b/gamma";"org.example:gamma";"a label nobody knows";"derived";"";"";"";"";"";"";"org.example:gamma";"org.example:gamma";"";"";"";"";"org.example:gamma (0.87)"⏎
"team-c/101-delta";"grp.delta:delta";"rejeté à la main";"rejete";"";"";"";"";"";"";"";"grp.delta:delta";"";"";"";"";"grp.delta:delta (0.91)"⏎
"team-e/12-";"TEAM-E_12";"clé normalisée = chemin GitLab";"derived";"";"";"";"";"";"";"";"";"TEAM-E_12";"";"";"";"TEAM-E_12 (1.00)"⏎
"team-f/zp-web";"org.example:zportal";"nom du pom = nom Sonar";"derived";"";"";"";"";"";"";"";"";"";"org.example:zportal";"";"";"TEAM-E_12 (0.16, sous le seuil)"⏎
"team-f/common-ui";"org.example:common-b";"saisi à la main";"manuel";"org.example:common-a";"0.57";"org.example:common-b (0.57)";"ambigu";"";"";"";"";"";"org.example:common-a org.example:common-b";"";"";"org.example:common-a (0.57, ambigu)"⏎
"team-g/inventory-tracker";"org.example:invntory-tracker";"noms ressemblants (TF-IDF)";"suggestion";"org.example:invntory-tracker";"0.82";"";"";"";"";"";"";"";"";"";"";"org.example:invntory-tracker (0.82)"⏎
"team-h/lonely-thing";"";"aucune";"none";"";"";"";"aucun candidat";"";"";"";"";"";"";"org.example:beta-legacy";"";"org.example:eps-key (0.29, sous le seuil)"⏎
"app1";"";"aucune";"none";"";"";"";"numéros différents";"";"";"";"";"";"";"";"";""⏎
"team-j/report-engine";"org.example:reporting-engine-svc";"noms ressemblants (TF-IDF)";"suggestion";"org.example:reporting-engine-svc";"0.85";"";"";"";"";"";"";"";"";"org.example:reporting-engine-svc";"oui";"org.example:reporting-engine-svc (0.85)"⏎
"team-k/report-engine";"org.example:reporting-engine-svc";"saisi à la main";"manuel";"org.example:reporting-engine-svc";"0.85";"";"déjà proposé à team-j/report-engine";"";"";"";"";"";"";"";"";"org.example:reporting-engine-svc (0.85)"⏎
"team-l/gamma-tools-extra-stuff";"";"aucune";"none";"org.example:gamma-gh";"0.23";"";"sous le seuil";"";"";"";"";"";"";"";"";"org.example:gamma (0.36, sous le seuil)"⏎
"team-m/epsilon-api";"org.example:epsilon-api";"noms ressemblants (TF-IDF)";"derived";"";"";"";"";"org.example:eps-key";"";"";"org.example:epsilon-api";"";"";"";"";"org.example:epsilon-api (0.95)"⏎
"old/removed-project";"org.example:x";"saisi à la main";"manuel";"";"";"";"";"";"";"";"";"";"";"";"";""⏎

╔═ anEditedMappingWinsWhereItDecidedAndIsKeptInABak/report ═╗
{
  "summary" : {
    "left" : 14,
    "right" : 16,
    "byMethod" : {
      "clé lue dans la CI" : 1,
      "clé Maven du pom" : 1,
      "liaison DevOps Sonar → GitLab" : 0,
      "mapping namespace GL→Sonar" : 0,
      "clé normalisée = chemin GitLab" : 1,
      "nom du pom = nom Sonar" : 1,
      "noms ressemblants (TF-IDF)" : 3,
      "aucune" : 3,
      "a label nobody knows" : 1,
      "rejeté à la main" : 1,
      "saisi à la main" : 2
    },
    "joined" : 8,
    "suggestions" : 2,
    "unmatched" : 3,
    "rightUnmatched" : 7,
    "threshold" : 0.5,
    "margin" : 0.1
  },
  "unavailable" : [ ],
  "methods" : [ {
    "id" : "cle_ci",
    "label" : "clé lue dans la CI",
    "kind" : "exact",
    "proposes" : 2,
    "ambiguous" : 0,
    "wins" : 1,
    "alone" : 1
  }, {
    "id" : "cle_pom",
    "label" : "clé Maven du pom",
    "kind" : "exact",
    "proposes" : 1,
    "ambiguous" : 0,
    "wins" : 1,
    "alone" : 0
  }, {
    "id" : "liaison",
    "label" : "liaison DevOps Sonar → GitLab",
    "kind" : "exact",
    "proposes" : 1,
    "ambiguous" : 0,
    "wins" : 0,
    "alone" : 0
  }, {
    "id" : "ns_map",
    "label" : "mapping namespace GL→Sonar",
    "kind" : "mapped",
    "proposes" : 4,
    "ambiguous" : 0,
    "wins" : 0,
    "alone" : 1
  }, {
    "id" : "chemin",
    "label" : "clé normalisée = chemin GitLab",
    "kind" : "derived",
    "proposes" : 1,
    "ambiguous" : 0,
    "wins" : 1,
    "alone" : 1
  }, {
    "id" : "nom_pom",
    "label" : "nom du pom = nom Sonar",
    "kind" : "name",
    "proposes" : 2,
    "ambiguous" : 1,
    "wins" : 1,
    "alone" : 2
  }, {
    "id" : "liens",
    "label" : "liens saisis dans Sonar",
    "kind" : "link",
    "proposes" : 4,
    "ambiguous" : 0,
    "wins" : 0,
    "alone" : 2
  }, {
    "id" : "noms",
    "label" : "noms ressemblants (TF-IDF)",
    "kind" : "names",
    "proposes" : 9,
    "ambiguous" : 0,
    "wins" : 3,
    "alone" : 3
  } ],
  "agreements" : [ {
    "a" : "cle_ci",
    "b" : "ns_map",
    "both" : 1,
    "agree" : 0,
    "disagree" : 1
  }, {
    "a" : "cle_ci",
    "b" : "liens",
    "both" : 1,
    "agree" : 1,
    "disagree" : 0
  }, {
    "a" : "cle_ci",
    "b" : "noms",
    "both" : 2,
    "agree" : 1,
    "disagree" : 1
  }, {
    "a" : "cle_pom",
    "b" : "ns_map",
    "both" : 1,
    "agree" : 1,
    "disagree" : 0
  }, {
    "a" : "cle_pom",
    "b" : "liens",
    "both" : 1,
    "agree" : 0,
    "disagree" : 1
  }, {
    "a" : "cle_pom",
    "b" : "noms",
    "both" : 1,
    "agree" : 1,
    "disagree" : 0
  }, {
    "a" : "liaison",
    "b" : "ns_map",
    "both" : 1,
    "agree" : 1,
    "disagree" : 0
  }, {
    "a" : "liaison",
    "b" : "noms",
    "both" : 1,
    "agree" : 1,
    "disagree" : 0
  }, {
    "a" : "ns_map",
    "b" : "liens",
    "both" : 1,
    "agree" : 0,
    "disagree" : 1
  }, {
    "a" : "ns_map",
    "b" : "noms",
    "both" : 4,
    "agree" : 4,
    "disagree" : 0
  }, {
    "a" : "chemin",
    "b" : "noms",
    "both" : 1,
    "agree" : 1,
    "disagree" : 0
  }, {
    "a" : "liens",
    "b" : "noms",
    "both" : 3,
    "agree" : 2,
    "disagree" : 1
  } ],
  "disagreements" : [ {
    "left" : "team-m/epsilon-api",
    "detail" : "cle_ci → org.example:eps-key ; ns_map → org.example:epsilon-api"
  }, {
    "left" : "team-m/epsilon-api",
    "detail" : "cle_ci → org.example:eps-key ; noms → org.example:epsilon-api"
  }, {
    "left" : "team-a/beta",
    "detail" : "cle_pom → org.example:beta ; liens → org.example:beta-legacy"
  }, {
    "left" : "team-a/beta",
    "detail" : "ns_map → org.example:beta ; liens → org.example:beta-legacy"
  }, {
    "left" : "team-a/beta",
    "detail" : "liens → org.example:beta-legacy ; noms → org.example:beta"
  } ],
  "links" : [ {
    "id" : "liens",
    "rightWithLinks" : 3,
    "leftPointedAt" : 4,
    "confirmSure" : 1,
    "confirmSuggestion" : 1,
    "contradict" : 1,
    "wouldJoin" : 1
  } ],
  "names" : {
    "evaluatedOn" : 8,
    "scoreOfBestCandidate" : [ {
      "range" : "0.0–0.1",
      "correct" : 0,
      "wrong" : 0
    }, {
      "range" : "0.1–0.2",
      "correct" : 0,
      "wrong" : 1
    }, {
      "range" : "0.2–0.3",
      "correct" : 0,
      "wrong" : 0
    }, {
      "range" : "0.3–0.4",
      "correct" : 0,
      "wrong" : 0
    }, {
      "range" : "0.4–0.5",
      "correct" : 0,
      "wrong" : 0
    }, {
      "range" : "0.5–0.6",
      "correct" : 0,
      "wrong" : 1
    }, {
      "range" : "0.6–0.7",
      "correct" : 0,
      "wrong" : 0
    }, {
      "range" : "0.7–0.8",
      "correct" : 0,
      "wrong" : 0
    }, {
      "range" : "0.8–0.9",
      "correct" : 3,
      "wrong" : 0
    }, {
      "range" : "0.9–1.0",
      "correct" : 3,
      "wrong" : 0
    } ],
    "thresholds" : [ {
      "threshold" : 0.3,
      "proposed" : 6,
      "correct" : 6,
      "precision" : 1.0,
      "recall" : 0.75
    }, {
      "threshold" : 0.4,
      "proposed" : 6,
      "correct" : 6,
      "precision" : 1.0,
      "recall" : 0.75
    }, {
      "threshold" : 0.5,
      "proposed" : 6,
      "correct" : 6,
      "precision" : 1.0,
      "recall" : 0.75
    }, {
      "threshold" : 0.6,
      "proposed" : 6,
      "correct" : 6,
      "precision" : 1.0,
      "recall" : 0.75
    }, {
      "threshold" : 0.7,
      "proposed" : 6,
      "correct" : 6,
      "precision" : 1.0,
      "recall" : 0.75
    }, {
      "threshold" : 0.8,
      "proposed" : 6,
      "correct" : 6,
      "precision" : 1.0,
      "recall" : 0.75
    }, {
      "threshold" : 0.9,
      "proposed" : 3,
      "correct" : 3,
      "precision" : 1.0,
      "recall" : 0.375
    } ],
    "rejections" : {
      "ambigu" : 1,
      "aucun candidat" : 1,
      "numéros différents" : 1,
      "déjà proposé" : 1,
      "sous le seuil" : 1
    },
    "wrongOnSurePairs" : [ ],
    "divergentSurePairs" : [ {
      "left" : "team-f/zp-web",
      "detail" : "org.example:zportal / zeta-portal — score 0.00, apparié par nom_pom"
    } ],
    "nearMisses" : [ ],
    "unmatchedLeftTokens" : [ {
      "token" : "app",
      "count" : 1,
      "onRight" : true
    }, {
      "token" : "delta",
      "count" : 1,
      "onRight" : true
    }, {
      "token" : "extra",
      "count" : 1,
      "onRight" : false
    }, {
      "token" : "gamma",
      "count" : 1,
      "onRight" : true
    }, {
      "token" : "lonely",
      "count" : 1,
      "onRight" : false
    }, {
      "token" : "stuff",
      "count" : 1,
      "onRight" : false
    }, {
      "token" : "thing",
      "count" : 1,
      "onRight" : true
    }, {
      "token" : "tools",
      "count" : 1,
      "onRight" : false
    } ],
    "unmatchedRightPrefixes" : [ {
      "token" : "org.example:",
      "count" : 5,
      "onRight" : true
    }, {
      "token" : "grp.delta:",
      "count" : 1,
      "onRight" : true
    } ]
  }
}
╔═ anEditedMappingWinsWhereItDecidedAndIsKeptInABak/stdout ═╗

Appariement
  Gauche : 14 lignes (<tmp>/pratiques.csv)
  Droite : 16 lignes (<tmp>/inventaire.csv)

  Appariement repris de <tmp>/correspondances.csv
    3 gardées, 2 saisies à la main, 1 rejetées, 2 recalculées, 4 nouveaux projets
    1 lignes pour des projets absents de cet audit, conservées.
    3 paires vers une clé qui n'existe plus, recalculées :
      team-a/beta → org.example:gone
      team-g/inventory-tracker → org.example:vanished (manuel)
      team-j/report-engine → (manuel)
    Ancienne version : correspondances.csv.bak

Méthodes retenues
-----------------
  clé lue dans la CI                 :     1
  clé Maven du pom                   :     1
  liaison DevOps Sonar → GitLab      :     0
  mapping namespace GL→Sonar         :     0
  clé normalisée = chemin GitLab     :     1
  nom du pom = nom Sonar             :     1
  noms ressemblants (TF-IDF)         :     3
  aucune                             :     3
  a label nobody knows               :     1
  rejeté à la main                   :     1
  saisi à la main                    :     2

  Jointures sûres : 8 / 14 (57 %) · suggestions : 2 · sans rien : 3
  Lignes de droite jamais retenues : 7 / 16

Chaque méthode, seule
---------------------
  propose = désigne quelque chose · ambiguë = plusieurs candidats
  retenue = a tranché · seule = aucune autre méthode sûre ne propose
                   propose   ambiguë   retenue     seule
  cle_ci                 2         0         1         1
  cle_pom                1         0         1         0
  liaison                1         0         0         0
  ns_map                 4         0         0         1
  chemin                 1         0         1         1
  nom_pom                2         1         1         2
  liens                  4         0         0         2
  noms                   9         0         3         3

Accords entre méthodes
----------------------
  cle_ci     × ns_map     :    1 en commun,    0 d'accord, 1 en désaccord
  cle_ci     × liens      :    1 en commun,    1 d'accord, 0 en désaccord
  cle_ci     × noms       :    2 en commun,    1 d'accord, 1 en désaccord
  cle_pom    × ns_map     :    1 en commun,    1 d'accord, 0 en désaccord
  cle_pom    × liens      :    1 en commun,    0 d'accord, 1 en désaccord
  cle_pom    × noms       :    1 en commun,    1 d'accord, 0 en désaccord
  liaison    × ns_map     :    1 en commun,    1 d'accord, 0 en désaccord
  liaison    × noms       :    1 en commun,    1 d'accord, 0 en désaccord
  ns_map     × liens      :    1 en commun,    0 d'accord, 1 en désaccord
  ns_map     × noms       :    4 en commun,    4 d'accord, 0 en désaccord
  chemin     × noms       :    1 en commun,    1 d'accord, 0 en désaccord
  liens      × noms       :    3 en commun,    2 d'accord, 1 en désaccord
      team-m/epsilon-api — cle_ci → org.example:eps-key ; ns_map → org.example:epsilon-api
      team-m/epsilon-api — cle_ci → org.example:eps-key ; noms → org.example:epsilon-api
      team-a/beta — cle_pom → org.example:beta ; liens → org.example:beta-legacy
      team-a/beta — ns_map → org.example:beta ; liens → org.example:beta-legacy
      team-a/beta — liens → org.example:beta-legacy ; noms → org.example:beta

Liens « liens » (informatif, n'apparie rien)
--------------------------------------------
  Lignes de droite avec un lien de dépôt : 3
  Lignes de gauche désignées             : 4
    confirment une jointure sûre         : 1
    confirment une suggestion            : 1
    contredisent la jointure             : 1
    apparieraient une ligne seule        : 1

Ressemblance de noms, jugée sur 8 paires sûres
----------------------------------------------
  Meilleur candidat contre toute la droite, par tranche de score :
    0.1–0.2  juste    0  faux    1
    0.5–0.6  juste    0  faux    1
    0.8–0.9  juste    3  faux    0
    0.9–1.0  juste    3  faux    0
  Avec la marge actuelle, à chaque seuil :
    0.3  proposées    6  justes    6  précision 100 %  rappel  75 %
    0.4  proposées    6  justes    6  précision 100 %  rappel  75 %
    0.5  proposées    6  justes    6  précision 100 %  rappel  75 %
    0.6  proposées    6  justes    6  précision 100 %  rappel  75 %
    0.7  proposées    6  justes    6  précision 100 %  rappel  75 %
    0.8  proposées    6  justes    6  précision 100 %  rappel  75 %
    0.9  proposées    3  justes    3  précision 100 %  rappel  38 %
  Suggestions refusées : {ambigu=1, aucun candidat=1, numéros différents=1, déjà proposé=1, sous le seuil=1}

  Paires sûres dont les noms divergent (score < 0.3) :
      team-f/zp-web — org.example:zportal / zeta-portal — score 0.00, apparié par nom_pom

  Mots fréquents des lignes seules (* = absent à droite) :
    app 1, delta 1, extra* 1, gamma 1, lonely* 1, stuff* 1, thing 1, tools* 1
  Préfixes des clés de droite jamais retenues :
    org.example: 5, grp.delta: 1
  Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start correspondances.csv`. Pour un autre outil : --comma.
  Rapport     : <tmp>/rapport.json

╔═ moreThanTenStalePairsPrintOnlyTheFirstTen/mapping ═╗
﻿"gauche_nom";"droite_code";"methode";"confiance";"candidat_nom";"score_nom";"second_nom";"rejet_nom";"m_ref"⏎
"p0";"p0";"ref";"exact";"";"";"";"";"p0"⏎
"p1";"";"aucune";"none";"";"";"";"";""⏎
"p2";"";"aucune";"none";"";"";"";"";""⏎
"p3";"";"aucune";"none";"";"";"";"";""⏎
"p4";"";"aucune";"none";"";"";"";"";""⏎
"p5";"";"aucune";"none";"";"";"";"";""⏎
"p6";"";"aucune";"none";"";"";"";"";""⏎
"p7";"";"aucune";"none";"";"";"";"";""⏎
"p8";"";"aucune";"none";"";"";"";"";""⏎
"p9";"";"aucune";"none";"";"";"";"";""⏎
"p10";"";"aucune";"none";"";"";"";"";""⏎
"p11";"";"aucune";"none";"";"";"";"";""⏎

╔═ moreThanTenStalePairsPrintOnlyTheFirstTen/stdout ═╗

Appariement
  Gauche : 12 lignes (<tmp>/l.csv)
  Droite : 1 lignes (<tmp>/r.csv)

  Appariement repris de <tmp>/correspondances.csv
    0 gardées, 0 saisies à la main, 0 rejetées, 12 recalculées, 0 nouveaux projets
    12 paires vers une clé qui n'existe plus, recalculées :
      p0 → gone0
      p1 → gone1
      p2 → gone2
      p3 → gone3
      p4 → gone4
      p5 → gone5
      p6 → gone6
      p7 → gone7
      p8 → gone8
      p9 → gone9
    Ancienne version : correspondances.csv.bak

Méthodes retenues
-----------------
  ref                                :     1
  aucune                             :    11

  Jointures sûres : 1 / 12 (8 %) · suggestions : 0 · sans rien : 11
  Lignes de droite jamais retenues : 0 / 1

Chaque méthode, seule
---------------------
  propose = désigne quelque chose · ambiguë = plusieurs candidats
  retenue = a tranché · seule = aucune autre méthode sûre ne propose
                   propose   ambiguë   retenue     seule
  ref                    1         0         1         1
  Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start correspondances.csv`. Pour un autre outil : --comma.

╔═ reapparierRecomputesExactAndDerivedButNotManualOrRejected/mapping ═╗
"gauche_path","droite_key","methode","confiance","candidat_nom","score_nom","second_nom","rejet_nom","m_cle_ci","m_cle_pom","m_liaison","m_ns_map","m_chemin","m_nom_pom","m_liens","liens_concorde","m_noms_libre"
"team-a/alpha-service","team-a:alpha","clé lue dans la CI","exact","","","","","team-a:alpha","","","","","","team-a:alpha","oui","team-a:alpha (0.95)"
"team-a/beta","org.example:beta","clé Maven du pom","exact","","","","","","org.example:beta","","org.example:beta","","","org.example:beta-legacy","non","org.example:beta (0.89)"
"team-b/gamma","org.example:gamma","liaison DevOps Sonar → GitLab","exact","","","","","","","org.example:gamma","org.example:gamma","","","","","org.example:gamma (0.87)"
"team-c/101-delta","grp.delta:delta","rejeté à la main","rejete","","","","","","","","grp.delta:delta","","","","","grp.delta:delta (0.91)"
"team-e/12-","TEAM-E_12","clé normalisée = chemin GitLab","derived","","","","","","","","","TEAM-E_12","","","","TEAM-E_12 (1.00)"
"team-f/zp-web","org.example:zportal","nom du pom = nom Sonar","derived","","","","","","","","","","org.example:zportal","","","TEAM-E_12 (0.16, sous le seuil)"
"team-f/common-ui","org.example:common-b","saisi à la main","manuel","org.example:common-a","0.57","org.example:common-b (0.57)","ambigu","","","","","","org.example:common-a org.example:common-b","","","org.example:common-a (0.57, ambigu)"
"team-g/inventory-tracker","org.example:invntory-tracker","noms ressemblants (TF-IDF)","suggestion","org.example:invntory-tracker","0.82","","","","","","","","","","","org.example:invntory-tracker (0.82)"
"team-h/lonely-thing","","aucune","none","","","","aucun candidat","","","","","","","org.example:beta-legacy","","org.example:eps-key (0.29, sous le seuil)"
"app1","","aucune","none","","","","numéros différents","","","","","","","","",""
"team-j/report-engine","org.example:reporting-engine-svc","noms ressemblants (TF-IDF)","suggestion","org.example:reporting-engine-svc","0.85","","","","","","","","","org.example:reporting-engine-svc","oui","org.example:reporting-engine-svc (0.85)"
"team-k/report-engine","org.example:reporting-engine-svc","saisi à la main","manuel","org.example:reporting-engine-svc","0.85","","déjà proposé à team-j/report-engine","","","","","","","","","org.example:reporting-engine-svc (0.85)"
"team-l/gamma-tools-extra-stuff","","aucune","none","org.example:gamma-gh","0.23","","sous le seuil","","","","","","","","","org.example:gamma (0.36, sous le seuil)"
"team-m/epsilon-api","org.example:eps-key","clé lue dans la CI","exact","","","","","org.example:eps-key","","","org.example:epsilon-api","","","","","org.example:epsilon-api (0.95)"
"old/removed-project","org.example:x","saisi à la main","manuel","","","","","","","","","","","","",""

╔═ reapparierRecomputesExactAndDerivedButNotManualOrRejected/stdout ═╗

Appariement
  Gauche : 14 lignes (<tmp>/pratiques.csv)
  Droite : 16 lignes (<tmp>/inventaire.csv)

  Appariement repris de <tmp>/correspondances.csv (--reapparier : exact et derived recalculés)
    0 gardées, 2 saisies à la main, 1 rejetées, 5 recalculées, 4 nouveaux projets
    1 lignes pour des projets absents de cet audit, conservées.
    2 paires vers une clé qui n'existe plus, recalculées :
      team-g/inventory-tracker → org.example:vanished (manuel)
      team-j/report-engine → (manuel)
    Ancienne version : correspondances.csv.bak

Méthodes retenues
-----------------
  clé lue dans la CI                 :     2
  clé Maven du pom                   :     1
  liaison DevOps Sonar → GitLab      :     1
  mapping namespace GL→Sonar         :     0
  clé normalisée = chemin GitLab     :     1
  nom du pom = nom Sonar             :     1
  noms ressemblants (TF-IDF)         :     2
  aucune                             :     3
  rejeté à la main                   :     1
  saisi à la main                    :     2

  Jointures sûres : 8 / 14 (57 %) · suggestions : 2 · sans rien : 3
  Lignes de droite jamais retenues : 7 / 16

Chaque méthode, seule
---------------------
  propose = désigne quelque chose · ambiguë = plusieurs candidats
  retenue = a tranché · seule = aucune autre méthode sûre ne propose
                   propose   ambiguë   retenue     seule
  cle_ci                 2         0         2         1
  cle_pom                1         0         1         0
  liaison                1         0         1         0
  ns_map                 4         0         0         1
  chemin                 1         0         1         1
  nom_pom                2         1         1         2
  liens                  4         0         0         2
  noms                   9         0         2         3

Accords entre méthodes
----------------------
  cle_ci     × ns_map     :    1 en commun,    0 d'accord, 1 en désaccord
  cle_ci     × liens      :    1 en commun,    1 d'accord, 0 en désaccord
  cle_ci     × noms       :    2 en commun,    1 d'accord, 1 en désaccord
  cle_pom    × ns_map     :    1 en commun,    1 d'accord, 0 en désaccord
  cle_pom    × liens      :    1 en commun,    0 d'accord, 1 en désaccord
  cle_pom    × noms       :    1 en commun,    1 d'accord, 0 en désaccord
  liaison    × ns_map     :    1 en commun,    1 d'accord, 0 en désaccord
  liaison    × noms       :    1 en commun,    1 d'accord, 0 en désaccord
  ns_map     × liens      :    1 en commun,    0 d'accord, 1 en désaccord
  ns_map     × noms       :    4 en commun,    4 d'accord, 0 en désaccord
  chemin     × noms       :    1 en commun,    1 d'accord, 0 en désaccord
  liens      × noms       :    3 en commun,    2 d'accord, 1 en désaccord
      team-m/epsilon-api — cle_ci → org.example:eps-key ; ns_map → org.example:epsilon-api
      team-m/epsilon-api — cle_ci → org.example:eps-key ; noms → org.example:epsilon-api
      team-a/beta — cle_pom → org.example:beta ; liens → org.example:beta-legacy
      team-a/beta — ns_map → org.example:beta ; liens → org.example:beta-legacy
      team-a/beta — liens → org.example:beta-legacy ; noms → org.example:beta

Liens « liens » (informatif, n'apparie rien)
--------------------------------------------
  Lignes de droite avec un lien de dépôt : 3
  Lignes de gauche désignées             : 4
    confirment une jointure sûre         : 1
    confirment une suggestion            : 1
    contredisent la jointure             : 1
    apparieraient une ligne seule        : 1

Ressemblance de noms, jugée sur 8 paires sûres
----------------------------------------------
  Meilleur candidat contre toute la droite, par tranche de score :
    0.1–0.2  juste    0  faux    1
    0.5–0.6  juste    0  faux    1
    0.8–0.9  juste    3  faux    0
    0.9–1.0  juste    2  faux    1
  Avec la marge actuelle, à chaque seuil :
    0.3  proposées    6  justes    5  précision  83 %  rappel  63 %
    0.4  proposées    6  justes    5  précision  83 %  rappel  63 %
    0.5  proposées    6  justes    5  précision  83 %  rappel  63 %
    0.6  proposées    6  justes    5  précision  83 %  rappel  63 %
    0.7  proposées    6  justes    5  précision  83 %  rappel  63 %
    0.8  proposées    6  justes    5  précision  83 %  rappel  63 %
    0.9  proposées    3  justes    2  précision  67 %  rappel  25 %
  Suggestions refusées : {ambigu=1, aucun candidat=1, numéros différents=1, déjà proposé=1, sous le seuil=1}

  Fausses propositions sur des paires sûres :
      team-m/epsilon-api — proposé org.example:epsilon-api (0.95), vrai org.example:eps-key (0.00, par cle_ci)

  Paires sûres dont les noms divergent (score < 0.3) :
      team-f/zp-web — org.example:zportal / zeta-portal — score 0.00, apparié par nom_pom
      team-m/epsilon-api — org.example:eps-key / Unrelated Thing — score 0.00, apparié par cle_ci

  Mots fréquents des lignes seules (* = absent à droite) :
    app 1, delta 1, extra* 1, gamma 1, lonely* 1, stuff* 1, thing 1, tools* 1
  Préfixes des clés de droite jamais retenues :
    org.example: 5, grp.delta: 1
  Format machine (virgule, sans BOM).

╔═ [end of file] ═╗
