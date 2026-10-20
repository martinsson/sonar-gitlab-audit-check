╔═ aRefusedTokenStopsWithExitOne ═╗
exit 1

Instance : http://gitlab.test

0. Connectivité, identité, licence
----------------------------------
  api/v4/user                      [REFUSÉ]  HTTP 401 — jeton refusé

  Le jeton n'est pas accepté. La portée read_api suffit,
  mais un jeton expiré ou révoqué donne le même 401.

╔═ anEmptyGroupStopsWithExitOneAfterSayingSo ═╗
exit 1

Instance : http://gitlab.test

0. Connectivité, identité, licence
----------------------------------
  api/v4/user                      [OK]  alice (Alice A)
  api/v4/metadata                  [VIDE]  GitLab 17.0.0 · Community — DORA et approbations indisponibles

1. Inventaire (étape 0)
-----------------------
  Projets retournés               : 0
  Retenus pour analyse            : 0 (— du parc)

  Aucun projet dans le périmètre. Rien à faire.

╔═ anUnreachableInstanceStopsWithExitOne ═╗

Instance : http://127.0.0.1:<PORT>

0. Connectivité, identité, licence
----------------------------------
  api/v4/user                      [ERREUR]  ConnectException: Connection refused

  Instance injoignable. Vérifie l'URL, le proxy, le TLS.

╔═ [end of file] ═╗
