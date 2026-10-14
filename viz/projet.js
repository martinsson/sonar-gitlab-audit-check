/*
 * La fiche d'un projet : on choisit une clé Sonar ou un chemin GitLab, la page
 * suit les appariements sûrs (exact, derived, manuel) et montre chaque projet
 * relié dans sa propre colonne. Un Sonar pour plusieurs GitLab, ou l'inverse,
 * donne simplement plusieurs colonnes ; les suggestions sont listées à part et
 * jamais suivies. Lit les mêmes CSV que index.html, avec le chargeur de app.js.
 */
const RANK = { manuel: 4, exact: 3, derived: 2, suggestion: 1 };
const SAFE = new Set(['manuel', 'exact', 'derived']);
/** Au-delà, le groupe relié n'est plus une fiche lisible : on coupe et on le dit. */
const MAX_NODES = 12;

const Page = {
  idx: null,
  selected: null,

  start() {
    Viz.render = () => this.render();
    const input = document.getElementById('files');
    input.addEventListener('change', () => Viz.load(input.files));
    const header = document.querySelector('header');
    addEventListener('dragover', e => { e.preventDefault(); header.classList.add('dragging'); });
    addEventListener('dragleave', () => header.classList.remove('dragging'));
    addEventListener('drop', e => {
      e.preventDefault();
      header.classList.remove('dragging');
      Viz.load(e.dataTransfer.files);
    });
    for (const id of ['sonar-url', 'gitlab-url']) {
      const el = document.getElementById(id);
      el.value = store.get(id) || '';
      el.addEventListener('change', () => { store.set(id, el.value.trim()); this.renderProject(); });
    }
    document.getElementById('search').addEventListener('input', e => this.search(e.target.value));
    addEventListener('hashchange', () => this.fromHash());
    this.render();
  },

  render() {
    this.renderFiles();
    this.idx = buildIndex(Viz.data);
    this.fromHash();
  },

  fromHash() {
    const h = decodeURIComponent(location.hash.slice(1));
    this.selected = /^[sg]:/.test(h) ? h : null;
    this.renderProject();
  },

  renderFiles() {
    const box = document.getElementById('files-loaded');
    box.innerHTML = '';
    for (const k of Viz.kinds) {
      const rows = Viz.data[k.kind];
      const span = el('span', rows ? 'ok' : '', `${rows ? '✓' : '✗'} ${k.label}${rows ? ` (${rows.length})` : ''}`);
      box.append(span, document.createTextNode('   '));
    }
  },

  search(q) {
    const ul = document.getElementById('results');
    ul.innerHTML = '';
    q = q.trim().toLowerCase();
    if (q.length < 2 || !this.idx) return;
    const hits = [];
    for (const n of this.idx.nodes.values()) {
      const pos = n.text.indexOf(q);
      if (pos >= 0) hits.push({ n, score: (pos === 0 ? 0 : 1) + n.text.length / 1000 });
    }
    hits.sort((a, b) => a.score - b.score);
    for (const { n } of hits.slice(0, 30)) {
      const li = el('li');
      li.tabIndex = 0;
      li.append(el('span', 'tag', n.kind === 's' ? 'Sonar' : 'GitLab'), el('span', '', n.id));
      if (n.name && n.name !== n.id) li.append(el('span', 'note', `  ${n.name}`));
      const linked = (this.idx.adj.get(n.ref) || []).some(p => SAFE.has(p.confiance));
      li.append(el('span', 'note', linked ? '  · apparié' : '  · sans appariement'));
      const go = () => { location.hash = encodeURIComponent(n.ref); ul.innerHTML = ''; };
      li.addEventListener('click', go);
      li.addEventListener('keydown', e => { if (e.key === 'Enter') go(); });
      ul.append(li);
    }
    if (!hits.length) ul.append(el('li', 'note', 'Aucun projet ne correspond.'));
  },

  renderProject() {
    const main = document.getElementById('project');
    main.innerHTML = '';
    if (!this.idx || !this.idx.nodes.size) {
      main.append(section('Aucun fichier chargé',
        el('p', '', "Déposer au moins l'inventaire Sonar ou l'inventaire GitLab, puis chercher un projet.")));
      return;
    }
    if (!this.selected) return;
    const start = this.idx.nodes.get(this.selected);
    if (!start) {
      main.append(section('Projet introuvable', el('p', '', `${this.selected.slice(2)} n'est dans aucun fichier chargé.`)));
      return;
    }
    const group = component(this.idx, this.selected);
    const sonar = group.nodes.filter(r => r.startsWith('s:')).map(r => r.slice(2));
    const gitlab = group.nodes.filter(r => r.startsWith('g:')).map(r => r.slice(2));

    const title = el('h1', '', start.id);
    main.append(title, el('p', '', relation(sonar, gitlab, group.truncated)));
    main.append(linksSection(this.idx, group, sonar, gitlab));
    if (sonar.length) {
      main.append(sonarSection(this.idx, sonar));
      main.append(trendSection(this.idx, sonar));
    }
    if (gitlab.length) {
      main.append(activitySection(this.idx, gitlab));
      main.append(practiceSection(this.idx, gitlab));
      main.append(crossSection(this.idx, gitlab));
    }
    main.append(missingSection(sonar, gitlab));
  },
};

