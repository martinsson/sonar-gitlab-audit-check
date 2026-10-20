╔═ a_bare_project_seen_anonymously ═╗
  Aucun jeton : lecture anonyme, projets publics seulement.

=== 1. Le projet ======================================================
  Chemin                          : team-a/alpha-service
  Id / branche par défaut         : 42 / n/a
  Visibilité                      : n/a
  Créé le                         : n/a
  Dernière activité               : n/a
  Statistiques du dépôt           : indisponibles (rôle < Reporter ?)
  → dépôt sans branche par défaut : vide, ou jamais poussé.

=== 2. Cadence (branche par défaut, 180 derniers jours) ===============
  ERREUR   repository/commits                         pas de branche par défaut

=== 3. Contributeurs et concentration =================================
  Pas de commit sur la fenêtre : rien à mesurer.

=== 4. Merge requests et revue ========================================
  Aucune MR créée sur la fenêtre.
    Le travail arrive donc directement sur la branche par défaut.
  Ce n'est pas une absence de données : c'est l'absence de revue.

=== 5. Constats =======================================================
  Rien de saillant sur les seuils retenus.

  2 appels API.

╔═ an_unknown_project_is_a_404_with_an_explanation ═╗
Projet introuvable : team-a/alpha-service
  HTTP 404 — 404 Project Not Found
404 ne distingue pas « n'existe pas » de « pas de droit de lecture » :
GitLab masque les projets privés. Vérifier le chemin, puis la portée
du jeton (read_api) et l'accès au projet.

╔═ bots_only ═╗

