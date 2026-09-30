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
        + 'Lignes grises = médianes. Taille = lignes de code. ▲ = au-delà du haut de l\'échelle, posé sur le bord. '
        + 'Molette = zoom vertical, glisser = déplacer, double-clic = revenir. '
        + 'Couleur = les 10 namespaces les plus représentés ; clic sur la légende = un seul namespace.',
    needs: ['croisement'],
    spec(d, h) {
      const values = d.croisement.filter(h.paired).map(r => ({
        projet: r.projet, cle: r.sq_key, ns: h.ns(r.projet), namespace: h.ns(r.projet),
        churn: h.num(r.gl_lignes_modifiees), y: axis.value(r, h),
        ncloc: h.num(r.sq_ncloc) || 0, commits: h.num(r.gl_commits_window),
        gate: r.sq_alert_status || '—',
      })).filter(v => v.churn > 0 && v.y !== null);
      if (!values.length) return `Aucun projet apparié n'a de valeur pour « ${axis.label} » dans croisement.csv.`;
      const domain = h.top(values, 'ns');
      // Les pourcentages ont déjà une échelle naturelle ; les densités ont des
      // valeurs extrêmes qui écraseraient le reste.
      const cap = axis.lowerIsWorse ? null : h.cap(values.map(v => v.y));
      values.forEach(v => { v.hors = cap !== null && v.y > cap; });
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
            mark: { type: 'point', filled: true, stroke: 'white', strokeWidth: 0.5, clip: true },
            encoding: {
              x: { field: 'churn', type: 'quantitative', scale: { type: 'log' },
                   axis: h.logAxis('lignes modifiées sur la fenêtre GitLab') },
              y: { field: 'y', type: 'quantitative', title: axis.label,
                   scale: { reverse: !!axis.lowerIsWorse, clamp: true, ...(cap !== null ? { domain: [0, cap] } : {}) } },
              size: { field: 'ncloc', type: 'quantitative', title: 'lignes de code', scale: { range: [25, 1200] } },
              shape: { field: 'hors', type: 'nominal', legend: null,
                       scale: { domain: [false, true], range: ['circle', 'triangle-up'] } },
              color: { field: 'ns', type: 'nominal', title: 'namespace', scale: h.colors(domain),
                       legend: { labelLimit: 260 } },
              opacity: { condition: { param: 'ns', value: 0.8 }, value: 0.08 },
              tooltip: [
                { field: 'projet' }, { field: 'namespace' }, { field: 'cle', title: 'clé Sonar' },
                { field: 'churn', title: 'lignes modifiées', format: ',' }, { field: 'commits' },
                { field: 'y', title: axis.label, format: '.1f' },
                { field: 'ncloc', title: 'lignes de code', format: ',' }, { field: 'gate', title: 'quality gate' },
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
