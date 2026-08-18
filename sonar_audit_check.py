#!/usr/bin/env python3
"""
sonar_audit_check.py

Diagnostic préalable à un audit de parc SonarQube :

  1. L'API répond-elle et mon token est-il valide ?
  2. Quelles requêtes puis-je réellement faire (parmi celles qui comptent) ?
  3. Combien de projets je vois, et combien m'échappent ?
  4. Quels signaux d'activité sont disponibles sans passer par Git ?

Dépendances : aucune (stdlib uniquement). Python >= 3.8.

Usage :
    export SONAR_URL=https://sonar.example.com
    export SONAR_TOKEN=squ_xxxxxxxx
    python3 sonar_audit_check.py
    python3 sonar_audit_check.py --csv projets.csv --stale-days 120
    python3 sonar_audit_check.py --organization my-org      # SonarQube Cloud
"""

import argparse
import csv
import json
import os
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta

# --------------------------------------------------------------------------
# Client HTTP minimal
# --------------------------------------------------------------------------


class Sonar:
    def __init__(self, base_url, token, organization=None, timeout=30, insecure=False):
        self.base = base_url.rstrip("/")
        self.token = token
        self.organization = organization
        self.timeout = timeout
        self.ctx = None
        if insecure:
            self.ctx = ssl.create_default_context()
            self.ctx.check_hostname = False
            self.ctx.verify_mode = ssl.CERT_NONE
        self.calls = 0

    def get(self, path, **params):
        """Retourne (status_code, payload). payload = dict/list si JSON, sinon str."""
        if self.organization and "organization" not in params:
            if path.startswith("api/components/search_projects") or path.startswith(
                "api/qualityprofiles"
            ):
                params["organization"] = self.organization
        params = {k: v for k, v in params.items() if v is not None}
        qs = urllib.parse.urlencode(params, doseq=True)
        url = f"{self.base}/{path.lstrip('/')}" + (f"?{qs}" if qs else "")
        req = urllib.request.Request(url, method="GET")
        if self.token:
            req.add_header("Authorization", f"Bearer {self.token}")
        req.add_header("Accept", "application/json")
        self.calls += 1
        try:
            with urllib.request.urlopen(req, timeout=self.timeout, context=self.ctx) as r:
                body = r.read().decode("utf-8", "replace")
                status = r.status
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", "replace")
            status = e.code
        except urllib.error.URLError as e:
            return 0, f"{e.reason}"
        except Exception as e:  # timeout, ssl, ...
            return 0, str(e)
        try:
            return status, json.loads(body)
        except ValueError:
            return status, body


# --------------------------------------------------------------------------
# Présentation
# --------------------------------------------------------------------------

BOLD, DIM, RESET = "\033[1m", "\033[2m", "\033[0m"
GREEN, RED, YELLOW = "\033[32m", "\033[31m", "\033[33m"
NO_COLOR = not sys.stdout.isatty() or os.environ.get("NO_COLOR")


def c(text, color):
    return text if NO_COLOR else f"{color}{text}{RESET}"


def title(text):
    print()
    print(c(f"=== {text} ", BOLD) + c("=" * max(0, 66 - len(text)), BOLD))


VERDICTS = {
    "ok": (GREEN, "OK      "),
    "denied": (RED, "REFUSE  "),
    "missing": (YELLOW, "ABSENT  "),
    "empty": (YELLOW, "VIDE    "),
    "error": (RED, "ERREUR  "),
}


def verdict(status, payload):
    if status == 200:
        return "ok"
    if status in (401, 403):
        return "denied"
    if status == 404:
        return "missing"
    if status == 0:
        return "error"
    return "error"


def line(label, kind, detail=""):
    color, tag = VERDICTS.get(kind, (YELLOW, "?       "))
    print(f"  {c(tag, color)} {label:<42} {c(detail, DIM) if detail else ''}")


