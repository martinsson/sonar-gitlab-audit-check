package ch.ge.edg.sonar.gitlab.audit.cli.shared;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.nio.file.Path;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Option;

/**
 * De quoi joindre une instance GitLab. Les options portent un {@code order} : réparties en
 * plusieurs groupes, elles gardent dans l'aide l'ordre où elles ont toujours été listées.
 */
public final class GitlabConnectionOptions {

  @Option(
      names = "--url",
      order = 1,
      defaultValue = "${env:GITLAB_URL}",
      description = "URL de l'instance GitLab")
  @Nullable String url;

  @Option(
      names = "--token",
      order = 2,
      defaultValue = "${env:GITLAB_TOKEN}",
      description = "jeton personnel glpat-... (portée read_api)")
  @Nullable String token;

  @Option(
      names = "--timeout",
      order = 25,
      defaultValue = "30",
      description = "délai HTTP en secondes (défaut : ${DEFAULT-VALUE})")
  int timeout = 30;

  @Option(names = "--insecure", order = 26, description = "ignorer la validation TLS")
  boolean insecure;

  @Option(
      names = "--dump-dir",
      order = 27,
      description = "répertoire où consigner les réponses brutes")
  @Nullable Path dumpDir;

  /** Vide quand l'URL ou le jeton manque : sans eux, aucun appel n'a de sens. */
  public Optional<Gitlab.Connection> connection() {
    String u = url;
    String t = token;
    if (u == null || t == null || u.isBlank() || t.isBlank()) return Optional.empty();
    return Optional.of(new Gitlab.Connection(u, t, timeout, insecure, dumpDir));
  }

  /** Sans jeton : lecture anonyme ; sans URL : {@code fallbackUrl}. */
  public Gitlab.Connection connectionOrAnonymous(String fallbackUrl) {
    String u = url == null || url.isBlank() ? fallbackUrl : url;
    return new Gitlab.Connection(u, token, timeout, insecure, dumpDir);
  }

  /** Vrai sans jeton utilisable. */
  public boolean anonymous() {
    return token == null || token.isBlank();
  }
}