/** localStorage peut manquer (navigation privée) : la page marche sans. */
const store = {
  get(k) { try { return localStorage.getItem('fiche.' + k); } catch { return null; } },
  set(k, v) { try { localStorage.setItem('fiche.' + k, v); } catch { /* sans mémoire */ } },
};

// ── Index : nœuds, appariements, lignes de chaque fichier ──────────────────

function buildIndex(d) {
  const nodes = new Map();
  const node = (kind, id, name) => {
    if (!id) return;
    const ref = `${kind}:${id}`;
    const n = nodes.get(ref) || { ref, kind, id, name: '' };
    if (name && !n.name) n.name = name;
    n.text = `${id} ${n.name}`.toLowerCase();
    nodes.set(ref, n);
  };

  const sonar = new Map(), gitlab = new Map(), pratiques = new Map(), crois = new Map();
  const history = new Map(), byId = new Map();
  for (const r of d.sonar || []) { sonar.set(r.key, r); node('s', r.key, r.name); }
  for (const r of d.gitlab || []) { gitlab.set(r.path, r); byId.set(r.id, r.path); node('g', r.path, r.name); }
  for (const r of d.pratiques || []) { pratiques.set(r.path, r); if (r.id) byId.set(r.id, r.path); node('g', r.path); }
  for (const r of d.croisement || []) {
    crois.set(r.projet, r);
    node('g', r.projet);
    if (r.sq_key) node('s', r.sq_key, r.sq_name);
  }
  for (const r of d.historique || []) {
    if (!history.has(r.key)) history.set(r.key, []);
    history.get(r.key).push(r);
    node('s', r.key);
  }
  for (const r of d.etendu || []) { node('s', r.sonar_key, r.sonar_name); node('g', r.gitlab_path); }

  // Les appariements, d'où qu'ils viennent ; le plus sûr l'emporte, et un
  // « rejete » posé à la main dans appariement.csv les annule tous.
  const pairs = new Map(), rejected = new Set();
  const add = (key, path, confiance, methode, source) => {
    if (!key || !path || !RANK[confiance]) return;
    const id = `${key}\n${path}`;
    const p = pairs.get(id);
    if (!p) { pairs.set(id, { key, path, confiance, methode, sources: [source] }); return; }
    if (!p.sources.includes(source)) p.sources.push(source);
    if (RANK[confiance] > RANK[p.confiance]) Object.assign(p, { confiance, methode });
  };
  for (const r of d.appariement || []) {
    if (r.confiance === 'rejete') rejected.add(`${r.droite_key}\n${r.gauche_path}`);
    else add(r.droite_key, r.gauche_path, r.confiance, r.methode, 'appariement.csv');
  }
  for (const r of d.croisement || []) add(r.sq_key, r.projet, r.confiance, r.methode_jointure, 'croisement.csv');
  for (const r of d.etendu || []) add(r.sonar_key, r.gitlab_path, r.confiance, r.methode, 'sonar-gitlab.csv');
  for (const r of d.sonar || []) {
    const path = r.alm === 'gitlab' && byId.get(r.alm_repository);
    if (path) add(r.key, path, 'exact', 'liaison DevOps Sonar → GitLab', 'inventaire Sonar');
  }
  for (const id of rejected) pairs.delete(id);

  const adj = new Map();
  const link = (ref, p) => { if (!adj.has(ref)) adj.set(ref, []); adj.get(ref).push(p); };
  for (const p of pairs.values()) {
    node('s', p.key); node('g', p.path);
    link(`s:${p.key}`, p); link(`g:${p.path}`, p);
  }
  for (const rows of history.values()) rows.sort((a, b) => H.date(a.date) - H.date(b.date));
  return { nodes, adj, sonar, gitlab, pratiques, crois, history };
}

