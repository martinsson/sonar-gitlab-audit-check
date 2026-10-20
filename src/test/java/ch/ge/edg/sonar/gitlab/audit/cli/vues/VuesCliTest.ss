╔═ an_older_crossing_without_the_drift_columns_is_read_and_the_gap_said/derive ═╗
﻿"projet";"signaux";"sq_violations_kloc_pente_pct_mois";"sq_dette_ratio_pente_pct_mois";"sq_violations_pente_mois";"issues_par_kloc_modifie";"issues_par_commit";"sq_tendance_analyses";"sq_tendance_jours";"sq_analysisDate";"sq_days_since_analysis";"gl_commits_window";"gl_lignes_modifiees";"gl_authors_window";"gl_mr_fusionnees";"gl_auto_merge";"gl_part_approuvee";"sq_new_violations";"sq_new_lines";"sq_new_coverage";"gl_ci_securite";"sq_alert_status";"gl_taux_succes";"gl_rouge_non_resolu";"sq_ncloc";"sq_sqale_debt_ratio";"confiance"
"alpha";"";"";"";"";"";"";"";"";"";"";"3";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"exact"

╔═ an_older_crossing_without_the_drift_columns_is_read_and_the_gap_said/essentiel ═╗
﻿"projet";"confiance";"gl_commits_window"
"alpha";"exact";"3"
"beta";"";"4"

╔═ an_older_crossing_without_the_drift_columns_is_read_and_the_gap_said/non-apparies ═╗
﻿"projet";"confiance";"gl_commits_window";"gl_lignes_modifiees";"gl_authors_window";"gl_ci_sonar";"gl_cle_sonar";"gl_source_cle_sonar";"gl_cle_pom";"gl_nom_pom";"candidat_nom";"score_nom";"rejet_nom";"m_noms_libre"
"beta";"";"4";"";"";"";"";"";"";"";"";"";"";""

╔═ an_older_crossing_without_the_drift_columns_is_read_and_the_gap_said/stdout ═╗
  Vue essentiel : 2 lignes → <dir>/old-essentiel.csv
  Vue dérive    : 1 projets appariés → <dir>/old-derive.csv
    Triée par nombre de signaux, puis par pente de la densité d'issues.
  Vue non appariés : 1 projets actifs sans projet Sonar → <dir>/old-non-apparies.csv
    Les plus actifs d'abord. Seulement l'échantillon de pratiques.csv : un projet hors sélection n'y est pas.
  Colonnes absentes du croisement, laissées vides : sq_violations_kloc_pente_pct_mois, sq_dette_ratio_pente_pct_mois, sq_violations_pente_mois, issues_par_kloc_modifie, issues_par_commit, sq_tendance_analyses, sq_tendance_jours, sq_analysisDate, sq_days_since_analysis, gl_lignes_modifiees, gl_authors_window, gl_mr_fusionnees, gl_auto_merge, gl_part_approuvee, sq_new_violations, sq_new_lines, sq_new_coverage, gl_ci_securite, sq_alert_status, gl_taux_succes, gl_rouge_non_resolu, sq_ncloc, sq_sqale_debt_ratio
    Un croisement plus ancien : relancer les trois outils les remplit.

╔═ comma_mode_writes_plain_csv_without_bom/derive ═╗
"projet","signaux","sq_violations_kloc_pente_pct_mois","sq_dette_ratio_pente_pct_mois","sq_violations_pente_mois","issues_par_kloc_modifie","issues_par_commit","sq_tendance_analyses","sq_tendance_jours","sq_analysisDate","sq_days_since_analysis","gl_commits_window","gl_lignes_modifiees","gl_authors_window","gl_mr_fusionnees","gl_auto_merge","gl_part_approuvee","sq_new_violations","sq_new_lines","sq_new_coverage","gl_ci_securite","sq_alert_status","gl_taux_succes","gl_rouge_non_resolu","sq_ncloc","sq_sqale_debt_ratio","confiance"
"epsilon-lib","densité en hausse, code neuf < 50 % couvert, sans appsec","9","","","","","","","","","","","","","","","","","10","false","","","","","","exact"
"gamma-ui","densité en hausse, modifs ajoutent des issues, code neuf < 50 % couvert","3","","","0.4","","","","","","","","","","","","","","20","","","","","","","manuel"
"alpha-service","aucune revue, quality gate rouge","1.5","","","","","","","","","40","","","0","","","","","","","ERROR","","","","","exact"
"beta-api","densité en hausse","7","","","","","","","","","3","","","2","","","","","","","","","","","","derived"
"delta-batch","","","","","","","","","","","","","","","","","","","","","","","","","","exact"

