╔═ noVisibleProjectWritesAHeaderOnlyCsvAndSkipsTheSampleSections/stdout ═╗

Instance : <URL>

=== 1. Connectivité et identité =======================================
  OK       api/system/status                          UP · v10.4.1 · inst-1
  OK       api/authentication/validate                token valide
  OK       api/users/current                          alice (Alice Audit)

  Permissions globales : scan, provisioning
  Groupes              : team-a, sonar-users
  Métriques inconnues de cette instance : test_errors
    Retirées de la requête. Demandées quand même, elles feraient échouer
    l'appel entier et le parc ressortirait sans aucune mesure.

=== 2. Capacités du token sur les endpoints utiles ====================
  Aucun projet visible : impossible de tester les endpoints projet.

=== 3. Inventaire des projets visibles ================================
  Projets visibles avec ce token : 0
  Projets réellement présents    : 0
  → Périmètre complet.
  Dernier scan sur une branche non principale : 0

  Analysés il y a < 90 j        : 0
  Analysés il y a > 90 j        : 0
  Jamais analysés                : 0
  Sans aucune donnée de couverture: 0

  CSV écrit : <TMP>/inventaire.csv
  Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start inventaire.csv`. Pour un autre outil : --comma.

=== Synthèse ==========================================================
  8 appels API effectués (8 en vol).
  Rappel : search_projects filtre silencieusement sur ce que le token
  peut voir. Une permission manquante ne produit pas d'erreur, seulement
  un classement incomplet — et les projets manquants sont souvent ceux
  qui auraient le plus besoin d'aide.


╔═ [end of file] ═╗
