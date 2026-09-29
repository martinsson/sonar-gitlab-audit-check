/*
 * Le socle : lire les CSV, les reconnaître à leur en-tête, et appeler chaque
 * graphique déclaré par Viz.add(). Un graphique ne touche jamais au DOM : il
 * rend une spec Vega ou Vega-Lite, vega-embed s'occupe du reste.
 */
const Viz = {
  charts: [],
  data: {},
  opts: { depth: 2 },

  /**
   * { id, title, help, needs: ['croisement', …], spec: (data, h) => spec }
   * `needs` liste les fichiers sans lesquels le graphique n'a pas de sens.
   */
  add(chart) { this.charts.push(chart); },

  // Chaque fichier est reconnu par des colonnes qui lui sont propres, pas par
  // son nom : les deux inventaires s'appellent inventaire.csv.
  kinds: [
    { kind: 'croisement', label: 'croisement.csv (CrossAudit)', has: ['projet', 'confiance'] },
    { kind: 'appariement', label: 'appariement.csv', has: ['gauche_path', 'droite_key'] },
    { kind: 'pratiques', label: 'pratiques.csv (GitLab --deep)', has: ['path', 'ci_sonar'] },
    { kind: 'gitlab', label: 'inventaire.csv GitLab', has: ['path', 'selectionne'] },
    { kind: 'historique', label: 'historique Sonar', has: ['key', 'date', 'violations'] },
    { kind: 'sonar', label: 'inventaire.csv Sonar', has: ['key', 'analysisDate'] },
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
    const list = document.getElementById('loaded');
    list.innerHTML = '';
    for (const k of this.kinds) {
      const rows = this.data[k.kind];
      if (!rows) continue;
      const li = document.createElement('li');
      li.textContent = `${k.label} : ${rows.file}, ${rows.length} lignes`;
      list.append(li);
    }
    this.render();
  },

  render() {
    const main = document.getElementById('charts');
    main.innerHTML = '';
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
        plot.textContent = 'Il manque : ' + missing.join(', ');
        continue;
      }
      try {
        const spec = c.spec(this.data, H);
        if (!spec) { plot.className = 'missing'; plot.textContent = 'Aucune donnée à tracer.'; continue; }
        vegaEmbed(plot, spec, {
          theme: matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : undefined,
          actions: { export: true, source: true, editor: true, compiled: false } })
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
