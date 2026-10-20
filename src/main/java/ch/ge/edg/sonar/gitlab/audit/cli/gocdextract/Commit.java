package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import ch.ge.edg.sonar.gitlab.audit.dora.Times;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Un commit qu'un run a embarqué, et le chemin par lequel il est arrivé.
 *
 * @param via « direct » pour un matériau git du run lui-même, sinon le run amont, « build/12 »
 * @param keys les clés Jira citées dans son message, dans l'ordre
 */
public record Commit(
    String via, String repo, String revision, @Nullable Instant at, Set<String> keys) {

  public Commit {
    keys = Collections.unmodifiableSet(new LinkedHashSet<>(keys));
  }

  static final String[] HEADER = {
    "pipeline", "compteur", "via", "depot", "revision", "commit_le", "cles_jira"
  };

  String[] cells(String pipeline, int counter, ZoneId zone) {
    return new String[] {
      pipeline,
      String.valueOf(counter),
      via,
      repo,
      revision,
      Times.format(at, zone),
      String.join("|", keys)
    };
  }
}