/** Le groupe relié au projet choisi par des appariements sûrs, plafonné. */
function component(idx, start) {
  const seen = new Set([start]), queue = [start];
  let truncated = false;
  while (queue.length) {
    const ref = queue.shift();
    for (const p of idx.adj.get(ref) || []) {
      if (!SAFE.has(p.confiance)) continue;
      for (const other of [`s:${p.key}`, `g:${p.path}`]) {
        if (seen.has(other)) continue;
        if (seen.size >= MAX_NODES) { truncated = true; continue; }
        seen.add(other); queue.push(other);
      }
    }
  }
  const nodes = [...seen];
  const pairs = [...new Set(nodes.flatMap(r => idx.adj.get(r) || []))];
  return { nodes, pairs, truncated };
}

function relation(sonar, gitlab, truncated) {
  const s = sonar.length, g = gitlab.length;
  let t;
  if (!s) t = "Projet GitLab sans projet Sonar apparié.";
  else if (!g) t = 'Projet Sonar sans projet GitLab apparié.';
  else if (s === 1 && g === 1) t = '1 projet Sonar ↔ 1 projet GitLab.';
  else t = `${s} projet(s) Sonar ↔ ${g} projet(s) GitLab : un projet par colonne dans chaque section.`;
  return truncated ? `${t} Groupe coupé à ${MAX_NODES} projets : les appariements vont plus loin.` : t;
}

// ── Sections ───────────────────────────────────────────────────────────────

function linksSection(idx, group, sonar, gitlab) {
  const safe = group.pairs.filter(p => SAFE.has(p.confiance));
  const sugg = group.pairs.filter(p => !SAFE.has(p.confiance));
  const body = [];
  const head = ['Sonar', 'GitLab', 'confiance', 'méthode', 'source'];
  const rowOf = p => [sonarLink(p.key), gitlabLink(p.path),
    el('span', `conf-${p.confiance}`, p.confiance), p.methode || '', p.sources.join(', ')];
  if (safe.length) body.push(table(head, safe.map(rowOf)));
  else body.push(el('p', '', 'Aucun appariement sûr.'));
  // Un projet seul garde quand même ses liens.
  if (!safe.length) {
    const solo = el('p');
    for (const k of sonar) solo.append('Sonar : ', sonarLink(k), '  ');
    for (const g of gitlab) solo.append('GitLab : ', gitlabLink(g), '  ');
    body.push(solo);
  }
  if (sugg.length) {
    body.push(el('h2', '', 'Suggestions à vérifier'),
      el('p', 'note', "Noms ressemblants : jamais suivies. Pour en confirmer une, écrire « manuel » dans appariement.csv."),
      table(head, sugg.map(p => {
        const r = rowOf(p);
        // La suggestion mène ailleurs : un lien vers sa fiche.
        const other = sonar.includes(p.key) ? `g:${p.path}` : `s:${p.key}`;
        r.push(ficheLink(other, 'fiche'));
        return r;
      })));
  }
  if (!document.getElementById('sonar-url').value || !document.getElementById('gitlab-url').value)
    body.push(el('p', 'note', 'Renseigner les URL Sonar et GitLab en haut pour rendre les liens cliquables.'));
  return section('Liens et appariement', ...body);
}

