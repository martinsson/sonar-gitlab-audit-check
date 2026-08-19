const pptxgen = require("pptxgenjs");

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE"; // 13.333 x 7.5
pres.author = "Vision pitch";
pres.title = "What good development looks like for us";

// ---------------------------------------------------------------- palette
const INK = "17242B";       // deep slate — dark backgrounds, headings
const SLATE = "3D5766";     // body text on light
const MUTED = "7C8D97";     // captions
const MUTED_D = "9FB4BF";   // captions on dark
const TINT = "EDF1F3";      // card fill on light
const TINT2 = "E1E7EA";     // stronger tint
const ACCENT = "C1512E";    // burnt orange — attention / mandatory / us
const ACCENT_T = "F3E4DE";  // accent tint
const GREEN = "2F7D6B";     // teal — feasible / positive
const WHITE = "FFFFFF";

const HEAD = "Cambria";
const BODY = "Calibri";

const W = 13.333, H = 7.5;
const M = 0.9;              // left margin

// ---------------------------------------------------------------- helpers
function darkSlide() {
  const s = pres.addSlide();
  s.background = { color: INK };
  return s;
}
function lightSlide() {
  const s = pres.addSlide();
  s.background = { color: WHITE };
  return s;
}

// small accent ring used as the recurring motif
function ring(slide, x, y, d, color, wdt, transparency) {
  const o = { x, y, w: d, h: d, fill: { type: "none" }, line: { color, width: wdt || 1.25 } };
  if (transparency !== undefined) o.line.transparency = transparency;
  slide.addShape(pres.ShapeType.ellipse, o);
}

function kicker(slide, text, color, x, y) {
  slide.addText(text, {
    x: x === undefined ? M : x, y: y === undefined ? 0.52 : y, w: 9, h: 0.3,
    fontFace: BODY, fontSize: 11.5, bold: true, color: color || ACCENT,
    charSpacing: 2.2, valign: "top", margin: 0,
  });
}

function title(slide, text, color, y) {
  slide.addText(text, {
    x: M, y: y === undefined ? 0.88 : y, w: W - 2 * M, h: 0.75,
    fontFace: HEAD, fontSize: 34, bold: true, color: color || INK, valign: "top", margin: 0,
    valign: "top",
  });
}

function footer(slide, n) {
  ring(slide, W - 1.02, H - 0.62, 0.15, MUTED, 1);
  slide.addText(String(n), {
    x: W - 0.82, y: H - 0.68, w: 0.4, h: 0.28,
    fontFace: BODY, fontSize: 10, color: MUTED, align: "left", valign: "top", margin: 0,
  });
}
function footerDark(slide, n) {
  ring(slide, W - 1.02, H - 0.62, 0.15, ACCENT, 1);
  slide.addText(String(n), {
    x: W - 0.82, y: H - 0.68, w: 0.4, h: 0.28,
    fontFace: BODY, fontSize: 10, color: MUTED_D, align: "left", valign: "top", margin: 0,
  });
}

// a straight line/arrow between two arbitrary points
function arrowLine(slide, x1, y1, x2, y2, o) {
  o = o || {};
  let sx = x1, sy = y1, ex = x2, ey = y2, swapped = false;
  if (ex < sx) { const tx = sx, ty = sy; sx = ex; sy = ey; ex = tx; ey = ty; swapped = true; }
  const x = sx, y = Math.min(sy, ey), w = ex - sx, h = Math.abs(ey - sy);
  const flipV = ey < sy;
  const line = { color: o.color || SLATE, width: o.width || 1.5 };
  if (o.dash) line.dashType = o.dash;
  if (o.head !== false) {
    if (o.both) { line.beginArrowType = "triangle"; line.endArrowType = "triangle"; }
    else if (swapped) line.beginArrowType = "triangle";
    else line.endArrowType = "triangle";
  }
  slide.addShape(pres.ShapeType.line, { x, y, w, h, flipV, line });
}

function card(slide, x, y, w, h, fill, opts) {
  opts = opts || {};
  const o = { x, y, w, h, fill: { color: fill }, rectRadius: 0.06 };
  if (opts.line) o.line = opts.line;
  slide.addShape(pres.ShapeType.roundRect, o);
}

// =================================================================== 1 title
(function () {
  const s = darkSlide();

  // motif: three overlapping faint rings, echoing the loops of slide 9
  ring(s, 9.05, 1.55, 2.55, ACCENT, 1.5, 45);
  ring(s, 10.25, 3.05, 2.55, GREEN, 1.5, 45);
  ring(s, 8.15, 3.35, 2.55, MUTED_D, 1.5, 55);

  kicker(s, "A SHARED PICTURE — NOT A PLAN", ACCENT, M, 1.75);
  s.addText("What good development\nlooks like for us.", {
    x: M, y: 2.25, w: 7.4, h: 1.9,
    fontFace: HEAD, fontSize: 42, bold: true, color: WHITE, lineSpacing: 48, valign: "top", margin: 0,
  });
  s.addText(
    "A reference point we agree on now, so that later disagreements about tactics have something to be measured against.",
    { x: M, y: 4.5, w: 6.6, h: 1.0, fontFace: BODY, fontSize: 15.5, color: MUTED_D, lineSpacing: 24, valign: "top", margin: 0 }
  );
  footerDark(s, 1);
  s.addNotes(
    "Framing, said out loud: this deck is not a proposal for a project or a reorganisation. " +
    "It is an attempt to agree on what good looks like. If we agree here, then when we disagree later about a " +
    "specific tactic, we have a shared reference point to measure that disagreement against.\n\n" +
    "Tone for the whole deck: motivate by opportunity, not fear. Do not overclaim. Repeat the central message " +
    "three times at increasing resolution — one line up front (slide 3), implicitly through the middle, explicit at the close (slide 9)."
  );
})();

