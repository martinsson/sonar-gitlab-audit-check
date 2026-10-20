╔═ aRightKeyWhoseShortNameNormalisesToItselfIsIndexedOnce ═╗
"gauche_path","droite_key","methode","confiance","candidat_nom","score_nom","second_nom","rejet_nom","m_ns_map","m_chemin","m_noms_libre"
"team-a/alpha","-alpha","mapping namespace GL→Sonar","derived","","","","","-alpha","","-alpha (0.82)"

╔═ evaluationCornersWithPlainNames/mapping ═╗
"gauche_nom","droite_code","methode","confiance","candidat_nom","score_nom","second_nom","rejet_nom","m_ref","m_noms_libre"
"42","c42","ref","exact","","","","","c42",""
"alpha beta","c5","ref","exact","","","","","c5","ab1 (0.27, sous le seuil)"
"orphan words","","aucune","none","","","","aucun candidat","",""

╔═ evaluationCornersWithPlainNames/report ═╗
{
  "summary" : {
    "left" : 3,
    "right" : 3,
    "byMethod" : {
      "ref" : 2,
      "noms ressemblants (TF-IDF)" : 0,
      "aucune" : 1
    },
    "joined" : 2,
    "suggestions" : 0,
    "unmatched" : 1,
    "rightUnmatched" : 1,
    "threshold" : 0.5,
    "margin" : 0.1
  },
  "unavailable" : [ ],
  "methods" : [ {
    "id" : "ref",
    "label" : "ref",
    "kind" : "exact",
    "proposes" : 2,
    "ambiguous" : 0,
    "wins" : 2,
    "alone" : 2
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
    "evaluatedOn" : 2,
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
      "wrong" : 1
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
    "rejections" : {
      "aucun candidat" : 1
    },
    "wrongOnSurePairs" : [ ],
    "divergentSurePairs" : [ {
      "left" : "42",
      "detail" : " — score 0.00, apparié par ref"
    }, {
      "left" : "alpha beta",
      "detail" : "zzzz — score 0.00, apparié par ref"
    } ],
    "nearMisses" : [ ],
    "unmatchedLeftTokens" : [ {
      "token" : "orphan",
      "count" : 1,
      "onRight" : false
    }, {
      "token" : "words",
      "count" : 1,
      "onRight" : false
    } ],
    "unmatchedRightPrefixes" : [ {
      "token" : "(sans séparateur)",
      "count" : 1,
      "onRight" : true
    } ]
  }
}
╔═ evaluationCornersWithPlainNames/stdout ═╗

Appariement
  Gauche : 3 lignes (<tmp>/a.csv)
  Droite : 3 lignes (<tmp>/b.csv)

  Appariement : 3 lignes → <tmp>/m.csv
    Réutilisé aux prochains lancements. Pour corriger une paire :
    confiance = manuel (et la clé dans droite_…), ou rejete.

Méthodes retenues
-----------------
  ref                                :     2
  noms ressemblants (TF-IDF)         :     0
  aucune                             :     1

  Jointures sûres : 2 / 3 (67 %) · suggestions : 0 · sans rien : 1
  Lignes de droite jamais retenues : 1 / 3

Chaque méthode, seule
---------------------
  propose = désigne quelque chose · ambiguë = plusieurs candidats
  retenue = a tranché · seule = aucune autre méthode sûre ne propose
                   propose   ambiguë   retenue     seule
  ref                    2         0         2         2
  noms                   0         0         0         0

Ressemblance de noms, jugée sur 2 paires sûres
----------------------------------------------
  Meilleur candidat contre toute la droite, par tranche de score :
    0.2–0.3  juste    0  faux    1
  Avec la marge actuelle, à chaque seuil :
    0.3  proposées    0  justes    0  précision    —  rappel   0 %
    0.4  proposées    0  justes    0  précision    —  rappel   0 %
    0.5  proposées    0  justes    0  précision    —  rappel   0 %
    0.6  proposées    0  justes    0  précision    —  rappel   0 %
    0.7  proposées    0  justes    0  précision    —  rappel   0 %
    0.8  proposées    0  justes    0  précision    —  rappel   0 %
    0.9  proposées    0  justes    0  précision    —  rappel   0 %
  Suggestions refusées : {aucun candidat=1}

  Paires sûres dont les noms divergent (score < 0.3) :
      42 —  — score 0.00, apparié par ref
      alpha beta — zzzz — score 0.00, apparié par ref

  Mots fréquents des lignes seules (* = absent à droite) :
    orphan* 1, words* 1
  Préfixes des clés de droite jamais retenues :
    (sans séparateur) 1
  Format machine (virgule, sans BOM).
  Rapport     : <tmp>/r.json

╔═ onlyADerivedMethod ═╗
"gauche_nom","droite_code","methode","confiance","candidat_nom","score_nom","second_nom","rejet_nom","m_d"
"Alpha One","alpha_one","d","derived","","","","","alpha_one"
"beta","","aucune","none","","","","",""

╔═ onlyALinkMethodJoinsNothing/mapping ═╗
"gauche_nom","droite_code","methode","confiance","candidat_nom","score_nom","second_nom","rejet_nom","m_l","l_concorde"
"grp/alpha","","aucune","none","","","","","k1",""

╔═ onlyALinkMethodJoinsNothing/stdout ═╗

Appariement
  Gauche : 1 lignes (<tmp>/a.csv)
  Droite : 1 lignes (<tmp>/b.csv)

  Appariement : 1 lignes → <tmp>/m.csv
    Réutilisé aux prochains lancements. Pour corriger une paire :
    confiance = manuel (et la clé dans droite_…), ou rejete.

Méthodes retenues
-----------------
  aucune                             :     1

  Jointures sûres : 0 / 1 (0 %) · suggestions : 0 · sans rien : 1
  Lignes de droite jamais retenues : 1 / 1

Chaque méthode, seule
---------------------
  propose = désigne quelque chose · ambiguë = plusieurs candidats
  retenue = a tranché · seule = aucune autre méthode sûre ne propose
                   propose   ambiguë   retenue     seule
  l                      1         0         0         1

Liens « l » (informatif, n'apparie rien)
----------------------------------------
  Lignes de droite avec un lien de dépôt : 1
  Lignes de gauche désignées             : 1
    confirment une jointure sûre         : 0
    confirment une suggestion            : 0
    contredisent la jointure             : 0
    apparieraient une ligne seule        : 1
  Format machine (virgule, sans BOM).

╔═ [end of file] ═╗
