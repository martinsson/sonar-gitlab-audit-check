# frontend-inventory

Inventaire des technos frontend d'une instance GitLab entière : qui est en Vue 2,
Vue 3, React, Angular..., avec la part de code front, ses lignes au dernier commit et
l'activité récente de chaque projet. Lecture seule.

Un seul lien avec le reste de ce dépôt : le comptage des lignes est dans
`src/main/java/ch/ge/edg/sonar/gitlab/audit/text/FrontLines.java` (JDK seul), que le script
inclut par `//SOURCES` et que `./verify` teste. Lancer le script depuis une copie du dépôt.

```bash
export GITLAB_URL=https://gitlab.example.com GITLAB_TOKEN=glpat-xxxxxxxx
jbang FrontendInventory.java                    # → frontend.csv, fenêtre de 90 jours
jbang FrontendInventory.java --days 30 --out f.csv --limit 50
jbang FrontendInventory.java --seuil-front 10 --threads 4 --profondeur 4
jbang FrontendInventory.java --sans-comptage     # sans télécharger les archives (plus rapide)
jbang FrontendInventory.java --max-archive-mo 50 --archives-simultanees 1
```

Trois fichiers sortent, nommés d'après `--out` :

| Fichier | Contenu |
|---|---|
| `frontend.csv` | tous les projets |
| `frontend-front.csv` | les projets dont la part de front atteint `--seuil-front` (5 % par défaut) |
| `frontend-rapport.html` | par techno et par outil : nombre de projets, projets actifs, lignes front, lignes modifiées sur la fenêtre |

Les CSV sont au format Excel (point-virgule, BOM UTF-8). Un fichier existant n'est jamais
écrasé : si `frontend.csv` existe, la sortie va dans `frontend-1.csv` (et `frontend-1-front.csv`,
`frontend-1-rapport.html`), puis `frontend-2.csv`...
Chaque ligne de `frontend.csv` est écrite dès que son projet est traité, dans l'ordre où ils se
terminent : après un arrêt ou une erreur, le fichier contient déjà les projets finis. Les deux
autres fichiers sont écrits à la fin.

## Méthode