// =================================================================== 2 problem
(function () {
  const s = lightSlide();
  kicker(s, "THE PROBLEM");
  title(s, "Long cycles, late feedback.");

  s.addText(
    [
      { text: "We are not flying blind. ", options: { bold: true, color: INK } },
      { text: "We do get signal — from demos, from testers, from QA.", options: {} },
    ],
    { x: M, y: 2.25, w: 5.3, h: 0.8, fontFace: BODY, fontSize: 16, color: SLATE, lineSpacing: 24, margin: 0 }
  );
  s.addText(
    "But it is thin, and it arrives after the big decisions have already been locked. By the time we learn something, " +
    "the cost of acting on it is at its highest.",
    { x: M, y: 3.1, w: 5.3, h: 1.2, fontFace: BODY, fontSize: 16, color: SLATE, lineSpacing: 24, valign: "top", margin: 0 }
  );

  card(s, M, 5.05, W - 2 * M, 1.35, TINT);
  s.addText(
    [
      { text: "Not ", options: { bold: true, color: INK } },
      { text: "“we learn nothing.”", options: { italic: true } },
      { text: "     Rather: ", options: { bold: true, color: INK } },
      { text: "“we learn slowly, from a weak signal.”", options: { italic: true, color: ACCENT, bold: true } },
    ],
    { x: M + 0.45, y: 5.05, w: W - 2 * M - 0.9, h: 1.35, fontFace: BODY, fontSize: 17, color: SLATE, valign: "middle", margin: 0 }
  );

  // ---- diagram: one long cycle, feedback bunched at the far end
  const dx0 = 6.9, dx1 = 12.5, ty = 3.35;
  s.addText("ONE CYCLE", { x: dx0, y: 2.22, w: 3, h: 0.25, fontFace: BODY, fontSize: 10.5, bold: true, color: MUTED, charSpacing: 1.6, valign: "top", margin: 0 });

  // the long build stretch
  s.addShape(pres.ShapeType.rect, { x: dx0, y: ty, w: dx1 - dx0, h: 0.42, fill: { color: TINT2 } });
  s.addText("months of building", {
    x: dx0, y: ty, w: dx1 - dx0, h: 0.42, fontFace: BODY, fontSize: 11, color: SLATE, align: "center", valign: "middle", margin: 0,
  });

  // decisions locked, at the start
  s.addShape(pres.ShapeType.rect, { x: dx0, y: ty - 0.02, w: 0.055, h: 0.46, fill: { color: ACCENT } });
  s.addText("decisions\nlocked", {
    x: dx0 - 0.08, y: ty - 0.75, w: 1.5, h: 0.65, fontFace: BODY, fontSize: 10.5, bold: true, color: ACCENT, lineSpacing: 13, valign: "top", margin: 0,
  });

  // thin signal, bunched at the end
  const marks = ["demo", "testers", "QA"];
  marks.forEach(function (m, i) {
    const mx = dx1 - 1.55 + i * 0.55;
    s.addShape(pres.ShapeType.ellipse, { x: mx, y: ty + 0.12, w: 0.18, h: 0.18, fill: { color: GREEN } });
  });
  s.addText("thin signal, all at the end", {
    x: dx1 - 2.55, y: ty - 0.42, w: 2.6, h: 0.3, fontFace: BODY, fontSize: 10.5, bold: true, color: GREEN, align: "right", valign: "top", margin: 0,
  });
  s.addText("demo · testers · QA", {
    x: dx1 - 2.55, y: ty + 0.5, w: 2.6, h: 0.3, fontFace: BODY, fontSize: 10.5, color: MUTED, align: "right", valign: "top", margin: 0,
  });

  // the long way back
  arrowLine(s, dx1 - 0.2, ty + 1.05, dx0 + 0.1, ty + 1.05, { color: MUTED, width: 1.5, dash: "dash" });
  s.addText("…and everything we learned only helps the next one", {
    x: dx0, y: ty + 1.2, w: dx1 - dx0, h: 0.3, fontFace: BODY, fontSize: 11, italic: true, color: MUTED, align: "center", valign: "top", margin: 0,
  });

  footer(s, 2);
  s.addNotes(
    "Be precise here, because overclaiming costs credibility: the signal is thin and late, not absent. " +
    "Everyone in the room can name a demo that taught us something — so do not say we learn nothing. " +
    "The problem is the latency and the strength of the signal, and that it arrives after the decisions it should have informed."
  );
})();

