Viz.add({
  id: 'fleches',
  title: 'Dérive de la densité d\'issues',
  help: 'Chaque flèche va de la densité estimée en début d\'historique Sonar à la densité actuelle. '
      + 'Rouge = le code se charge, vert = il s\'allège. Le point de départ est reconstitué depuis la pente '
      + '(sq_violations_kloc_pente_pct_mois × durée de l\'historique) : un ordre de grandeur, pas une mesure.',
  needs: ['croisement'],
  spec(d, h) {
    const values = d.croisement.filter(h.paired).map(r => {
      const now = h.density(r);
      const pct = h.num(r.sq_violations_kloc_pente_pct_mois);
      const months = (h.num(r.sq_tendance_jours) || 0) / 30;
      if (now === null || pct === null || !months) return null;
      return {
        projet: r.projet, ns: h.ns(r.projet),
        churn: h.num(r.gl_lignes_modifiees),
        avant: Math.max(0, now * (1 - pct / 100 * months)), maintenant: now,
        pente: pct, sens: pct > 0 ? 'se charge' : 's\'allège',
      };
    }).filter(v => v && v.churn > 0);
    if (!values.length) return null;
    const tooltip = [
      { field: 'projet' }, { field: 'ns', title: 'namespace' },
      { field: 'avant', title: 'issues/kLOC avant (estimé)', format: '.1f' },
      { field: 'maintenant', title: 'issues/kLOC maintenant', format: '.1f' },
      { field: 'pente', title: '% par mois', format: '+.1f' }, { field: 'churn', title: 'lignes modifiées' },
    ];
    const x = { field: 'churn', type: 'quantitative', scale: { type: 'log' }, title: 'lignes modifiées sur la fenêtre GitLab' };
    const color = { field: 'sens', type: 'nominal', title: null,
                    scale: { domain: ['se charge', 's\'allège'], range: ['#d62728', '#2ca02c'] } };
    return {
      $schema: 'https://vega.github.io/schema/vega-lite/v5.json',
      width: 800, height: 500, data: { values },
      layer: [
        { mark: { type: 'rule', strokeWidth: 2 },
          encoding: { x, y: { field: 'avant', type: 'quantitative', title: 'issues / kLOC' },
                      y2: { field: 'maintenant' }, color, tooltip } },
        { mark: { type: 'point', filled: true, size: 90 },
          encoding: { x, y: { field: 'maintenant', type: 'quantitative' }, color, tooltip,
                      shape: { field: 'sens', type: 'nominal', legend: null,
                               scale: { domain: ['se charge', 's\'allège'], range: ['triangle-up', 'triangle-down'] } } } },
      ],
    };
  },
});