const SONAR_ROWS = [
  ['Dernière analyse', 'analysisDate', 'date'],
  ['Jours depuis', 'days_since_analysis', 'int'],
  ['Branche lue', 'branch'],
  ['Branche principale ?', 'is_main_branch', 'bool'],
  ['Quality gate', 'alert_status'],
  ['Lignes de code', 'ncloc', 'int'],
  ['Couverture', 'coverage', 'pct'],
  ['Couverture du nouveau code', 'new_coverage', 'pct'],
  ['Lignes à couvrir', 'lines_to_cover', 'int'],
  ['Tests', 'tests', 'int'],
  ['Tests en échec', 'test_failures', 'int'],
  ['Issues', 'violations', 'int'],
  ['Issues / kLOC', '_density', 'dec'],
  ['Bugs', 'bugs', 'int'],
  ['Vulnérabilités', 'vulnerabilities', 'int'],
  ['Code smells', 'code_smells', 'int'],
  ['Ratio de dette', 'sqale_debt_ratio', 'pct'],
  ['Duplication', 'duplicated_lines_density', 'pct'],
  ['Nouvelles lignes', 'new_lines', 'int'],
  ['Nouvelles issues', 'new_violations', 'int'],
];

function sonarSection(idx, keys) {
  const rows = keys.map(k => sonarRow(idx, k));
  const from = rows.some(r => r && r._from === 'croisement') ? ' (colonnes sq_ de croisement.csv, faute d\'inventaire Sonar)' : '';
  return section('Sonar en bref',
    el('p', 'help', "Le détail est dans Sonar ; ici, de quoi lire le reste de la fiche."),
    grid(keys.map(k => sonarLink(k)), SONAR_ROWS, rows),
    el('div', 'src', `Source : inventaire Sonar${from}.`));
}

/** La ligne Sonar d'une clé : l'inventaire, sinon les colonnes sq_ du croisement. */
function sonarRow(idx, key) {
  let r = idx.sonar.get(key);
  if (!r) {
    const c = [...idx.crois.values()].find(c => c.sq_key === key);
    if (c) {
      r = { _from: 'croisement' };
      for (const [k, v] of Object.entries(c)) if (k.startsWith('sq_')) r[k.slice(3)] = v;
    }
  }
  if (!r) return null;
  return { ...r, _density: H.density({ sq_ncloc: r.ncloc, sq_violations: r.violations,
    sq_bugs: r.bugs, sq_vulnerabilities: r.vulnerabilities, sq_code_smells: r.code_smells }) };
}

function trendSection(idx, keys) {
  const stats = keys.map(k => trend(idx.history.get(k) || []));
  const inv = keys.map(k => sonarRow(idx, k));
  const rows = [
    ['Analyses dans l\'historique', 'n', 'int'],
    ['Première / dernière', 'span'],
    ['Issues', 'violations', 'change'],
    ['Issues, pente', 'violations_slope', 'slope', '/ mois'],
    ['Lignes de code', 'ncloc', 'change'],
    ['Issues / kLOC', 'density', 'change'],
    ['Issues / kLOC, pente', 'density_slope_pct', 'slopePct', '% / mois'],
    ['Ratio de dette', 'sqale_debt_ratio', 'change'],
    ['Ratio de dette, pente', 'debt_slope_pct', 'slopePct', '% / mois'],
  ];
  const merged = stats.map((s, i) => ({ ...s,
    inv_kloc: inv[i]?.violations_kloc_pente_pct_mois, inv_debt: inv[i]?.dette_ratio_pente_pct_mois,
    inv_days: inv[i]?.tendance_jours }));
  rows.push(['Issues / kLOC, pente (fenêtre de l\'inventaire)', 'inv_kloc', 'slopePct', '% / mois'],
    ['Ratio de dette, pente (fenêtre de l\'inventaire)', 'inv_debt', 'slopePct', '% / mois'],
    ['Jours couverts par cette fenêtre', 'inv_days', 'int']);
  return section('Tendances',
    el('p', 'help', 'Ce que Sonar ne montre pas pour un projet : le sens et la vitesse. '
      + 'Une pente positive, en rouge, veut dire que ça se dégrade ; les pentes en % sont rapportées à la moyenne. '
      + 'Sans historique, ou avec une seule analyse, il n\'y a pas de tendance : la case reste vide, ce n\'est pas zéro.'),
    grid(keys.map(k => sonarLink(k)), rows, merged),
    el('div', 'src', 'Source : inventaire-historique.csv (série complète) et inventaire Sonar (pentes sur --activity-days).'));
}

