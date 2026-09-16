#!/usr/bin/env python3
"""Faux SonarQube, pour les chemins de plantage uniquement.

KNOWLEDGE.md §1 : un mock écrit depuis nos propres croyances ne valide aucune
sémantique — il répond ce qu'on lui demande. Celui-ci ne prétend donc rien
prouver sur l'API SonarQube ; testing/verify-against-real-sonarqube.sh reste le
seul juge de ce que l'instance répond vraiment.

Il sert à ce qu'un mock sait faire, et que l'instance réelle ne sait pas faire
sur commande :

  * les formes de réponse qui ont déjà coûté cher — new_* sous `period`, et
    sous `periods` sur les versions plus anciennes : les lire mal rend un CSV
    de cellules vides qui ressemble à « pas de données » (KNOWLEDGE.md §2) ;
  * les branches d'erreur qu'on ne convoque pas à la demande — 403 sur
    projects/search, 400 sur une métrique inconnue, 429 avec Retry-After,
    502 isolé au milieu d'une passe concurrente ;
  * les cas limites du calcul — un historique à un seul point, un historique
    dont une valeur est vide : la vélocité doit alors être absente, jamais 0 ;
  * la non-régression : même parc, même CSV.

Le parc est biscornu à dessein. Chaque projet existe pour casser une hypothèse.
"""
import json
import sys
import threading
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs

NOW = datetime.now(timezone.utc)


def iso(days_ago):
    """Format Sonar : décalage collé, sans deux-points — « +0000 », pas « +00:00 »."""
    return (NOW - timedelta(days=days_ago)).strftime("%Y-%m-%dT%H:%M:%S+0000")


# --------------------------------------------------------------------------
# Le parc
# --------------------------------------------------------------------------
#
#   sain        — un projet ordinaire, toutes mesures présentes
#   develop     — jamais analysé sur main : invisible sans project_branches
#   jamais      — créé, jamais analysé du tout
#   sans-couv   — analysé, aucune mesure de couverture
#   legacy      — new_* sous `periods` (ancienne forme)
#   un-point    — un seul point d'historique : Δ indéfini, surtout pas 0
#   trou        — une valeur d'historique vide : Δ indéfini aussi

PROJECTS = [
    {"key": "org:sain", "name": "Service sain", "analysisDate": iso(2)},
    {"key": "org:develop", "name": "Scanne develop", "analysisDate": None},
    {"key": "org:jamais", "name": "Jamais analyse", "analysisDate": None},
    {"key": "org:sans-couv", "name": "Sans couverture", "analysisDate": iso(200)},
    {"key": "org:legacy", "name": "Sonar ancien", "analysisDate": iso(10)},
    {"key": "org:un-point", "name": "Une seule analyse", "analysisDate": iso(5)},
    {"key": "org:trou", "name": "Historique troue", "analysisDate": iso(7)},
]

BRANCHES = {
    "org:sain": [{"name": "main", "isMain": True, "type": "BRANCH", "analysisDate": iso(2)}],
    # Le scan vit sur develop, et main n'a jamais été analysée. C'est le projet
    # que search_projects rend indiscernable d'un projet mort.
    "org:develop": [
        {"name": "main", "isMain": True, "type": "BRANCH", "analysisDate": None},
        {"name": "develop", "isMain": False, "type": "BRANCH", "analysisDate": iso(1)},
    ],
    "org:jamais": [{"name": "main", "isMain": True, "type": "BRANCH", "analysisDate": None}],
    "org:sans-couv": [{"name": "main", "isMain": True, "type": "BRANCH", "analysisDate": iso(200)}],
    "org:legacy": [{"name": "main", "isMain": True, "type": "BRANCH", "analysisDate": iso(10)}],
    "org:un-point": [{"name": "main", "isMain": True, "type": "BRANCH", "analysisDate": iso(5)}],
    "org:trou": [{"name": "main", "isMain": True, "type": "BRANCH", "analysisDate": iso(7)}],
}

