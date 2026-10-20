╔═ customMethodsWithPlainNamesCommaOutputAndTunedThresholds/mapping ═╗
"gauche_nom","droite_code","methode","confiance","candidat_nom","score_nom","second_nom","rejet_nom","m_ref","m_nd","m_url","url_concorde","m_noms_libre"
"Alpha Billing","alpha-billing","ref","exact","","","","","alpha-billing","alpha-billing","","","alpha-billing (1.00)"
"beta-reports","beta-legacy","ref","exact","","","","","beta-legacy","","beta-legacy","oui","beta (0.95)"
"gamma_store","gamma-store","nd","derived","","","","","","gamma-store","","","gamma-store (1.00)"
"delta-hub","hub-one","ref","exact","","","","","hub-one hub-two","","","","hub-one (0.82)"
"epsilon","eps","noms ressemblants (TF-IDF)","suggestion","eps","0.52","","","","","eps","oui","eps (0.52)"
"Zeta Portal V2","zeta2","noms ressemblants (TF-IDF)","suggestion","zeta2","0.95","","","","","","","zeta2 (0.95)"
"theta","theta","nd","derived","","","","","","theta","","","theta (1.00)"

╔═ customMethodsWithPlainNamesCommaOutputAndTunedThresholds/report ═╗
{
  "summary" : {
    "left" : 7,
    "right" : 9,
    "byMethod" : {
      "ref" : 3,
      "nd" : 2,
      "noms ressemblants (TF-IDF)" : 2,
      "aucune" : 0
    },
    "joined" : 5,
    "suggestions" : 2,
    "unmatched" : 0,
    "rightUnmatched" : 2,
    "threshold" : 0.4,
    "margin" : 0.05
  },
  "unavailable" : [ ],
  "methods" : [ {
    "id" : "ref",
    "label" : "ref",
    "kind" : "exact",
    "proposes" : 3,
    "ambiguous" : 1,
    "wins" : 3,
    "alone" : 2
  }, {
    "id" : "nd",
    "label" : "nd",
    "kind" : "derived",
    "proposes" : 3,
    "ambiguous" : 0,
    "wins" : 2,
    "alone" : 2
  }, {
    "id" : "url",
    "label" : "url",
    "kind" : "link",
    "proposes" : 2,
    "ambiguous" : 0,
    "wins" : 0,
    "alone" : 1
  }, {
    "id" : "noms",
    "label" : "noms ressemblants (TF-IDF)",
    "kind" : "names",
    "proposes" : 7,
    "ambiguous" : 0,
    "wins" : 2,
    "alone" : 2
  } ],
  "agreements" : [ {
    "a" : "ref",
    "b" : "nd",
    "both" : 1,
    "agree" : 1,
    "disagree" : 0
  }, {
    "a" : "ref",
    "b" : "url",
    "both" : 1,
    "agree" : 1,
    "disagree" : 0
  }, {
    "a" : "ref",
    "b" : "noms",
    "both" : 3,
    "agree" : 2,
    "disagree" : 1
  }, {
    "a" : "nd",
    "b" : "noms",
    "both" : 3,
    "agree" : 3,
    "disagree" : 0
  }, {
    "a" : "url",
    "b" : "noms",
    "both" : 2,
    "agree" : 1,
    "disagree" : 1
  } ],
  "disagreements" : [ {
    "left" : "beta-reports",
    "detail" : "ref → beta-legacy ; noms → beta"
  }, {
    "left" : "beta-reports",
    "detail" : "url → beta-legacy ; noms → beta"
  } ],
  "links" : [ {
    "id" : "url",
    "rightWithLinks" : 2,
    "leftPointedAt" : 2,
    "confirmSure" : 1,
    "confirmSuggestion" : 1,
    "contradict" : 0,
    "wouldJoin" : 0
  } ],
  "names" : {
    "evaluatedOn" : 5,
    "scoreOfBestCandidate" : [ {
      "range" : "0.0–0.1",
      "correct" : 0,
      "wrong" : 0
    }, {
      "range" : "0.1–0.2",
      "correct" : 0,
      "wrong" : 0
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
      "wrong" : 0
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
      "correct" : 1,
      "wrong" : 0
    }, {
      "range" : "0.9–1.0",
      "correct" : 3,
      "wrong" : 1
    } ],
    "thresholds" : [ {
      "threshold" : 0.3,
      "proposed" : 5,
      "correct" : 4,
      "precision" : 0.8,
      "recall" : 0.8
    }, {
      "threshold" : 0.4,
      "proposed" : 5,
      "correct" : 4,
      "precision" : 0.8,
      "recall" : 0.8
    }, {
      "threshold" : 0.5,
      "proposed" : 5,
      "correct" : 4,
      "precision" : 0.8,
      "recall" : 0.8
    }, {
      "threshold" : 0.6,
      "proposed" : 5,
      "correct" : 4,
      "precision" : 0.8,
      "recall" : 0.8
    }, {
      "threshold" : 0.7,
      "proposed" : 5,
      "correct" : 4,
      "precision" : 0.8,
      "recall" : 0.8
    }, {
      "threshold" : 0.8,
      "proposed" : 5,
      "correct" : 4,
      "precision" : 0.8,
      "recall" : 0.8
    }, {
      "threshold" : 0.9,
      "proposed" : 4,
      "correct" : 3,
      "precision" : 0.75,
      "recall" : 0.6
    } ],
    "rejections" : { },
    "wrongOnSurePairs" : [ {
      "left" : "beta-reports",
      "detail" : "proposé beta (0.95), vrai beta-legacy (0.56, par ref)"
    } ],
    "divergentSurePairs" : [ ],
    "nearMisses" : [ ],
    "unmatchedLeftTokens" : [ ],
    "unmatchedRightPrefixes" : [ {
      "token" : "(sans séparateur)",
      "count" : 1,
      "onRight" : true
    }, {
      "token" : "hub-",
      "count" : 1,
      "onRight" : true
    } ]
  }
}
╔═ customMethodsWithPlainNamesCommaOutputAndTunedThresholds/stdout ═╗