/** Premier, dernier et pente (moindres carrés, par 30 jours) de chaque série. */
function trend(points) {
  const out = { n: points.length };
  if (!points.length) return out;
  out.span = `${String(points[0].date).slice(0, 10)} → ${String(points.at(-1).date).slice(0, 10)}`;
  const series = {
    violations: p => H.num(p.violations),
    ncloc: p => H.num(p.ncloc),
    density: p => { const v = H.num(p.violations), n = H.num(p.ncloc); return v !== null && n ? v / n * 1000 : null; },
    sqale_debt_ratio: p => H.num(p.sqale_debt_ratio),
  };
  for (const [name, f] of Object.entries(series)) {
    const xy = points.map(p => [H.date(p.date) / 864e5, f(p)]).filter(([x, y]) => x !== null && y !== null);
    if (!xy.length) continue;
    out[name] = { first: xy[0][1], last: xy.at(-1)[1] };
    const s = slope(xy);
    if (s === null) continue;
    const mean = xy.reduce((a, [, y]) => a + y, 0) / xy.length;
    if (name === 'violations') out.violations_slope = s * 30;
    if (name === 'density' && mean) out.density_slope_pct = s * 30 / mean * 100;
    if (name === 'sqale_debt_ratio' && mean) out.debt_slope_pct = s * 30 / mean * 100;
  }
  return out;
}

function slope(xy) {
  if (xy.length < 2) return null;
  const n = xy.length, mx = xy.reduce((a, [x]) => a + x, 0) / n, my = xy.reduce((a, [, y]) => a + y, 0) / n;
  let num = 0, den = 0;
  for (const [x, y] of xy) { num += (x - mx) * (y - my); den += (x - mx) ** 2; }
  return den ? num / den : null;
}

const ACTIVITY_ROWS = [
  ['Dernier commit', 'last_commit', 'date'],
  ['Dernière activité', 'last_activity_at', 'date'],
  ['Commits sur la fenêtre', 'commits_window', 'int'],
  ['dont bots', 'commits_bots', 'int'],
  ['Auteurs sur la fenêtre', 'authors_window', 'int'],
  ['Jours actifs', 'active_days', 'int'],
  ['Commits / semaine', 'commits_per_week', 'dec'],
  ['Commits de fusion', 'merge_commits', 'int'],
  ['Reverts', 'reverts', 'int'],
  ['Commits au total', 'total_commits', 'int'],
  ['Taille du dépôt', 'repo_size', 'bytes'],
  ['Créé le', 'created_at', 'date'],
  ['Branche par défaut', 'default_branch'],
  ['Visibilité', 'visibility'],
  ['Archivé / fork / miroir', '_flags'],
  ['Tranche d\'activité', 'bucket'],
  ['Retenu pour l\'audit des pratiques', 'selectionne', 'bool'],
  ['Pourquoi', 'motif_selection'],
  ['Exclu', 'exclu'],
];

function activitySection(idx, paths) {
  const rows = paths.map(p => {
    const r = idx.gitlab.get(p);
    if (!r) return null;
    const flags = ['archived', 'fork', 'mirror'].filter(f => H.bool(r[f]));
    return { ...r, _flags: flags.length ? flags.join(', ') : 'non' };
  });
  return section('Activité GitLab',
    el('p', 'help', 'Comment le code bouge : rien de cela n\'est dans Sonar.'),
    grid(paths.map(p => gitlabLink(p)), ACTIVITY_ROWS, rows),
    el('div', 'src', 'Source : inventaire.csv (GitLab), sur la fenêtre --since de GitlabActivityAudit.'));
}