def err_msg(payload):
    if isinstance(payload, dict) and payload.get("errors"):
        return "; ".join(e.get("msg", "") for e in payload["errors"])[:90]
    if isinstance(payload, str):
        return payload.replace("\n", " ")[:90]
    return ""


def parse_date(s):
    if not s:
        return None
    try:
        return datetime.strptime(s[:19], "%Y-%m-%dT%H:%M:%S")
    except ValueError:
        return None


# --------------------------------------------------------------------------
# 1. Connectivité et identité
# --------------------------------------------------------------------------


def check_connectivity(sq):
    title("1. Connectivité et identité")

    status, payload = sq.get("api/system/status")
    if status == 0:
        line("api/system/status", "error", err_msg(payload))
        print()
        print(c("  Impossible de joindre l'instance. Vérifie l'URL, le proxy, le TLS.", RED))
        return None
    if status == 200 and isinstance(payload, dict):
        line(
            "api/system/status",
            "ok",
            f"{payload.get('status')} · v{payload.get('version')} · {payload.get('id','')}",
        )
    else:
        line("api/system/status", verdict(status, payload), f"HTTP {status}")

    status, payload = sq.get("api/authentication/validate")
    valid = isinstance(payload, dict) and payload.get("valid") is True
    line(
        "api/authentication/validate",
        "ok" if valid else "denied",
        "token valide" if valid else "token invalide ou expiré",
    )
    if not valid:
        print()
        print(c("  Le token n'est pas accepté. Vérifie qu'il s'agit bien d'un", RED))
        print(c("  token de type 'User' (squ_...) et non d'un token d'analyse.", RED))
        return None

    status, me = sq.get("api/users/current")
    if status != 200 or not isinstance(me, dict):
        line("api/users/current", verdict(status, me), err_msg(me))
        return {}

    perms = (me.get("permissions") or {}).get("global") or []
    line("api/users/current", "ok", f"{me.get('login')} ({me.get('name','')})")
    print()
    print(f"  Permissions globales : {c(', '.join(perms) if perms else 'aucune', DIM)}")
    groups = me.get("groups") or []
    if groups:
        print(f"  Groupes              : {c(', '.join(groups[:8]), DIM)}")

    if "admin" in perms:
        print()
        print(c("  Note : ce token a 'Administer System'. Il verra tout, donc ce", YELLOW))
        print(c("  diagnostic ne reflétera pas le périmètre d'un compte d'audit", YELLOW))
        print(c("  restreint. Pour un audit récurrent, préfère un compte dédié.", YELLOW))
    return me


# --------------------------------------------------------------------------
# 2. Capacités réelles
# --------------------------------------------------------------------------

# Les métriques qui nous intéressent pour un ciblage "dette en cours de création"
METRICS = ",".join(
    [
        "ncloc",
        "coverage",
        "duplicated_lines_density",
        "sqale_index",
        "sqale_debt_ratio",
        "sqale_rating",
        "reliability_rating",
        "security_rating",
        "alert_status",
        "new_coverage",
        "new_lines",
        "new_violations",
        "new_duplicated_lines_density",
        "security_hotspots_reviewed",
    ]
)