// =================================================================== 3 principle
(function () {
  const s = darkSlide();
  kicker(s, "THE ORGANISING PRINCIPLE", ACCENT, M, 2.45);
  s.addText("Shorten the loop.", {
    x: M, y: 2.95, w: 11, h: 1.4, fontFace: HEAD, fontSize: 66, bold: true, color: WHITE, valign: "top", margin: 0,
  });
  s.addText("Everything that follows is a way of doing that.", {
    x: M, y: 4.45, w: 8, h: 0.5, fontFace: BODY, fontSize: 16.5, color: MUTED_D, valign: "top", margin: 0,
  });
  ring(s, 11.15, 2.9, 1.5, ACCENT, 1.75, 30);
  footerDark(s, 3);
  s.addNotes("First of three passes at the central message. One line, nothing else on the slide. Pause here.");
})();

// =================================================================== 4 coupling
(function () {
  const s = lightSlide();
  kicker(s, "THE COUPLING");
  title(s, "Technical quality and product iteration require each other.");

  const cy = 2.35, ch = 1.9;
  card(s, M, cy, 4.5, ch, TINT);
  s.addText("Technical quality", { x: M + 0.35, y: cy + 0.3, w: 3.8, h: 0.4, fontFace: HEAD, fontSize: 21, bold: true, color: INK, margin: 0 });
  s.addText("Change stays cheap and safe to make, for as long as the product is alive.", {
    x: M + 0.35, y: cy + 0.82, w: 3.8, h: 0.85, fontFace: BODY, fontSize: 13.5, color: SLATE, lineSpacing: 19, valign: "top", margin: 0,
  });

  const bx = 7.93;
  card(s, bx, cy, 4.5, ch, TINT);
  s.addText("Product iteration", { x: bx + 0.35, y: cy + 0.3, w: 3.8, h: 0.4, fontFace: HEAD, fontSize: 21, bold: true, color: INK, valign: "top", margin: 0 });
  s.addText("Small steps, put in front of real users, often enough to learn from.", {
    x: bx + 0.35, y: cy + 0.82, w: 3.8, h: 0.85, fontFace: BODY, fontSize: 13.5, color: SLATE, lineSpacing: 19, valign: "top", margin: 0,
  });

  // two arrows, one each way
  arrowLine(s, 5.62, cy + 0.55, 7.78, cy + 0.55, { color: ACCENT, width: 2 });
  s.addText("makes iteration\npossible", {
    x: 5.45, y: cy + 0.05, w: 2.5, h: 0.5, fontFace: BODY, fontSize: 10.5, italic: true, color: ACCENT, align: "center", lineSpacing: 13, valign: "top", margin: 0,
  });
  arrowLine(s, 7.78, cy + 1.35, 5.62, cy + 1.35, { color: GREEN, width: 2 });
  s.addText("makes quality worth\npaying for", {
    x: 5.45, y: cy + 1.45, w: 2.5, h: 0.5, fontFace: BODY, fontSize: 10.5, italic: true, color: GREEN, align: "center", lineSpacing: 13, valign: "top", margin: 0,
  });

  card(s, M, 4.95, W - 2 * M, 1.3, ACCENT_T);
  s.addText(
    [
      { text: "Yes — this crosses the departmental line.", options: { bold: true, color: INK } },
      { text: "  Saying it now, before anyone has to notice it. It is not a claim on anyone's territory: the loop simply does not close if only one half of it moves.", options: {} },
    ],
    { x: M + 0.45, y: 4.95, w: W - 2 * M - 0.9, h: 1.3, fontFace: BODY, fontSize: 14.5, color: SLATE, lineSpacing: 22, valign: "middle", margin: 0 }
  );

  footer(s, 4);
  s.addNotes(
    "This slide is the answer to “this isn't your remit”, and it only works if it is stated here, early — " +
    "before the product-side material on slide 6, not after someone objects to it.\n\n" +
    "Quality makes iteration possible; iteration makes quality worth paying for. Neither half pays off alone: " +
    "quality without iteration is craft with no feedback, iteration without quality stalls as soon as change gets expensive."
  );
})();

