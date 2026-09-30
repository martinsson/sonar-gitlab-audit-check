// Les namespaces de moins de projets que ça sont regroupés sur une ligne « autres ».
const PRATIQUES_MIN = 3;

// Les pratiques en colonnes. Ajouter une colonne = ajouter une ligne ici.
const PRATIQUES = [
  ['branche protégée', r => H.bool(r.branche_protegee)],
  ['push interdit (MR obligatoire)', r => H.bool(r.push_verrouille)],
  ['MR fusionnées', r => (H.num(r.mr_fusionnees) || 0) > 0],
  ['MR approuvées (≥ 50 %)', r => (H.num(r.part_approuvee) || 0) >= 0.5],
  ['pipeline ≥ 80 % vert', r => (H.num(r.taux_succes) || 0) >= 0.8],
  ['Sonar dans la CI', r => H.bool(r.ci_sonar)],
  ['clé Sonar lisible', r => !!(r.cle_sonar || r.cle_pom)],
  ['scan sécurité', r => H.bool(r.ci_securite)],
  ['environnements déclarés', r => (H.num(r.environnements) || 0) > 0],
  ['plus d\'un auteur', r => (H.num(r.authors_window) || 0) > 1],
  ['CODEOWNERS', r => String(r.fichiers || '').includes('CODEOWNERS')],
  ['README', r => String(r.fichiers || '').includes('README')],
];

Viz.add({
  id: 'pratiques',
  title: 'Pratiques par namespace',
  help: 'Part des projets analysés de chaque namespace qui suivent la pratique. Une vue d\'équipe, '
      + 'pas de projet. Le nombre entre parenthèses est l\'effectif ; les namespaces de moins de '
      + `${PRATIQUES_MIN} projets analysés sont regroupés dans « autres ». Les plus gros en haut.`,
  needs: ['pratiques'],
  spec(d, h) {
    const byNs = {};
    for (const r of d.pratiques) (byNs[h.ns(r.path)] ||= []).push(r);
    const groups = Object.entries(byNs).filter(([, rows]) => rows.length >= PRATIQUES_MIN)
      .sort((a, b) => b[1].length - a[1].length);
    const rest = Object.values(byNs).filter(rows => rows.length < PRATIQUES_MIN).flat();
    if (rest.length) groups.push(['autres', rest]);
    const values = [];
    for (const [ns, rows] of groups) {
      for (const [pratique, test] of PRATIQUES) {
        const n = rows.filter(test).length;
        values.push({ equipe: `${ns} (${rows.length})`, pratique, part: n / rows.length, n, total: rows.length });
      }
    }
    return {
      $schema: 'https://vega.github.io/schema/vega-lite/v5.json',
      data: { values },
      encoding: {
        x: { field: 'pratique', type: 'nominal', sort: PRATIQUES.map(p => p[0]), title: null,
             axis: { labelAngle: -35, labelLimit: 200, orient: 'top' } },
        y: { field: 'equipe', type: 'nominal', title: null, axis: { labelLimit: 300 },
             sort: groups.map(([ns, rows]) => `${ns} (${rows.length})`) },
      },
      layer: [
        { mark: 'rect', encoding: {
            color: { field: 'part', type: 'quantitative', title: 'part', scale: { scheme: 'redyellowgreen', domain: [0, 1] },
                     legend: { format: '.0%' } },
            tooltip: [{ field: 'equipe' }, { field: 'pratique' }, { field: 'n', title: 'projets' },
                      { field: 'total' }, { field: 'part', format: '.0%' }] } },
        { mark: { type: 'text', fontSize: 11, color: 'black' }, encoding: { text: { field: 'part', format: '.0%' } } },
      ],
      config: { view: { step: 34 } },
    };
  },
});
