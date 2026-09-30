/*
 * Le même nuage — activité GitLab en x — pour plusieurs mesures Sonar en y.
 * Une mesure de plus = une ligne dans QUADRANT_AXES.
 *   value(row, h)  la valeur, ou null pour ne pas placer le projet
 *   lowerIsWorse   l'axe est retourné, pour que le coin « à traiter » reste en haut à droite
 */
const QUADRANT_AXES = [
  { id: 'densite', label: 'issues / kLOC', value: (r, h) => h.density(r) },
  { id: 'couverture-neuf', label: 'couverture du code neuf %', lowerIsWorse: true,
    value: (r, h) => h.num(r.sq_new_coverage) },
  { id: 'issues-neuves', label: 'issues neuves / kLOC neuf',
    value: (r, h) => { const n = h.num(r.sq_new_lines), v = h.num(r.sq_new_violations);
                       return n && v !== null ? v / n * 1000 : null; } },
  { id: 'couverture', label: 'couverture %', lowerIsWorse: true, value: (r, h) => h.num(r.sq_coverage) },
  { id: 'dette', label: 'ratio de dette %', value: (r, h) => h.num(r.sq_sqale_debt_ratio) },
  { id: 'duplication-neuf', label: 'duplication du code neuf %',
    value: (r, h) => h.num(r.sq_new_duplicated_lines_density) },
];

for (const axis of QUADRANT_AXES) {
  Viz.add({
    id: 'quadrant-' + axis.id,
    title: `Activité × ${axis.label}`,
    help: 'Un point par projet apparié. En haut à droite : beaucoup de changements, et la mesure est mauvaise — '
        + 'là où agir d\'abord' + (axis.lowerIsWorse ? ' (axe retourné : bas en haut)' : '') + '. '
        + 'Lignes grises = médianes. Taille = lignes de code. Molette = zoom vertical, glisser = déplacer, '
        + 'double-clic = revenir. Clic sur la légende = un seul namespace.',
    needs: ['croisement'],
    spec(d, h) {
      const values = d.croisement.filter(h.paired).map(r => ({
        projet: r.projet, cle: r.sq_key, ns: h.ns(r.projet),
        churn: h.num(r.gl_lignes_modifiees), y: axis.value(r, h),
        ncloc: h.num(r.sq_ncloc), commits: h.num(r.gl_commits_window),
        gate: r.sq_alert_status || '—',
      })).filter(v => v.churn > 0 && v.y !== null);
      if (!values.length) return null;
      return {
        $schema: 'https://vega.github.io/schema/vega-lite/v5.json',
        width: 800, height: 500, data: { values },
        layer: [
          {
            params: [
              { name: 'ns', select: { type: 'point', fields: ['ns'] }, bind: 'legend' },
              // Zoom sur l'axe des y seulement : x reste l'activité, lue en entier.
              { name: 'zoom', select: { type: 'interval', encodings: ['y'] }, bind: 'scales' },
            ],
            mark: { type: 'circle', stroke: 'white', strokeWidth: 0.5, clip: true },
            encoding: {
              x: { field: 'churn', type: 'quantitative', scale: { type: 'log' },
                   title: 'lignes modifiées sur la fenêtre GitLab' },
              y: { field: 'y', type: 'quantitative', title: axis.label,
                   scale: { reverse: !!axis.lowerIsWorse } },
              size: { field: 'ncloc', type: 'quantitative', title: 'lignes de code', scale: { range: [20, 1200] } },
              color: { field: 'ns', type: 'nominal', title: 'namespace', scale: { scheme: 'tableau20' } },
              opacity: { condition: { param: 'ns', value: 0.8 }, value: 0.08 },
              tooltip: [
                { field: 'projet' }, { field: 'cle', title: 'clé Sonar' },
                { field: 'churn', title: 'lignes modifiées' }, { field: 'commits' },
                { field: 'y', title: axis.label, format: '.1f' },
                { field: 'ncloc' }, { field: 'gate', title: 'quality gate' },
              ],
            },
          },
          { mark: { type: 'rule', color: 'gray', strokeDash: [4, 4], clip: true },
            encoding: { x: { aggregate: 'median', field: 'churn', type: 'quantitative' } } },
          { mark: { type: 'rule', color: 'gray', strokeDash: [4, 4], clip: true },
            encoding: { y: { aggregate: 'median', field: 'y', type: 'quantitative' } } },
        ],
      };
    },
  });
}