// =================================================================== 5 delivery
(function () {
  const s = lightSlide();
  kicker(s, "DELIVERY MECHANICS");
  title(s, "Capabilities, not a checklist of practices.");

  s.addText("What DORA measures is a set of capabilities. How a team gets to each one is theirs to work out.", {
    x: M, y: 1.7, w: 11.4, h: 0.4, fontFace: BODY, fontSize: 14.5, color: MUTED, valign: "top", margin: 0,
  });

  const items = [
    ["Version control and CI", "On everything — code, configuration, infrastructure."],
    ["Automated testing you trust", "Trusted enough that a green build is a decision, not a suggestion."],
    ["Loosely coupled architecture", "Teams change and deploy their part without a queue."],
    ["Small batches, deployed continuously", "Smaller change, smaller blast radius, faster answer."],
  ];
  const cw = 5.45, chh = 1.22;
  items.forEach(function (it, i) {
    const col = i % 2, row = Math.floor(i / 2);
    const x = M + col * (cw + 0.6), y = 2.32 + row * (chh + 0.28);
    card(s, x, y, cw, chh, TINT);
    ring(s, x + 0.34, y + 0.34, 0.26, ACCENT, 1.5);
    s.addText(it[0], { x: x + 0.82, y: y + 0.2, w: cw - 1.1, h: 0.32, fontFace: BODY, fontSize: 14.5, bold: true, color: INK, valign: "top", margin: 0 });
    s.addText(it[1], { x: x + 0.82, y: y + 0.56, w: cw - 1.1, h: 0.55, fontFace: BODY, fontSize: 12.5, color: SLATE, lineSpacing: 16, margin: 0 });
  });

  card(s, M, 5.32, W - 2 * M, 1.28, WHITE, { line: { color: ACCENT, width: 1.25, dashType: "dash" } });
  s.addText(
    [
      { text: "The honest caveat.  ", options: { bold: true, color: ACCENT } },
      { text: "Production is a long way from us. Until it isn't, we use proxy signals — deployment to QA and demo environments — and we treat closing the distance to real production feedback as part of the work, not a precondition for starting.", options: {} },
    ],
    { x: M + 0.4, y: 5.55, w: W - 2 * M - 0.8, h: 0.85, fontFace: BODY, fontSize: 13.5, color: SLATE, lineSpacing: 20, valign: "top", margin: 0 }
  );

  footer(s, 5);
  s.addNotes(
    "Deliberately capabilities rather than a list of practices to adopt — the practices come back on slide 7, as candidates.\n\n" +
    "Do not hide the caveat: our production environments are far away, so several DORA measures cannot be taken honestly today. " +
    "Proxy signals are worth having in the meantime, and the distance to production is itself one of the things worth shortening."
  );
})();

// =================================================================== 6 product side
(function () {
  const s = lightSlide();
  kicker(s, "THE PRODUCT SIDE");
  title(s, "Release in slices, to widening groups.");

  // widening groups visual — full width
  const bx = M, by = 2.05, bh = 0.58, gap = 0.24;
  const rows = [
    ["Slice 1", "the team, testers, a handful of willing users", 3.4],
    ["Slice 2", "one office, one user group, one region", 5.6],
    ["Slice 3", "everyone", 8.2],
  ];
  rows.forEach(function (r, i) {
    const y = by + i * (bh + gap);
    s.addShape(pres.ShapeType.rect, { x: bx, y: y, w: r[2], h: bh, fill: { color: i === 2 ? ACCENT : TINT2 } });
    s.addText(r[0], {
      x: bx + 0.25, y: y, w: 1.2, h: bh, fontFace: BODY, fontSize: 13, bold: true,
      color: i === 2 ? WHITE : INK, valign: "middle", margin: 0,
    });
    s.addText(r[1], {
      x: bx + r[2] + 0.25, y: y, w: 4.0, h: bh, fontFace: BODY, fontSize: 12.5, color: SLATE, valign: "middle", margin: 0,
    });
  });
  const ay = by + 3 * (bh + gap) + 0.04;
  arrowLine(s, bx, ay, bx + 8.2, ay, { color: MUTED, width: 1.25 });
  s.addText("a widening group of users", {
    x: bx, y: ay + 0.1, w: 8.2, h: 0.3, fontFace: BODY, fontSize: 11, italic: true, color: MUTED, align: "right", valign: "top", margin: 0,
  });

  s.addText(
    [
      { text: "Progressive delivery. ", options: { bold: true, color: INK } },
      { text: "The unit of release stops being “the feature, finished” and becomes “the smallest slice that is genuinely useful to someone.” Each slice is a question we get an answer to.", options: {} },
    ],
    { x: M, y: 5.2, w: 6.15, h: 1.5, fontFace: BODY, fontSize: 14, color: SLATE, lineSpacing: 21, margin: 0 }
  );

  // prototyping
  card(s, 7.35, 5.1, 5.08, 1.55, TINT);
  ring(s, 7.68, 5.42, 0.24, ACCENT, 1.5);
  s.addText("Prototype instead of specifying", {
    x: 8.12, y: 5.3, w: 4.0, h: 0.32, fontFace: BODY, fontSize: 14.5, bold: true, color: INK, valign: "top", margin: 0,
  });
  s.addText(
    "A long requirements phase produces agreement about a document. A prototype produces agreement about a thing — in days, from the people who will use it.",
    { x: 8.12, y: 5.68, w: 4.0, h: 0.85, fontFace: BODY, fontSize: 12, color: SLATE, lineSpacing: 16, valign: "top", margin: 0 }
  );

  footer(s, 6);
  s.addNotes(
    "Objection A — “we can't ship half-finished features to citizens.”\n" +
    "Answer: the risk is lower than it sounds, and we are partly doing this already. Product teams have already accepted " +
    "releasing features to different groups as they become ready. Users are not upset by getting something usable that " +
    "improves their situation; they are upset by waiting a year for it. A slice is not a broken feature — it is a smaller, whole one.\n\n" +
    "Verbal anchor, not on the slide: the two-day prototype."
  );
})();

