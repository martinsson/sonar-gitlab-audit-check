---
name: extraction-dora
description: Vérifier l'accès à Jira et GoCD, retrouver le projet Jira et les pipelines GoCD d'un projet GitLab, puis extraire tickets, runs de pipelines et commits déployés pour les métriques DORA (fréquence de déploiement, délai commit → prod, taux d'échec, temps de rétablissement) et le taux de bugs. À utiliser dès qu'on parle de DORA, de Jira, de GoCD, de déploiements, d'incidents, de taux de bugs, de PAT Jira ou GoCD, ou des dossiers probes/ et dora/.
metadata:
  opencode/slash: "true"
---

# Extraction DORA : Jira et GoCD

Scripts JBang en lecture seule (GET seulement), authentifiés par PAT. Détails dans
`probes/README.md` et `dora/README.md`.

## Règles à respecter

- Ces outils ne tournent que sur le réseau du client.
- Tout ce qu'ils écrivent va sous `extraction/` (ignoré par git). Ne jamais le commiter, ne jamais
  le publier.
- Les sondes masquent les noms par défaut : leur rapport peut sortir de l'entreprise.
  `--show-names` et `--dump-dir` sont pour une lecture locale seulement. Un résumé destiné à
  sortir ne contient ni nom de projet, ni nom de pipeline, ni nom de personne.
- Les correspondances proposées par `DoraLocate` sont des suppositions : les montrer et attendre
  une confirmation avant de lancer l'extraction.

## Prérequis

```bash
export GITLAB_URL=… GITLAB_TOKEN=…   # read_api
export JIRA_URL=…   JIRA_TOKEN=…     # PAT Jira Data Center (profil > Personal Access Tokens)
export GOCD_URL=…   GOCD_TOKEN=…     # PAT GoCD (menu utilisateur > Personal Access Tokens)
```

Vérifier leur présence sans les afficher. Instances connues : Jira 11.3.10, GoCD 25.3.0.

## 1. Valider l'accès (une fois, ou après un changement de jeton)

```bash
mkdir -p extraction/probes
jbang probes/JiraProbe.java > extraction/probes/jira-probe.txt
jbang probes/GocdProbe.java > extraction/probes/gocd-probe.txt
```

`GocdProbe` essaie aussi `GOCD_USER`/`GOCD_PASSWORD` et `GOCD_COOKIE` (JSESSIONID du navigateur)
s'ils sont présents, et garde le mode qui voit le plus de pipelines. Lire la section « Bilan » de
chaque rapport.

## 2. Retrouver Jira et GoCD pour un projet GitLab

```bash
jbang dora/DoraLocate.java --gitlab-project groupe/projet
```

- **Jira.** Les préfixes de clés `ABC-123` cités dans les commits, les MR et les branches, vérifiés
  dans Jira.
- **GoCD.** Les pipelines branchés sur le dépôt, puis ceux qui en dépendent (build → recette →
  prod).
- **L'index GoCD.** La première recherche lit un historique par pipeline visible et écrit
  `extraction/gocd-index.json`, réutilisé pendant un jour (`--refresh-index` pour le reconstruire).

## 3. Extraire

```bash
jbang dora/JiraExtract.java --project ABC --out-dir extraction/<projet>/jira
jbang dora/GocdExtract.java --pipeline p-build,p-deploy-prod --durations --out-dir extraction/<projet>/gocd
jbang dora/GocdExtract.java --group mon-groupe --out-dir extraction/<projet>/gocd
```

- **Période.** `--days 365` par défaut, pour les deux.
- **Jira.** `--jql` remplace la requête ; `--field` ajoute des champs (id ou nom).
- **Fichiers bruts.** `--no-raw` n'écrit pas les `.jsonl` bruts. Ils sont utiles pour retraiter
  sans ré-extraire.

Fichiers produits :

- `jira-tickets.csv` : dates, délais de résolution et de cycle, champs de plateforme ou de gravité ;
- `jira-transitions.csv` : chaque changement de statut ;
- `jira-versions.csv` : les versions du projet et leurs dates de livraison ;
- `gocd-runs.csv` : résultat de chaque run, commits embarqués, délai commit → run, clés Jira ;
- `gocd-stages.csv` : chaque stage, son approbation manuelle ou automatique, sa durée ;
- `gocd-commits.csv` : chaque commit déployé, et par quel pipeline amont il est arrivé.

## 4. Contrôler avant d'en tirer des chiffres

- **Volumes.** Nombre de lignes de chaque fichier et période réellement couverte.
- **Jira.** Répartition par type et par statut.
- **GoCD.** Runs par pipeline et par `plateforme_supposee`. C'est une supposition tirée du nom,
  à faire valider.
- **Commits manquants.** Part des runs prod sans aucun commit. Un rebuild forcé sans nouveau commit
  n'embarque rien, c'est normal ; une proportion forte ne l'est pas.
- **Lien GoCD ↔ Jira.** Les clés de `gocd-commits.csv` retrouvées dans `jira-tickets.csv`.
- **Définitions par projet.** Ce qu'est une mise en prod et ce qu'est un incident se décident
  projet par projet, avec l'utilisateur. Proposer une définition, compter ce qu'elle retient, et
  attendre son choix.

Si une API répond autrement qu'attendu (404 sur la recherche Jira, champ absent), le signaler avec
l'endpoint et le code HTTP plutôt que de contourner : le code se corrige ensuite, en suivant
`verifier-qualite`.