# value pour les métriques ordinaires ; period.value pour les new_*. Un projet
# porte la forme `periods` (liste) pour que les deux chemins soient parcourus.
MEASURES = {
    "org:sain": {
        "ncloc": "12000", "files": "300", "coverage": "64.2", "lines_to_cover": "5000",
        "sqale_index": "4800", "sqale_debt_ratio": "2.1", "sqale_rating": "1.0",
        "bugs": "12", "vulnerabilities": "1", "code_smells": "430", "violations": "443",
        "duplicated_lines_density": "3.4", "alert_status": "OK",
        "new_lines": "800", "new_violations": "9", "new_coverage": "71.0",
        "new_duplicated_lines_density": "1.2",
    },
    "org:develop": {
        "ncloc": "5400", "coverage": "0.0", "lines_to_cover": "900",
        "sqale_index": "9100", "sqale_debt_ratio": "8.8", "bugs": "40",
        "new_lines": "1200", "new_violations": "77", "new_coverage": "0.0",
    },
    "org:sans-couv": {
        # Pas de clé coverage du tout : le cas « absent ≠ zéro ».
        "ncloc": "3000", "sqale_index": "20000", "sqale_debt_ratio": "22.0",
        "bugs": "80", "code_smells": "900",
    },
    "org:legacy": {
        "ncloc": "7000", "coverage": "40.0", "sqale_index": "3000",
        "new_lines": "300", "new_violations": "15",
    },
    "org:un-point": {"ncloc": "900", "coverage": "10.0", "sqale_index": "600"},
    "org:trou": {"ncloc": "2500", "coverage": "55.0", "sqale_index": "1500"},
}

# La liaison DevOps n'existe que pour les projets importés depuis GitLab ; les
# autres répondent 404, comme une vraie instance. Les liens sont saisis à la
# main, sous plusieurs formes d'URL.
BINDINGS = {
    "org:sain": {"key": "gitlab-ocsin", "alm": "gitlab", "repository": "101",
                 "url": "https://gitlab.example.com/api/v4", "monorepo": False},
    "org:develop": {"key": "gitlab-ocsin", "alm": "gitlab", "repository": "102",
                    "url": "https://gitlab.example.com/api/v4", "monorepo": False},
}

LINKS = {
    "org:sain": [{"id": "1", "name": "Dépôt", "type": "scm",
                  "url": "git@gitlab.example.com:org/sain.git"}],
    "org:legacy": [{"id": "2", "name": "Home", "type": "homepage",
                    "url": "https://gitlab.example.com/org/legacy/-/tree/main"}],
}

NEW_METRICS = {"new_lines", "new_violations", "new_coverage", "new_bugs",
               "new_vulnerabilities", "new_code_smells", "new_duplicated_lines_density"}

# Historique : (date, valeur). Une valeur vide se dit "".
HISTORY = {
    "org:sain": {
        "ncloc": [(90, "10000"), (45, "11000"), (2, "12000")],
        "sqale_index": [(90, "4000"), (45, "4400"), (2, "4800")],
    },
    "org:un-point": {"ncloc": [(5, "900")], "sqale_index": [(5, "600")]},
    "org:trou": {
        "ncloc": [(60, ""), (7, "2500")],
        "sqale_index": [(60, "1200"), (7, "1500")],
    },
}

METRIC_KEYS = sorted({k for m in MEASURES.values() for k in m} | {
    "complexity", "cognitive_complexity", "comment_lines_density", "line_coverage",
    "branch_coverage", "uncovered_lines", "tests", "test_failures", "test_errors",
    "skipped_tests", "test_success_density", "reliability_rating", "security_rating",
    "security_hotspots", "security_hotspots_reviewed", "new_bugs",
    "new_vulnerabilities", "new_code_smells",
})
# Retirée du parc à dessein : l'outil doit la négocier hors de sa requête plutôt
# que de laisser measures/search répondre 400 et perdre les treize autres.
UNKNOWN_METRIC = "cognitive_complexity"
METRIC_KEYS = [k for k in METRIC_KEYS if k != UNKNOWN_METRIC]


def measure_entry(key, metric, value):
    """new_* loge sa valeur sous period — ou periods sur les versions anciennes."""
    entry = {"component": key, "metric": metric}
    if metric not in NEW_METRICS:
        entry["value"] = value
    elif key == "org:legacy":
        entry["periods"] = [{"index": 1, "value": value}]
    else:
        entry["period"] = {"index": 1, "value": value}
    return entry


