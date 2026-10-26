# Checklist d'Inspection de Story (Section 6.1)

**Source :** Annexe aux Plans Qualité Logiciel - Section 6.1, page 46

## Modèle de check-list d'inspection de story

| Points de contrôle | Résultat | Commentaires |
|-------------------|----------|--------------|
| Respect du titrage | ☐ | |
| Respect du type de story | ☐ | |
| Respect de la description | ☐ | |
| Numérotation des AC | ☐ | |
| Présence de l'AC de contexte | ☐ | |
| Qualité de la forme des AC | ☐ | |
| Respect du modèle INVEST | ☐ | |
| Respect du cycle de vie | ☐ | |
| Respect de l'attribution | ☐ | |
| Découpage en sous-tâches | ☐ | |
| Estimation des sous-tâches en heures | ☐ | |
| Reste à faire des tâches en heures | ☐ | |
| Lien vers une Feature ou épic | ☐ | |
| Lien vers le composant concerné | ☐ | |
| Lien vers la Release concernée | ☐ | |

---

**Évaluation finale de la qualité de la story sur ces points de contrôle :**

---

## Détails des points de contrôle

### 1. Respect du titrage
**Signification :** Le titre de la story doit suivre le format et les conventions de nommage définis par le projet.

**Comment vérifier :**
- Le titre est-il clair, concis et descriptif ?
- Respecte-t-il le format imposé (ex: `[Composant] Description courte`) ?
- Utilise-t-on une terminologie cohérente avec le projet ?

**Points potentiellement flous :**
- Quelles sont les conventions exactes de titrage pour ce projet ?
- Y a-t-il un préfixe ou un format spécifique requis ?

---

### 2. Respect du type de story
**Signification :** La story doit être classée dans le bon type (ex: Fonctionnelle, Technique, Bug, Tâche, Epic, Feature).

**Comment vérifier :**
- Le type de story dans Jira correspond-il à la nature du travail ?
- Une story fonctionnelle n'est pas confondue avec une tâche technique ?

**Points potentiellement flous :**
- Quelles sont les règles de classification des types de stories pour ce projet ?

---

### 3. Respect de la description
**Signification :** La description de la story doit être complète, structurée et contenir toutes les informations nécessaires.

**Comment vérifier :**
- La description suit-elle un modèle ou un template défini ?
- Le contexte, les prérequis et les objectifs sont-ils clairement exprimés ?
- La description permet-elle à un développeur de comprendre le besoin sans ambiguity ?

**Points potentiellement flous :**
- Existe-t-il un template de description standard ?
- Quels éléments doivent obligatoirement figurer dans la description ?

---

### 4. Numérotation des AC (Acceptance Criteria)
**Signification :** Les critères d'acceptation doivent être numérotés de manière séquentielle et cohérente.

**Comment vérifier :**
- Les AC sont-ils numérotés (AC1, AC2, AC3...) ?
- La numérotation est-elle continue sans saut ?
- Chaque AC est-il unique et non redondant ?

**Points potentiellement flous :**
- Y a-t-il un format spécifique pour les AC (ex: "AC 1.1", "AC 2.3" pour les sous-cas) ?

---

### 5. Présence de l'AC de contexte
**Signification :** Au moins un critère d'acceptation doit décrire le contexte ou le scénario global.

**Comment vérifier :**
- Y a-t-il un AC qui décrit le contexte global ou le "user journey" ?
- Cet AC permet-il de comprendre l'objectif métier derrière la story ?

**Points potentiellement flous :**
- Comment identifier un "AC de contexte" parmi les autres AC ?
- Est-ce un AC obligatoire ou recommandé ?

---

### 6. Qualité de la forme des AC
**Signification :** Les critères d'acceptation doivent être rédigés clairement, sans ambiguïté.

**Comment vérifier :**
- Les AC utilisent-ils un language précis et objectif ?
- Sont-ils testables (on peut dire "passé" ou "raté") ?
- Évite-t-on les termes vagues ("bien", "rapidement", "facilement") ?
- Format recommandé : "En tant que [rôle], je veux [action] afin de [bénéfice]"

**Points potentiellement flous :**
- Existe-t-il des exemples types de AC bien rédigés ?
- Quelle est la longueur idéale d'un AC ?

---

### 7. Respect du modèle INVEST
**Signification :** La story doit respecter les critères du modèle INVEST pour les user stories agiles.

**Comment vérifier :**
- **I**ndépendante : La story peut-elle être développée et livrée sans dépendre d'autres stories ?
- **N**égociable : Les spécifications peuvent-elles être discutées et ajustées ?
- **V**aluable : La story apporte-t-elle de la valeur métier ?
- **E**stimable : L'équipe peut-elle estimer la complexité ?
- **S**mall : La story est-elle assez petite pour être livrée dans un seul sprint ?
- **T**estable : Savons-nous comment tester la story ?

**Comment vérifier :**
- Examiner chaque critère INVEST individuellement.
- Poser les questions INVEST lors de l'inspection.

