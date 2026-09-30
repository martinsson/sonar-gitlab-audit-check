Viz.add({
  id: 'treemap',
  title: 'Carte du parc',
  help: 'Surface = commits sur la fenêtre GitLab, regroupés par namespace. Couleur = quality gate Sonar ; '
      + 'gris foncé = aucun projet Sonar apparié, c\'est-à-dire de l\'activité que Sonar ne voit pas.',
  needs: ['croisement'],
  spec(d, h) {
    const nodes = [{ id: '/', parent: null, name: '' }];
    const seen = new Set(['/']);
    for (const r of d.croisement) {
      const parts = r.projet.split('/');
      let parent = '/';
      for (let i = 1; i < parts.length; i++) {
        const id = parts.slice(0, i).join('/');
        if (!seen.has(id)) { seen.add(id); nodes.push({ id, parent, name: parts[i - 1] }); }
        parent = id;
      }
      nodes.push({
        id: r.projet + '#', parent, name: parts[parts.length - 1], projet: r.projet,
        size: Math.max(1, h.num(r.gl_commits_window) || 0),
        statut: !h.paired(r) ? 'non apparié' : ({ OK: 'gate OK', ERROR: 'gate en échec', WARN: 'gate en alerte' }[r.sq_alert_status] || 'sans gate'),
        cle: r.sq_key || '', ncloc: r.sq_ncloc || '', commits: r.gl_commits_window || '',
      });
    }
    return {
      $schema: 'https://vega.github.io/schema/vega/v5.json',
      width: 1000, height: 600, padding: 2,
      data: [
        { name: 'tree', values: nodes, transform: [
          { type: 'stratify', key: 'id', parentKey: 'parent' },
          { type: 'treemap', field: 'size', sort: { field: 'value', order: 'descending' },
            method: 'squarify', paddingInner: 1, paddingTop: 16, paddingOuter: 2, size: [{ signal: 'width' }, { signal: 'height' }] },
        ] },
        { name: 'groups', source: 'tree', transform: [{ type: 'filter', expr: 'datum.children && datum.depth > 0' }] },
        { name: 'leaves', source: 'tree', transform: [{ type: 'filter', expr: '!datum.children' }] },
      ],
      scales: [{ name: 'color', type: 'ordinal',
        domain: ['gate OK', 'gate en alerte', 'gate en échec', 'sans gate', 'non apparié'],
        range: ['#59a14f', '#f28e2b', '#e15759', '#bab0ac', '#4e4e4e'] }],
      legends: [{ fill: 'color', title: 'statut', orient: 'right' }],
      marks: [
        { type: 'rect', from: { data: 'groups' }, encode: { enter: {
            x: { field: 'x0' }, y: { field: 'y0' }, x2: { field: 'x1' }, y2: { field: 'y1' },
            fill: { value: '#8882' }, stroke: { value: '#8886' },
            tooltip: { signal: "{'namespace': datum.id, 'commits': datum.value}" } } } },
        { type: 'text', from: { data: 'groups' }, encode: { enter: {
            x: { signal: 'datum.x0 + 4' }, y: { signal: 'datum.y0 + 12' }, text: { field: 'name' },
            fontSize: { value: 11 }, fontWeight: { value: 'bold' }, fill: { value: '#666' },
            limit: { signal: 'datum.x1 - datum.x0 - 8' } } } },
        { type: 'rect', from: { data: 'leaves' }, encode: {
            enter: { x: { field: 'x0' }, y: { field: 'y0' }, x2: { field: 'x1' }, y2: { field: 'y1' },
                     fill: { scale: 'color', field: 'statut' }, stroke: { value: 'white' },
                     tooltip: { signal: "{'projet': datum.projet, 'statut': datum.statut, 'commits': datum.commits, 'clé Sonar': datum.cle, 'lignes de code': datum.ncloc}" } },
            hover: { fillOpacity: { value: 0.7 } }, update: { fillOpacity: { value: 1 } } } },
        { type: 'text', from: { data: 'leaves' }, interactive: false, encode: { enter: {
            x: { signal: 'datum.x0 + 3' }, y: { signal: 'datum.y0 + 12' }, text: { field: 'name' },
            fontSize: { value: 10 }, fill: { value: 'white' },
            limit: { signal: 'datum.x1 - datum.x0 - 6' },
            opacity: { signal: 'datum.y1 - datum.y0 > 14 ? 1 : 0' } } } },
      ],
    };
  },
});