const PRACTICE_ROWS = [
  ['Branche protégée', 'branche_protegee', 'bool'],
  ['Push direct verrouillé', 'push_verrouille', 'bool'],
  ['MR fusionnées', 'mr_fusionnees', 'int'],
  ['Délai médian de fusion', 'ttm_median_j', 'dec', 'j'],
  ['MR fusionnées par leur auteur', 'auto_merge', 'int'],
  ['Commentaires par MR', 'notes_par_mr', 'dec'],
  ['Part des MR approuvées', 'part_approuvee', 'share'],
  ['Auto-approbations', 'auto_approbation', 'int'],
  ['Pipelines', 'pipelines', 'int'],
  ['Taux de succès', 'taux_succes', 'share'],
  ['Incidents rouges', 'incidents_rouges', 'int'],
  ['Retour au vert, médiane', 'retour_au_vert_h', 'dec', 'h'],
  ['Encore rouge en fin de fenêtre', 'rouge_non_resolu', 'bool'],
  ['Environnements', 'environnements', 'int'],
  ['Déploiements', 'deploiements', 'int'],
  ['Sonar dans la CI', 'ci_sonar', 'bool'],
  ['Analyse de sécurité dans la CI', 'ci_securite', 'bool'],
  ['Clé Sonar envoyée par la CI', 'cle_sonar', 'code'],
  ['Lue dans', 'source_cle_sonar'],
  ['Clé du pom', 'cle_pom', 'code'],
  ['Lignes modifiées', 'lignes_modifiees', 'int'],
  ['Fichiers repérés', 'fichiers'],
];

function practiceSection(idx, paths) {
  const rows = paths.map(p => idx.pratiques.get(p) || null);
  const body = [el('p', 'help', 'Comment on travaille sur ce code.')];
  if (rows.every(r => !r))
    body.push(el('p', 'absent', 'Pas dans pratiques.csv : seuls les ~200 projets retenus par GitlabActivityAudit --deep y sont.'));
  else body.push(grid(paths.map(p => gitlabLink(p)), PRACTICE_ROWS, rows));
  body.push(el('div', 'src', 'Source : pratiques.csv.'));
  return section('Pratiques', ...body);
}

const CROSS_ROWS = [
  ['Issues par kLOC modifié', 'issues_par_kloc_modifie', 'dec'],
  ['Issues par commit', 'issues_par_commit', 'dec'],
  ['Clé Sonar retenue', 'sq_key', 'code'],
  ['Méthode', 'methode_jointure'],
];

function crossSection(idx, paths) {
  const rows = paths.map(p => idx.crois.get(p) || null);
  const body = [el('p', 'help', 'La dette rapportée au travail fait : seul le croisement des deux côtés la donne.')];
  if (rows.every(r => !r)) body.push(el('p', 'absent', 'Pas dans croisement.csv.'));
  else body.push(grid(paths.map(p => gitlabLink(p)), CROSS_ROWS, rows));
  body.push(el('div', 'src', 'Source : croisement.csv.'));
  return section('Sonar × GitLab', ...body);
}

function missingSection(sonar, gitlab) {
  const d = Viz.data, items = [];
  const need = (kind, why) => { if (!d[kind]) items.push(`${Viz.kinds.find(k => k.kind === kind).label} — ${why}`); };
  if (sonar.length) { need('sonar', 'état Sonar complet, pentes de l\'inventaire'); need('historique', 'tendances sur toute la série'); }
  if (gitlab.length) { need('gitlab', 'activité GitLab'); need('pratiques', 'pratiques'); need('croisement', 'indicateurs croisés'); }
  need('etendu', 'appariements hors des projets retenus par l\'audit GitLab');
  items.push('Couverture dans le temps — l\'historique Sonar ne relève que violations, ncloc et sqale_debt_ratio.');
  const ul = el('ul');
  for (const t of items) ul.append(el('li', '', t));
  return section('Ce qui manque sur cette fiche', ul);
}

// ── Petits outils ──────────────────────────────────────────────────────────

function el(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text !== undefined) e.textContent = text;
  return e;
}

function section(title, ...children) {
  const s = el('section');
  s.append(el('h2', '', title), ...children);
  return s;
}

