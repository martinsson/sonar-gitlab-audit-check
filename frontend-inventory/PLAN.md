# Frontend Inventory - Plan d'action

## Contexte

Script Java autonome (`FrontendInventory.java`) utilisant jbang qui scanne une instance GitLab entière pour inventorier les technos frontend. Il utilise l'API GitLab (`/languages`, `/repository/files/package.json/raw`, `/repository/commits`, `/merge_requests`) pour collecter des données sur chaque projet.

## Fichiers de sortie attendus

### 1. `frontend.csv` — Tous les projets
Format Excel (point-virgule, BOM UTF-8, CRLF). Colonnes dans cet ordre :

```
id, projet, url, branche, derniere_activite,
commits_90j, mr_90j, lignes_ajoutees_90j, lignes_supprimees_90j, commits_total,
part_front_pct, langages,
package_json, vue, react, angular, autres_front, outillage,
taille_depot_octets, estimation_lignes,
techno, a_verifier, erreur
```

**Nouvelles colonnes :**
- `taille_depot_octets` : `repository_size` via API `?statistics=true` sur chaque projet
- `estimation_lignes` : `repository_size / 40` (hypothèse : 1 ligne ≈ 40 octets)

### 2. `frontend-front50.csv` — Projets >= 5% de code front
Même format que `frontend.csv`, mais filtré sur `part_front_pct >= 5`.

### 3. `frontend-report.html` — Rapport HTML standalone
Fichier HTML autonome (CSS inline, pas de dépendance externe) avec un tableau :

| Métrique | Total | Vue | React | Angular | Svelte | jQuery | ... |
|----------|-------|-----|-------|---------|--------|--------|-----|
| Nb projets | X | ... | ... | ... | ... | ... | ... |
| Nb projets actifs (≥1 commit 90j) | X | ... | ... | ... | ... | ... | ... |
| Nb total lignes (tous projets) | X | ... | ... | ... | ... | ... | ... |
| Nb total lignes (projets actifs) | X | ... | ... | ... | ... | ... | ... |

## Définition "frontend"

Un projet est considéré comme ayant du frontend si :
1. **Langages GitLab front** : JavaScript, TypeScript, TSX, JSX, Vue, Svelte, HTML, CSS, SCSS, Sass, Less, Stylus (somme des % > 0)
   OU
2. **Framework UI détecté** via package.json : Vue, React, Angular, AngularJS, Svelte, jQuery, htmx, alpine.js, blazor, react-native, ionic, etc.

## Détention des technos

### Frameworks détectés (via package.json)

**Frameworks principaux :**
- Vue (version majeure extraite de la plage : `^2.6.14` → 2)
- React
- Angular / AngularJS
- Svelte
- jQuery

**Méta-frameworks / outillage :**
- nuxt, next, @vue/cli-service, vite, react-scripts, typescript

**Nouvelles technos à ajouter :**
- `htmx` — Hypermedia framework
- `alpine.js` — AlpineJS
- `@microsoft/blazor` ou `microsoft.aspnetcore.components.webassembly` — Blazor
- `react-native` — React Native
- `@ionic` — Ionic
- `@prefresh` — PWA/Prepack

### Technos INDÉTECTABLES par cette méthode

Ces technos ne seront **jamais** détectées par l'analyse de package.json ou `/languages` :

| Techno | Pourquoi indétectable |
|--------|----------------------|
| **Randall** | Framework interne GE, code backend Java qui génère du HTML, pas de package.json |
| **OutSystems** | Plateforme low-code externe, code non dans GitLab |
| **Mendix** | Plateforme low-code externe, code non dans GitLab |
| **PowerApps** | Microsoft, pas dans GitLab |
| **JSP / Servlets** | Code Java backend, détecté comme Java par Linguist mais pas comme frontend |
| **Thymeleaf** | Templates Java backend, détectés comme Java/XML |
| **ASP.NET MVC / Razor** | Code C# backend, détecté comme C# |
| **PHP (Symfony, Laravel)** | Détecté comme PHP, pas de package.json nécessaire |
| **jQuery commité sans framework** | Détecté comme jQuery mais sans version majeure claire |
| **Scripts CDN** | Frameworks chargés depuis CDN, pas dans package.json |
| **Web Components personnalisés** | Vanilla JS/TS sans framework |
| **GWT (Google Web Toolkit)** | Code Java compilé en JS |
| **Vaadin** | Framework Java, package.json optionnel |
| **React/Vue/Angular dans `target/` ou `build/`** | Dossiers ignorés par PRUNED (build outputs) |

## Méthode actuelle

- **Projets** : tous les projets non archivés que le jeton voit
- **Part de front** : somme des % de langages front via `/projects/:id/languages`
- **Où chercher les `package.json`** :
  - part de front ≥ `--seuil-front` (5% par défaut) : dans les dossiers jusqu'à `--profondeur` (3 par défaut)
  - sinon : seulement à la racine
- **Techno** : dépendances, devDependencies et peerDependencies sur tous les `package.json` trouvés
- **Activité** : commits, MR, lignes ajoutées/supprimées sur la fenêtre de N jours
- **Taille** : `repository_size` via API statistics

## Colonnes actuelles

| Colonne | Contenu |
|---------|---------|
| `part_front_pct` | part du code en langages front, en %. Vide si GitLab n'a pas calculé les langages |
| `langages` | tous les langages GitLab avec leur %, ex. `Java 80.0, TypeScript 15.0, HTML 5.0` |
| `techno` | `aucun`, `node sans framework UI`, ou les frameworks trouvés |
| `package_json` | chemins des `package.json` lus, ex. `front/package.json, admin/package.json` |
| `vue`, `react`, `angular` | plages de version déclarées, séparées par ` / ` s'il y en a plusieurs |
| `autres_front` | Svelte, jQuery, htmx, alpine.js... |
| `outillage` | nuxt, next, vite, @vue/cli-service, react-scripts, typescript... |
| `commits_Nj`, `mr_Nj`, `lignes_ajoutees_Nj`, `lignes_supprimees_Nj` | activité sur la fenêtre |
| `commits_total` | `commit_count` des statistiques GitLab |
| `taille_depot_octets` | Taille du dépôt en octets |
| `estimation_lignes` | `taille_depot_octets / 40` |
| `a_verifier` | cas que les règles ne tranchent pas |
| `erreur` | ce qui n'a pas pu être lu |

## Usage

```bash
export GITLAB_URL=https://gitlab.example.com GITLAB_TOKEN=glpat-xxxxxxxx
jbang FrontendInventory.java                    # → frontend.csv, frontend-front50.csv, frontend-report.html
jbang FrontendInventory.java --days 30 --limit 50 --threads 10 --seuil-front 5 --profondeur 3
```

## Optimisations actuelles

- Appels parallèles avec `CompletableFuture` + `ExecutorService`
- Option `--threads N` pour contrôler le nombre de threads
- Pagination keyset pour les appels API
- Retry automatique sur 429/5xx avec `Retry-After`