// =================================================================== 7 the scale (VISUAL 1)
(function () {
  const s = lightSlide();
  kicker(s, "PRACTICES ARE EMPIRICAL TOO");
  title(s, "Alarms, not certificates.");

  s.addText(
    "Low test coverage tells us something is wrong. High test coverage tells us almost nothing. Above the floor, " +
    "practices are candidates rather than commandments — picked by a team, tested against a measure declared upfront, published.",
    { x: M, y: 1.66, w: 11.4, h: 0.62, fontFace: BODY, fontSize: 13.5, color: MUTED, lineSpacing: 19, margin: 0 }
  );

  // four columns; y positions are explicit so nothing can collide
  const cols = [
    {
      x: M, w: 3.4, head: "MANDATORY", color: ACCENT,
      groups: [
        [2.86, "Machine-checkable — a tool verifies it,\nso enforcing it costs nothing", 3.36,
          ["source control", "continuous integration", "a coverage threshold, as an alarm", "the mandated tech stack"]],
        [4.44, "Judgement-based — reviewed, not gated", 4.74,
          ["ports and adapters / hexagonal", "business logic kept out of technology, tooling and the UI", "an agile working method"]],
      ],
    },
    {
      x: 4.72, w: 2.6, head: "VALIDATED", color: INK,
      groups: [[2.86, "Delivery and flow", 3.16,
        ["trunk-based development, short-lived branches", "feature flags — deployment decoupled from release", "continuous deployment to staging and demo"]]],
    },
    {
      x: 7.72, w: 2.6, head: "PROMISING", color: INK,
      groups: [
        [2.86, "Craft", 3.16, ["TDD", "pair programming", "ensemble / mob programming", "collective review formats"]],
        [4.44, "Testing beyond coverage", 4.74, ["contract testing", "mutation testing", "property-based testing", "architecture fitness functions"]],
      ],
    },
    {
      x: 10.72, w: 2.16, head: "UNTRIED", color: INK,
      groups: [[2.86, "Where AI sits, for now", 3.16,
        ["AI-assisted code review", "agent-assisted refactoring", "AI-generated test suites", "spec-driven workflows", "AI prototyping in discovery"]]],
    },
  ];

  cols.forEach(function (c) {
    s.addText(c.head, {
      x: c.x, y: 2.5, w: c.w, h: 0.28, fontFace: BODY, fontSize: 12.5, bold: true,
      color: c.color, charSpacing: 1.8, valign: "top", margin: 0,
    });
    c.groups.forEach(function (g) {
      s.addText(g[1], {
        x: c.x, y: g[0], w: c.w, h: 0.44, fontFace: BODY, fontSize: 9.5, italic: true, color: MUTED, lineSpacing: 12, valign: "top", margin: 0,
      });
      s.addText(
        g[3].map(function (t, i) { return { text: t, options: { bullet: true, breakLine: i < g[3].length - 1 } }; }),
        { x: c.x + 0.02, y: g[2], w: c.w - 0.02, h: 1.6, fontFace: BODY, fontSize: 10.5, color: SLATE, lineSpacing: 13.5, paraSpaceAfter: 3, valign: "top", margin: 0 }
      );
    });
  });

  // the wedge: scrutiny owed, thickening toward the mandatory end
  const wy = 5.98, wh = 0.6;
  s.addShape(pres.ShapeType.rtTriangle, {
    x: M, y: wy, w: 11.98, h: wh, fill: { color: ACCENT, transparency: 58 }, line: { color: "FFFFFF", width: 0 },
  });
  s.addText("scrutiny owed", {
    x: M + 0.2, y: wy + 0.22, w: 2.4, h: 0.32, fontFace: BODY, fontSize: 11.5, bold: true, color: INK, valign: "top", margin: 0,
  });
  s.addText("care in how it is trialled — not permission", {
    x: 8.3, y: wy + 0.02, w: 4.3, h: 0.3, fontFace: BODY, fontSize: 10, italic: true, color: MUTED, align: "right", valign: "top", margin: 0,
  });
  arrowLine(s, M, wy + wh + 0.12, M + 11.98, wy + wh + 0.12, { color: MUTED, width: 1.25 });
  s.addText("Moving something down towards mandatory has to be justified and challenged. Moving something up into untried does not.", {
    x: M, y: wy + wh + 0.2, w: 10.6, h: 0.3, fontFace: BODY, fontSize: 10.5, italic: true, color: MUTED, valign: "top", margin: 0,
  });

  footer(s, 7);
  s.addNotes(
    "Read the picture out loud: the floor is small and hard; the space above it is large and soft. " +
    "The common assumption is that the proposal is to soften everything — one look at this should kill that.\n\n" +
    "The dividing line inside the floor matters: what a tool can verify costs almost nothing to enforce, so it can be " +
    "genuinely mandatory. What needs human judgement sits just above it — reviewed rather than gated.\n\n" +
    "Domain-driven design is deliberately not on the scale: it is a family of approaches rather than a practice you either " +
    "do or don't, so it sits awkwardly on a single axis. Mention if asked.\n\n" +
    "The untried tier is the clearest argument for the whole model: assumptions about AI tooling go stale within months, " +
    "which is exactly why it needs a declared measure rather than a policy decided in advance.\n\n" +
    "This is not a tech radar — the radar's rings carry a scale but its angular dimension is only categories, and inheriting " +
    "that shape would mean explaining that half of it means nothing."
  );
})();

