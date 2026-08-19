#!/usr/bin/env python3
"""Faux GitLab, pour les chemins de plantage uniquement.

KNOWLEDGE.md §1 : un mock écrit depuis nos propres croyances ne valide aucune
sémantique — il répond ce qu'on lui demande. Celui-ci ne prétend donc rien
prouver sur l'API GitLab. Il sert à ce qu'un mock sait faire :

  * les branches d'erreur qu'on ne peut pas convoquer à la demande — 403 sur un
    projet, 429 avec Retry-After, GraphQL qui répond `errors`, X-Total absent ;
  * les cas limites qui font sauter le code — dépôt vide, projet sans branche
    par défaut, commits sans parent_ids, auteur sans adresse ;
  * la non-régression : même entrée, même CSV.

Les formes de réponse viennent de la documentation, pas d'une instance. Toute
affirmation sémantique doit être vérifiée ailleurs.
"""
import json
import re
import sys
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer

NOW = datetime.now(timezone.utc)


def iso(days_ago):
    return (NOW - timedelta(days=days_ago)).isoformat().replace("+00:00", "Z")


def project(pid, path, **kw):
    p = {
        "id": pid,
        "path_with_namespace": path,
        "name": path.split("/")[-1],
        "namespace": {"full_path": path.rsplit("/", 1)[0]},
        "default_branch": "main",
        "visibility": "private",
        "created_at": iso(800),
        "last_activity_at": iso(3),
        "archived": False,
        "empty_repo": False,
        "mirror": False,
        "statistics": {"commit_count": 500, "repository_size": 4_000_000},
    }
    p.update(kw)
    return p


# Un parc volontairement biscornu : chaque projet existe pour casser une hypothèse.
PROJECTS = [
    project(1, "equipe-a/service-actif"),
    project(2, "equipe-a/service-calme", last_activity_at=iso(2)),
    project(3, "equipe-a/monolithe", statistics={"commit_count": 40000, "repository_size": 9e8}),
    project(4, "equipe-b/petit-outil", statistics={"commit_count": 12, "repository_size": 30000}),
    project(5, "equipe-b/dormant", last_activity_at=iso(400)),
    project(6, "equipe-b/archive", archived=True),
    project(7, "equipe-c/vide", empty_repo=True),
    project(8, "equipe-c/sans-branche", default_branch=None),
    project(9, "equipe-c/miroir", mirror=True),
    project(10, "equipe-c/robot-seul"),
    project(11, "equipe-c/mono-auteur"),
    project(12, "equipe-d/interdit"),           # 403 sur ses commits
    project(13, "equipe-d/fuite", last_activity_at=iso(300)),  # inactif, mais commits récents
    project(14, "equipe-d/sans-total"),         # pas de X-Total sur les commits
]

# path -> (nb de commits humains, nb d'auteurs distincts, nb de commits robots)
ACTIVITY = {
    1: (60, 4, 3), 2: (9, 2, 0), 3: (300, 12, 40), 4: (7, 1, 0),
    10: (0, 0, 25), 11: (45, 1, 0), 13: (30, 3, 0), 14: (25, 2, 0),
}


def commits_for(pid):
    human, authors, bots = ACTIVITY.get(pid, (0, 0, 0))
    out = []
    for i in range(human):
        who = i % max(authors, 1)
        out.append({
            "id": f"{pid:02x}{i:038x}",
            "author_name": f"Dev {who}",
            # Un commit sur cinq sans adresse : l'identité doit retomber sur le nom.
            "author_email": "" if i % 5 == 0 else f"dev{who}@example.com",
            "committed_date": iso(i % 80),
            # parent_ids parfois absent : le taux de merge doit rester calculable.
            **({"parent_ids": ["a", "b"]} if i % 7 == 0 else {} if i % 11 == 0 else {"parent_ids": ["a"]}),
        })
    for i in range(bots):
        out.append({
            "id": f"b{pid:02x}{i:037x}",
            "author_name": "renovate[bot]",
            "author_email": "renovate@bot.example.com",
            "committed_date": iso(i % 40),
            "parent_ids": ["a"],
        })
    return out