class Flaky:
    """Un 429 puis un 502, une seule fois chacun, sur des endpoints différents."""

    def __init__(self):
        self.lock = threading.Lock()
        self.throttled = False
        self.broken = False

    def hit(self, path):
        with self.lock:
            if path.endswith("project_branches/list") and not self.throttled:
                self.throttled = True
                return 429
            if path.endswith("measures/component") and not self.broken:
                self.broken = True
                return 502
        return None


FLAKY = Flaky()


class Handler(BaseHTTPRequestHandler):

    def log_message(self, *a):
        pass

    def send_json(self, payload, status=200, headers=None):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def error(self, status, msg):
        self.send_json({"errors": [{"msg": msg}]}, status)

    def do_GET(self):
        u = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(u.query).items()}
        path = u.path.lstrip("/")

        code = FLAKY.hit(path)
        if code == 429:
            self.send_response(429)
            self.send_header("Retry-After", "1")
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        if code == 502:
            self.error(502, "Bad Gateway")
            return

        handler = getattr(self, "at_" + path.replace("/", "_").replace("api_", "", 1), None)
        if handler is None:
            self.error(404, "Unknown url: /" + path)
            return
        handler(q)

    # -- identité ---------------------------------------------------------

    def at_system_status(self, q):
        self.send_json({"id": "FAUX", "version": "26.8.0.126808", "status": "UP"})

    def at_authentication_validate(self, q):
        self.send_json({"valid": True})

    def at_users_current(self, q):
        # Pas de permission globale : l'audit doit conclure au point aveugle.
        self.send_json({"login": "auditeur", "name": "Auditeur",
                        "permissions": {"global": []}, "groups": ["sonar-users"]})

    # -- métriques --------------------------------------------------------

    def at_metrics_search(self, q):
        page = int(q.get("p", "1"))
        size = int(q.get("ps", "100"))
        chunk = METRIC_KEYS[(page - 1) * size: page * size]
        self.send_json({
            "paging": {"pageIndex": page, "pageSize": size, "total": len(METRIC_KEYS)},
            "metrics": [{"key": k, "name": k, "type": "INT"} for k in chunk],
        })

    # -- inventaire -------------------------------------------------------

    def at_components_search_projects(self, q):
        page = int(q.get("p", "1"))
        size = int(q.get("ps", "100"))
        rows = []
        for p in PROJECTS[(page - 1) * size: page * size]:
            c = {"key": p["key"], "name": p["name"], "qualifier": "TRK"}
            # search_projects ne connaît que la branche principale : un projet
            # scanné ailleurs ressort sans date, comme un projet mort.
            if p["analysisDate"]:
                c["analysisDate"] = p["analysisDate"]
            rows.append(c)
        self.send_json({
            "paging": {"pageIndex": page, "pageSize": size, "total": len(PROJECTS)},
            "components": rows,
        })

    def at_projects_search(self, q):
        # Le total réel demande 'Administer System'. Ce token ne l'a pas, donc
        # l'écart de périmètre doit être rapporté comme inconnu, pas comme nul.
        self.error(403, "Insufficient privileges")

    def at_project_branches_list(self, q):
        key = q.get("project", "")
        if key not in BRANCHES:
            self.error(404, "Component key '%s' not found" % key)
            return
        branches = []
        for b in BRANCHES[key]:
            row = {"name": b["name"], "isMain": b["isMain"], "type": b["type"]}
            if b["analysisDate"]:
                row["analysisDate"] = b["analysisDate"]
            branches.append(row)
        self.send_json({"branches": branches})

    def at_measures_search(self, q):
        asked = [m for m in q.get("metricKeys", "").split(",") if m]
        unknown = [m for m in asked if m not in METRIC_KEYS]
        if unknown:
            # Une seule clé inconnue fait échouer la requête entière : c'est la
            # raison d'être de la négociation de métriques.
            self.error(400, "Following metrics are not found: " + ",".join(unknown))
            return
        keys = [k for k in q.get("projectKeys", "").split(",") if k]
        out = []
        for k in keys:
            for metric, value in MEASURES.get(k, {}).items():
                if metric in asked:
                    out.append(measure_entry(k, metric, value))
        self.send_json({"measures": out})

    def at_measures_component(self, q):
        key = q.get("component", "")
        asked = [m for m in q.get("metricKeys", "").split(",") if m]
        if key not in MEASURES:
            self.error(404, "Component key '%s' not found" % key)
            return
        out = [measure_entry(key, m, v) for m, v in MEASURES[key].items() if m in asked]
        for e in out:
            e.pop("component", None)
        self.send_json({"component": {"key": key, "measures": out}})

    def at_measures_search_history(self, q):
        key = q.get("component", "")
        asked = [m for m in q.get("metrics", "").split(",") if m]
        series = HISTORY.get(key, {})
        self.send_json({
            "paging": {"pageIndex": 1, "pageSize": 1000, "total": 3},
            "measures": [
                {"metric": m,
                 "history": [{"date": iso(d), "value": v} for d, v in series.get(m, [])]}
                for m in asked
            ],
        })

    # -- signaux d'activité -----------------------------------------------

    def at_project_analyses_search(self, q):
        self.send_json({
            "paging": {"pageIndex": 1, "pageSize": 500, "total": 3},
            "analyses": [
                {"key": "a3", "date": iso(2), "projectVersion": "1.4",
                 "events": [{"key": "e2", "category": "VERSION", "name": "1.4"}]},
                {"key": "a2", "date": iso(40), "projectVersion": "1.3",
                 "events": [{"key": "e3", "category": "QUALITY_PROFILE",
                             "name": "Sonar way (Java) modifié"}]},
                {"key": "a1", "date": iso(88), "projectVersion": "1.2",
                 "events": [{"key": "e1", "category": "VERSION", "name": "1.2"}]},
            ],
        })

    def at_issues_search(self, q):
        # total à la racine, pas sous paging : la singularité de cet endpoint.
        payload = {"total": 443, "p": 1, "ps": 1, "issues": []}
        if "author" in q.get("facets", ""):
            payload["facets"] = [{"property": "author", "values": [
                {"val": "alice@example.com", "count": 300},
                {"val": "bob@example.com", "count": 100},
                {"val": "carol@example.com", "count": 43},
            ]}]
        elif "severities" in q.get("facets", ""):
            payload["facets"] = [{"property": "severities", "values": [
                {"val": "MAJOR", "count": 400}, {"val": "BLOCKER", "count": 43}]}]
        self.send_json(payload)

    def at_settings_values(self, q):
        self.send_json({"settings": [
            {"key": "sonar.exclusions", "values": ["**/generated/**"], "inherited": False}]})

    def at_qualitygates_get_by_project(self, q):
        self.send_json({"qualityGate": {"id": "1", "name": "Sonar way", "default": True}})

    def at_qualityprofiles_search(self, q):
        self.send_json({"profiles": [
            {"key": "p1", "name": "Sonar way", "language": "java", "languageName": "Java"}]})

    def at_components_tree(self, q):
        self.send_json({
            "paging": {"pageIndex": 1, "pageSize": 1, "total": 1},
            "components": [{"key": q.get("component", "") + ":src/Main.java",
                            "name": "Main.java", "qualifier": "FIL"}],
        })

    def at_alm_settings_get_binding(self, q):
        b = BINDINGS.get(q.get("project"))
        if b is None:
            self.error(404, "No binding for project '%s'" % q.get("project"))
            return
        self.send_json(b)

    def at_project_links_search(self, q):
        self.send_json({"links": LINKS.get(q.get("projectKey"), [])})

    def at_sources_scm(self, q):
        # Une entrée par changeset, pas par ligne : lignes 1, 13 et 21 pour un
        # fichier de 30 lignes. Compter les entrées, c'est compter des commits.
        self.send_json({"scm": [
            [1, "alice@example.com", iso(3), "abc123"],
            [13, "bob@example.com", iso(60), "def456"],
            [21, "alice@example.com", iso(400), "ghi789"],
        ]})


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9099
    server = HTTPServer(("127.0.0.1", port), Handler)
    server.serve_forever()


if __name__ == "__main__":
    main()
