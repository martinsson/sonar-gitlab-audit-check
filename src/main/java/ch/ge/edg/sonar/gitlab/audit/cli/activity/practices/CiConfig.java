package ch.ge.edg.sonar.gitlab.audit.cli.activity.practices;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.practices.SonarKeys.firstMatch;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.practices.SonarKeys.unquote;
import static ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab.enc;
import static ch.ge.edg.sonar.gitlab.audit.rest.Query.params;
import static ch.ge.edg.sonar.gitlab.audit.text.Text.isBlank;

import ch.ge.edg.sonar.gitlab.audit.cli.activity.model.GitlabProject;
import ch.ge.edg.sonar.gitlab.audit.cli.activity.model.Practices;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
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

  /** Le statut qui refuse ci/lint : le droit se donne au jeton, pas au projet. */
  private static final Set<Integer> LINT_REFUSED = Set.of(401, 403, 404);

  /** `ref:` absent : GitLab prend la tête du dépôt inclus. */
  private static final String HEAD = "HEAD";

  private static final String CI_FILE = ".gitlab-ci.yml";

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

  /** Un fichier d'un projet GitLab, à une révision : ce qu'un `include` désigne. */
  private record RepoFile(String projectId, String path, String ref) {}

  /** Une chasse aux includes : le YAML recollé, et les fichiers déjà lus pour ne pas boucler. */
  private record Chase(StringBuilder sink, Set<RepoFile> seen) {}

  private final Gitlab gitlab;

  /** Cache de résolution project_path -> ID, rempli au fur et à mesure. */
  private final Map<String, String> projPathToId = new ConcurrentHashMap<>();

  /** Contenu brut des templates inclus. Quelques templates servent tout le parc. */
  private final Map<RepoFile, String> includeCache = new ConcurrentHashMap<>();

  /** ci/lint refusé une fois l'est pour tout le parc : le droit est sur le jeton. */
  private volatile int lintRefusedStatus;

  CiConfig(Gitlab gitlab, List<GitlabProject> all) {
    this.gitlab = gitlab;
    for (GitlabProject p : all) {
      if (!p.path().isEmpty()) projPathToId.put(p.path(), String.valueOf(p.id()));
    }
  }

  /** Le statut qui a refusé ci/lint au jeton, 0 tant qu'il ne l'a pas été. */
  int lintRefusedStatus() {
    return lintRefusedStatus;
  }

  /** Le YAML de CI du projet, ou null quand ni ci/lint ni le fichier brut ne le donnent. */
  @Nullable Ci read(GitlabProject p) {
    Ci merged = lintRefusedStatus == 0 ? lint(p) : null;
    return merged != null ? merged : readWithIncludes(p);
  }

  private @Nullable Ci lint(GitlabProject p) {
    RestResponse lint =
        gitlab.get(
            "projects/" + p.id() + "/ci/lint",
            params(
                "ref", p.defaultBranch(),
                "content_ref", p.defaultBranch(),
                "include_merged_yaml", "true"));
    if (lint.is(200)) return mergedCi(lint.tree());
    if (lint instanceof RestResponse.Answered a && LINT_REFUSED.contains(a.status())) {
      // Refusé une fois, refusé partout. Insister coûterait un appel perdu
      // par projet du parc.
      lintRefusedStatus = a.status();
    }
    return null;
  }

  /**
   * Le merged_yaml d'une réponse ci/lint. Un 200 sans merged_yaml (configuration invalide, ou
   * version de GitLab qui ne le renvoie pas) se replie sur la chasse.
   */
  private static @Nullable Ci mergedCi(JsonNode lint) {
    String merged = mergedYaml(lint);
    return merged == null || merged.isBlank() ? null : new Ci(merged, Practices.Sonar.LINT);
  }

  /** Le merged_yaml à la racine, ou sous `content` selon la version de GitLab. */
  private static @Nullable String mergedYaml(JsonNode lint) {
    String top = lint.path("merged_yaml").asText(null);
    return isBlank(top) ? lint.path("content").path("merged_yaml").asText(null) : top;
  }

  private @Nullable Ci readWithIncludes(GitlabProject p) {
    RepoFile ciFile = new RepoFile(String.valueOf(p.id()), CI_FILE, p.defaultBranch());
    RestResponse raw = requestRaw(ciFile);
    if (!raw.is(200)) return null;
    String body = raw.body();
    StringBuilder all = new StringBuilder(body);
    chaseIncludes(body, new Chase(all, new HashSet<>()), 0);
    return new Ci(all.toString(), Practices.Sonar.FALLBACK);
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
      chaseEntry(entry.group(), chase, depth);
    }
  }

  /** Une entrée `- project: …` : son projet résolu, puis chacun de ses fichiers. */
  private void chaseEntry(String block, Chase chase, int depth) {
    String projPath = unquote(firstMatch(INCLUDE_PROJECT, block));
    if (projPath == null) return;
    String projId = resolveProjectPathCached(projPath);
    if (projId == null) return;
    String ref = includeRef(block);
    // `file:` accepte une liste : un template Sonar et un template build dans la même entrée.
    Matcher files = INCLUDE_FILE.matcher(block);
    while (files.find()) {
      String file = unquote(files.group(1));
      if (file != null) chaseFile(new RepoFile(projId, file, ref), chase, depth);
    }
  }

  private void chaseFile(RepoFile file, Chase chase, int depth) {
    if (!chase.seen().add(file)) return;
    String body = includeCache.computeIfAbsent(file, this::fetchRaw);
    if (body.isEmpty()) return;
    chase.sink().append('\n').append(body);
    chaseIncludes(body, chase, depth + 1);
  }

  private static String includeRef(String block) {
    String ref = unquote(firstMatch(INCLUDE_REF, block));
    return ref == null ? HEAD : ref;
  }

  private String fetchRaw(RepoFile file) {
    RestResponse fr = requestRaw(file);
    return fr.is(200) ? fr.body() : "";
  }

  private RestResponse requestRaw(RepoFile file) {
    return gitlab.get(
        "projects/" + enc(file.projectId()) + "/repository/files/" + enc(file.path()) + "/raw",
        Map.of("ref", file.ref()));
  }

  /**
   * Résout le path complet d'un projet GitLab en son ID numérique, avec cache pour éviter de
   * refaire la même requête API.
   */
  private @Nullable String resolveProjectPathCached(String projPath) {
    String cached = projPathToId.get(projPath);
    if (cached != null) return cached.isEmpty() ? null : cached;
    String id = isNumericId(projPath) ? projPath : fetchProjectId(projPath);
    projPathToId.put(projPath, id == null ? "" : id);
    return id;
  }

  /** L'ID numérique d'un projet désigné par son chemin ; vide si GitLab ne le donne pas. */
  private @Nullable String fetchProjectId(String projPath) {
    RestResponse proj = gitlab.get("projects/" + enc(projPath), Map.of("per_page", "1"));
    if (!proj.is(200)) return null;
    JsonNode node = proj.tree();
    if (!node.hasNonNull("id")) return null;
    return String.valueOf(node.path("id").asLong());
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
