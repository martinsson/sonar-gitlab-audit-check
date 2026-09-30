// Combien de projets au plus.
const NON_APPARIES_MAX = 40;

Viz.add({
  id: 'non-apparies',
  title: 'Projets actifs sans projet Sonar',
  help: `Les ${NON_APPARIES_MAX} projets les plus actifs que rien n'apparie à Sonar : du travail que Sonar ne voit pas, `
      + 'ou une clé à retrouver à la main dans appariement.csv. Couleur = la CI mentionne-t-elle Sonar. '
      + 'La liste complète : Vues --in croisement.csv écrit croisement-non-apparies.csv.',
  needs: ['croisement'],
  spec(d, h) {
    const values = d.croisement
      .filter(r => !h.paired(r) && (h.num(r.gl_commits_window) || 0) > 0)
      .map(r => ({
        projet: r.projet, commits: h.num(r.gl_commits_window), lignes: h.num(r.gl_lignes_modifiees),
        confiance: r.confiance, sonar_ci: h.bool(r.gl_ci_sonar) ? 'Sonar dans la CI' : 'pas de Sonar dans la CI',
        cle: r.gl_cle_sonar || r.gl_cle_pom || '', indice: r.candidat_nom || r.m_noms_libre || '',
      }))
      .sort((a, b) => b.commits - a.commits || (b.lignes || 0) - (a.lignes || 0))
      .slice(0, NON_APPARIES_MAX);
    if (!values.length) return null;
    return {
      $schema: 'https://vega.github.io/schema/vega-lite/v5.json',
      width: 600, data: { values },
      mark: 'bar',
      encoding: {
        y: { field: 'projet', type: 'nominal', sort: '-x', title: null, axis: { labelLimit: 360 } },
        x: { field: 'commits', type: 'quantitative', title: 'commits sur la fenêtre GitLab' },
        color: { field: 'sonar_ci', type: 'nominal', title: null,
                 scale: { domain: ['Sonar dans la CI', 'pas de Sonar dans la CI'], range: ['#f28e2b', '#4e79a7'] } },
        tooltip: [{ field: 'projet' }, { field: 'commits' }, { field: 'lignes', title: 'lignes modifiées' },
                  { field: 'confiance' }, { field: 'cle', title: 'clé lue (CI ou pom)' },
                  { field: 'indice', title: 'candidat par le nom' }],
      },
    };
  },
});
