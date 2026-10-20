---
name: verifier-qualite
description: Vérifier et faire passer les gates qualité de ce dépôt avec ./verify (format, Error Prone, NullAway, Checkstyle, PMD, tests, couverture, ratchet, règle des scripts JBang) avant tout commit ou push. À utiliser dès qu'on modifie du code Java, un script JBang, le pom, la config des gates, ou qu'on s'apprête à commiter ou pousser.
metadata:
  opencode/slash: "true"
---

# Vérifier la qualité avant de commiter

`./verify` est l'unique point d'entrée. La sortie est courte, et le log complet est dans
`target/verify.log`. Le code de sortie 0 veut dire que tout est vert.

```bash
./verify                 # gates rapides (~30 s) : à lancer avant chaque commit
./verify --fix           # formate (Spotless) puis lance les gates
./verify --snapshots     # ré-enregistre les snapshots Selfie après un changement voulu, puis relire le diff
./verify --full          # en plus : tests Docker, mutation (PIT), copier-coller (CPD)
```

## Hooks

- **Installation.** Le premier `./verify` hors CI installe les hooks (`core.hooksPath=.githooks`).
- **pre-commit.** Formate les fichiers Java indexés, sans jamais bloquer.
- **pre-push.** Lance `./verify` et bloque si c'est rouge.
- **Jamais `--no-verify`.** La CI rattrape un push qui l'utilise, mais seulement après coup.
- **Vérifier qu'ils sont en place.** `git config core.hooksPath` doit répondre `.githooks`.

## JDK

Le build vise Java 26. Si Maven tourne sur un JDK plus ancien, `./verify` emprunte celui de JBang
(`jbang jdk home 26`). Une erreur `release version 26 not supported` hors de `./verify` se règle
avec `export JAVA_HOME=$(jbang jdk home 26)`.

## Règles

- **Faire passer un gate en corrigeant le code**, jamais en l'assouplissant :
  - pas d'augmentation d'une limite ni d'une constante `MAX_…`, pas de baisse d'un minimum ;
  - pas de nouvelle règle désactivée, ni de `@SuppressWarnings`, `NOPMD`, `@Disabled` ;
  - pas de nouvelle entrée dans `config/baseline/`.

  Le ratchet refuse tout cela.
- **`Ratchet-Override: <raison>`** dans un message de commit lève le ratchet. C'est une décision
  humaine : ne jamais l'ajouter sans l'accord explicite de l'utilisateur.
- **Un gate de structure** (Checkstyle, PMD, architecture) signale un problème de conception. Si
  la seule correction possible est mécanique (découper pour tomber sous un seuil), s'arrêter et
  demander.
- **Les scripts JBang** (`*.java` hors `src/` et `tools/`) ne sont que des points d'entrée.
  `JbangScriptsTest` impose :
  - au plus 20 lignes ;
  - un `//SOURCES` qui pointe seulement sous `src/main/java` ;
  - une seule classe dont le `main` appelle un `main` existant de `src/main/java`.

  Le code va dans `src/main/java/ch/ge/edg/sonar/gitlab/audit/…`, avec ses tests ; s'inspirer de
  `CrossAudit.java` à la racine. Les scripts plus anciens qui dérogent sont listés dans
  `config/baseline/jbang-scripts.txt`. Cette liste ne peut que rétrécir : après avoir migré un
  script, retirer sa ligne (le test échoue sinon).
- **Ce que le dépôt ne contient jamais :**
  - de données client : CSV d'audit, captures, `extraction/` ;
  - de noms de projets ou de personnes du client, dans le code comme dans les messages de commit.
- **Pousser.** Personne ne pousse sur `main`. On pousse une branche, puis on ouvre une PR (GitHub,
  `gh pr create --fill`) ou une merge request (GitLab), et seulement quand l'utilisateur le demande.
  Elle se merge seule, sans relecture humaine, quand `verify` est vert ; `verify-full` tourne
  ensuite sur `main`. Si un
  check est rouge, corriger sur la branche et pousser de nouveau (voir « CI » dans `TOOLS.md`).

## En cas d'échec

Lire les lignes affichées et les `HINT:`, qui disent quoi faire :

| Échec | Que faire |
|---|---|
| Format | `./verify --fix` |
| Couverture | `target/site/jacoco/index.html` |
| PMD | `target/pmd.xml` |
| Mutants survivants | `target/pit-reports/index.html` |
| Snapshot différent | `./verify --snapshots` si le changement est voulu |

Relancer jusqu'à `VERIFY OK`, puis seulement commiter. Ce qui reste rouge doit être dit tel quel,
avec la sortie.
