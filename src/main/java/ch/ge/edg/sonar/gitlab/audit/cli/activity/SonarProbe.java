package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.text;
import static ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab.enc;
import static ch.ge.edg.sonar.gitlab.audit.rest.Query.params;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/** Les fichiers surveillés à la racine, et ce que la CI dit de Sonar. */
final class SonarProbe {

  private static final List<String> WATCHED =
      List.of(".gitlab-ci.yml", "README.md", "CODEOWNERS", "Dockerfile", "renovate.json");

  private final Gitlab gitlab;
  private final SonarCache cache;
  private final CiConfig ci;

  SonarProbe(Gitlab gitlab, SonarCache cache, CiConfig ci) {
    this.gitlab = gitlab;
    this.cache = cache;
    this.ci = ci;
  }

  /**
   * Les cinq fichiers surveillés sont tous à la racine, et l'arbre racine les liste tous d'un coup.
   * Cinq sondes HEAD par projet — une par fichier — répondaient à la même question pour cinq fois
   * le prix.
   */
  Practices.Sonar measure(AuditedProject a) {
    Set<String> root = rootEntries(a.project());
    List<String> files = WATCHED.stream().filter(root::contains).toList();
    CiFacts hit = cache.get(cache.keyFor(a));
    if (hit != null) return new Practices.Sonar(files, hit, true);
    return new Practices.Sonar(files, sonar(a, root), false);
  }

  /** Noms des entrées à la racine de la branche par défaut, en un appel. */
  private Set<String> rootEntries(GitlabProject p) {
    Set<String> names = new TreeSet<>();
    for (JsonNode e :
        gitlab.paged("projects/" + p.id() + "/repository/tree", params("ref", p.defaultBranch()))) {
      names.add(text(e, "name"));
    }
    return names;
  }

  /**
   * Ce que la CI dit de Sonar, et sous quelle clé.
   *
   * <p>Le résultat est mis en cache sur disque, indexé par la tête de la branche par défaut : tant
   * que le dépôt n'a pas bougé, sa clé Sonar est la même qu'au dernier passage, et la reposer à
   * GitLab ne peut que redonner la même réponse. C'est la seule partie de l'audit dont la réponse
   * est stable entre deux exécutions — le reste est fenêtré, donc périmé par construction.
   */
  private CiFacts sonar(AuditedProject a, Set<String> root) {
    GitlabProject p = a.project();
    CiFacts f = new CiFacts();
    // sonar-project.properties porte la clé littérale quand il existe :
    // c'est la source la moins ambiguë, et elle coûte un appel.
    if (root.contains("sonar-project.properties")) properties(p, f);
    if (root.contains(".gitlab-ci.yml")) pipeline(p, f);
    // Sans clé explicite, le scanner Maven prend groupId:artifactId du
    // pom. C'est le cas du composant Sonar partagé quand project_key est
    // vide, et la forme des clés de l'instance (ch.ge.x.y:artefact).
    // Lu même quand la CI donne une clé : c'est une seconde méthode de
    // jointure, notée à part, et le <name> du pom est le nom affiché.
    if (root.contains("pom.xml")) pom(p, f);
    cache.put(cache.keyFor(a), f);
    return f;
  }

  private void properties(GitlabProject p, CiFacts f) {
    String body = raw(p, "sonar-project.properties");
    if (body == null) return;
    f.sonarKey = SonarKeys.firstMatch(SonarKeys.SONAR_KEY_PROP, body);
    if (f.sonarKey != null) f.keySource = "sonar-project.properties";
    f.ciSonar = true;
  }

  private void pipeline(GitlabProject p, CiFacts f) {
    CiConfig.Ci yaml = ci.read(p);
    if (yaml == null) return;
    f.ciRoute = yaml.route();
    String lower = yaml.yaml().toLowerCase(Locale.ROOT);
    if (lower.contains("sonar")) f.ciSonar = true;
    f.ciSecurity =
        lower.contains("sast")
            || lower.contains("secret-detection")
            || lower.contains("dependency-scanning");
    if (f.sonarKey == null) SonarKeys.ciKey(yaml, p, f);
  }

  private void pom(GitlabProject p, CiFacts f) {
    String body = raw(p, "pom.xml");
    Pom read = body == null ? null : Pom.parse(body);
    if (read == null) return;
    f.pomName = read.name();
    if (read.key() == null) f.pomUnresolved = read.rawKey();
    else f.pomKey = read.key();
  }

  /** Le contenu d'un fichier de la branche par défaut, ou null s'il ne se lit pas. */
  private @Nullable String raw(GitlabProject p, String file) {
    RestResponse r =
        gitlab.get(
            "projects/" + p.id() + "/repository/files/" + enc(file) + "/raw",
            Map.of("ref", p.defaultBranch()));
    return r.is(200) ? r.body() : null;
  }
}
