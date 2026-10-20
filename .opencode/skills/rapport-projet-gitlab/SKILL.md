---
name: rapport-projet-gitlab
description: Produire le rapport détaillé d'un seul projet GitLab avec GitLabProjectReport — cadence des commits, contributeurs et facteur bus, merge requests et revue, constats. À utiliser quand on veut regarder un projet précis de près, comprendre comment une équipe travaille sur un dépôt, ou compléter la fiche d'un projet repéré par l'audit.
metadata:
  opencode/slash: "true"
---

# Rapport d'un projet GitLab

Lecture seule. Un seul projet, déjà choisi (sinon, utiliser `audit-sonar-gitlab` pour le
trouver). Les données sont celles du client : rien ne se commite ni ne sort de l'entreprise.

```bash
export GITLAB_URL=https://gitlab… GITLAB_TOKEN=glpat-…   # portée read_api

jbang GitLabProjectReport.java --path groupe/sous-groupe/projet
jbang GitLabProjectReport.java --path https://gitlab…/groupe/projet/-/tree/main --days 365
jbang GitLabProjectReport.java --path 1234               # id numérique
```

Options :

- **Fenêtre.** `--days 180` par défaut.
- **Plafonds.** `--max-commits 5000` et `--max-mrs 500` par défaut. Quand un plafond est atteint,
  les taux portent sur la période réellement couverte, et le rapport le dit.
- **Échantillon de revue.** `--sample 30` merge requests pour le délai de revue.
- **Bots.** `--include-bots` les compte comme contributeurs ; `--bot-pattern` remplace le motif
  par défaut.
- **Capture.** `--dump-dir` garde les réponses brutes.

À savoir en lisant le rapport :

- Les commits de merge sont exclus partout.
- Sous le rôle Reporter, les statistiques du projet sont absentes. Le rapport le dit au lieu
  d'afficher zéro.
- Un fort taux de squash transforme les heures de commit en heures de merge.

Rendre compte en résumant les sections et les constats, et en citant les limites signalées par
le rapport (plafonds atteints, données absentes).