def check_capabilities(sq, sample_project):
    title("2. Capacités du token sur les endpoints utiles")

    if not sample_project:
        print(c("  Aucun projet visible : impossible de tester les endpoints projet.", YELLOW))
        return {}

    print(f"  Projet témoin : {c(sample_project, DIM)}\n")

    results = {}

    def probe(label, path, ok_hint=None, **params):
        status, payload = sq.get(path, **params)
        v = verdict(status, payload)
        detail = ""
        if v == "ok" and ok_hint:
            try:
                detail = ok_hint(payload)
            except Exception:
                detail = ""
        elif v != "ok":
            detail = f"HTTP {status} {err_msg(payload)}"
        line(label, v, detail)
        results[label] = (v, payload)
        return payload if v == "ok" else None

    probe(
        "Inventaire projets (search_projects)",
        "api/components/search_projects",
        lambda p: f"{p.get('paging', {}).get('total', '?')} projets visibles",
        ps=1,
    )
    probe(
        "Mesures en masse (measures/search)",
        "api/measures/search",
        lambda p: f"{len(p.get('measures', []))} mesures",
        projectKeys=sample_project,
        metricKeys=METRICS,
    )
    probe(
        "Historique de mesures (search_history)",
        "api/measures/search_history",
        lambda p: f"{p.get('paging', {}).get('total', '?')} points",
        component=sample_project,
        metrics="ncloc,coverage,sqale_index",
        ps=5,
    )
    probe(
        "Issues + facettes (issues/search)",
        "api/issues/search",
        lambda p: f"{p.get('total', p.get('paging', {}).get('total', '?'))} issues",
        componentKeys=sample_project,
        ps=1,
        facets="severities",
    )
    probe(
        "Contournements (WONTFIX / FALSE-POSITIVE)",
        "api/issues/search",
        lambda p: f"{p.get('total', p.get('paging', {}).get('total', '?'))} issues neutralisées",
        componentKeys=sample_project,
        resolutions="WONTFIX,FALSE-POSITIVE",
        ps=1,
    )
    probe(
        "Exclusions (settings/values)",
        "api/settings/values",
        lambda p: f"{len(p.get('settings', []))} réglage(s) d'exclusion",
        component=sample_project,
        keys="sonar.exclusions,sonar.coverage.exclusions,sonar.cpd.exclusions",
    )
    probe(
        "Quality gate du projet",
        "api/qualitygates/get_by_project",
        lambda p: (p.get("qualityGate") or {}).get("name", ""),
        project=sample_project,
    )
    probe(
        "Quality profiles du projet",
        "api/qualityprofiles/search",
        lambda p: f"{len(p.get('profiles', []))} profil(s)",
        project=sample_project,
    )
    probe(
        "Historique d'analyses (project_analyses)",
        "api/project_analyses/search",
        lambda p: f"{p.get('paging', {}).get('total', '?')} analyses",
        project=sample_project,
        ps=5,
    )
    probe(
        "Arbre de composants (components/tree)",
        "api/components/tree",
        lambda p: f"{p.get('paging', {}).get('total', '?')} fichiers",
        component=sample_project,
        qualifiers="FIL",
        ps=1,
    )
    probe(
        "Total réel des projets (admin uniquement)",
        "api/projects/search",
        lambda p: f"{p.get('paging', {}).get('total', '?')} projets au total",
        ps=1,
    )

    # Blame SCM : nécessite 'See Source Code' en plus de 'Browse'
    tree = sq.get("api/components/tree", component=sample_project, qualifiers="FIL", ps=1)[1]
    file_key = None
    if isinstance(tree, dict) and tree.get("components"):
        file_key = tree["components"][0]["key"]
    if file_key:
        probe(
            "Blame SCM (sources/scm)",
            "api/sources/scm",
            lambda p: f"{len(p.get('scm', []))} ligne(s) datée(s)",
            key=file_key,
            **{"from": 1, "to": 20},
        )
    else:
        line("Blame SCM (sources/scm)", "empty", "aucun fichier accessible pour tester")

    return results


# --------------------------------------------------------------------------
# 3. Inventaire des projets
# --------------------------------------------------------------------------