╔═ comma_mode_writes_plain_csv_without_bom/essentiel ═╗
"projet","confiance","sq_violations_kloc_pente_pct_mois","issues_par_kloc_modifie","sq_alert_status","sq_analysisDate","sq_days_since_analysis","gl_commits_window","gl_lignes_modifiees","gl_authors_window","gl_mr_fusionnees","gl_auto_merge","gl_ci_securite","sq_ncloc","sq_sqale_debt_ratio","sq_coverage","sq_new_coverage","zz_extra","m_noms_libre","rejet_nom","score_nom","candidat_nom","gl_nom_pom","gl_cle_pom","gl_source_cle_sonar","gl_cle_sonar","gl_ci_sonar","gl_rouge_non_resolu","gl_taux_succes","sq_new_lines","sq_new_violations","gl_part_approuvee","sq_tendance_jours","sq_tendance_analyses","issues_par_commit","sq_violations_pente_mois","sq_dette_ratio_pente_pct_mois"
"alpha-service","exact","1.5","","ERROR","","","40","","","0","","","","","","","x1","","","","","","","","","","","","","","","","","","",""
"beta-api","derived","7","","","","","3","","","2","","","","","","","","","","","","","","","","true","","","","","","","","","",""
"gamma-ui","manuel","3","0.4","","","","","","","","","","","","","20","","","","","","","","","","","","","","","","","","","",""
"delta-batch","exact","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","",""
"epsilon-lib","exact","9","","","","","","","","","","false","","","","10","","","","","","","","","","","","","","","","","","","",""
"team-a/zeta","","","","","","","12","300","","","","","","","","","","","","0.7","zeta-old","","","","","","","","","","","","","","",""
"team-a/eta","name","","","","","","12","900","","","","","","","","","","","","","","","","","eta-key","","","","","","","","","","",""
"team-b/theta","","","","","","","50","","","","","","","","","","","","","","","","","","","","","","","","","","","","",""
"team-b/iota","","","","","","","0","","","","","","","","","","","","","","","","","","","","","","","","","","","","",""
"team-b/kappa","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","",""

╔═ comma_mode_writes_plain_csv_without_bom/non-apparies ═╗
"projet","confiance","gl_commits_window","gl_lignes_modifiees","gl_authors_window","gl_ci_sonar","gl_cle_sonar","gl_source_cle_sonar","gl_cle_pom","gl_nom_pom","candidat_nom","score_nom","rejet_nom","m_noms_libre"
"team-b/theta","","50","","","","","","","","","","",""
"team-a/eta","name","12","900","","","eta-key","","","","","","",""
"team-a/zeta","","12","300","","","","","","","zeta-old","0.7","",""

