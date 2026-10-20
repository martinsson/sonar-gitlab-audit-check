---
name: audit-sonar-gitlab
description: Lancer l'audit habituel de ce dépôt — inventaire SonarQube (SonarAuditCheck), classement (SonarRank), audit d'activité et de pratiques GitLab (GitlabActivityAudit), croisement Sonar × GitLab (CrossAudit), vues (Vues) et graphiques (viz/). À utiliser dès qu'on parle d'inventaire Sonar, de classement, de dette, d'audit GitLab, de croisement ou d'appariement Sonar/GitLab, de croisement.csv ou des graphiques.
metadata:
  opencode/slash: "true"
---

# Audit Sonar × GitLab

Outils en lecture seule (uniquement des GET), lancés avec JBang depuis la racine du dépôt.
Les deux premières étapes appellent les instances ; les suivantes ne lisent que les CSV et se
relancent gratuitement. Méthode : `README.md`, `ANALYSIS.md`, `GITLAB_ANALYSIS.md`. Colonnes :
`COLUMNS.md`. Chaque outil a un `--help` qui finit par des recettes.

## Règles à respecter

- Ces outils ne tournent que sur le réseau du client. Les CSV, captures et rapports sont des
  données client : ne jamais les commiter (ils sont dans `.gitignore`), ne jamais les publier, ne
  pas recopier de noms de projets ou de personnes hors de l'entreprise.
- Ne pas modifier le code pour faire passer une exécution : signaler le problème. Une
  modification de code suit le skill `verifier-qualite`.
- Un jeton ne se colle pas dans une commande : il vient de l'environnement.

## Prérequis

```bash
export SONAR_URL=https://sonar…   SONAR_TOKEN=squ_…      # User token, pas un jeton d'analyse
export GITLAB_URL=https://gitlab… GITLAB_TOKEN=glpat-…   # portée read_api
```

Vérifier qu'ils sont présents (`[ -n "$SONAR_TOKEN" ]`) sans jamais les afficher. JBang télécharge
le JDK et les dépendances au premier lancement.

## La chaîne habituelle

```bash
# 0. Accès seul, sans rien écrire : que voit ce jeton, combien de projets lui échappent
jbang SonarAuditCheck.java

# 1. Inventaire Sonar → inventaire.csv (+ inventaire-historique.csv)
jbang SonarAuditCheck.java --csv inventaire.csv

# 2. GitLab : sélection par activité puis pratiques → ./audit/inventaire.csv, ./audit/pratiques.csv
jbang GitlabActivityAudit.java --deep --out-dir ./audit                 # toute l'instance
jbang GitlabActivityAudit.java --group mon/groupe --deep --out-dir ./audit   # un groupe

# 3. Croisement → ./audit/croisement.csv, ./audit/sonar-gitlab.csv, ./audit/appariement.csv
jbang CrossAudit.java --sonar inventaire.csv --gitlab ./audit/pratiques.csv --out ./audit/croisement.csv

# 4. Vues → croisement-essentiel.csv, croisement-derive.csv, croisement-non-apparies.csv
jbang Vues.java --in ./audit/croisement.csv

# Classement Sonar seul, sans appel API → classement.csv (filtrer liste, puis retenu = O)
jbang SonarRank.java --in inventaire.csv --out classement.csv
```

`--deep` est ce qui rend l'étape 3 possible : il lit la clé Sonar dans la CI et le `pom.xml`.

Options utiles :

- **Fenêtre et budget GitLab.** `--since 365` élargit la fenêtre (90 jours par défaut). `--top 150`
  réduit le budget de la passe profonde (200 par défaut). `--projects liste.txt` limite l'audit à
  une liste de projets.
- **Exclusions.** `--exclusions fichier`, ou `exclusions.txt` dans le dossier courant (lu par les
  étapes 1 et 2).
- **Capture et rejeu.** `--dump-dir ./captures` garde les réponses brutes de Sonar ;
  `--replay-dir ./captures` rejoue sans URL ni jeton.
- **Format machine.** `--comma` écrit pour un outil plutôt qu'Excel.
- **Instance auto-signée.** `--insecure`.

## Corriger l'appariement

`./audit/appariement.csv` est relu à chaque `CrossAudit`. Dans la colonne `confiance` :

- `manuel` confirme une paire ; mettre la bonne clé dans `droite_key` pour la corriger ;
- `rejete` refuse une paire, qui ne sera plus jamais proposée.

`--reapparier` recalcule les paires `exact` et `derived`, et garde les lignes faites à la main.
Pour apparier deux CSV quelconques, voir `ProjectMapper` dans `TOOLS.md`.

## Graphiques

Ouvrir `viz/index.html` (parc) ou `viz/projet.html` (un projet) et y déposer les CSV : ils sont
lus dans le navigateur, jamais envoyés. L'export « page autonome » contient des noms et des
chiffres : vérifier qui le reçoit.

## Rendre compte

Donner les chiffres que la console affiche : projets visibles et réels côté Sonar, entonnoir côté
GitLab, taux d'appariement et constats du croisement. Indiquer aussi où sont les fichiers écrits,
et tout appel refusé (401, 403), avec l'outil concerné.
