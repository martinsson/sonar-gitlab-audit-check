/*
 * Le socle : lire les CSV, les reconnaître à leur en-tête, et appeler chaque
 * graphique déclaré par Viz.add(). Un graphique ne touche jamais au DOM : il
 * rend une spec Vega ou Vega-Lite, vega-embed s'occupe du reste.
 */
const Viz = {
  charts: [],
  data: {},
  /** Les vues Vega rendues, par id : pour les inspecter depuis la console. */
  views: {},
  /** Les specs rendues, avec leurs données : ce que l'export emporte. */
  specs: {},
  opts: { depth: 2 },

  /**
   * { id, title, help, needs: ['croisement', …], spec: (data, h) => spec }
   * `needs` liste les fichiers sans lesquels le graphique n'a pas de sens,
   * `uses` ceux qu'il lit en plus s'ils sont là.
   */
  add(chart) { this.charts.push(chart); },

  // Chaque fichier est reconnu par des colonnes qui lui sont propres, pas par
  // son nom : les deux inventaires s'appellent inventaire.csv.
  kinds: [
    { kind: 'gitlab', label: 'inventaire.csv (GitLab)', has: ['path', 'selectionne'],
      cmd: 'GitlabActivityAudit --out-dir ./audit' },
    { kind: 'pratiques', label: 'pratiques.csv', has: ['path', 'ci_sonar'],
      cmd: 'GitlabActivityAudit --deep --out-dir ./audit' },
    { kind: 'croisement', label: 'croisement.csv', has: ['projet', 'confiance'],
      cmd: 'CrossAudit --out ./audit/croisement.csv' },
    { kind: 'historique', label: 'inventaire-historique.csv (Sonar)', has: ['key', 'date', 'violations'],
      cmd: 'SonarAuditCheck --csv inventaire.csv' },
    { kind: 'sonar', label: 'inventaire.csv (Sonar)', has: ['key', 'analysisDate'],
      cmd: 'SonarAuditCheck --csv inventaire.csv' },
    { kind: 'appariement', label: 'appariement.csv', has: ['gauche_path', 'droite_key'],
      cmd: 'CrossAudit' },
  ],

  start() {
    const input = document.getElementById('files');
    input.addEventListener('change', () => this.load(input.files));
    document.getElementById('depth').addEventListener('change', e => {
      this.opts.depth = +e.target.value;
      this.render();
    });
    const header = document.querySelector('header');
    addEventListener('dragover', e => { e.preventDefault(); header.classList.add('dragging'); });
    addEventListener('dragleave', () => header.classList.remove('dragging'));
    addEventListener('drop', e => {
      e.preventDefault();
      header.classList.remove('dragging');
      this.load(e.dataTransfer.files);
    });
    this.render();
  },

  async load(files) {
    for (const f of files) {
      const text = (await f.text()).replace(/^﻿/, '');
      const parsed = Papa.parse(text, { header: true, skipEmptyLines: true });
      const cols = parsed.meta.fields || [];
      const k = this.kinds.find(k => k.has.every(c => cols.includes(c)));
      if (!k) { console.warn('CSV non reconnu :', f.name, cols); continue; }
      this.data[k.kind] = parsed.data;
      this.data[k.kind].file = f.name;
    }
    this.render();
  },

  /** Les fichiers que les graphiques déclarés lisent, chargés ou non. */
  renderFiles() {
    const used = new Set(this.charts.flatMap(c => [...(c.needs || []), ...(c.uses || [])]));
    const tbody = document.querySelector('#files-needed tbody');
    tbody.innerHTML = '';
    for (const k of this.kinds.filter(k => used.has(k.kind))) {
      const rows = this.data[k.kind];
      const charts = this.charts.filter(c => (c.needs || []).includes(k.kind)).map(c => c.title);
      const optional = this.charts.filter(c => (c.uses || []).includes(k.kind)).map(c => c.title);
      const tr = document.createElement('tr');
      tr.className = rows ? 'ok' : 'absent';
      [rows ? `✓ ${rows.file} (${rows.length} lignes)` : '✗ manquant', k.label, k.cmd,
       [...charts, ...optional.map(t => t + ' (en plus)')].join(', ')]
        .forEach((text, i) => {
          const td = document.createElement('td');
          td.textContent = text;
          if (i === 2) td.className = 'cmd';
          tr.append(td);
        });
      tbody.append(tr);
    }
  },

  render() {
    this.renderFiles();
    const main = document.getElementById('charts');
    main.innerHTML = '';
    this.specs = {};
    for (const c of this.charts) {
      const s = document.createElement('section');
      s.id = c.id;
      s.innerHTML = `<h2></h2><div class="help"></div><div class="plot"></div>`;
      s.querySelector('h2').textContent = c.title;
      s.querySelector('.help').textContent = c.help || '';
      main.append(s);
      const plot = s.querySelector('.plot');
      const missing = (c.needs || []).filter(n => !this.data[n]);
      if (missing.length) {
        plot.className = 'missing';
        plot.textContent = 'Il manque : '
          + missing.map(n => this.kinds.find(k => k.kind === n)?.label || n).join(', ');
        continue;
      }
      try {
        const spec = c.spec(this.data, H);
        if (!spec) { plot.className = 'missing'; plot.textContent = 'Aucune donnée à tracer.'; continue; }
        this.specs[c.id] = spec;
        vegaEmbed(plot, spec, {
          theme: matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : undefined,
          actions: { export: true, source: true, editor: true, compiled: false } })
          .then(res => { this.views[c.id] = res.view; })
          .catch(e => this.fail(plot, e));
      } catch (e) {
        this.fail(plot, e);
      }
    }
  },

  fail(plot, e) {
    console.error(e);
    plot.className = 'error';
    plot.textContent = String(e && e.stack || e);
  },
};

/** Petits outils partagés par les graphiques. */
const H = {
  /** Nombre, ou null pour une cellule vide : absent n'est pas zéro. */
  num(v) {
    if (v === undefined || v === null || v === '') return null;
    const n = Number(String(v).replace(',', '.'));
    return Number.isFinite(n) ? n : null;
  },
  bool(v) { return ['true', 'oui', '1', 'O'].includes(String(v).trim()); },
  /** Le namespace d'un chemin GitLab, tronqué à la profondeur choisie. */
  ns(path) {
    const parts = String(path || '').split('/');
    parts.pop();
    return parts.slice(0, Viz.opts.depth).join('/') || '(racine)';
  },
  /** Appariement sûr : ce que les vues de Vues.java retiennent aussi. */
  paired(row) { return ['exact', 'derived', 'manuel'].includes(row.confiance); },
  /** Issues par millier de lignes, depuis les colonnes sq_ de croisement.csv. */
  density(row) {
    const ncloc = H.num(row.sq_ncloc);
    if (!ncloc) return null;
    let v = H.num(row.sq_violations);
    if (v === null) {
      const parts = [row.sq_bugs, row.sq_vulnerabilities, row.sq_code_smells].map(H.num);
      if (parts.every(p => p === null)) return null;
      v = parts.reduce((a, b) => a + (b || 0), 0);
    }
    return v / ncloc * 1000;
  },
  /** Dates Sonar (« 2026-08-30T10:00:00+0200 ») en millisecondes. */
  date(v) {
    const t = Date.parse(String(v).replace(/([+-]\d\d)(\d\d)$/, '$1:$2'));
    return Number.isFinite(t) ? t : null;
  },
};
