╔═ coloursAlwaysWrapsTitlesAndHintsInAnsiCodes ═╗
<ESC>[1m
Appariement<ESC>[0m
  Gauche : 14 lignes (<tmp>/pratiques.csv)
  Droite : 16 lignes (<tmp>/inventaire.csv)

  Appariement repris de <tmp>/m.csv<ESC>[33m (--reapparier : exact et derived recalculés)<ESC>[0m
    0 gardées, 0 saisies à la main, 0 rejetées, 1 recalculées, 13 nouveaux projets
<ESC>[2m    Ancienne version : m.csv.bak<ESC>[0m

<ESC>[1mMéthodes retenues<ESC>[0m
<ESC>[2m-----------------<ESC>[0m
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

<ESC>[1mChaque méthode, seule<ESC>[0m
<ESC>[2m---------------------<ESC>[0m
<ESC>[2m  propose = désigne quelque chose · ambiguë = plusieurs candidats<ESC>[0m
<ESC>[2m  retenue = a tranché · seule = aucune autre méthode sûre ne propose<ESC>[0m
                   propose   ambiguë   retenue     seule
  cle_ci                 2         0         2         1
  cle_pom                1         0         1         0
  liaison                1         0         1         0
  ns_map                 4         0         1         1
  chemin                 1         0         1         1
  nom_pom                2         1         1         2
  liens                  4         0         0         2
  noms                   9         0         2         3

<ESC>[1mAccords entre méthodes<ESC>[0m
<ESC>[2m----------------------<ESC>[0m
  cle_ci     × ns_map     :    1 en commun,    0 d'accord, <ESC>[33m1<ESC>[0m en désaccord
  cle_ci     × liens      :    1 en commun,    1 d'accord, <ESC>[32m0<ESC>[0m en désaccord
  cle_ci     × noms       :    2 en commun,    1 d'accord, <ESC>[33m1<ESC>[0m en désaccord
  cle_pom    × ns_map     :    1 en commun,    1 d'accord, <ESC>[32m0<ESC>[0m en désaccord
  cle_pom    × liens      :    1 en commun,    0 d'accord, <ESC>[33m1<ESC>[0m en désaccord
  cle_pom    × noms       :    1 en commun,    1 d'accord, <ESC>[32m0<ESC>[0m en désaccord
  liaison    × ns_map     :    1 en commun,    1 d'accord, <ESC>[32m0<ESC>[0m en désaccord
  liaison    × noms       :    1 en commun,    1 d'accord, <ESC>[32m0<ESC>[0m en désaccord
  ns_map     × liens      :    1 en commun,    0 d'accord, <ESC>[33m1<ESC>[0m en désaccord
  ns_map     × noms       :    4 en commun,    4 d'accord, <ESC>[32m0<ESC>[0m en désaccord
  chemin     × noms       :    1 en commun,    1 d'accord, <ESC>[32m0<ESC>[0m en désaccord
  liens      × noms       :    3 en commun,    2 d'accord, <ESC>[33m1<ESC>[0m en désaccord
      team-m/epsilon-api — cle_ci → org.example:eps-key ; ns_map → org.example:epsilon-api
      team-m/epsilon-api — cle_ci → org.example:eps-key ; noms → org.example:epsilon-api
      team-a/beta — cle_pom → org.example:beta ; liens → org.example:beta-legacy
      team-a/beta — ns_map → org.example:beta ; liens → org.example:beta-legacy
      team-a/beta — liens → org.example:beta-legacy ; noms → org.example:beta

<ESC>[1mLiens « liens » (informatif, n'apparie rien)<ESC>[0m
<ESC>[2m--------------------------------------------<ESC>[0m
  Lignes de droite avec un lien de dépôt : 3
  Lignes de gauche désignées             : 4
    confirment une jointure sûre         : 1
    confirment une suggestion            : 1
    contredisent la jointure             : 1
    apparieraient une ligne seule        : 1

<ESC>[1mRessemblance de noms, jugée sur 7 paires sûres<ESC>[0m
<ESC>[2m----------------------------------------------<ESC>[0m
<ESC>[2m  Meilleur candidat contre toute la droite, par tranche de score :<ESC>[0m
    0.1–0.2  juste    0  faux    1
    0.8–0.9  juste    2  faux    0
    0.9–1.0  juste    3  faux    1
<ESC>[2m  Avec la marge actuelle, à chaque seuil :<ESC>[0m
    0.3  proposées    6  justes    5  précision  83 %  rappel  71 %
    0.4  proposées    6  justes    5  précision  83 %  rappel  71 %
    0.5  proposées    6  justes    5  précision  83 %  rappel  71 %
    0.6  proposées    6  justes    5  précision  83 %  rappel  71 %
    0.7  proposées    6  justes    5  précision  83 %  rappel  71 %
    0.8  proposées    6  justes    5  précision  83 %  rappel  71 %
    0.9  proposées    4  justes    3  précision  75 %  rappel  43 %
  Suggestions refusées : {ambigu=1, aucun candidat=1, numéros différents=1, déjà proposé=1, sous le seuil=1}
<ESC>[2m
  Fausses propositions sur des paires sûres :<ESC>[0m
      team-m/epsilon-api — proposé org.example:epsilon-api (0.95), vrai org.example:eps-key (0.00, par cle_ci)
<ESC>[2m
  Paires sûres dont les noms divergent (score < 0.3) :<ESC>[0m
      team-f/zp-web — org.example:zportal / zeta-portal — score 0.00, apparié par nom_pom
      team-m/epsilon-api — org.example:eps-key / Unrelated Thing — score 0.00, apparié par cle_ci
<ESC>[2m
  Quasi-réussites :<ESC>[0m
      team-k/report-engine — org.example:reporting-engine-svc (0.85) — déjà proposé à team-j/report-engine
      team-f/common-ui — org.example:common-a (0.57) — ambigu, suivant org.example:common-b (0.57)
<ESC>[2m
  Mots fréquents des lignes seules (* = absent à droite) :<ESC>[0m
    app 1, common 1, engine 1, extra* 1, gamma 1, lonely* 1, report* 1, stuff* 1, thing 1, tools* 1, ui* 1
<ESC>[2m  Préfixes des clés de droite jamais retenues :<ESC>[0m
    org.example: 6
<ESC>[2m  Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start m.csv`. Pour un autre outil : --comma.<ESC>[0m

╔═ helpListsEveryOptionAndTheFooter ═╗
Usage: ProjectMapper [-hV] [--comma] [--plain-names] [--reapparier]
                     [--color=<mode>] [--examples=<examples>] --left=<leftCsv>
                     [--left-key=<leftKey>] [--marge-nom=<margin>]
                     [--names=<names>] [--namespace-mapping=<yaml>]
                     [--out=<out>] [--report=<report>] --right=<rightCsv>
                     [--right-key=<rightKey>] [--seuil-nom=<threshold>]
                     [--derived=<derived>]... [--exact=<exact>]...
                     [--link=<link>]...
Apparie deux CSV et évalue chaque méthode d'appariement.
      --left=<leftCsv>       CSV à apparier (une ligne de sortie par ligne)
      --right=<rightCsv>     CSV où chercher l'homologue
      --left-key=<leftKey>   colonne qui nomme une ligne à gauche (défaut du
                               préréglage : path)
      --right-key=<rightKey> colonne qui nomme une ligne à droite (défaut du
                               préréglage : key)
      --exact=<exact>        méthode par égalité stricte (répétable)
      --derived=<derived>    méthode par égalité normalisée (répétable)
      --link=<link>          méthode informative par URL de dépôt (répétable)
      --names=<names>        ressemblance de noms : gauche=droite1,droite2
      --plain-names          le nom de gauche n'est pas un chemin : pas de
                               dernier segment privilégié
      --seuil-nom=<threshold>
                             score minimal d'une suggestion, 0–1 (défaut : 0.5)
      --marge-nom=<margin>   écart minimal avec le candidat suivant (défaut :
                               0.1)
      --namespace-mapping=<yaml>
                             namespace GitLab → préfixe Sonar (défaut :
                               namespace-mapping.yaml)
      --out=<out>            CSV d'appariement, une ligne par ligne de gauche
      --reapparier           recalculer les paires exact/derived d'un --out
                               existant (manuel et rejete restent)
      --report=<report>      rapport JSON d'évaluation des méthodes
      --examples=<examples>  exemples par rubrique du rapport (défaut : 15)
  -h, --help                 Show this help message and exit.
  -V, --version              Print version information and exit.
      --comma                CSV séparé par des virgules, sans BOM (pour un
                               outil, pas Excel)
      --color=<mode>         auto | always | never (défaut : auto)

Sans --exact, --derived, --link ni --names : préréglage Sonar × GitLab
(gauche = pratiques.csv de GitlabActivityAudit --deep, droite =
inventaire de SonarAuditCheck --csv).

Méthode : id:colonne_gauche=colonne_droite[?colonne=valeur]
  --exact    égalité stricte             → confiance exact
  --derived  égalité après normalisation → confiance derived
  --link     la colonne droite porte des URL de dépôt ; informatif
Appliquées dans l'ordre donné, --exact d'abord.

--names gauche=droite1,droite2 : ressemblance TF-IDF, en suggestion.

╔═ [end of file] ═╗