def inventory(sq, stale_days, csv_path):
    title("3. Inventaire des projets visibles")

    projects = []
    page, page_size = 1, 500
    total = None
    while True:
        status, payload = sq.get(
            "api/components/search_projects",
            ps=page_size,
            p=page,
            f="analysisDate,leakPeriodDate",
        )
        if status != 200 or not isinstance(payload, dict):
            print(c(f"  Échec de la pagination page {page} : HTTP {status}", RED))
            break
        total = payload.get("paging", {}).get("total", 0)
        batch = payload.get("components", [])
        projects.extend(batch)
        if len(projects) >= total or not batch:
            break
        page += 1
        if page > 40:  # garde-fou : 20 000 projets
            print(c("  Pagination interrompue à 20 000 projets.", YELLOW))
            break

    print(f"  Projets visibles avec ce token : {c(str(len(projects)), BOLD)}")

    # Comparaison avec le total réel (nécessite Administer System)
    status, payload = sq.get("api/projects/search", ps=1)
    if status == 200 and isinstance(payload, dict):
        real_total = payload.get("paging", {}).get("total")
        gap = real_total - len(projects)
        print(f"  Projets réellement présents    : {c(str(real_total), BOLD)}")
        if gap > 0:
            print(
                c(
                    f"  → {gap} projet(s) hors de ton périmètre. Ton classement sera "
                    f"tronqué\n    de {gap/real_total:.0%} sans aucun message d'erreur.",
                    RED,
                )
            )
        else:
            print(c("  → Périmètre complet.", GREEN))
    else:
        print(
            c(
                "  Total réel indisponible (nécessite 'Administer System').\n"
                "    Fais comparer ce chiffre à un admin : l'écart est le point aveugle\n"
                "    de ton audit, et il n'apparaît dans aucune réponse d'erreur.",
                YELLOW,
            )
        )

    # Fraîcheur
    now = datetime.utcnow()
    never, stale, fresh = [], [], []
    for p in projects:
        d = parse_date(p.get("analysisDate"))
        if d is None:
            never.append(p)
        elif (now - d).days > stale_days:
            stale.append(p)
        else:
            fresh.append(p)

    print()
    print(f"  Analysés il y a < {stale_days} j        : {len(fresh)}")
    print(f"  Analysés il y a > {stale_days} j        : {c(str(len(stale)), YELLOW)}")
    print(f"  Jamais analysés                : {c(str(len(never)), YELLOW)}")
    if never:
        print(c("    (projets créés puis abandonnés, ou analyse jamais configurée)", DIM))

    # Mesures par paquets de 100 clés
    measures_by_key = {}
    keys = [p["key"] for p in projects]
    for i in range(0, len(keys), 100):
        chunk = keys[i : i + 100]
        status, payload = sq.get(
            "api/measures/search", projectKeys=",".join(chunk), metricKeys=METRICS
        )
        if status != 200 or not isinstance(payload, dict):
            continue
        for m in payload.get("measures", []):
            measures_by_key.setdefault(m["component"], {})[m["metric"]] = m.get("value")

    no_coverage = [k for k in keys if "coverage" not in measures_by_key.get(k, {})]
    print(f"  Sans aucune donnée de couverture: {c(str(len(no_coverage)), YELLOW)}")
    if no_coverage:
        print(c("    (coverage absente ≠ coverage à 0 : ces projets sont invisibles", DIM))
        print(c("     dans tout tri par couverture, et souvent les plus à risque)", DIM))

    if csv_path:
        with open(csv_path, "w", newline="", encoding="utf-8") as f:
            cols = [
                "key",
                "name",
                "analysisDate",
                "days_since_analysis",
                "ncloc",
                "coverage",
                "new_coverage",
                "duplicated_lines_density",
                "new_duplicated_lines_density",
                "sqale_debt_ratio",
                "sqale_rating",
                "new_violations",
                "new_lines",
                "reliability_rating",
                "security_rating",
                "security_hotspots_reviewed",
                "alert_status",
            ]
            w = csv.DictWriter(f, fieldnames=cols, extrasaction="ignore")
            w.writeheader()
            for p in projects:
                m = measures_by_key.get(p["key"], {})
                d = parse_date(p.get("analysisDate"))
                row = {
                    "key": p["key"],
                    "name": p.get("name", ""),
                    "analysisDate": p.get("analysisDate", ""),
                    "days_since_analysis": (now - d).days if d else "",
                }
                row.update(m)
                w.writerow(row)
        print()
        print(f"  CSV écrit : {c(csv_path, BOLD)}")

    return projects


# --------------------------------------------------------------------------
# 4. Signaux d'activité sans Git
# --------------------------------------------------------------------------