// =================================================================== 8 governance
(function () {
  const s = lightSlide();
  kicker(s, "GOVERNANCE");
  title(s, "Govern the experiments, not the answers.");

  card(s, M, 2.15, 5.45, 2.6, TINT);
  s.addText("What we stop deciding centrally", {
    x: M + 0.4, y: 2.45, w: 4.65, h: 0.35, fontFace: BODY, fontSize: 15, bold: true, color: MUTED, valign: "top", margin: 0,
  });
  s.addText(
    [
      { text: "Which practice wins.", options: { bullet: true, breakLine: true } },
      { text: "Whether a team should pair, or mob, or do TDD.", options: { bullet: true, breakLine: true } },
      { text: "Which tool is allowed to be tried.", options: { bullet: true, breakLine: false } },
    ],
    { x: M + 0.4, y: 2.92, w: 4.65, h: 1.5, fontFace: BODY, fontSize: 13.5, color: SLATE, lineSpacing: 19, paraSpaceAfter: 7, margin: 0 }
  );

  card(s, 6.95, 2.15, 5.45, 2.6, ACCENT_T);
  s.addText("What we govern instead", {
    x: 7.35, y: 2.45, w: 4.65, h: 0.35, fontFace: BODY, fontSize: 15, bold: true, color: ACCENT, valign: "top", margin: 0,
  });
  s.addText(
    [
      { text: "How many experiments run at once.", options: { bullet: true, breakLine: true } },
      { text: "What is declared upfront — including the measure.", options: { bullet: true, breakLine: true } },
      { text: "When each one is reviewed.", options: { bullet: true, breakLine: true } },
      { text: "What evidence gets published afterwards.", options: { bullet: true, breakLine: false } },
    ],
    { x: 7.35, y: 2.92, w: 4.65, h: 1.7, fontFace: BODY, fontSize: 13.5, color: SLATE, lineSpacing: 19, paraSpaceAfter: 7, valign: "top", margin: 0 }
  );

  s.addText("Control that produces evidence rather than documents.", {
    x: M, y: 5.1, w: 11.5, h: 0.6, fontFace: HEAD, fontSize: 26, bold: true, color: INK, valign: "top", margin: 0,
  });
  s.addText("Inspection is required. Who does it — peers, architects, a guild, a board — is tactical, and left open here on purpose.", {
    x: M, y: 5.82, w: 11.5, h: 0.4, fontFace: BODY, fontSize: 14, color: MUTED, valign: "top", margin: 0,
  });

  footer(s, 8);
  s.addNotes(
    "Objections B and C — “experiments mean teams doing whatever they want” and “who pays for a failed experiment?” — " +
    "are the same objection in two costumes: loss of control over time, and over money.\n\n" +
    "Cost answer: the expensive thing is not experiments, it is the failed projects we already have — several of them recently. " +
    "This is a structured way of reducing that risk. It is auditable, and it could be certified as a quality process for " +
    "developing the process. It is close to guaranteed to bring that cost down eventually.\n\n" +
    "People answer, probably the stronger one here: management genuinely values the people in this organisation — engaged, " +
    "not burnt out, many of them spending a whole career with us. Letting them choose which experiments to run and validate " +
    "is how we get their full intelligence and commitment, not just their compliance.\n\n" +
    "Plus the shared discipline: experiments are small and bounded, the measure is declared beforehand, and a failed experiment " +
    "still produces knowledge the organisation keeps."
  );
})();

