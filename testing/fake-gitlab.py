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
from urllib.parse import unquote

NOW = datetime.now(timezone.utc)
LINT_FORBIDDEN = False


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
            # Un revert sur treize, et un « revert » en milieu de phrase qui ne doit
            # surtout pas compter : le motif est ancré au début du titre.
            "title": ("Revert \"correction hâtive\"" if i % 13 == 0
                      else "Documente le revert de la migration" if i % 17 == 0
                      else f"Modification {i}"),
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


def pipelines_for(pid):
    """
    Pipelines de la branche par défaut, du plus récent au plus ancien comme le
    fait GitLab. Le jeu contient les trois cas qui décident du calcul :

      * deux échecs consécutifs séparés par un pipeline annulé — UN incident,
        pas deux, et l'annulation ne clôt pas la panne ;
      * un second incident isolé, pour que la médiane porte sur deux durées ;
      * sur un projet sur deux, un échec final jamais suivi d'un vert : incident
        réel, durée inconnue, à compter sans l'inventer.

    Incidents attendus : 2 (ou 3), médiane de retour au vert 48 h.
    """
    runs = [(40, "success"), (30, "failed"), (29, "canceled"), (28, "failed"),
            (27, "success"), (10, "failed"), (9, "success"), (2, "success")]
    if pid % 2 == 1:
        runs.append((1, "failed"))
    runs.sort(key=lambda r: -r[0])
    out = [{"id": pid * 100 + i, "status": st, "created_at": iso(d), "ref": "main"}
           for i, (d, st) in enumerate(runs)]
    return list(reversed(out))


# Le cas qui a motivé la réécriture : chez le client, le job Sonar n'est presque
# jamais dans le .gitlab-ci.yml du projet. Il est dans un template partagé, inclus
# par `include: project:`, lequel en inclut parfois un autre. L'ancien mock servait
# un .gitlab-ci.yml contenant littéralement « sonar » pour tout le monde, donc la
# chasse aux includes n'était jamais exercée — et son motif cassé passait le test.
#
# Trois formes de racine, pour trois façons de rater la détection :
#   * SANS_SONAR  — includes seuls, la clé est deux niveaux plus bas ;
#   * SONAR_BRUT  — le job est en clair dans le fichier du projet ;
#   * PAS_DE_CI   — pas de .gitlab-ci.yml du tout.
CI_INCLUDES = """include:
  - project: 'outils/templates-ci'
    ref: main
    file: '/qualite/analyse.gitlab-ci.yml'
  - project: 'outils/templates-ci'
    file:
      - '/build/java.yml'
      - '/build/docker.yml'
  - local: /ci/local.yml
stages:
  - build
  - test
"""

CI_SONAR_BRUT = """stages:
  - test
sonarqube-check:
  script:
    - sonar-scanner -Dsonar.projectKey=equipe-a_monolithe
"""

# Le template partagé n'a pas le job : il inclut celui qui l'a. Un seul niveau de
# chasse ne trouve rien, et c'est exactement le cas que l'ancien code manquait.
TEMPLATES = {
    "/qualite/analyse.gitlab-ci.yml": """include:
  - project: 'outils/templates-ci'
    ref: main
    file: '/qualite/scanner.yml'
""",
    "/qualite/scanner.yml": """sonarqube-check:
  image: sonarsource/sonar-scanner-cli
  script:
    - sonar-scanner -Dsonar.projectKey=${CI_PROJECT_PATH_SLUG}
""",
    "/build/java.yml": "build-java:\n  script:\n    - mvn -B package\n",
    "/build/docker.yml": "build-image:\n  script:\n    - docker build .\n",
    "/sast.yml": "include:\n  - template: Security/SAST.gitlab-ci.yml\n",
}

# id -> contenu de .gitlab-ci.yml à la racine. Absent = pas de CI.
CI_ROOT = {
    1: CI_INCLUDES,      # Sonar à deux niveaux d'include, clé en variable
    2: CI_INCLUDES,
    3: CI_SONAR_BRUT,    # Sonar en clair, clé littérale
    11: CI_INCLUDES,
    13: CI_INCLUDES,
    14: CI_SONAR_BRUT,
}