Appariement
  Gauche : 7 lignes (<tmp>/a.csv)
  Droite : 9 lignes (<tmp>/b.csv)

  Appariement : 7 lignes → <tmp>/map.csv
    Réutilisé aux prochains lancements. Pour corriger une paire :
    confiance = manuel (et la clé dans droite_…), ou rejete.

Méthodes retenues
-----------------
  ref                                :     3
  nd                                 :     2
  noms ressemblants (TF-IDF)         :     2
  aucune                             :     0

  Jointures sûres : 5 / 7 (71 %) · suggestions : 2 · sans rien : 0
  Lignes de droite jamais retenues : 2 / 9

Chaque méthode, seule
---------------------
  propose = désigne quelque chose · ambiguë = plusieurs candidats
  retenue = a tranché · seule = aucune autre méthode sûre ne propose
                   propose   ambiguë   retenue     seule
  ref                    3         1         3         2
  nd                     3         0         2         2
  url                    2         0         0         1
  noms                   7         0         2         2

Accords entre méthodes
----------------------
  ref        × nd         :    1 en commun,    1 d'accord, 0 en désaccord
  ref        × url        :    1 en commun,    1 d'accord, 0 en désaccord
  ref        × noms       :    3 en commun,    2 d'accord, 1 en désaccord
  nd         × noms       :    3 en commun,    3 d'accord, 0 en désaccord
  url        × noms       :    2 en commun,    1 d'accord, 1 en désaccord
      beta-reports — ref → beta-legacy ; noms → beta
      beta-reports — url → beta-legacy ; noms → beta