- **Projets** : tous les projets non archivés que le jeton voit.
- **Part de front** : les langages que GitLab calcule (`/projects/:id/languages`,
  % d'octets, branche par défaut, fichiers tiers exclus). La part de front est la
  somme de JavaScript, TypeScript, TSX, JSX, Vue, Svelte, HTML, CSS, SCSS, Sass,
  Less et Stylus.
- **Où chercher les `package.json`** :
  - part de front ≥ `--seuil-front` (5 % par défaut) : dans les dossiers jusqu'à
    `--profondeur` (3 par défaut, la racine vaut 0 : `src/main/webapp/package.json` est
    trouvé, `module/src/main/frontend/package.json` non). Un appel par dossier visité,
    sans descendre dans `node_modules/`, `bower_components/`, `vendor/`, `target/`,
    `build/`, `dist/`, `src/main/java/` ni `src/test/`. Au plus 200 appels par projet ;
  - sinon : seulement à la racine, un appel.
- **Techno** : dépendances, devDependencies et peerDependencies confondues, sur
  tous les `package.json` trouvés.
  - pas de `package.json` → `aucun`
  - `package.json` sans framework UI → `node sans framework UI`
  - sinon la liste des frameworks trouvés, ex. `Vue 2`, `Vue 3 + React`
  - la version majeure vient de la plage déclarée (`^2.6.14` → 2), pas du lockfile
- **Lignes front** : seulement pour les projets dont la `techno` est un framework UI (pas
  `aucun` ni `node sans framework UI`). Pour chaque dossier qui contient un `package.json`
  trouvé (sans ceux inclus dans un autre ; un `package.json` à la racine couvre tout le
  dépôt), l'archive de la branche par défaut :
  `GET /projects/:id/repository/archive.tar.gz?sha=<branche>&path=<dossier>`. C'est l'arbre
  du dernier commit, sans historique, lu en flux et compté en mémoire, rien n'est écrit sur disque.
  - comptés : les fichiers `.js .jsx .mjs .cjs .ts .tsx .vue .svelte .css .scss .sass .less
    .styl .html .htm` ; une ligne compte si elle n'est pas vide (blancs seuls = vide).
    Les commentaires comptent : c'est le `code + comment` de cloc, pas son `code` ;
  - écartés : tout ce qui est sous `node_modules/`, `bower_components/`, `jspm_packages/`,
    `vendor/`, `dist/`, `build/`, `target/`, `coverage/`, `.nuxt/`, `.next/`, `.output/`,
    `.svelte-kit/`, `.angular/`, `.cache/` ; les `*.min.js`, `*.min.css`, `*.bundle.js`,
    `*.chunk.js` ; les fichiers de plus de 1 Mo ; ceux qui contiennent un octet NUL (binaires) ;
    ceux dont les lignes font en moyenne plus de 300 octets (minifiés sous un autre nom).
    Les fichiers écartés pour une de ces quatre dernières raisons sont comptés dans
    `fichiers_ecartes`, pour vérifier que la règle n'a pas jeté du vrai code ;
  - coût : une archive par dossier, au plus `--archives-simultanees` (2) en même temps quel
    que soit `--threads`, car GitLab (Gitaly) construit l'archive et peut en limiter le débit
    (429 : on attend `Retry-After`, jusqu'à 8 essais). Au-delà de `--max-archive-mo` (200 Mo
    compressés par dossier), la lecture s'arrête et `methode_lignes` le dit.
    `--sans-comptage` ne télécharge rien.
- **Activité** sur la fenêtre : commits de la branche par défaut, MR créées
  (tous états), lignes ajoutées et supprimées hors commits de merge. Un projet est
  actif s'il a au moins un commit sur la fenêtre.
- **Rapport HTML** : il part de la colonne `techno` ; un projet `Vue 3 + React` compte
  dans Vue 3 et dans React, la ligne Total ne le compte qu'une fois.

## Colonnes

| Colonne | Contenu |
|---|---|
| `part_front_pct` | part du code en langages front, en %. Vide si GitLab n'a pas calculé les langages |
| `langages` | tous les langages GitLab avec leur %, ex. `Java 80.0, TypeScript 15.0, HTML 5.0` |
| `techno` | `aucun`, `node sans framework UI`, ou les frameworks trouvés |
| `package_json` | chemins des `package.json` lus, ex. `front/package.json, admin/package.json` |
| `vue`, `react`, `angular` | plages de version déclarées, séparées par ` / ` s'il y en a plusieurs |
| `autres_front` | Svelte, jQuery, htmx, AlpineJS, React Native, Ionic, Preact |
| `outillage` | nuxt, next, vite, @vue/cli-service, react-scripts, typescript |
| `commits_Nj`, `mr_Nj`, `lignes_ajoutees_Nj`, `lignes_supprimees_Nj` | activité sur la fenêtre |
| `commits_total` | `commit_count` des statistiques GitLab (vide sans le rôle Reporter) |
| `taille_depot_octets` | `repository_size` des statistiques GitLab : le dépôt git compressé, tout l'historique et tous les fichiers. Ne dit rien des lignes (ci-dessous) |
| `lignes_front` | lignes non vides des fichiers front au dernier commit (Méthode). Vide si non mesuré |
| `fichiers_front` | nombre de fichiers comptés |
| `lignes_front_par_ext` | les mêmes lignes par extension, ex. `vue 5308, js 641, html 36` |
| `perimetre_lignes` | dossiers comptés, ex. `front/, admin/` ou `(racine)` |
| `methode_lignes` | `archive HEAD`, `archive HEAD, tronquée à 200 Mo`, `non mesuré (pas de framework UI)`, `non mesuré (--sans-comptage)` ou `erreur` (détail dans `erreur`) |
| `fichiers_ecartes` | fichiers à extension front écartés : `minifiés`, `> 1 Mo`, `binaires`, `lignes trop longues` |
| `a_verifier` | cas que les règles ne tranchent pas, à regarder à la main (ci-dessous) |
| `erreur` | ce qui n'a pas pu être lu pour ce projet |

### `a_verifier`

| Valeur | Situation |
|---|---|
| `front sans package.json` | part de front au-dessus du seuil, aucun `package.json` : JSP, HTML avec scripts chargés par CDN, jQuery commité... |
| `front sans framework déclaré` | part de front au-dessus du seuil, des `package.json` mais aucun des frameworks suivis |
| `fichiers .vue sans dépendance vue` | GitLab voit du Vue, aucun `package.json` ne déclare `vue` (souvent Nuxt 3) |
| `plusieurs versions de Vue` (ou d'un autre framework) | des `package.json` qui déclarent des versions majeures différentes |
| `arborescence tronquée` | plus de 200 appels pour parcourir les dossiers : les `package.json` restants n'ont pas été cherchés |
| `<chemin> illisible` | un `package.json` qui n'est pas du JSON valide |
| `langages non calculés par GitLab` | `/languages` vide : seule la racine a été regardée |

## Pourquoi l'ancienne `estimation_lignes` était fausse

Jusqu'ici la colonne valait `repository_size / 40`. `repository_size` est la taille sur disque
du dépôt git : toutes les versions de tous les fichiers depuis le premier commit (refs de MR
comprises), compressées par git, binaires commités compris (images, jar, pdf...), et tous
langages confondus. La part de front de `/languages` n'y était même pas appliquée. Les
40 octets par ligne étaient arbitraires.

Les erreurs ne se compensent pas : l'historique et les binaires gonflent, la compression git
dégonfle (le texte se compresse beaucoup, les binaires peu). Le rapport entre l'estimation et le
vrai nombre dépend donc du projet, aucun coefficient ne la corrige. Mesuré sur trois projets
publics de gitlab.com (taille du pack d'un `git clone --mirror`, qui approche `repository_size` ;
comptage au dernier commit de la branche par défaut, dans les dossiers des `package.json`) :

| Projet (public) | Pack git | Ancienne estimation | Lignes front non vides | cloc `code` (mêmes fichiers) | Écart |
|---|---:|---:|---:|---:|---:|
| bounca/bounca (Django + Vue, `front/`) | 49,9 Mo | 1 247 000 | 6 043 | 5 985 | × 206 |
| verbose-equals-true/django-postgres-vue-gitlab-ecs (`quasar/`, `documentation/`) | 5,0 Mo | 126 000 | 3 065 | 2 912 | × 41 |
| radiobrowser/radiobrowser-web-angular (racine) | 2,3 Mo | 56 800 | 11 381 | 10 860 | × 5 |

Sur bounca, l'arbre du dernier commit pèse 46 Mo, presque tout en captures PNG de la
documentation : même « % front × taille » donnerait 34 % × 1,25 M = 425 000 lignes, pour 6 000.
L'écart entre lignes non vides et cloc `code` (1 à 5 %) correspond aux commentaires.

## Recoupement avec SonarQube

Pour les projets analysés, la mesure `ncloc_language_distribution` de Sonar
(`/api/measures/component?component=<clé>&metricKeys=ncloc_language_distribution`, ex.
`css=1295;ts=5244;web=4001`) est un bon contrôle : même ordre de grandeur attendu, un peu en
dessous de `lignes_front` (Sonar ne compte ni commentaires ni lignes vides). Ce n'est pas
une source : seulement les projets analysés, pour la branche et la date de la dernière analyse,
sur le périmètre `sonar.sources`/exclusions du projet, et il faut apparier projet Sonar et
projet GitLab (ce que fait `CrossAudit` dans ce dépôt). Pas implémenté ici.

## Autres façons de mesurer, écartées

| Idée | Pourquoi non |
|---|---|
| % front de `/languages` × `repository_size` | multiplie une part du dernier commit par une taille d'historique compressé binaires compris : × 70 sur bounca |
| autres statistiques GitLab (`storage_size`, `lfs_objects_size`, `wiki_size`...) | aucune ne mesure l'arbre courant ; `storage_size` ajoute wiki, LFS, artefacts, paquets |
| `git clone --depth 1 --filter=blob:none` + sparse checkout + cloc/scc | exact, sépare code et commentaires, mais demande git et cloc/scc sur le poste, du disque, un jeton `read_repository` (git en HTTPS) en plus de `read_api` ; l'archive donne les mêmes octets par l'API déjà utilisée |
| `/repository/tree?recursive=true` puis la taille de chaque fichier | l'arbre ne donne pas de taille : un appel par fichier (des milliers par projet), et une taille reste à convertir en lignes |
| GraphQL `repository { blobs(paths) { rawSize } }` | des tailles, pas des lignes, et il faut d'abord la liste des chemins |
| `ncloc` de SonarQube | contrôle utile (ci-dessus), pas une source : projets non analysés absents, périmètre et date variables |
| somme des ajouts − suppressions de tous les commits | tous les commits avec `with_stats`, énorme sur un vieux dépôt ; compte le généré, le vendorisé, les renommages |
| archive de tout le dépôt quel que soit l'emplacement des `package.json` | télécharge plus et compte du HTML hors front (templates serveur, documentation) ; fait seulement quand le `package.json` est à la racine |
| retirer les commentaires (le `code` de cloc) | il faut un analyseur par langage (gabarits, regex JS, sections `.vue`) ; l'écart mesuré est de 1 à 5 % |