// =================================================================== 9 the loops (VISUAL 2)
(function () {
  const s = lightSlide();
  kicker(s, "CLOSING THE LOOP");
  title(s, "Three parts, one system.");
  s.addText("Any one of them alone stalls.", {
    x: M, y: 1.68, w: 8, h: 0.35, fontFace: BODY, fontSize: 15, color: MUTED, valign: "top", margin: 0,
  });

  // node centres
  const N = {
    q: { x: 6.66, y: 2.72, label: "Technical quality" },
    p: { x: 3.55, y: 5.32, label: "Product iteration" },
    e: { x: 9.77, y: 5.32, label: "Experimentation" },
  };
  const nw = 2.75, nh = 0.72;

  function edge(a, b, sign, off) {
    // unit vector a -> b
    const dx = b.x - a.x, dy = b.y - a.y;
    const L = Math.sqrt(dx * dx + dy * dy);
    const ux = dx / L, uy = dy / L;
    // perpendicular offset so the pair reads as two arrows
    const px = -uy * off, py = ux * off;
    // inset from node centre: approximate rectangle boundary
    const inset = Math.min(Math.abs(ux) > 0.001 ? Math.abs((nw / 2 + 0.14) / ux) : 99,
      Math.abs(uy) > 0.001 ? Math.abs((nh / 2 + 0.14) / uy) : 99);
    const x1 = a.x + ux * inset + px, y1 = a.y + uy * inset + py;
    const x2 = b.x - ux * inset + px, y2 = b.y - uy * inset + py;
    arrowLine(s, x1, y1, x2, y2, { color: GREEN, width: 2 });
    // polarity marker near the arrow head
    s.addText(sign, {
      x: x2 - ux * 0.42 + px * 1.5 - 0.16, y: y2 - uy * 0.42 + py * 1.5 - 0.15,
      w: 0.32, h: 0.3, fontFace: BODY, fontSize: 14, bold: true, color: GREEN, align: "center", valign: "middle", margin: 0,
    });
  }

  const OFF = 0.19;
  edge(N.q, N.p, "+", OFF); edge(N.p, N.q, "+", OFF);
  edge(N.q, N.e, "+", OFF); edge(N.e, N.q, "+", OFF);
  edge(N.p, N.e, "+", OFF); edge(N.e, N.p, "+", OFF);

  // R markers at the midpoint of each pair
  function rMark(a, b) {
    const mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2;
    s.addShape(pres.ShapeType.ellipse, { x: mx - 0.26, y: my - 0.26, w: 0.52, h: 0.52, fill: { color: WHITE }, line: { color: ACCENT, width: 1.5 } });
    s.addText("R", { x: mx - 0.26, y: my - 0.26, w: 0.52, h: 0.52, fontFace: BODY, fontSize: 13, bold: true, color: ACCENT, align: "center", valign: "middle", margin: 0 });
    // a small circular hint around the R
    ring(s, mx - 0.4, my - 0.4, 0.8, ACCENT, 1, 65);
  }
  rMark(N.q, N.p); rMark(N.q, N.e); rMark(N.p, N.e);

  // nodes on top
  Object.keys(N).forEach(function (k) {
    const n = N[k];
    s.addShape(pres.ShapeType.roundRect, {
      x: n.x - nw / 2, y: n.y - nh / 2, w: nw, h: nh, fill: { color: INK }, rectRadius: 0.1,
    });
    s.addText(n.label, {
      x: n.x - nw / 2, y: n.y - nh / 2, w: nw, h: nh, fontFace: BODY, fontSize: 14.5, bold: true,
      color: WHITE, align: "center", valign: "middle", margin: 0,
    });
  });

  // legend
  s.addText(
    [
      { text: "+   the two move in the same direction", options: { breakLine: true } },
      { text: "R   a loop that feeds itself", options: {} },
    ],
    { x: M, y: 2.35, w: 3.1, h: 0.6, fontFace: BODY, fontSize: 10.5, color: MUTED, lineSpacing: 15, margin: 0 }
  );

  // loop captions
  const caps = [
    ["Quality ⇄ iteration", "Quality makes change cheap and safe; iterating creates the real demand for quality."],
    ["Quality ⇄ experimentation", "Experiments improve practice, so quality rises; quality shortens cycles, so experiments are cheap to run."],
    ["Iteration ⇄ experimentation", "Short cycles make experiments evaluable; experiments improve how we iterate."],
  ];
  caps.forEach(function (c, i) {
    const x = M + i * 3.95;
    s.addText(c[0], { x: x, y: 6.06, w: 3.6, h: 0.26, fontFace: BODY, fontSize: 12, bold: true, color: ACCENT, valign: "top", margin: 0 });
    s.addText(c[1], { x: x, y: 6.34, w: 3.6, h: 0.72, fontFace: BODY, fontSize: 10.5, color: SLATE, lineSpacing: 13, margin: 0 });
  });

  footer(s, 9);
  s.addNotes(
    "Third and highest-resolution pass at the central message. Say the loops out loud, one at a time, and finish on: " +
    "three parts, one system — any one of them alone stalls.\n\n" +
    "It reads fine to people who know causal loop diagrams and stays legible to people who don't: every arrow is positive, " +
    "so more of one means more of the other, and R marks a loop that feeds itself."
  );
})();