class Handler(BaseHTTPRequestHandler):
    throttled_once = False

    def log_message(self, *a):
        pass

    def _send(self, code, body, headers=None):
        raw = json.dumps(body).encode() if not isinstance(body, (bytes, str)) else (
            body.encode() if isinstance(body, str) else body)
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        for k, v in (headers or {}).items():
            self.send_header(k, str(v))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(raw)

    def do_HEAD(self):
        self.do_GET()

    def do_POST(self):
        # GraphQL répond `errors` : le code doit retomber sur REST sans broncher.
        self.rfile.read(int(self.headers.get("Content-Length", 0)))
        self._send(200, {"errors": [{"message": "field 'lastCommit' doesn't exist"}]})

    def do_GET(self):
        path, _, qs = self.path.partition("?")
        q = dict(p.split("=", 1) for p in qs.split("&") if "=" in p)
        pid = None
        m = re.match(r"/api/v4/projects/(\d+)", path)
        if m:
            pid = int(m.group(1))

        # Un 429 unique, une seule fois, pour exercer Retry-After.
        if not Handler.throttled_once and path.endswith("/repository/commits"):
            Handler.throttled_once = True
            return self._send(429, {"message": "429 Too Many Requests"}, {"Retry-After": "1"})

        if path == "/api/v4/user":
            return self._send(200, {"username": "auditeur", "name": "Auditeur"})
        if path == "/api/v4/metadata":
            return self._send(200, {"version": "17.4.0-ee", "enterprise": True})

        if path == "/api/v4/projects":
            page = int(q.get("page", 1))
            per = int(q.get("per_page", 20))
            chunk = PROJECTS[(page - 1) * per: page * per]
            nxt = page + 1 if page * per < len(PROJECTS) else ""
            return self._send(200, chunk, {"X-Total": len(PROJECTS), "X-Next-Page": nxt})

        if pid == 12:
            return self._send(403, {"message": "403 Forbidden"})

        if path.endswith("/repository/commits"):
            since = q.get("since", "")
            all_c = commits_for(pid)
            per = int(q.get("per_page", 20))
            page = int(q.get("page", 1))
            chunk = all_c[(page - 1) * per: page * per]
            # Projet 14 : X-Total absent. Absent n'est pas zéro.
            headers = {} if pid == 14 else {"X-Total": len(all_c)}
            return self._send(200, chunk, headers)

        if path.endswith("/protected_branches"):
            return self._send(200, [{"name": "main",
                                     "push_access_levels": [{"access_level": 0}]}] if pid % 2 else [])
        if path.endswith("/merge_requests"):
            n = 0 if pid in (11, 14) else 12
            return self._send(200, [{
                "iid": i + 1,
                "author": {"id": 7},
                "merged_by": {"id": 7 if i % 3 == 0 else 8},
                "created_at": iso(20 + i),
                "merged_at": iso(18 + i),
                "user_notes_count": i % 4,
            } for i in range(n)])
        if re.search(r"/merge_requests/\d+/approvals$", path):
            return self._send(200, {"approved_by": [{"user": {"id": 8}}]})
        if path.endswith("/pipelines"):
            return self._send(200, [{"status": "success" if i % 3 else "failed"} for i in range(9)])
        if path.endswith("/environments"):
            return self._send(200, [], {"X-Total": 0 if pid in (4, 11) else 2})
        if path.endswith("/dora/metrics"):
            return self._send(200, [{"date": "2026-07-01", "value": 12}])
        if path.endswith("/raw"):
            return self._send(200, "stages:\n  - test\nsonarqube-check:\n  script: sonar-scanner\n")
        if "/repository/files/" in path:
            return self._send(200 if pid % 2 else 404, {})

        return self._send(404, {"message": "404 Not Found"})


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8099
    HTTPServer(("127.0.0.1", port), Handler).serve_forever()