function table(head, rows) {
  const t = el('table'), tr = el('tr');
  for (const h of head) tr.append(el('th', '', h));
  t.append(tr);
  for (const r of rows) {
    const row = el('tr');
    for (const c of r) { const td = el('td'); td.append(c instanceof Node ? c : document.createTextNode(c ?? '')); row.append(td); }
    t.append(row);
  }
  return t;
}

/** Une ligne par mesure, une colonne par projet. Absent n'est jamais zéro. */
function grid(heads, spec, rows) {
  const t = el('table'), tr = el('tr');
  tr.append(el('th'));
  for (const h of heads) { const th = el('th'); th.append(h); tr.append(th); }
  t.append(tr);
  for (const [label, field, fmt, unit] of spec) {
    const row = el('tr');
    row.append(el('td', 'label', label));
    for (const r of rows) {
      const td = el('td', 'v');
      td.append(r ? cell(r[field], fmt, unit) : el('span', 'absent', 'absent'));
      row.append(td);
    }
    t.append(row);
  }
  return t;
}

const nf = (d) => new Intl.NumberFormat('fr-CH', { maximumFractionDigits: d });

function cell(v, fmt, unit) {
  const absent = () => el('span', 'absent', 'absent');
  if (v === undefined || v === null || v === '') return absent();
  const u = unit ? ` ${unit}` : '';
  switch (fmt) {
    case 'date': return document.createTextNode(String(v).slice(0, 10));
    case 'bool': return document.createTextNode(H.bool(v) ? 'oui' : (['false', 'non', '0', 'N'].includes(String(v).trim()) ? 'non' : String(v)));
    case 'code': return el('code', '', v);
    case 'int': case 'dec': case 'pct': case 'share': case 'bytes': {
      const n = H.num(v);
      if (n === null) return document.createTextNode(String(v));
      if (fmt === 'pct') return document.createTextNode(`${nf(1).format(n)} %`);
      if (fmt === 'share') return document.createTextNode(`${nf(0).format(n * 100)} %`);
      if (fmt === 'bytes') return document.createTextNode(`${nf(1).format(n / 1048576)} Mo`);
      return document.createTextNode(nf(fmt === 'int' ? 0 : 2).format(n) + u);
    }
    case 'change': {
      if (typeof v !== 'object') return absent();
      const s = el('span');
      s.textContent = `${nf(2).format(v.first)} → ${nf(2).format(v.last)}`;
      return s;
    }
    case 'slope': case 'slopePct': {
      const n = H.num(v);
      if (n === null) return absent();
      // Le signe et la couleur suivent ce qu'on affiche : -0,001 arrondi n'est pas une baisse.
      const digits = fmt === 'slope' ? 1 : 2, r = Number(n.toFixed(digits));
      return el('span', r > 0 ? 'up' : r < 0 ? 'down' : '', `${r > 0 ? '+' : ''}${nf(digits).format(r || 0)}${u}`);
    }
    default: return document.createTextNode(String(v) + u);
  }
}

function sonarLink(key) {
  const base = document.getElementById('sonar-url').value.replace(/\/+$/, '');
  const span = el('span');
  span.append(base ? Object.assign(el('a', '', key), { href: `${base}/dashboard?id=${encodeURIComponent(key)}`, target: '_blank', rel: 'noopener' }) : el('code', '', key));
  span.append(' ', ficheLink(`s:${key}`, '↗'));
  return span;
}

function gitlabLink(path) {
  const base = document.getElementById('gitlab-url').value.replace(/\/+$/, '');
  const span = el('span');
  span.append(base ? Object.assign(el('a', '', path), { href: `${base}/${path}`, target: '_blank', rel: 'noopener' }) : el('code', '', path));
  span.append(' ', ficheLink(`g:${path}`, '↗'));
  return span;
}

/** Lien vers la fiche d'un autre projet, dans cette page. */
function ficheLink(ref, text) {
  const a = el('a', 'note', text);
  a.href = '#' + encodeURIComponent(ref);
  a.title = 'Ouvrir sa fiche';
  return a;
}
