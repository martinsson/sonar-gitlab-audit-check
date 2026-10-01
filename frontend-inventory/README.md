# frontend-inventory

Inventaire des technos frontend d'une instance GitLab entière : qui est en Vue 2,
Vue 3, React, Angular..., avec l'activité récente et la taille de chaque projet.
Lecture seule. Autonome : aucun lien avec le reste de ce dépôt.

```bash
export GITLAB_URL=https://gitlab.example.com GITLAB_TOKEN=glpat-xxxxxxxx
jbang FrontendInventory.java                    # → frontend.csv, fenêtre de 90 jours
jbang FrontendInventory.java --days 30 --out f.csv --limit 50
```

Le CSV est au format Excel (point-virgule, BOM UTF-8).

## Méthode

- **Projets** : tous les projets non archivés que le jeton voit.
- **Techno** : `package.json` à la racine de la branche par défaut, dépendances,
  devDependencies et peerDependencies confondues.
  - pas de `package.json` → `aucun`
  - `package.json` sans framework UI → `node sans framework UI`
  - sinon la liste des frameworks trouvés, ex. `Vue 2`, `Vue 3 + React`
  - la version majeure vient de la plage déclarée (`^2.6.14` → 2), pas du lockfile
- **Activité** sur la fenêtre : commits de la branche par défaut, MR créées
  (tous états), lignes ajoutées et supprimées hors commits de merge.
- **Taille** : `repository_size` et `commit_count` des statistiques GitLab
  (vides si le jeton n'a pas au moins le rôle Reporter).

## Colonnes

| Colonne | Contenu |
|---|---|
| `techno` | `aucun`, `node sans framework UI`, ou les frameworks trouvés |
| `vue`, `react`, `angular` | plage de version déclarée |
| `autres_front` | Svelte, jQuery |
| `outillage` | nuxt, next, vite, @vue/cli-service, react-scripts, typescript |
| `commits_Nj`, `mr_Nj`, `lignes_ajoutees_Nj`, `lignes_supprimees_Nj` | activité sur la fenêtre |
| `taille_depot_mo`, `commits_total` | taille du projet |
| `erreur` | ce qui n'a pas pu être lu pour ce projet |
