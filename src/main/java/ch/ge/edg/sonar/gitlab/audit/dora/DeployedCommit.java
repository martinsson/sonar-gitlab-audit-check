package ch.ge.edg.sonar.gitlab.audit.dora;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Un commit qu'un run GoCD a embarqué, et le chemin par lequel il est arrivé.
 *
 * @param via « direct » pour un matériau git du run lui-même, sinon le run amont, « build/12 »
 * @param keys les clés Jira citées dans son message, dans l'ordre
 */
public record DeployedCommit(
    String via, String repo, String revision, @Nullable Instant at, Set<String> keys) {

  public DeployedCommit {
    keys = Collections.unmodifiableSet(new LinkedHashSet<>(keys));
  }
}