Liens « url » (informatif, n'apparie rien)
------------------------------------------
  Lignes de droite avec un lien de dépôt : 2
  Lignes de gauche désignées             : 2
    confirment une jointure sûre         : 1
    confirment une suggestion            : 1
    contredisent la jointure             : 0
    apparieraient une ligne seule        : 0

Ressemblance de noms, jugée sur 5 paires sûres
----------------------------------------------
  Meilleur candidat contre toute la droite, par tranche de score :
    0.8–0.9  juste    1  faux    0
    0.9–1.0  juste    3  faux    1
  Avec la marge actuelle, à chaque seuil :
    0.3  proposées    5  justes    4  précision  80 %  rappel  80 %
    0.4  proposées    5  justes    4  précision  80 %  rappel  80 %
    0.5  proposées    5  justes    4  précision  80 %  rappel  80 %
    0.6  proposées    5  justes    4  précision  80 %  rappel  80 %
    0.7  proposées    5  justes    4  précision  80 %  rappel  80 %
    0.8  proposées    5  justes    4  précision  80 %  rappel  80 %
    0.9  proposées    4  justes    3  précision  75 %  rappel  60 %

  Fausses propositions sur des paires sûres :
      beta-reports — proposé beta (0.95), vrai beta-legacy (0.56, par ref)
  Préfixes des clés de droite jamais retenues :
    (sans séparateur) 1, hub- 1
  Format machine (virgule, sans BOM).
  Rapport     : <tmp>/rep.json

╔═ emptyInputsGiveAnEmptyReport/report ═╗
{
  "summary" : {
    "left" : 0,
    "right" : 0,
    "byMethod" : {
      "mapping namespace GL→Sonar" : 0,
      "clé normalisée = chemin GitLab" : 0,
      "noms ressemblants (TF-IDF)" : 0,
      "aucune" : 0
    },
    "joined" : 0,
    "suggestions" : 0,
    "unmatched" : 0,
    "rightUnmatched" : 0,
    "threshold" : 0.5,
    "margin" : 0.1
  },
  "unavailable" : [ "clé lue dans la CI (colonne absente, gauche : cle_sonar)", "clé Maven du pom (colonne absente, gauche : cle_pom)", "liaison DevOps Sonar → GitLab (colonne absente, gauche : id, droite : alm_repository, droite : alm)", "nom du pom = nom Sonar (colonne absente, gauche : nom_pom, droite : name)", "liens saisis dans Sonar (colonne absente, droite : liens)" ],
  "methods" : [ {
    "id" : "ns_map",
    "label" : "mapping namespace GL→Sonar",
    "kind" : "mapped",
    "proposes" : 0,
    "ambiguous" : 0,
    "wins" : 0,
    "alone" : 0
  }, {
    "id" : "chemin",
    "label" : "clé normalisée = chemin GitLab",
    "kind" : "derived",
    "proposes" : 0,
    "ambiguous" : 0,
    "wins" : 0,
    "alone" : 0
  }, {
    "id" : "noms",
    "label" : "noms ressemblants (TF-IDF)",
    "kind" : "names",
    "proposes" : 0,
    "ambiguous" : 0,
    "wins" : 0,
    "alone" : 0
  } ],
  "agreements" : [ ],
  "disagreements" : [ ],
  "links" : [ ],
  "names" : {
    "evaluatedOn" : 0,
    "scoreOfBestCandidate" : [ {
      "range" : "0.0–0.1",
      "correct" : 0,
      "wrong" : 0
    }, {
      "range" : "0.1–0.2",
      "correct" : 0,
      "wrong" : 0
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
      "wrong" : 0
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
      "correct" : 0,
      "wrong" : 0
    }, {
      "range" : "0.9–1.0",
      "correct" : 0,
      "wrong" : 0
    } ],
    "thresholds" : [ {
      "threshold" : 0.3,
      "proposed" : 0,
      "correct" : 0,
      "precision" : null,
      "recall" : 0.0
    }, {
      "threshold" : 0.4,
      "proposed" : 0,
      "correct" : 0,
      "precision" : null,
      "recall" : 0.0
    }, {
      "threshold" : 0.5,
      "proposed" : 0,
      "correct" : 0,
      "precision" : null,
      "recall" : 0.0
    }, {
      "threshold" : 0.6,
      "proposed" : 0,
      "correct" : 0,
      "precision" : null,
      "recall" : 0.0
    }, {
      "threshold" : 0.7,
      "proposed" : 0,
      "correct" : 0,
      "precision" : null,
      "recall" : 0.0
    }, {
      "threshold" : 0.8,
      "proposed" : 0,
      "correct" : 0,
      "precision" : null,
      "recall" : 0.0
    }, {
      "threshold" : 0.9,
      "proposed" : 0,
      "correct" : 0,
      "precision" : null,
      "recall" : 0.0
    } ],
    "rejections" : { },
    "wrongOnSurePairs" : [ ],
    "divergentSurePairs" : [ ],
    "nearMisses" : [ ],
    "unmatchedLeftTokens" : [ ],
    "unmatchedRightPrefixes" : [ ]
  }
}
╔═ emptyInputsGiveAnEmptyReport/stdout ═╗

Appariement
  Gauche : 0 lignes (<tmp>/a.csv)
  Droite : 0 lignes (<tmp>/b.csv)

Méthodes retenues
-----------------
  mapping namespace GL→Sonar         :     0
  clé normalisée = chemin GitLab     :     0
  noms ressemblants (TF-IDF)         :     0
  aucune                             :     0

  Jointures sûres : 0 / 0 (—) · suggestions : 0 · sans rien : 0
  Lignes de droite jamais retenues : 0 / 0
  Non évaluée : clé lue dans la CI (colonne absente, gauche : cle_sonar)
  Non évaluée : clé Maven du pom (colonne absente, gauche : cle_pom)
  Non évaluée : liaison DevOps Sonar → GitLab (colonne absente, gauche : id, droite : alm_repository, droite : alm)
  Non évaluée : nom du pom = nom Sonar (colonne absente, gauche : nom_pom, droite : name)
  Non évaluée : liens saisis dans Sonar (colonne absente, droite : liens)

Chaque méthode, seule
---------------------
  propose = désigne quelque chose · ambiguë = plusieurs candidats
  retenue = a tranché · seule = aucune autre méthode sûre ne propose
                   propose   ambiguë   retenue     seule
  ns_map                 0         0         0         0
  chemin                 0         0         0         0
  noms                   0         0         0         0

Ressemblance de noms, jugée sur 0 paires sûres
----------------------------------------------
  Meilleur candidat contre toute la droite, par tranche de score :
  Avec la marge actuelle, à chaque seuil :
    0.3  proposées    0  justes    0  précision    —  rappel   0 %
    0.4  proposées    0  justes    0  précision    —  rappel   0 %
    0.5  proposées    0  justes    0  précision    —  rappel   0 %
    0.6  proposées    0  justes    0  précision    —  rappel   0 %
    0.7  proposées    0  justes    0  précision    —  rappel   0 %
    0.8  proposées    0  justes    0  précision    —  rappel   0 %
    0.9  proposées    0  justes    0  précision    —  rappel   0 %
  Rapport     : <tmp>/r.json

╔═ manyDisagreementsPrintFiveAndCountTheRest ═╗

Appariement
  Gauche : 7 lignes (<tmp>/a.csv)
  Droite : 14 lignes (<tmp>/b.csv)

Méthodes retenues
-----------------
  ref                                :     7
  nd                                 :     0
  aucune                             :     0

  Jointures sûres : 7 / 7 (100 %) · suggestions : 0 · sans rien : 0
  Lignes de droite jamais retenues : 7 / 14
  Non évaluée : url (colonne absente, gauche : url, droite : urls)

Chaque méthode, seule
---------------------
  propose = désigne quelque chose · ambiguë = plusieurs candidats
  retenue = a tranché · seule = aucune autre méthode sûre ne propose
                   propose   ambiguë   retenue     seule
  ref                    7         0         7         0
  nd                     7         0         0         0

Accords entre méthodes
----------------------
  ref        × nd         :    7 en commun,    0 d'accord, 7 en désaccord
      item-0 — ref → other-0 ; nd → item-0
      item-1 — ref → other-1 ; nd → item-1
      item-2 — ref → other-2 ; nd → item-2
      item-3 — ref → other-3 ; nd → item-3
      item-4 — ref → other-4 ; nd → item-4
      … 1 de plus dans le rapport JSON.

╔═ methodsAndNamesOnMissingColumnsAreReportedUnavailable ═╗

Appariement
  Gauche : 1 lignes (<tmp>/a.csv)
  Droite : 1 lignes (<tmp>/b.csv)

Méthodes retenues
-----------------
  nd                                 :     1
  aucune                             :     0

  Jointures sûres : 1 / 1 (100 %) · suggestions : 0 · sans rien : 0
  Lignes de droite jamais retenues : 0 / 1
  Non évaluée : ref (colonne absente, gauche : ref, droite : ref_externe, droite : kind)
  Non évaluée : noms ressemblants (TF-IDF) (colonne absente)

Chaque méthode, seule
---------------------
  propose = désigne quelque chose · ambiguë = plusieurs candidats
  retenue = a tranché · seule = aucune autre méthode sûre ne propose
                   propose   ambiguë   retenue     seule
  nd                     1         0         1         1

╔═ namesAsPathsScoreTheLastSegmentDouble ═╗
"gauche_nom","droite_code","methode","confiance","candidat_nom","score_nom","second_nom","rejet_nom","m_noms_libre"
"grp/alpha-billing","x1","noms ressemblants (TF-IDF)","suggestion","x1","0.88","x2 (0.27)","","x1 (0.88)"
"billing/other","x2","noms ressemblants (TF-IDF)","suggestion","x2","0.97","x1 (0.17)","","x2 (0.97)"

╔═ onlyARightKeyOverrideKeepsThePresetLeftKey ═╗
"gauche_path","droite_code","methode","confiance","candidat_nom","score_nom","second_nom","rejet_nom","m_ns_map","m_chemin","m_noms_libre"
"team-a/alpha","","aucune","none","","","","aucun candidat","","",""

╔═ thePresetKeysCanBeOverridden/mapping ═╗
"gauche_cle_sonar","droite_key","methode","confiance","candidat_nom","score_nom","second_nom","rejet_nom","m_cle_ci","m_ns_map","m_chemin","m_noms_libre"
"Alpha","k1","noms ressemblants (TF-IDF)","suggestion","k1","0.82","","","","","","k1 (0.82)"

╔═ thePresetKeysCanBeOverridden/stdout ═╗

Appariement
  Gauche : 1 lignes (<tmp>/a.csv)
  Droite : 1 lignes (<tmp>/b.csv)

  Appariement : 1 lignes → <tmp>/m.csv
    Réutilisé aux prochains lancements. Pour corriger une paire :
    confiance = manuel (et la clé dans droite_…), ou rejete.

Méthodes retenues
-----------------
  clé lue dans la CI                 :     0
  mapping namespace GL→Sonar         :     0
  clé normalisée = chemin GitLab     :     0
  noms ressemblants (TF-IDF)         :     1
  aucune                             :     0

  Jointures sûres : 0 / 1 (0 %) · suggestions : 1 · sans rien : 0
  Lignes de droite jamais retenues : 0 / 1
  Non évaluée : clé Maven du pom (colonne absente, gauche : cle_pom)
  Non évaluée : liaison DevOps Sonar → GitLab (colonne absente, gauche : id, droite : alm_repository, droite : alm)
  Non évaluée : nom du pom = nom Sonar (colonne absente, gauche : nom_pom)
  Non évaluée : liens saisis dans Sonar (colonne absente, droite : liens)

Chaque méthode, seule
---------------------
  propose = désigne quelque chose · ambiguë = plusieurs candidats
  retenue = a tranché · seule = aucune autre méthode sûre ne propose
                   propose   ambiguë   retenue     seule
  cle_ci                 0         0         0         0
  ns_map                 0         0         0         0
  chemin                 0         0         0         0
  noms                   1         0         1         1

Ressemblance de noms, jugée sur 0 paires sûres
----------------------------------------------
  Meilleur candidat contre toute la droite, par tranche de score :
  Avec la marge actuelle, à chaque seuil :
    0.3  proposées    0  justes    0  précision    —  rappel   0 %
    0.4  proposées    0  justes    0  précision    —  rappel   0 %
    0.5  proposées    0  justes    0  précision    —  rappel   0 %
    0.6  proposées    0  justes    0  précision    —  rappel   0 %
    0.7  proposées    0  justes    0  précision    —  rappel   0 %
    0.8  proposées    0  justes    0  précision    —  rappel   0 %
    0.9  proposées    0  justes    0  précision    —  rappel   0 %
  Format machine (virgule, sans BOM).

╔═ [end of file] ═╗
