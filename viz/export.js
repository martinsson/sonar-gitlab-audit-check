/*
 * Une page autonome à envoyer : les graphiques déjà calculés, leurs données,
 * et les bibliothèques, dans un seul fichier HTML qui s'ouvre hors ligne.
 *
 * On emporte les specs rendues, pas les CSV ni le code des graphiques : le
 * destinataire reçoit ce qui est tracé et rien de plus, et la page n'a pas
 * besoin de relire des fichiers — ce qu'un navigateur refuse sous file://.
 */
Viz.export = async function () {
  const shown = this.charts.filter(c => this.specs[c.id]);
  if (!shown.length) { alertLike('Aucun graphique à exporter : charger d\'abord les CSV.'); return; }

  // Les bibliothèques, recopiées depuis jsDelivr. Sans réseau, la page exportée
  // les appellera en ligne, comme celle-ci.
  const libs = [...document.querySelectorAll('script[src^="https://"]')]
    .map(s => s.src).filter(src => !/papaparse/.test(src));
  const scripts = await Promise.all(libs.map(async src => {
    try {
      const r = await fetch(src);
      if (!r.ok) throw new Error(r.status);
      return `<script>${(await r.text()).replace(/<\/script/gi, '<\\/script')}</script>`;
    } catch {
      return `<script src="${src}"></script>`;
    }
  }));

  const date = new Date().toISOString().slice(0, 10);
  const files = this.kinds.filter(k => this.data[k.kind]).map(k => this.data[k.kind].file).join(', ');
  const payload = shown.map(c => ({ id: c.id, title: c.title, help: c.help || '', spec: this.specs[c.id] }));
  const json = JSON.stringify(payload).replace(/</g, '\\u003c');
  const css = document.querySelector('style').textContent;

  const html = `<!doctype html>
<html lang="fr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Audit Sonar × GitLab — ${date}</title>
<style>${css}</style>
${scripts.join('\n')}
</head>
<body>
<header>
  <h1>Audit Sonar × GitLab</h1>
  <p>Exporté le ${date}, depuis : ${escapeHtml(files)}.
     Namespaces regroupés sur ${this.opts.depth} niveau(x).</p>
</header>
<main id="charts"></main>
<script>
const CHARTS = ${json};
const dark = matchMedia('(prefers-color-scheme: dark)').matches;
for (const c of CHARTS) {
  const s = document.createElement('section');
  s.innerHTML = '<h2></h2><div class="help"></div><div class="plot"></div>';
  s.querySelector('h2').textContent = c.title;
  s.querySelector('.help').textContent = c.help;
  document.getElementById('charts').append(s);
  vegaEmbed(s.querySelector('.plot'), c.spec,
    { theme: dark ? 'dark' : undefined, actions: { export: true, source: false, editor: false, compiled: false } });
}
</script>
</body>
</html>
`;
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([html], { type: 'text/html' }));
  a.download = `audit-sonar-gitlab-${date}.html`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
};

function escapeHtml(s) {
  return String(s).replace(/[&<>"]/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[ch]));
}

/** Pas d'alert() : un message dans l'en-tête, qui ne bloque pas la page. */
function alertLike(text) {
  let p = document.getElementById('export-message');
  if (!p) {
    p = document.createElement('p');
    p.id = 'export-message';
    document.getElementById('export').after(p);
  }
  p.textContent = text;
}

document.getElementById('export').addEventListener('click', () => Viz.export());
