╔═ an_abandoned_project_trips_the_process_findings ═╗

=== 1. Le projet ======================================================
  Chemin                          : team-a/alpha-service
  Id / branche par défaut         : 42 / main
  Visibilité                      : private   ARCHIVÉ
  Créé le                         : <created>   (il y a 400 j)
  Dernière activité               : <active>   (il y a 3 j)
  Commits (toute l'histoire)      : 1234
  Taille du dépôt                 : 5.3 Mo

=== 2. Cadence (branche par défaut, 365 derniers jours) ===============
  Commits (hors bots)             : 10
  Commits de bots écartés         : 11
  Rythme                          : 0.2 commits / semaine
  Jours avec au moins un commit   : 10 sur 365
  Écart médian entre commits      : 24.0 h
  Plus long silence               : 43.0 j

  Répartition hebdomadaire        : L:▅ M:▁ M:▁ J:▁ V:▁ S:█ D:█
  Week-end                        : 80 %
  Hors 8h-19h (heure de l'auteur) : 40 %

  Taille médiane d'un commit      : 10 lignes
  9e décile                       : 259 lignes
  Commits > 2000 lignes           : 1 (10 %) — imports, code généré, vendoring ?
  Lignes touchées / lignes nettes : 2574 / 2570 = 1.0
    (le net masque la réécriture : un fichier refait cinq fois ne bouge pas d'une ligne)

=== 3. Contributeurs et concentration =================================
  Auteurs distincts               : 3
  Auteur principal                : Alice (80 % des commits)
  Personnes couvrant 80 % du code : 1

    Alice                                 8  ################
    Carol                                 1  ##
    Bob                                   1  ##

  Actifs sur 90 j                 : 0 sur 3
  Écartés comme bots              : dependabot

=== 4. Merge requests et revue ========================================
  Aucune MR créée sur la fenêtre.
    Le travail arrive donc directement sur la branche par défaut.
    Ce n'est pas une absence de données : c'est l'absence de revue.

=== 5. Constats =======================================================
  → Projet archivé : lecture historique seulement.
  → Dernier commit il y a 200 j : dépôt dormant.
  → Le dépôt bouge encore (MR, tickets) mais plus la branche par défaut : le travail vit ailleurs, ou personne ne fusionne plus.
  → 10 commits, aucune MR : le code arrive sans revue sur la branche par défaut.
  → Une seule personne signe 80 % des commits (80 % pour la première).
  → 3 auteurs sur la fenêtre, 0 actif sur 90 j : l'équipe s'est vidée.
  → 80 % des commits le week-end.
  → Plus de commits de bots que d'humains : dépôt surtout maintenu par l'outillage.

  Ces constats décrivent un procédé, pas une qualité de code. Le croisement
  avec Sonar — fréquence de modification contre complexité, quality gate
  contre MR fusionnées — reste à faire.

  4 appels API.

╔═ rubber_stamp_reviews_trip_the_review_findings ═╗

=== 1. Le projet ======================================================
  Chemin                          : team-a/alpha-service
  Id / branche par défaut         : 42 / main
  Visibilité                      : private
  Créé le                         : <created>   (il y a 400 j)
  Dernière activité               : <active>   (il y a 3 j)
  Commits (toute l'histoire)      : 1234
  Taille du dépôt                 : 5.3 Mo

=== 2. Cadence (branche par défaut, 180 derniers jours) ===============
  Commits (hors bots)             : 2
  Rythme                          : 0.1 commits / semaine
  Jours avec au moins un commit   : 2 sur 180
  Écart médian entre commits      : 24.0 h
  Plus long silence               : 24.0 h

  Répartition hebdomadaire        : L:█ M:▁ M:▁ J:▁ V:▁ S:▁ D:▁
  Week-end                        : 0 %
  Hors 8h-19h (heure de l'auteur) : 0 %

  Taille médiane d'un commit      : 2 lignes
  9e décile                       : 2 lignes
  Commits > 2000 lignes           : 0 (0 %) — imports, code généré, vendoring ?
  Lignes touchées / lignes nettes : 4 touchées, solde nul
    (le net masque la réécriture : un fichier refait cinq fois ne bouge pas d'une ligne)

=== 3. Contributeurs et concentration =================================
  Auteurs distincts               : 2
  Auteur principal                : Bob (50 % des commits)
  Personnes couvrant 80 % du code : 2

    Bob                                   1  ##########
    Alice                                 1  ##########

  Actifs sur 90 j                 : 2 sur 2
  Contributeurs de tout temps     : 2 (commits de fusion inclus)

=== 4. Merge requests et revue ========================================
  MR créées                       : 6 (0.2 / semaine)
  Fusionnées / fermées / ouvertes : 6 / 0 / 0
  Délai création → fusion         : médiane 24.0 h, 9e décile 35.5 j
  MR sans aucun commentaire       : 6 (100 %)
  Fusionnées par leur auteur      : 6 sur 6 (100 %)
  Squash à la fusion              : 100 %   (écrase l'historique par commit)

  Échantillon de revue            : 6 MR fusionnées
  Fusionnées sans relecture       : 6 (100 %) — aucun commentaire d'un tiers

=== 5. Constats =======================================================
  → 100 % des MR sont fusionnées par leur auteur : la revue est facultative.
  → 100 % des MR n'ont aucun commentaire : la revue est un tampon.
  → 9e décile du délai de fusion à 35.5 j : une MR sur dix traîne un mois.
  → Squash quasi systématique : l'historique par commit n'existe plus, la cadence ci-dessus mesure le bouton « fusionner ».

  Ces constats décrivent un procédé, pas une qualité de code. Le croisement
  avec Sonar — fréquence de modification contre complexité, quality gate
  contre MR fusionnées — reste à faire.

  10 appels API.

╔═ [end of file] ═╗
