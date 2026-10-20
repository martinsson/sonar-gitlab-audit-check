package ch.ge.edg.sonar.gitlab.audit.cli.doralocate;

import ch.ge.edg.sonar.gitlab.audit.dora.RepoPath;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Le projet GitLab de départ.
 *
 * @param path le dernier segment du chemin, qui nomme les dossiers d'extraction proposés
 */
record GitlabProject(String id, String pathWithNamespace, String path) {

  /**
   * Le projet désigné par « groupe/projet », son URL web ou git, ou son id numérique. Introuvable,
   * rien de la suite n'a de sens : l'outil s'arrête.
   */
  public static GitlabProject find(Gitlab gitlab, String ref) throws Stop {
    String path = "projects/" + Gitlab.enc(RepoPath.keepCase(ref));
    Gitlab.Response r = Access.checked(gitlab.get(path, Map.of()), gitlab);
    if (!r.ok()) throw new Stop("/api/v4/" + path + " : " + Access.why(r));
    JsonNode p = r.json();
    return new GitlabProject(
        p.path("id").asText(),
        p.path("path_with_namespace").asText(),
        p.path("path").asText("projet"));
  }
}
