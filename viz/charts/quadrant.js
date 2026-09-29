Viz.add({
  id: 'quadrant',
  title: 'Activité × qualité',
  help: 'Un point par projet apparié. En haut à droite : beaucoup de changements sur du code chargé en issues, '
      + 'là où agir d\'abord. Les lignes grises sont les médianes. Taille = lignes de code.',
  needs: ['croisement'],
  spec(d, h) {
    const values = d.croisement.filter(h.paired).map(r => ({
      projet: r.projet, cle: r.sq_key, ns: h.ns(r.projet),
      churn: h.num(r.gl_lignes_modifiees), densite: h.density(r),
      ncloc: h.num(r.sq_ncloc), commits: h.num(r.gl_commits_window),
      gate: r.sq_alert_status || '—', couverture: h.num(r.sq_coverage),
    })).filter(v => v.churn > 0 && v.densite !== null);
    if (!values.length) return null;
    return {
      $schema: 'https://vega.github.io/schema/vega-lite/v5.json',
      width: 800, height: 500, data: { values },
      layer: [
        {
          params: [{ name: 'ns', select: { type: 'point', fields: ['ns'] }, bind: 'legend' }],
          mark: { type: 'circle', stroke: 'white', strokeWidth: 0.5 },
          encoding: {
            x: { field: 'churn', type: 'quantitative', scale: { type: 'log' },
                 title: 'lignes modifiées sur la fenêtre GitLab' },
            y: { field: 'densite', type: 'quantitative', title: 'issues / kLOC' },
            size: { field: 'ncloc', type: 'quantitative', title: 'lignes de code', scale: { range: [20, 1200] } },
            color: { field: 'ns', type: 'nominal', title: 'namespace', scale: { scheme: 'tableau20' } },
            opacity: { condition: { param: 'ns', value: 0.8 }, value: 0.08 },
            tooltip: [
              { field: 'projet' }, { field: 'cle', title: 'clé Sonar' },
              { field: 'churn', title: 'lignes modifiées' }, { field: 'commits' },
              { field: 'densite', title: 'issues/kLOC', format: '.1f' },
              { field: 'ncloc' }, { field: 'couverture', title: 'couverture %' }, { field: 'gate', title: 'quality gate' },
            ],
          },
        },
        { mark: { type: 'rule', color: 'gray', strokeDash: [4, 4] },
          encoding: { x: { aggregate: 'median', field: 'churn', type: 'quantitative' } } },
        { mark: { type: 'rule', color: 'gray', strokeDash: [4, 4] },
          encoding: { y: { aggregate: 'median', field: 'densite', type: 'quantitative' } } },
      ],
    };
  },
});
