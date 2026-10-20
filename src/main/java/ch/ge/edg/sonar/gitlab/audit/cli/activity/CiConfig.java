package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.enc;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.params;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.isBlank;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.SonarKeys.firstMatch;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.SonarKeys.unquote;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Le piège de ce dépôt : chez nous, le job Sonar n'est presque jamais dans le .gitlab-ci.yml du
 * projet. Il est dans un template partagé, inclus par `include: project:`, lequel en inclut souvent
 * un autre. Lire le fichier brut ne voit rien et conclut « pas de Sonar » — sur la majorité du
 * parc.
 *
 * <p>Chasser les includes à la main était la mauvaise réponse : il faut résoudre les chemins de
 * projet, suivre l'imbrication, gérer `ref` absent, `file` en liste, `component`, `local`, `remote`
 * — soit réimplémenter le moteur d'inclusion de GitLab, contre une cible qui bouge. GitLab le fait
 * déjà et le rend : ci/lint renvoie merged_yaml, l'arbre d'inclusion entièrement déplié, en un
 * appel et sans erreur d'interprétation possible.
 *
 * <p>La chasse manuelle reste, corrigée, pour les instances qui refusent ci/lint — le jeton n'y a
 * parfois que Reporter. C'est un repli, pas la route : il ne voit que ce qu'il sait lire.
 */
final class CiConfig {

  /** Le YAML de CI effectivement exécuté, et par quelle route on l'a obtenu. */
  record Ci(String yaml, String route) {}

  /** Un include ne dépasse pas trois niveaux chez nous ; la borne évite les cycles. */
  private static final int MAX_INCLUDE_DEPTH = 3;

  /**
   * Découpe le YAML en entrées `- project: …`, chacune jusqu'à la suivante. Sans ce découpage, un
   * motif unique recolle les champs de trois includes différents — le défaut corrigé ici.
   */
  private static final Pattern INCLUDE_ENTRY =
      Pattern.compile(
          "-\\s*project:[\\s\\S]*?(?=\\n\\s*-\\s*(?:project|local|remote|template|component):|\\n\\S|\\z)");

  private static final Pattern INCLUDE_PROJECT = Pattern.compile("project:\\s*(\\S+)");
  private static final Pattern INCLUDE_REF = Pattern.compile("(?m)^\\s*ref:\\s*(\\S+)");
  private static final Pattern INCLUDE_FILE =
      Pattern.compile(
          "(?:file:\\s*|^\\s*-\\s*)(['\"]?[\\w./-]+\\.ya?ml['\"]?)\\s*$", Pattern.MULTILINE);

  private final Gitlab gl;
  private final Counters counters;

  /** Cache de résolution project_path -> ID, rempli au fur et à mesure. */
  private final Map<String, String> projPathToId = new ConcurrentHashMap<>();

  /** Contenu brut des templates inclus. Quelques templates servent tout le parc. */
  private final Map<String, String> includeCache = new ConcurrentHashMap<>();

  /** ci/lint refusé une fois l'est pour tout le parc : le droit est sur le jeton. */
  private volatile boolean lintRefused;

  CiConfig(Gitlab gl, Counters counters, List<GitlabProject> all) {
    this.gl = gl;
    this.counters = counters;
    for (GitlabProject p : all) {
      if (!p.path().isEmpty()) projPathToId.put(p.path(), String.valueOf(p.id()));
    }
  }

  /** Le YAML de CI du projet, ou null quand ni ci/lint ni le fichier brut ne le donnent. */
  @Nullable Ci read(GitlabProject p) {
    if (!lintRefused) {
      Ci merged = lint(p);
      if (merged != null) return merged;
    }
    Gitlab.Response raw =
        gl.get(
            "projects/" + p.id() + "/repository/files/" + enc(".gitlab-ci.yml") + "/raw",
            Map.of("ref", p.defaultBranch()));
    String body = raw.body();
    if (raw.status() != 200 || body == null) return null;
    counters.ciByFallbackN.incrementAndGet();
    StringBuilder all = new StringBuilder(body);
    chaseIncludes(body, new Chase(all, new HashSet<>()), 0);
    return new Ci(all.toString(), "includes suivis");
  }