=== 1. Le projet ======================================================
  Chemin                          : team-a/alpha-service
  Id / branche par défaut         : 42 / main
  Visibilité                      : private
  Créé le                         : <created>   (il y a 400 j)
  Dernière activité               : <active>   (il y a 3 j)
  Commits (toute l'histoire)      : 1234
  Taille du dépôt                 : 5.3 Mo

=== 2. Cadence (branche par défaut, 180 derniers jours) ===============
  Aucun commit humain sur la fenêtre.
  (2 commits de bots sur la même période)

=== 3. Contributeurs et concentration =================================
  Pas de commit sur la fenêtre : rien à mesurer.

=== 4. Merge requests et revue ========================================
  Aucune MR créée sur la fenêtre.
    Le travail arrive donc directement sur la branche par défaut.
  Ce n'est pas une absence de données : c'est l'absence de revue.

=== 5. Constats =======================================================
  → Plus de commits de bots que d'humains : dépôt surtout maintenu par l'outillage.

  Ces constats décrivent un procédé, pas une qualité de code. Le croisement
  avec Sonar — fréquence de modification contre complexité, quality gate
  contre MR fusionnées — reste à faire.

  3 appels API.

╔═ caps_are_reported_and_the_window_shrinks_to_what_was_read ═╗

=== 1. Le projet ======================================================
  Chemin                          : team-a/alpha-service
  Id / branche par défaut         : 42 / main
  Visibilité                      : private
  Créé le                         : <created>   (il y a 400 j)
  Dernière activité               : <active>   (il y a 3 j)
  Commits (toute l'histoire)      : 1234
  Taille du dépôt                 : 5.3 Mo

=== 2. Cadence (branche par défaut, 180 derniers jours) ===============
  Plafond --max-commits atteint : les chiffres portent sur les 3 plus récents.
  Commits (hors bots)             : 3
  Rythme                          : 1.5 commits / semaine   (sur les 14 j couverts)
  Jours avec au moins un commit   : 3 sur 14
  Écart médian entre commits      : 6.5 j
  Plus long silence               : 7.0 j

  Répartition hebdomadaire        : L:█ M:▁ M:▁ J:▁ V:▁ S:▁ D:▁
  Week-end                        : 0 %
  Hors 8h-19h (heure de l'auteur) : 0 %

  Taille médiane d'un commit      : 4 lignes
  9e décile                       : 4 lignes
  Commits > 2000 lignes           : 0 (0 %) — imports, code généré, vendoring ?
  Lignes touchées / lignes nettes : 12 / 6 = 2.0
    (le net masque la réécriture : un fichier refait cinq fois ne bouge pas d'une ligne)

=== 3. Contributeurs et concentration =================================
  Auteurs distincts               : 2
  Auteur principal                : Alice (67 % des commits)
  Personnes couvrant 80 % du code : 2

    Alice                                 2  #############
    Bob                                   1  #######

  Actifs sur 90 j                 : 2 sur 2

=== 4. Merge requests et revue ========================================
  Plafond --max-mrs atteint : chiffres portant sur les 2 plus récentes.
  MR créées                       : 2 (0.7 / semaine)   (sur les 20 j couverts)
  Fusionnées / fermées / ouvertes : 0 / 1 / 1
  MR sans aucun commentaire       : 2 (100 %)
  Auto-fusion                     : aucune MR fusionnée sur la fenêtre
  Squash à la fusion              : 0 %

=== 5. Constats =======================================================
  → 100 % des MR n'ont aucun commentaire : la revue est un tampon.

  Ces constats décrivent un procédé, pas une qualité de code. Le croisement
  avec Sonar — fréquence de modification contre complexité, quality gate
  contre MR fusionnées — reste à faire.

  4 appels API.

╔═ commits_without_stats_or_dates ═╗

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
  Jours avec au moins un commit   : 1 sur 180
  Volumes de lignes indisponibles (stats absentes de la réponse).

=== 3. Contributeurs et concentration =================================
  Auteurs distincts               : 1
  Auteur principal                : Alice (100 % des commits)
  Personnes couvrant 80 % du code : 1

    Alice                                 2  ####################

  Actifs sur 90 j                 : 1 sur 1

=== 4. Merge requests et revue ========================================
  Aucune MR créée sur la fenêtre.
    Le travail arrive donc directement sur la branche par défaut.
  Ce n'est pas une absence de données : c'est l'absence de revue.

=== 5. Constats =======================================================
  → 2 commits, aucune MR : le code arrive sans revue sur la branche par défaut.
  → Une seule personne signe 80 % des commits (100 % pour la première).

  Ces constats décrivent un procédé, pas une qualité de code. Le croisement
  avec Sonar — fréquence de modification contre complexité, quality gate
  contre MR fusionnées — reste à faire.

  4 appels API.

╔═ merge_commits_only ═╗

=== 1. Le projet ======================================================
  Chemin                          : team-a/alpha-service
  Id / branche par défaut         : 42 / main
  Visibilité                      : private
  Créé le                         : <created>   (il y a 400 j)
  Dernière activité               : <active>   (il y a 3 j)
  Commits (toute l'histoire)      : 1234
  Taille du dépôt                 : 5.3 Mo

=== 2. Cadence (branche par défaut, 180 derniers jours) ===============
  Que des commits de fusion : rien à mesurer sur cette branche.

=== 3. Contributeurs et concentration =================================
  Pas de commit sur la fenêtre : rien à mesurer.

=== 4. Merge requests et revue ========================================
  Aucune MR créée sur la fenêtre.
    Le travail arrive donc directement sur la branche par défaut.
  Ce n'est pas une absence de données : c'est l'absence de revue.

=== 5. Constats =======================================================
  Rien de saillant sur les seuils retenus.

  3 appels API.

╔═ merged_requests_without_merger_cannot_say_who_merged ═╗

=== 1. Le projet ======================================================
  Chemin                          : team-a/alpha-service
  Id / branche par défaut         : 42 / main
  Visibilité                      : private
  Créé le                         : <created>   (il y a 400 j)
  Dernière activité               : <active>   (il y a 3 j)
  Commits (toute l'histoire)      : 1234
  Taille du dépôt                 : 5.3 Mo

=== 2. Cadence (branche par défaut, 180 derniers jours) ===============
  Commits (hors bots)             : 1
  Rythme                          : 0.0 commits / semaine
  Jours avec au moins un commit   : 1 sur 180

  Répartition hebdomadaire        : L:█ M:▁ M:▁ J:▁ V:▁ S:▁ D:▁
  Week-end                        : 0 %
  Hors 8h-19h (heure de l'auteur) : 0 %

  Taille médiane d'un commit      : 4 lignes
  9e décile                       : 4 lignes
  Commits > 2000 lignes           : 0 (0 %) — imports, code généré, vendoring ?
  Lignes touchées / lignes nettes : 4 / 2 = 2.0
    (le net masque la réécriture : un fichier refait cinq fois ne bouge pas d'une ligne)

=== 3. Contributeurs et concentration =================================
  Auteurs distincts               : 1
  Auteur principal                : Alice (100 % des commits)
  Personnes couvrant 80 % du code : 1

    Alice                                 1  ####################

  Actifs sur 90 j                 : 1 sur 1

=== 4. Merge requests et revue ========================================
  MR créées                       : 2 (0.1 / semaine)
  Fusionnées / fermées / ouvertes : 2 / 0 / 0
  MR sans aucun commentaire       : 2 (100 %)
  Fusionnées par leur auteur      : 0 sur 1 (0 %)
  Squash à la fusion              : 0 %

  Échantillon de revue            : 2 MR fusionnées
  Fusionnées sans relecture       : 2 (100 %) — aucun commentaire d'un tiers

=== 5. Constats =======================================================
  → 100 % des MR n'ont aucun commentaire : la revue est un tampon.
  → Une seule personne signe 80 % des commits (100 % pour la première).

  Ces constats décrivent un procédé, pas une qualité de code. Le croisement
  avec Sonar — fréquence de modification contre complexité, quality gate
  contre MR fusionnées — reste à faire.

  6 appels API.

╔═ one_person_with_several_addresses_and_names ═╗

=== 1. Le projet ======================================================
  Chemin                          : team-a/alpha-service
  Id / branche par défaut         : 42 / main
  Visibilité                      : private
  Créé le                         : <created>   (il y a 400 j)
  Dernière activité               : <active>   (il y a 3 j)
  Commits (toute l'histoire)      : 1234
  Taille du dépôt                 : 5.3 Mo

=== 2. Cadence (branche par défaut, 180 derniers jours) ===============
  Commits (hors bots)             : 5
  Rythme                          : 0.2 commits / semaine
  Jours avec au moins un commit   : 5 sur 180
  Écart médian entre commits      : 24.0 h
  Plus long silence               : 24.0 h

  Répartition hebdomadaire        : L:█ M:▁ M:▁ J:▁ V:▁ S:▁ D:▁
  Week-end                        : 0 %
  Hors 8h-19h (heure de l'auteur) : 0 %

  Taille médiane d'un commit      : 4 lignes
  9e décile                       : 4 lignes
  Commits > 2000 lignes           : 0 (0 %) — imports, code généré, vendoring ?
  Lignes touchées / lignes nettes : 20 / 10 = 2.0
    (le net masque la réécriture : un fichier refait cinq fois ne bouge pas d'une ligne)

=== 3. Contributeurs et concentration =================================
  Auteurs distincts               : 2
  Adresses distinctes             : 3 — ramenées à 2 personnes
  Noms distincts                  : 3 — une identité porte plusieurs noms
  Auteur principal                : Dev 0 (80 % des commits)
  Personnes couvrant 80 % du code : 1

    Dev 0                                 4  ################
    ?                                     1  ####

  Actifs sur 90 j                 : 2 sur 2

=== 4. Merge requests et revue ========================================
  Aucune MR créée sur la fenêtre.
    Le travail arrive donc directement sur la branche par défaut.
  Ce n'est pas une absence de données : c'est l'absence de revue.

=== 5. Constats =======================================================
  → 5 commits, aucune MR : le code arrive sans revue sur la branche par défaut.
  → Une seule personne signe 80 % des commits (80 % pour la première).

  Ces constats décrivent un procédé, pas une qualité de code. Le croisement
  avec Sonar — fréquence de modification contre complexité, quality gate
  contre MR fusionnées — reste à faire.

  4 appels API.

╔═ refused_commits_and_merge_requests_are_said ═╗

=== 1. Le projet ======================================================
  Chemin                          : team-a/alpha-service
  Id / branche par défaut         : 42 / main
  Visibilité                      : private
  Créé le                         : <created>   (il y a 400 j)
  Dernière activité               : <active>   (il y a 3 j)
  Commits (toute l'histoire)      : 1234
  Taille du dépôt                 : 5.3 Mo

=== 2. Cadence (branche par défaut, 180 derniers jours) ===============
  ERREUR   repository/commits                         HTTP 403 — 403 Forbidden

=== 3. Contributeurs et concentration =================================
  Pas de commit sur la fenêtre : rien à mesurer.

=== 4. Merge requests et revue ========================================
  ERREUR   merge_requests                             HTTP 500 — oops

=== 5. Constats =======================================================
  Rien de saillant sur les seuils retenus.

  3 appels API.

╔═ refused_notes_stop_the_sample_at_the_first_refusal ═╗

=== 1. Le projet ======================================================
  Chemin                          : team-a/alpha-service
  Id / branche par défaut         : 42 / main
  Visibilité                      : private
  Créé le                         : <created>   (il y a 400 j)
  Dernière activité               : <active>   (il y a 3 j)
  Commits (toute l'histoire)      : 1234
  Taille du dépôt                 : 5.3 Mo

=== 2. Cadence (branche par défaut, 180 derniers jours) ===============
  Commits (hors bots)             : 1
  Rythme                          : 0.0 commits / semaine
  Jours avec au moins un commit   : 1 sur 180

  Répartition hebdomadaire        : L:█ M:▁ M:▁ J:▁ V:▁ S:▁ D:▁
  Week-end                        : 0 %
  Hors 8h-19h (heure de l'auteur) : 0 %

  Taille médiane d'un commit      : 4 lignes
  9e décile                       : 4 lignes
  Commits > 2000 lignes           : 0 (0 %) — imports, code généré, vendoring ?
  Lignes touchées / lignes nettes : 4 / 2 = 2.0
    (le net masque la réécriture : un fichier refait cinq fois ne bouge pas d'une ligne)

=== 3. Contributeurs et concentration =================================
  Auteurs distincts               : 1
  Auteur principal                : Alice (100 % des commits)
  Personnes couvrant 80 % du code : 1

    Alice                                 1  ####################

  Actifs sur 90 j                 : 1 sur 1

=== 4. Merge requests et revue ========================================
  MR créées                       : 2 (0.1 / semaine)
  Fusionnées / fermées / ouvertes : 2 / 0 / 0
  Délai création → fusion         : médiane 24.0 h, 9e décile 24.0 h
  MR sans aucun commentaire       : 2 (100 %)
  Auto-fusion                     : non mesurable (merge_user absent)
  Squash à la fusion              : 0 %

  REFUSE   merge_requests/:iid/notes                  HTTP 401 — 401 Unauthorized
    Délai de revue et part de MR non relues : non mesurés.
    Les notes exigent un jeton, même sur un projet public.

=== 5. Constats =======================================================
  → 100 % des MR n'ont aucun commentaire : la revue est un tampon.
  → Une seule personne signe 80 % des commits (100 % pour la première).

  Ces constats décrivent un procédé, pas une qualité de code. Le croisement
  avec Sonar — fréquence de modification contre complexité, quality gate
  contre MR fusionnées — reste à faire.

  5 appels API.

╔═ [end of file] ═╗