**Points potentiellement flous :**
- Quelle est la tolérance pour les dépendances partielles ?
- Comment mesurer si une story est "assez petite" ?

---

### 8. Respect du cycle de vie
**Signification :** La story doit suivre le workflow Jira défini par le projet (ex: Backlog → Affinée → Ready → En cours → En recette → Done).

**Comment vérifier :**
- La story est-elle dans le bon statut pour son avancement ?
- Les transitions de statut sont-elles correctement appliquées ?
- Les critères de passage d'un statut à l'autre sont-ils respectés ?

**Points potentiellement flous :**
- Quels sont les statuts exacts du cycle de vie pour le projet VA ?
- Y a-t-il des gates ou validations obligatoires entre certains statuts ?

---

### 9. Respect de l'attribution
**Signification :** La story doit être attribuée à la personne responsable de son développement ou de son avancement.

**Comment vérifier :**
- La story a-t-elle un assigné dans Jira ?
- L'assigné est-il la personne appropriée (développeur, équipe) ?
- L'attribution a-t-elle été faite au bon moment ?

**Points potentiellement flous :**
- Faut-il attribuer la story dès la création ou uniquement lors du démarrage du développement ?
- Que faire si la story est partagée entre plusieurs développeurs ?

---

### 10. Découpage en sous-tâches
**Signification :** La story doit être décomposée en sous-tâches techniques pour le suivi de l'avancement.

**Comment vérifier :**
- La story a-t-elle des sous-tâches associées dans Jira ?
- Le découpage est-il logique et couvrant tous les aspects techniques ?
- Chaque sous-tâche est-elle suffisamment granulaire (idéalement < 1 jour) ?

**Points potentiellement flous :**
- Y a-t-il un nombre minimum ou maximum de sous-tâches ?
- Quelles catégories de sous-tâches sont attendues (dev, test, doc, déploiement) ?

---

### 11. Estimation des sous-tâches en heures
**Signification :** Chaque sous-tâche doit être estimée en heures de travail.

**Comment vérifier :**
- Toutes les sous-tâches ont-elles une estimation en heures ?
- Les estimations sont-elles réalistes et justifiées ?
- L'estimation totale est-elle cohérente avec les story points de la story ?

**Points potentiellement flous :**
- Quelle est l'estimation maximale recommandée pour une sous-tâche ?
- Doit-on estimer en heures réelles ou en heures de développeur ?

---

### 12. Reste à faire des tâches en heures
**Signification :** Le temps restant sur les sous-tâches doit être suivi et mis à jour dans Jira.

**Comment vérifier :**
- Le "reste à faire" est-il mis à jour régulièrement ?
- La somme des "restes à faire" des sous-tâches est-elle cohérente avec l'avancement global ?
- Le temps consommé est-il enregistré correctement ?

**Points potentiellement flous :**
- À quelle fréquence le "reste à faire" doit-il être mis à jour ?
- Qui est responsable de cette mise à jour (développeur, Scrum Master) ?

---

### 13. Lien vers une Feature ou épic
**Signification :** La story doit être liée à une Feature ou un Epic parent dans Jira.

**Comment vérifier :**
- Un lien Jira existe-t-il vers la Feature ou l'Epic parent ?
- Le lien est-il du bon type (ex: "blocks", "relates to", "parent") ?
- La story contribue-t-elle clairement aux objectifs de la Feature/Epic ?

**Points potentiellement flous :**
- Faut-il systématiquement un lien vers un Epic ou une Feature suffit ?
- Quel type de lien Jira utiliser ?

---

### 14. Lien vers le composant concerné
**Signification :** La story doit être associée au(x) composant(s) technique(s) concerné(s) dans Jira.

**Comment vérifier :**
- Le(s) composant(s) Jira sont-ils renseignés ?
- Le composant correspond-il au module/code concerné ?
- Si plusieurs composants, sont-ils tous listés ?

**Points potentiellement flous :**
- Comment sont définis les composants dans le projet VA ?
- Faut-il un composant par fichier/module ou par grande fonctionnalité ?

---

### 15. Lien vers la Release concernée
**Signification :** La story doit être associée à une Release (version) dans Jira.

**Comment vérifier :**
- La release cible est-elle renseignée dans Jira ?
- La release correspond-elle au planning du projet ?
- Si la story n'a pas de release fixe, le champ est-il explicitement laissé vide ou marqué comme "Sans release" ?

**Points potentiellement flous :**
- Faut-il une release fixe ou "À définir" est-il acceptable ?
- Que faire si la story peut être déployée sur plusieurs releases ?

---

## Évaluation finale

**Note globale de la qualité de la story :** [À compléter lors de l'inspection]

**Points forts :**
- 

**Points d'amélioration :**
- 

**Actions correctives requises :**
- 

---

*Document généré à partir de la Section 6.1 de l'Annexe aux Plans Qualité Logiciel*
