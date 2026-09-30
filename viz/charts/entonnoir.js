Viz.add({
  id: 'entonnoir',
  title: 'Entonnoir de couverture',
  help: 'Combien de projets GitLab arrivent jusqu\'à une mesure Sonar récente. Chaque marche perdue est un constat. '
      + 'Les étapes ne sont pas strictement emboîtées : un projet peut être apparié sans que sa CI mentionne Sonar.',
  needs: ['gitlab'],
  uses: ['pratiques', 'croisement'],
  spec(d, h) {
    // La liste d'exclusion retire ce que l'audit a décidé de ne pas regarder :
    // ce n'est pas une perte de l'entonnoir.
    const parc = d.gitlab.filter(r => r.exclu !== 'liste d\'exclusion');
    const stages = [
      ['Projets GitLab (hors liste d\'exclusion)', parc.length],
      ['Non exclus (actifs, ni archive, ni fork, ni miroir)', parc.filter(r => !r.exclu).length],
      ['Sélectionnés pour l\'analyse fine', parc.filter(r => h.bool(r.selectionne)).length],
    ];
    if (d.pratiques) stages.push(['Sonar mentionné dans la CI', d.pratiques.filter(r => h.bool(r.ci_sonar)).length]);
    if (d.croisement) {
      const paired = d.croisement.filter(h.paired);
      stages.push(['Appariés à un projet Sonar', paired.length]);
      stages.push(['Analysés dans les 90 derniers jours',
        paired.filter(r => { const a = h.num(r.sq_days_since_analysis); return a !== null && a <= 90; }).length]);
    }
    const top = stages[0][1] || 1;
    const values = stages.map(([etape, n], i) => ({
      ordre: i, etape, n, part: n / top,
      label: `${n}  (${Math.round(n / top * 100)} %)`,
    }));
    return {
      $schema: 'https://vega.github.io/schema/vega-lite/v5.json',
      width: 700, data: { values },
      encoding: {
        y: { field: 'etape', sort: { field: 'ordre' }, title: null, axis: { labelLimit: 360 } },
        x: { field: 'n', type: 'quantitative', title: 'projets' },
        tooltip: [{ field: 'etape' }, { field: 'n' }, { field: 'part', format: '.0%', title: 'du total' }],
      },
      layer: [
        { mark: { type: 'bar', color: '#4c78a8' } },
        { mark: { type: 'text', align: 'left', dx: 4 }, encoding: { text: { field: 'label' } } },
      ],
    };
  },
});
