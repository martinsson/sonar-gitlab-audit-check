╔═ print_of_nothing ═╗
  Projets Sonar : 0, dont 0 appariés sûrement (—)
  Appariés à un dépôt hors de la sélection GitLab : 0, dont 0 actifs (10 commits ou plus)
  Sonar × tout GitLab : 0 lignes → <tmp>/sg.csv

╔═ print_sums_up_by_confidence_outside_selection_reasons_and_checks ═╗
  Projets Sonar : 7, dont 4 appariés sûrement (57 %)
    derived    2
    exact      1
    manuel     1
    none       2
    suggestion 1
  Appariés à un dépôt hors de la sélection GitLab : 2, dont 1 actifs (10 commits ou plus)
  Sans dépôt GitLab, par raison :
    ambigu                               1
    déjà proposé à un autre              1
  Méthodes dérivées rejouées sur les paires exactes : 2 sur 3 d'accord
    m0 : 1 d'accord, 0 non
    m1 : 1 d'accord, 1 non
  Sonar × tout GitLab : 7 lignes → <tmp>/sg.csv

╔═ write_gives_one_row_per_sonar_project_with_its_repository_counts ═╗
"sonar_key","sonar_name","gitlab_path","gitlab_id","methode","confiance","score","autres_candidats","raison","cles_sonar_sur_ce_projet","dans_selection","gl_commits_window","gl_last_activity_at","gl_exclu","sq_analysisDate","sq_ncloc"
"k1","One","team-a/shared","1","croisement : x","exact","","","","2","oui","50","2026-01-01","","2026-01-01","100"
"k2","Two","team-a/shared","1","liaison","manuel","0.50","c, d","","2","oui","50","2026-01-01","","2026-01-02","200"
"k3","Three","team-b/out-active","2","derived","derived","1.00","","","1","non","10","2026-01-02","oui","",""
"k4","Four","team-c/out-quiet","3","derived","derived","0.12","","","1","non","9","2026-01-03","","",""
"k5","Five","team-d/suggested","4","noms","suggestion","0.99","z (0.1)","","1","non","99","2026-01-04","","",""
"k6","Six","","","aucune","none","","a (0.4), b","ambigu","","","","","","",""
"k7","Seven","","","aucune","none","","x","déjà proposé à k1","","","","","","",""

╔═ [end of file] ═╗
