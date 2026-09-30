// Combien de petits graphiques au plus, les projets les plus modifiés d'abord.
const HISTORIQUE_MAX = 24;
// Moins d'analyses que ça, il n'y a pas de courbe à lire.
const HISTORIQUE_MIN_POINTS = 3;

Viz.add({
  id: 'historique',
  title: 'Historique des issues / kLOC',
  help: `Une courbe par projet, sur la fenêtre --activity-days de SonarAuditCheck. Échelles indépendantes : `
      + `comparer les formes, pas les hauteurs. Avec croisement.csv, seuls les projets appariés, `
      + `les ${HISTORIQUE_MAX} plus modifiés côté GitLab ; sinon les ${HISTORIQUE_MAX} premiers. `
      + `Seuls les projets avec au moins ${HISTORIQUE_MIN_POINTS} analyses.`,
  needs: ['historique'],
  uses: ['croisement'],
  spec(d, h) {
    const points = {};
    for (const r of d.historique) points[r.key] = (points[r.key] || 0) + 1;
    let keys = Object.keys(points).filter(k => points[k] >= HISTORIQUE_MIN_POINTS);
    const label = {};
    if (d.croisement) {
      const paired = d.croisement.filter(r => h.paired(r) && r.sq_key)
        .sort((a, b) => (h.num(b.gl_lignes_modifiees) || 0) - (h.num(a.gl_lignes_modifiees) || 0));
      const seen = new Set();
      paired.forEach(r => { label[r.sq_key] = h.short(r.projet, seen); });
      keys = paired.map(r => r.sq_key).filter(k => keys.includes(k));
    }
    keys = new Set(keys.slice(0, HISTORIQUE_MAX));
    const values = d.historique.filter(r => keys.has(r.key)).map(r => {
      const v = h.num(r.violations), n = h.num(r.ncloc);
      return { projet: label[r.key] || r.key, date: h.date(r.date),
               densite: v !== null && n ? v / n * 1000 : null, violations: v, ncloc: n };
    }).filter(v => v.date !== null && v.densite !== null);
    if (!values.length) return null;
    return {
      $schema: 'https://vega.github.io/schema/vega-lite/v5.json',
      data: { values },
      facet: { field: 'projet', type: 'nominal', title: null, header: { labelLimit: 180 } },
      columns: 4,
      spec: {
        width: 200, height: 90,
        mark: { type: 'line', point: { size: 12 } },
        encoding: {
          x: { field: 'date', type: 'temporal', title: null, axis: { format: '%d/%m', tickCount: 4 } },
          y: { field: 'densite', type: 'quantitative', title: 'issues/kLOC', scale: { zero: false } },
          tooltip: [{ field: 'projet' }, { field: 'date', type: 'temporal' },
                    { field: 'densite', format: '.1f', title: 'issues/kLOC' }, { field: 'violations' }, { field: 'ncloc' }],
        },
      },
      resolve: { scale: { y: 'independent' } },
    };
  },
});