╔═ writes_three_excel_views_and_summarizes_them/derive ═╗
﻿"projet";"signaux";"sq_violations_kloc_pente_pct_mois";"sq_dette_ratio_pente_pct_mois";"sq_violations_pente_mois";"issues_par_kloc_modifie";"issues_par_commit";"sq_tendance_analyses";"sq_tendance_jours";"sq_analysisDate";"sq_days_since_analysis";"gl_commits_window";"gl_lignes_modifiees";"gl_authors_window";"gl_mr_fusionnees";"gl_auto_merge";"gl_part_approuvee";"sq_new_violations";"sq_new_lines";"sq_new_coverage";"gl_ci_securite";"sq_alert_status";"gl_taux_succes";"gl_rouge_non_resolu";"sq_ncloc";"sq_sqale_debt_ratio";"confiance"
"epsilon-lib";"densité en hausse, code neuf < 50 % couvert, sans appsec";"9";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"10";"false";"";"";"";"";"";"exact"
"gamma-ui";"densité en hausse, modifs ajoutent des issues, code neuf < 50 % couvert";"3";"";"";"0.4";"";"";"";"";"";"";"";"";"";"";"";"";"";"20";"";"";"";"";"";"";"manuel"
"alpha-service";"aucune revue, quality gate rouge";"1.5";"";"";"";"";"";"";"";"";"40";"";"";"0";"";"";"";"";"";"";"ERROR";"";"";"";"";"exact"
"beta-api";"densité en hausse";"7";"";"";"";"";"";"";"";"";"3";"";"";"2";"";"";"";"";"";"";"";"";"";"";"";"derived"
"delta-batch";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"exact"

╔═ writes_three_excel_views_and_summarizes_them/essentiel ═╗
﻿"projet";"confiance";"sq_violations_kloc_pente_pct_mois";"issues_par_kloc_modifie";"sq_alert_status";"sq_analysisDate";"sq_days_since_analysis";"gl_commits_window";"gl_lignes_modifiees";"gl_authors_window";"gl_mr_fusionnees";"gl_auto_merge";"gl_ci_securite";"sq_ncloc";"sq_sqale_debt_ratio";"sq_coverage";"sq_new_coverage";"zz_extra";"m_noms_libre";"rejet_nom";"score_nom";"candidat_nom";"gl_nom_pom";"gl_cle_pom";"gl_source_cle_sonar";"gl_cle_sonar";"gl_ci_sonar";"gl_rouge_non_resolu";"gl_taux_succes";"sq_new_lines";"sq_new_violations";"gl_part_approuvee";"sq_tendance_jours";"sq_tendance_analyses";"issues_par_commit";"sq_violations_pente_mois";"sq_dette_ratio_pente_pct_mois"
"alpha-service";"exact";"1.5";"";"ERROR";"";"";"40";"";"";"0";"";"";"";"";"";"";"x1";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";""
"beta-api";"derived";"7";"";"";"";"";"3";"";"";"2";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"true";"";"";"";"";"";"";"";"";"";""
"gamma-ui";"manuel";"3";"0.4";"";"";"";"";"";"";"";"";"";"";"";"";"20";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";""
"delta-batch";"exact";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";""
"epsilon-lib";"exact";"9";"";"";"";"";"";"";"";"";"";"false";"";"";"";"10";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";""
"team-a/zeta";"";"";"";"";"";"";"12";"300";"";"";"";"";"";"";"";"";"";"";"";"0.7";"zeta-old";"";"";"";"";"";"";"";"";"";"";"";"";"";"";""
"team-a/eta";"name";"";"";"";"";"";"12";"900";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"eta-key";"";"";"";"";"";"";"";"";"";"";""
"team-b/theta";"";"";"";"";"";"";"50";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";""
"team-b/iota";"";"";"";"";"";"";"0";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";""
"team-b/kappa";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";"";""

╔═ writes_three_excel_views_and_summarizes_them/non-apparies ═╗
﻿"projet";"confiance";"gl_commits_window";"gl_lignes_modifiees";"gl_authors_window";"gl_ci_sonar";"gl_cle_sonar";"gl_source_cle_sonar";"gl_cle_pom";"gl_nom_pom";"candidat_nom";"score_nom";"rejet_nom";"m_noms_libre"
"team-b/theta";"";"50";"";"";"";"";"";"";"";"";"";"";""
"team-a/eta";"name";"12";"900";"";"";"eta-key";"";"";"";"";"";"";""
"team-a/zeta";"";"12";"300";"";"";"";"";"";"";"zeta-old";"0.7";"";""

╔═ [end of file] ═╗