def activity_signals(sq, sample_project, days=90):
    title(f"4. Signaux d'activité disponibles sans Git (sur {sample_project})")

    since = (datetime.utcnow() - timedelta(days=days)).strftime("%Y-%m-%d")

    # a) Cadence d'analyses = cadence de livraison
    status, payload = sq.get(
        "api/project_analyses/search", project=sample_project, ps=500, **{"from": since}
    )
    if status == 200 and isinstance(payload, dict):
        analyses = payload.get("analyses", [])
        versions = [
            e["name"]
            for a in analyses
            for e in a.get("events", [])
            if e.get("category") == "VERSION"
        ]
        print(f"  Analyses sur {days} j            : {c(str(len(analyses)), BOLD)}")
        print(f"  Versions livrées                : {len(set(versions))}")
        if analyses:
            dates = sorted(filter(None, (parse_date(a.get("date")) for a in analyses)))
            if len(dates) > 1:
                span = (dates[-1] - dates[0]).days or 1
                print(f"  Cadence moyenne                 : 1 analyse / {span/len(dates):.1f} j")
    else:
        line("api/project_analyses/search", verdict(status, payload), f"HTTP {status}")

    # b) Auteurs SCM via les facettes d'issues : taille et concentration de l'équipe
    status, payload = sq.get(
        "api/issues/search",
        componentKeys=sample_project,
        createdAfter=since,
        facets="author",
        ps=1,
    )
    if status == 200 and isinstance(payload, dict):
        facets = {f["property"]: f["values"] for f in payload.get("facets", [])}
        authors = facets.get("author", [])
        total_issues = sum(a["count"] for a in authors) or 1
        print()
        print(f"  Auteurs distincts ({days} j)      : {c(str(len(authors)), BOLD)}")
        if authors:
            top = authors[0]
            print(
                f"  Auteur principal                : {top['val']} "
                f"({top['count']/total_issues:.0%} des issues récentes)"
            )
            if top["count"] / total_issues > 0.7 and len(authors) > 1:
                print(c("    → forte concentration : proxy de bus factor faible", YELLOW))
    else:
        line("Facette 'author' sur issues", verdict(status, payload), f"HTTP {status}")

    # c) Volume de code neuf sur la période de new code
    status, payload = sq.get(
        "api/measures/component",
        component=sample_project,
        metricKeys="new_lines,new_violations,new_coverage,ncloc",
    )
    if status == 200 and isinstance(payload, dict):
        ms = {
            m["metric"]: (m.get("value") or (m.get("period") or {}).get("value"))
            for m in (payload.get("component", {}).get("measures", []))
        }
        print()
        print(f"  Lignes neuves (new code)        : {ms.get('new_lines', 'n/a')}")
        print(f"  Violations neuves               : {ms.get('new_violations', 'n/a')}")
        print(f"  Couverture du code neuf         : {ms.get('new_coverage', 'n/a')}")

    # d) Croissance nette : ncloc dans le temps (proxy grossier de churn)
    status, payload = sq.get(
        "api/measures/search_history",
        component=sample_project,
        metrics="ncloc,sqale_index",
        ps=1000,
        **{"from": since},
    )
    if status == 200 and isinstance(payload, dict):
        series = {m["metric"]: m.get("history", []) for m in payload.get("measures", [])}
        ncloc = [h for h in series.get("ncloc", []) if h.get("value")]
        debt = [h for h in series.get("sqale_index", []) if h.get("value")]
        if len(ncloc) > 1 and len(debt) > 1:
            d_ncloc = int(ncloc[-1]["value"]) - int(ncloc[0]["value"])
            d_debt = int(debt[-1]["value"]) - int(debt[0]["value"])
            print()
            print(f"  Δ lignes sur {days} j            : {d_ncloc:+d}")
            print(f"  Δ dette sur {days} j (min)       : {d_debt:+d}")
            if d_ncloc > 0:
                print(
                    f"  Dette ajoutée par ligne écrite  : "
                    f"{c(f'{d_debt/d_ncloc:+.2f} min/LOC', BOLD)}"
                )
                print(c("    (négatif = l'équipe rembourse ; > 10 = signal fort)", DIM))

    # e) Blame SCM : Sonar stocke la date et l'auteur du dernier commit par ligne
    tree = sq.get("api/components/tree", component=sample_project, qualifiers="FIL", ps=1)[1]
    if isinstance(tree, dict) and tree.get("components"):
        fk = tree["components"][0]["key"]
        status, payload = sq.get("api/sources/scm", key=fk, **{"from": 1, "to": 200})
        if status == 200 and isinstance(payload, dict) and payload.get("scm"):
            rows = payload["scm"]  # [[line, author, date, revision], ...]
            dates = sorted(filter(None, (parse_date(r[2]) for r in rows if len(r) > 2)))
            authors = {r[1] for r in rows if len(r) > 1 and r[1]}
            print()
            print(c("  Blame SCM accessible via l'API :", GREEN))
            print(f"    Fichier témoin                : {fk.split(':')[-1][:50]}")
            print(f"    Auteurs sur les 200 1res lignes: {len(authors)}")
            if dates:
                print(f"    Dernière modification         : {dates[-1].date()}")
                print(f"    Plus ancienne ligne           : {dates[0].date()}")
            print(c("    → churn et âge du code calculables sans accès Git", DIM))
        else:
            print()
            print(
                c(
                    "  Blame SCM inaccessible : il manque 'See Source Code' au token,\n"
                    "  ou le scanner tourne sans métadonnées SCM (clone shallow en CI).",
                    YELLOW,
                )
            )