  private @Nullable Ci lint(GitlabProject p) {
    Gitlab.Response lint =
        gl.get(
            "projects/" + p.id() + "/ci/lint",
            params(
                "ref", p.defaultBranch(),
                "content_ref", p.defaultBranch(),
                "include_merged_yaml", "true"));
    int status = lint.status();
    if (status == 200) {
      JsonNode j = lint.json();
      String merged = j.path("merged_yaml").asText(null);
      if (isBlank(merged)) merged = j.path("content").path("merged_yaml").asText(null);
      if (merged != null && !merged.isBlank()) {
        counters.ciByLintN.incrementAndGet();
        return new Ci(merged, "ci/lint");
      }
      // 200 sans merged_yaml : configuration invalide, ou version
      // de GitLab qui ne le renvoie pas. Les deux se replient.
    } else if (status == 401 || status == 403 || status == 404) {
      // Refusé une fois, refusé partout : le droit se donne au
      // jeton, pas au projet. Insister coûterait un appel perdu
      // par projet du parc.
      lintRefused = true;
      counters.lintRefusedStatus = status;
    }
    return null;
  }

  /**
   * Repli : suivre soi-même les `include: project:`, en descendant dans les templates qui en
   * incluent d'autres.
   *
   * <p>Le motif précédent était en DOTALL avec des `.*` gourmands, donc il matchait une seule fois
   * sur tout le fichier et recollait le `project:` du premier include, le `ref:` du deuxième et le
   * `file:` du troisième. Trois includes donnaient un match, faux, vers un fichier qui n'existe pas
   * — un 404 payé pour ne rien apprendre. On découpe donc par entrée, et `ref:` est facultatif :
   * GitLab prend HEAD quand il manque.
   */
  private void chaseIncludes(String yaml, Chase chase, int depth) {
    if (depth >= MAX_INCLUDE_DEPTH) return;
    Matcher entry = INCLUDE_ENTRY.matcher(yaml);
    while (entry.find()) {
      String block = entry.group();
      String projPath = unquote(firstMatch(INCLUDE_PROJECT, block));
      if (projPath == null) continue;
      String maybeRef = unquote(firstMatch(INCLUDE_REF, block));
      // `ref:` est facultatif : sans lui GitLab prend la tête du dépôt
      // inclus. L'ancien motif l'exigeait, donc ne voyait pas ces
      // includes-là du tout.
      String incRef = maybeRef == null ? "HEAD" : maybeRef;
      String projId = resolveProjectPathCached(projPath);
      if (projId != null) chaseFiles(block, projId, incRef, chase, depth);
    }
  }

  /** Une chasse aux includes : le YAML recollé, et les fichiers déjà lus pour ne pas boucler. */
  private record Chase(StringBuilder sink, Set<String> seen) {}

  /**
   * `file:` accepte une liste : un template Sonar et un template build dans la même entrée, ce que
   * l'ancien motif ne voyait pas.
   */
  private void chaseFiles(String block, String projId, String incRef, Chase chase, int depth) {
    Matcher files = INCLUDE_FILE.matcher(block);
    while (files.find()) {
      String incFile = unquote(files.group(1));
      if (incFile == null) continue;
      String cacheKey = projId + ":" + incFile + ":" + incRef;
      if (!chase.seen().add(cacheKey)) continue;
      String body = includeCache.computeIfAbsent(cacheKey, k -> fetchRaw(projId, incFile, incRef));
      if (body.isEmpty()) continue;
      chase.sink().append('\n').append(body);
      chaseIncludes(body, chase, depth + 1);
    }
  }

  private String fetchRaw(String projId, String file, String ref) {
    Gitlab.Response fr =
        gl.get(
            "projects/" + enc(projId) + "/repository/files/" + enc(file) + "/raw",
            Map.of("ref", ref));
    String body = fr.body();
    return fr.status() == 200 && body != null ? body : "";
  }

  /**
   * Résout le path complet d'un projet GitLab en son ID numérique, avec cache pour éviter de
   * refaire la même requête API.
   */
  private @Nullable String resolveProjectPathCached(String projPath) {
    String cached = projPathToId.get(projPath);
    if (cached != null) return cached.isEmpty() ? null : cached;
    if (isNumericId(projPath)) {
      projPathToId.put(projPath, projPath);
      return projPath;
    }
    Gitlab.Response proj = gl.get("projects/" + enc(projPath), Map.of("per_page", "1"));
    if (proj.status() == 200 && proj.body() != null) {
      JsonNode node = proj.json();
      if (node.hasNonNull("id")) {
        String id = String.valueOf(node.path("id").asLong());
        projPathToId.put(projPath, id);
        return id;
      }
    }
    projPathToId.put(projPath, "");
    return null;
  }

  /** Un include peut désigner le projet par son ID numérique plutôt que par son chemin. */
  private static boolean isNumericId(String s) {
    try {
      Long.parseLong(s);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
