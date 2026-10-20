---
name: inventaire-frontend
description: Inventorier les technos frontend (Vue 2, Vue 3, React, Angular…) de tous les projets d'une instance GitLab avec FrontendInventory, avec leur activité récente et leur taille. À utiliser pour savoir quels projets sont en Vue 2, préparer une migration frontend, ou compter les projets par framework.
metadata:
  opencode/slash: "true"
---

# Inventaire frontend

Lecture seule, autonome (voir `frontend-inventory/README.md`). La sortie est une donnée client :
ne pas la commiter, ne pas la publier.

```bash
export GITLAB_URL=https://gitlab… GITLAB_TOKEN=glpat-…   # read_api

cd frontend-inventory
jbang FrontendInventory.java                              # → frontend.csv, fenêtre de 90 jours
jbang FrontendInventory.java --days 30 --out f.csv --limit 50
```

La techno vient du `package.json` à la racine de la branche par défaut, et la version majeure de
la plage déclarée, pas du lockfile. `taille_depot_mo` et `commits_total` restent vides sous le
rôle Reporter. La colonne `erreur` dit ce qui n'a pas pu être lu.

Rendre compte en donnant le nombre de projets par techno, puis les projets Vue 2 ou AngularJS
les plus actifs.