# --------------------------------------------------------------------------


def main():
    ap = argparse.ArgumentParser(
        description="Diagnostic d'accès Sonar avant audit de parc.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    ap.add_argument("--url", default=os.environ.get("SONAR_URL"), help="URL de l'instance")
    ap.add_argument("--token", default=os.environ.get("SONAR_TOKEN"), help="User token squ_...")
    ap.add_argument("--organization", default=os.environ.get("SONAR_ORG"), help="SonarQube Cloud")
    ap.add_argument("--project", help="projet témoin pour les tests (sinon: le premier visible)")
    ap.add_argument("--csv", help="chemin du CSV d'inventaire à écrire")
    ap.add_argument("--stale-days", type=int, default=90, help="seuil d'obsolescence (défaut 90)")
    ap.add_argument("--activity-days", type=int, default=90, help="fenêtre d'activité (défaut 90)")
    ap.add_argument("--timeout", type=int, default=30)
    ap.add_argument("--insecure", action="store_true", help="ignorer la validation TLS")
    args = ap.parse_args()

    if not args.url or not args.token:
        ap.error("SONAR_URL et SONAR_TOKEN sont requis (variables d'env ou --url/--token).")

    sq = Sonar(args.url, args.token, args.organization, args.timeout, args.insecure)

    print(c(f"\nInstance : {sq.base}", BOLD))

    me = check_connectivity(sq)
    if me is None:
        sys.exit(1)

    sample = args.project
    if not sample:
        status, payload = sq.get("api/components/search_projects", ps=1)
        if status == 200 and isinstance(payload, dict) and payload.get("components"):
            sample = payload["components"][0]["key"]

    check_capabilities(sq, sample)
    inventory(sq, args.stale_days, args.csv)
    if sample:
        activity_signals(sq, sample, args.activity_days)

    title("Synthèse")
    print(f"  {sq.calls} appels API effectués.")
    print(
        c(
            "  Rappel : search_projects filtre silencieusement sur ce que le token\n"
            "  peut voir. Une permission manquante ne produit pas d'erreur, seulement\n"
            "  un classement incomplet — et les projets manquants sont souvent ceux\n"
            "  qui auraient le plus besoin d'aide.\n",
            DIM,
        )
    )


if __name__ == "__main__":
    main()
