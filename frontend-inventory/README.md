# frontend-inventory

Inventaire des technos frontend d'une instance GitLab entière : qui est en Vue 2,
Vue 3, React, Angular..., avec la part de code front et l'activité récente de chaque projet.
Lecture seule. Autonome : aucun lien avec le reste de ce dépôt.

```bash
export GITLAB_URL=https://gitlab.example.com GITLAB_TOKEN=glpat-xxxxxxxx
jbang FrontendInventory.java                    # → frontend.csv, fenêtre de 90 jours
jbang FrontendInventory.java --days 30 --out f.csv --limit 50
jbang FrontendInventory.java --seuil-front 10 --threads 4 --profondeur 4
```

Trois fichiers sortent, nommés d'après `--out` :

| Fichier | Contenu |
|---|---|
| `frontend.csv` | tous les projets |
| `frontend-front.csv` | les projets dont la part de front atteint `--seuil-front` (5 % par défaut) |
| `frontend-rapport.html` | par techno et par outil : nombre de projets, projets actifs, lignes estimées, lignes modifiées sur la fenêtre |

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
| `taille_depot_octets` | `repository_size` des statistiques GitLab (historique git compris) |
| `estimation_lignes` | `taille_depot_octets` / 40 : un ordre de grandeur, pas un comptage |
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