// =================================================================== 10 why now (VISUAL 3)
(function () {
  const s = darkSlide();
  kicker(s, "WHY NOW", ACCENT, M, 0.62);
  s.addText("The gap is not new. It just got much wider.", {
    x: M, y: 0.98, w: 11.4, h: 0.6, fontFace: HEAD, fontSize: 32, bold: true, color: WHITE, valign: "top", margin: 0,
  });

  // ---- plot frame
  const px0 = 1.15, px1 = 12.35, pyB = 5.62, pyT = 2.15;
  const today = 8.55;

  // faint axes, no values
  arrowLine(s, px0, pyB, px1, pyB, { color: MUTED_D, width: 1, head: true });
  arrowLine(s, px0, pyB, px0, pyT - 0.25, { color: MUTED_D, width: 1, head: false });
  s.addText("time", { x: px1 - 1.3, y: pyB + 0.12, w: 1.3, h: 0.28, fontFace: BODY, fontSize: 10.5, italic: true, color: MUTED_D, align: "right", valign: "top", margin: 0 });
  s.addText("capability", {
    x: px0 - 0.62, y: pyT - 0.05, w: 1.6, h: 0.28, fontFace: BODY, fontSize: 10.5, italic: true, color: MUTED_D, valign: "top", margin: 0,
  });

  // today marker
  arrowLine(s, today, pyT - 0.3, today, pyB, { color: MUTED_D, width: 1, dash: "sysDot", head: false });
  s.addText("today", { x: today - 0.5, y: pyT - 0.62, w: 1.0, h: 0.28, fontFace: BODY, fontSize: 10.5, bold: true, color: MUTED_D, align: "center", margin: 0 });

  // ---- what is technically feasible
  const F = [[1.45, 4.72], [4.3, 4.35], [6.35, 4.05], [6.95, 3.95]];
  for (let i = 0; i < F.length - 1; i++) arrowLine(s, F[i][0], F[i][1], F[i + 1][0], F[i + 1][1], { color: GREEN, width: 2.75, head: false });
  // the step change
  arrowLine(s, 6.95, 3.95, 7.85, 2.72, { color: GREEN, width: 2.75, head: false });
  arrowLine(s, 7.85, 2.72, today, 2.6, { color: GREEN, width: 2.75, head: false });
  // projection
  arrowLine(s, today, 2.6, 10.4, 2.3, { color: GREEN, width: 2.75, dash: "dash", head: false });
  arrowLine(s, 10.4, 2.3, 12.1, 1.85, { color: GREEN, width: 2.75, dash: "dash", head: false });
  s.addText("what is technically feasible", {
    x: 1.75, y: 3.82, w: 3.0, h: 0.28, fontFace: BODY, fontSize: 12, bold: true, color: GREEN, valign: "top", margin: 0,
  });

  // step-change annotation
  s.addShape(pres.ShapeType.ellipse, { x: 6.86, y: 3.86, w: 0.18, h: 0.18, fill: { color: ACCENT } });
  s.addText("AI prototyping\narrives", {
    x: 5.62, y: 2.9, w: 1.5, h: 0.6, fontFace: BODY, fontSize: 11, bold: true, color: ACCENT, align: "right", lineSpacing: 14, valign: "top", margin: 0,
  });
  arrowLine(s, 7.16, 3.52, 6.98, 3.86, { color: ACCENT, width: 1.25 });

  // ---- what we actually deliver
  const D = [[1.45, 5.18], [4.3, 5.05], [6.6, 4.9], [today, 4.82]];
  for (let i = 0; i < D.length - 1; i++) arrowLine(s, D[i][0], D[i][1], D[i + 1][0], D[i + 1][1], { color: MUTED_D, width: 2.75, head: false });
  arrowLine(s, today, 4.82, 10.4, 4.68, { color: MUTED_D, width: 2.75, dash: "dash", head: false });
  arrowLine(s, 10.4, 4.68, 12.1, 4.5, { color: MUTED_D, width: 2.75, dash: "dash", head: false });
  s.addText("what we actually deliver", {
    x: 3.5, y: 5.26, w: 3.2, h: 0.28, fontFace: BODY, fontSize: 12, bold: true, color: MUTED_D, valign: "top", margin: 0,
  });

  // the gap, then and now
  arrowLine(s, 2.05, 4.63, 2.05, 5.14, { color: ACCENT, width: 1.5, both: true });
  s.addText("the gap\nthen", {
    x: 1.05, y: 4.66, w: 0.85, h: 0.5, fontFace: BODY, fontSize: 10.5, bold: true, color: ACCENT,
    align: "right", lineSpacing: 12, valign: "top", margin: 0,
  });
  arrowLine(s, 12.1, 1.95, 12.1, 4.4, { color: ACCENT, width: 1.5, both: true });
  s.addText("the gap\nnow", {
    x: 10.95, y: 2.95, w: 1.0, h: 0.5, fontFace: BODY, fontSize: 12, bold: true, color: ACCENT,
    align: "right", lineSpacing: 14, valign: "top", margin: 0,
  });

  s.addText(
    [
      { text: "The opportunity: ", options: { bold: true, color: ACCENT } },
      { text: "to be the department that works out how modern delivery actually functions inside a public agency.", options: {} },
    ],
    { x: M, y: 6.35, w: 11.4, h: 0.5, fontFace: BODY, fontSize: 15.5, color: WHITE, valign: "top", margin: 0 }
  );

  footerDark(s, 10);
  s.addNotes(
    "The picture is deliberately abstract — no axis values, no units, no figures. Real numbers would start an argument " +
    "about the numbers instead of the point. Concrete anchors (the two-day prototype) go in the telling, not on the slide.\n\n" +
    "The rhetorical point is the left-hand side: the lines were already diverging years ago, when waterfall delivery met what " +
    "was already feasible. This is an old gap that widened sharply — much harder to dismiss than a hockey stick that starts at today.\n\n" +
    "SAY ORALLY, NOT ON A SLIDE: other public administrations have already closed this gap, using lean-startup-style development " +
    "practices. It is proof of existence, and it neutralises “we're different, we're government” better than any argument from " +
    "first principles. Keep it off the slide — on a slide it invites a detailed organisational comparison and drags us into tactics. " +
    "Describe the practices, not the label: startup framing reads as reckless in a compliance culture.\n\n" +
    "Do not name the pressure everyone already feels. Motivate by opportunity. The gap is mostly tooling and process, not " +
    "developer capability — say that, and let them ask what closing it would cost."
  );
})();

pres.writeFile({ fileName: process.argv[2] || "vision-pitch.pptx" }).then(function (f) {
  console.log("wrote " + f);
});