def merged_yaml(pid):
    """Ce que GitLab renvoie dans merged_yaml : l'arbre d'inclusion déplié."""
    root = CI_ROOT.get(pid)
    if root is None:
        return None
    out = [root]
    if root is CI_INCLUDES:
        out += [TEMPLATES["/qualite/scanner.yml"], TEMPLATES["/build/java.yml"],
                TEMPLATES["/build/docker.yml"]]
    return "\n".join(out)


def tree_for(pid):
    names = ["README.md"]
    if pid in CI_ROOT:
        names.append(".gitlab-ci.yml")
    if pid % 2:
        names += ["CODEOWNERS", "Dockerfile"]
    # Un seul projet porte la clé en clair dans sonar-project.properties : c'est
    # la source la moins ambiguë, et elle doit gagner sur la CI.
    if pid == 11:
        names.append("sonar-project.properties")
    return [{"id": f"{i:040x}", "name": n, "type": "blob", "path": n}
            for i, n in enumerate(sorted(names))]


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

        # Résolution d'un chemin de projet en ID : c'est ainsi que le repli
        # trouve le dépôt de templates, qui n'est pas dans le périmètre audité.
        m2 = re.match(r"/api/v4/projects/([^/]+)$", path)
        if m2 and not m2.group(1).isdigit():
            if unquote(m2.group(1)) == "outils/templates-ci":
                return self._send(200, {"id": 99, "path_with_namespace": "outils/templates-ci"})
            return self._send(404, {"message": "404 Not Found"})

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
            return self._send(200, pipelines_for(pid))
        if path.endswith("/environments"):
            return self._send(200, [], {"X-Total": 0 if pid in (4, 11) else 2})
        if path.endswith("/dora/metrics"):
            return self._send(200, [{"date": "2026-07-01", "value": 12}])
        if path.endswith("/repository/tree"):
            return self._send(200, tree_for(pid), {"X-Next-Page": ""})

        # ci/lint : la route qui rend l'arbre d'inclusion déjà déplié. Refusée
        # sur le projet 13, pour que le repli — la chasse aux includes à la main —
        # soit exercé lui aussi, et pas seulement en théorie.
        if path.endswith("/ci/lint"):
            if LINT_FORBIDDEN:
                return self._send(403, {"message": "403 Forbidden"})
            merged = merged_yaml(pid)
            if merged is None:
                return self._send(200, {"valid": False, "errors": ["file not found"]})
            return self._send(200, {"valid": True, "errors": [], "merged_yaml": merged})

        if path.endswith("/raw"):
            # Le fichier demandé, pas un fichier passe-partout : servir du YAML
            # contenant « sonar » quoi qu'on demande masquait tout le sujet.
            m = re.search(r"/repository/files/([^/]+)/raw", path)
            wanted = unquote(m.group(1)) if m else ""
            if wanted == "sonar-project.properties":
                return self._send(200, "sonar.projectKey=equipe-c_mono-auteur\nsonar.sources=src\n")
            if wanted == ".gitlab-ci.yml":
                root = CI_ROOT.get(pid)
                return self._send(200, root) if root else self._send(404, {})
            if wanted in TEMPLATES:
                return self._send(200, TEMPLATES[wanted])
            return self._send(404, {"message": "404 Not Found"})

        if "/repository/files/" in path:
            # HEAD d'existence : doit répondre comme l'arbre, sinon le mock se
            # contredit lui-même selon la route empruntée.
            m = re.search(r"/repository/files/([^/]+)", path)
            wanted = unquote(m.group(1)) if m else ""
            present = {e["name"] for e in tree_for(pid)}
            return self._send(200 if wanted in present else 404, {})

        return self._send(404, {"message": "404 Not Found"})


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8099
    # LINT=refuse : l'instance interdit ci/lint au jeton. Le repli doit alors
    # trouver la même chose, en plus d'appels — c'est ce qu'on veut vérifier.
    LINT_FORBIDDEN = len(sys.argv) > 2 and sys.argv[2] == "refuse"
    HTTPServer(("127.0.0.1", port), Handler).serve_forever()
